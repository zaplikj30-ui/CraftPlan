package cz.craftplan.app

import android.graphics.Bitmap
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ImportPreview(
    val candidate: ImportCandidate, val photo: StoredPhoto, val bitmap: Bitmap, val sourceDamaged: Boolean,
)
internal data class PhotoList(
    val confirmed: List<PhotoId>, val pending: List<ImportCandidate>, val captures: List<PhotoId> = emptyList(),
    val confirmedWithRecoverySources: List<PhotoId> = emptyList(),
)

internal sealed interface CaptureSource {
    val photoId: PhotoId

    data class Camera(override val photoId: PhotoId) : CaptureSource
    data class Stage(val candidate: ImportCandidate) : CaptureSource {
        override val photoId get() = candidate.photoId
    }
}

internal data class CaptureCopy(val candidate: ImportCandidate, val bytes: ByteArray)

internal class PhotoImports(
    private val directory: File,
    private val syncDirectory: (File) -> Unit,
    private val staging: ImportStaging,
    private val transactions: DocumentTransactions,
    private val catalog: PhotoImportDao,
    private val repository: (StoredPhoto, () -> ByteArray) -> DocumentRepository,
) {
    private val captureLock = Mutex()
    private val captures = CameraCaptureStore(File(directory, CameraCaptureStore.DIRECTORY), syncDirectory)

    suspend fun list(): PhotoList {
        // Listing is lightweight. Verify the reachable revision and media when a photo opens.
        val confirmed = transactions.read { confirmedPhotoIds() }.mapNotNull { id ->
            try {
                PhotoId(id).takeIf { it.documentId == id }
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        val cameraAttempts = captures.attempts()
        val committed = confirmed.toHashSet()
        val staged = staging.candidates()
        val conflictingStages = staged.groupBy { it.photoId }.filterValues { it.size > 1 }.keys
        val pendingCaptures = (cameraAttempts + conflictingStages).distinct().filterNot { it in committed }
        val represented = (confirmed + pendingCaptures).toHashSet()
        val recoveryIds = (cameraAttempts + staged.map { it.photoId }).toHashSet()
        return PhotoList(confirmed, staged.filterNot { it.photoId in represented },
            pendingCaptures, confirmed.filter { it in recoveryIds })
    }

    fun receive(input: InputStream): ImportCandidate = storagePhase(StoragePhase.IMPORT_STAGE) {
        staging.receive(input)
    }

    fun beginCapture(): PhotoId = storagePhase(StoragePhase.CAMERA_CAPTURE) { captures.begin() }

    suspend fun finishCanceledCapture(id: PhotoId) = captureLock.withLock {
        storagePhase(StoragePhase.CAMERA_CAPTURE) {
            // Some camera apps write even when they return CANCELED. Keep any real output for
            // an explicit recovery/discard decision, and never promote it from this callback.
            captures.discardEmpty(id)
        }
    }

    suspend fun stageCapture(id: PhotoId): ImportCandidate = captureLock.withLock {
        storagePhase(StoragePhase.CAMERA_CAPTURE) {
            transactions.read { check(head(id.documentId)?.revisionId == null && !hasRevisions(id.documentId)) }
            val existing = captureCandidate(id)
            if (existing != null) {
                if (captures.hasOutput(id)) checkCaptureBytes(existing)
                existing
            } else captures.readOutput(id) { input ->
                if (input.channel.size() == 0L) throw ImportValidationFailure(ImportRejection.CAMERA_OUTPUT_EMPTY)
                staging.receive(input, id, beforePublish = ::checkCaptureBytes)
            }
        }
    }

    suspend fun captureSources(id: PhotoId): List<CaptureSource> = captureLock.withLock {
        buildList {
            if (captures.hasOutput(id)) add(CaptureSource.Camera(id))
            staging.candidates().filter { it.photoId == id }.forEach { add(CaptureSource.Stage(it)) }
        }
    }

    suspend fun readCaptureCopy(source: CaptureSource): CaptureCopy = captureLock.withLock {
        val bytes = readCaptureSource(source)
        CaptureCopy(copyCandidate(source.photoId, sha256(bytes)), bytes)
    }

    suspend fun previewCaptureCopy(copy: CaptureCopy): ImportPreview = storagePhase(StoragePhase.IMPORT_PREVIEW) {
        previewJpeg(copy.candidate, copy.bytes, transactions.read { catalogPhoto(copy.candidate.photoId) })
    }

    suspend fun stageCaptureCopy(source: CaptureSource, expected: ImportCandidate): Unit = captureLock.withLock {
        require(expected == copyCandidate(source.photoId, expected.digest))
        // The approved bytes may already have survived a failed write or lost acknowledgement.
        if (transactions.read { head(expected.photoId.documentId)?.revisionId != null }) return@withLock
        if (expected in staging.candidates()) {
            staging.read(expected)
        } else {
            val bytes = readCaptureSource(source)
            require(sha256(bytes) == expected.digest)
            check(staging.receive(bytes.inputStream(), expected.photoId) == expected)
        }
    }

    private fun copyCandidate(sourceId: PhotoId, digest: String): ImportCandidate {
        val id = UUID.nameUUIDFromBytes("craftplan-camera-rescue:${sourceId.name}:$digest".toByteArray(Charsets.US_ASCII))
        return ImportCandidate(PhotoId(id.toString()), digest)
    }

    private fun readCaptureSource(source: CaptureSource): ByteArray = when (source) {
        is CaptureSource.Stage -> staging.read(source.candidate)
        is CaptureSource.Camera -> {
            val bytes = captures.readOutput(source.photoId) { it.readNBytes(MAX_PHOTO_BYTES + 1) }
            if (bytes.size > MAX_PHOTO_BYTES) throw ImportValidationFailure(ImportRejection.PHOTO_SIZE_LIMIT)
            checkCaptureBytes(ImportCandidate(source.photoId, sha256(bytes)))
            bytes
        }
    }

    suspend fun discardCaptureSource(source: CaptureSource, digest: String) = captureLock.withLock {
        val id = source.photoId
        val candidate = ImportCandidate(id, digest)
        transactions.write {
            val hasPendingPublication = head(id.documentId)?.revisionId == null && catalog.exists(id.name)
            if (hasPendingPublication || isProtectedRecoveryMedia(id, digest) { existingRepository(it).verifyPublishedMedia() }) {
                throw ImportValidationFailure(ImportRejection.CAMERA_SOURCE_PROTECTED)
            }
            when (source) {
                is CaptureSource.Camera -> captures.removeApprovedOutput(id, digest)
                is CaptureSource.Stage -> {
                    require(candidate == source.candidate)
                    if (candidate in staging.candidates()) staging.read(candidate)
                    staging.remove(candidate)
                }
            }
        }
    }

    suspend fun discardCapture(id: PhotoId) = captureLock.withLock {
        val candidate = captureCandidate(id)
        if (candidate != null) discard(candidate)
        else transactions.read { check(head(id.documentId)?.revisionId == null && !hasRevisions(id.documentId)) }
        captures.remove(id)
    }

    private fun checkCaptureBytes(candidate: ImportCandidate) {
        if (!captures.matches(candidate.photoId, candidate.digest)) {
            throw ImportValidationFailure(ImportRejection.CAMERA_OUTPUT_CHANGED)
        }
    }

    private fun captureCandidate(id: PhotoId): ImportCandidate? {
        val candidates = staging.candidates().filter { it.photoId == id }
        check(candidates.size <= 1)
        return candidates.firstOrNull()
    }

    suspend fun preview(candidate: ImportCandidate): ImportPreview = storagePhase(StoragePhase.IMPORT_PREVIEW) {
        val bytes = staging.read(candidate)
        // Earlier versions could write the catalog before finishing the first revision.
        val existing = transactions.read { catalogPhoto(candidate.photoId) }
        previewJpeg(candidate, bytes, existing)
    }

    suspend fun confirm(candidate: ImportCandidate, photo: StoredPhoto): OpenDocument {
        require(photo.id == candidate.photoId && photo.sha256 == candidate.digest)
        val metadata = SnapshotCodec.encode(DocumentSnapshot(emptyList(), true, photo))
        transactions.write {
            val existing = catalog.find(candidate.photoId.name)
            if (existing == null) catalog.insert(PhotoImportRow(candidate.photoId.name, metadata))
            else check(existing.metadata.contentEquals(metadata))
        }
        val document = repository(photo) { staging.read(candidate) }
        val base = document.open().version
        if (base.revisionId == null) {
            // These IDs already survived staging/restart. Retry never invents another operation.
            document.save(DocumentSave(base, 1, DocumentSnapshot(emptyList(), true, photo),
                operationId = photo.id.name, targetId = photo.mediaId.toString()))
        }
        return document.open().also { verified ->
            check(verified.version.revisionId != null && verified.snapshot.photo == photo)
            finishVerifiedImport(verified)
        }
    }

    suspend fun open(id: PhotoId): OpenDocument =
        DocumentRepository.openConfirmed(transactions, id, ::existingRepository).also { finishVerifiedImport(it) }

    suspend fun recoverConfirmed(id: PhotoId): OpenDocument = captureLock.withLock {
        val recovered = DocumentRepository.openConfirmedMedia(transactions, id, ::readMatchingRecoveryMedia)
        // Match the snapshot and media to the authoritative revision, but keep this session read-only.
        storagePhase(StoragePhase.DB_DIRECTORY_SYNC) { syncDirectory(directory) }
        recovered.copy(recovered = true, recoveredFromMedia = true)
    }

    private fun readMatchingRecoveryMedia(photo: StoredPhoto): ByteArray {
        val expected = ImportCandidate(photo.id, photo.sha256)
        val sources = listOf(CaptureSource.Stage(expected), CaptureSource.Camera(photo.id))
        var failure: Exception = MissingConfirmedPhoto()
        var hardFailure: Exception? = null
        for (source in sources) {
            try {
                val bytes = readCaptureSource(source)
                if (bytes.size == photo.size && sha256(bytes) == photo.sha256) return bytes
                failure = ImportValidationFailure(ImportRejection.STAGED_INTEGRITY)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                when (error) {
                    is FileNotFoundException, is NoSuchFileException, is IllegalArgumentException -> failure = error
                    else -> if (hardFailure == null) hardFailure = error
                }
            }
        }
        hardFailure?.let { throw it }
        // This revision cannot be shown with these bytes. Let the normal validator try its predecessor.
        throw StorageFailure(StoragePhase.PHOTO_VERIFY, MissingConfirmedPhoto().apply { initCause(failure) })
    }

    private suspend fun finishVerifiedImport(verified: OpenDocument) {
        if (verified.recovered) return
        // A visible head can be the result of an earlier save with a lost directory ACK.
        // Re-establish this barrier before acknowledging it or removing either private source.
        storagePhase(StoragePhase.DB_DIRECTORY_SYNC) { syncDirectory(directory) }
        val photo = verified.snapshot.photo
        removeRedundantStage(ImportCandidate(photo.id, photo.sha256))
        removeRedundantCapture(photo)
    }

    suspend fun save(request: DocumentSave): DocumentAck =
        existingRepository(request.snapshot.photo).save(request)

    suspend fun discard(candidate: ImportCandidate) {
        val id = candidate.photoId
        transactions.write {
            check(head(id.documentId)?.revisionId == null && !hasRevisions(id.documentId))
            val photo = catalogPhoto(id)
            if (photo != null) {
                check(photo.sha256 == candidate.digest)
                Files.deleteIfExists(File(directory, photo.mediaFileName()).toPath())
            }
            staging.remove(candidate)
            syncDirectory(directory)
            catalog.delete(id.name)
            catalog.deleteEmptyHead(id.documentId)
        }
    }

    private suspend fun catalogPhoto(id: PhotoId): StoredPhoto? =
        catalog.find(id.name)?.let {
            val snapshot = SnapshotCodec.decode(it.metadata)
            check(snapshot.photo.id == id && snapshot.dimensions.isEmpty())
            snapshot.photo
        }

    private fun existingRepository(photo: StoredPhoto): DocumentRepository =
        repository(photo) { error("Confirmed photos are reopened from their private final file") }

    private suspend fun removeRedundantCapture(photo: StoredPhoto) = captureLock.withLock {
        try {
            captures.removeIfMatching(photo.id, photo.sha256)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Keep the output if cleanup is uncertain, and retry when reopening the verified photo.
        }
    }

    private fun removeRedundantStage(candidate: ImportCandidate) {
        try {
            staging.remove(candidate)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A confirmed photo remains usable. The redundant stage can be removed on next open.
        }
    }
}
