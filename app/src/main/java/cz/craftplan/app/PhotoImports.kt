package cz.craftplan.app

import android.graphics.Bitmap
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.NoSuchFileException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class PhotoConfirmation {
    AUTOMATIC, EXPLICIT, EXPLICIT_DAMAGED;

    fun permits(sourceDamaged: Boolean): Boolean = sourceDamaged == (this == EXPLICIT_DAMAGED)
}

internal enum class CameraResultMode {
    CRAFTPLAN_PREVIEW, DIRECT_AFTER_SUCCESS;

    fun confirmsAutomatically(resultSucceeded: Boolean, sourceDamaged: Boolean): Boolean =
        this == DIRECT_AFTER_SUCCESS && resultSucceeded && PhotoConfirmation.AUTOMATIC.permits(sourceDamaged)
}

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

internal sealed interface PhotoRecoveryCase {
    val id: PhotoId
    data class Camera(override val id: PhotoId) : PhotoRecoveryCase
    data class Import(val candidate: ImportCandidate) : PhotoRecoveryCase {
        override val id get() = candidate.photoId
    }
    data class Saved(override val id: PhotoId) : PhotoRecoveryCase
}

internal fun PhotoList.recoveryCases(active: PhotoId?, failed: PhotoId?): List<PhotoRecoveryCase> = buildList {
    captures.filterNot { it == active }.forEach { add(PhotoRecoveryCase.Camera(it)) }
    pending.forEach { add(PhotoRecoveryCase.Import(it)) }
    (listOfNotNull(failed) + confirmedWithRecoverySources).distinct().filterNot { it == active }
        .forEach { add(PhotoRecoveryCase.Saved(it)) }
}

internal data class RecoveryImage<T>(val image: T, val sourceDamaged: Boolean)

internal data class PhotoRecoveryPreview<T>(
    val image: T, val discardCandidate: ImportCandidate?, val sourceDamaged: Boolean,
)

internal class PhotoImports(
    private val directory: File,
    private val syncDirectory: (File) -> Unit,
    private val staging: ImportStaging,
    private val transactions: DocumentTransactions,
    private val catalog: PhotoImportDao,
    private val repository: (StoredPhoto, () -> ByteArray) -> DocumentRepository,
) {
    private val captureLock = Mutex()
    private val recoveryPreviewLock = Mutex()
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
            transactions.write {
                if (head(id.documentId) == null && !hasRevisions(id.documentId) && !catalog.exists(id.name) &&
                    staging.candidates().none { it.photoId == id } && staging.rejectedCapture(id) == null
                ) captures.discardEmpty(id)
            }
        }
    }

    suspend fun stageCapture(id: PhotoId): ImportCandidate = captureLock.withLock {
        storagePhase(StoragePhase.CAMERA_CAPTURE) {
            transactions.read { check(head(id.documentId)?.revisionId == null && !hasRevisions(id.documentId)) }
            check(staging.rejectedCapture(id) == null)
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

    suspend fun resumeCaptureRejections(active: PhotoId?, releaseOutput: (PhotoId) -> Unit) {
        for (id in staging.rejectedCaptureIds()) {
            if (id == active) continue
            inspectCameraRecovery {
                currentCoroutineContext().ensureActive()
                releaseOutput(id)
                captureLock.withLock {
                    staging.rejectedCapture(id)?.let { finishCaptureRejection(it) }
                }
            }
        }
    }

    /** No bitmap or source bytes are prepared until a single case is selected. */
    suspend fun <T : Any> inspectRecovery(
        case: PhotoRecoveryCase, releaseOutput: (PhotoId) -> Unit, decode: (CaptureCopy) -> RecoveryImage<T>,
    ): PhotoRecoveryPreview<T>? = recoveryPreviewLock.withLock {
        val sources = when (case) {
            is PhotoRecoveryCase.Import -> listOf(CaptureSource.Stage(case.candidate))
            else -> {
                currentCoroutineContext().ensureActive()
                releaseOutput(case.id)
                captureSources(case.id)
            }
        }
        for (source in sources) {
            val result = inspectCameraRecovery {
                val copy = readCaptureCopy(source)
                val candidate = ImportCandidate(case.id, copy.candidate.digest)
                val canDiscard = case is PhotoRecoveryCase.Camera && inspectCameraRecovery {
                    canRejectRecoveryCapture(candidate)
                } == true
                currentCoroutineContext().ensureActive()
                val decoded = decode(copy)
                PhotoRecoveryPreview(decoded.image, candidate.takeIf { canDiscard }, decoded.sourceDamaged)
            }
            if (result != null) return@withLock result
        }
        null
    }

    private suspend fun canRejectRecoveryCapture(candidate: ImportCandidate): Boolean = captureLock.withLock {
        val id = candidate.photoId
        if (staging.rejectedCapture(id) != null ||
            transactions.read { head(id.documentId) != null || hasRevisions(id.documentId) || catalog.exists(id.name) }
        ) return@withLock false
        val staged = captureCandidate(id)
        // A raw-only attempt may still have an external writer. Open and stage it before offering discard.
        if (staged != candidate) return@withLock false
        staging.read(staged)
        if (captures.hasOutput(id)) checkCaptureBytes(candidate)
        true
    }

    private suspend fun <T> inspectCameraRecovery(action: suspend () -> T): T? = try {
        action()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Leave unverified or conflicted sources on disk and do not describe them as a verified image.
        null
    }

    suspend fun discardInterruptedCapture(candidate: ImportCandidate) {
        val actual = stageCapture(candidate.photoId)
        check(actual == candidate)
        rejectCapture(actual)
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

    suspend fun rejectCapture(candidate: ImportCandidate) = captureLock.withLock {
        finishCaptureRejection(candidate)
    }

    private suspend fun finishCaptureRejection(candidate: ImportCandidate) {
        val id = candidate.photoId
        transactions.write {
            if (head(id.documentId) != null || hasRevisions(id.documentId) || catalog.exists(id.name)) {
                throw ImportValidationFailure(ImportRejection.CAMERA_SOURCE_PROTECTED)
            }
            val recorded = staging.rejectedCapture(id)
            val current = captureCandidate(id)
            check(if (recorded == null) current == candidate else recorded == candidate && (current == null || current == candidate))
            if (current != null) staging.read(current)
            if (captures.hasOutput(id)) checkCaptureBytes(candidate)

            // Record the user's decision durably before removing either source. The writer
            // also excludes a concurrent first publication until the decision is visible.
            staging.recordCaptureRejection(candidate)
            captures.removeApprovedOutput(id, candidate.digest)
            staging.remove(candidate)
            staging.clearCaptureRejection(id)
        }
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

    suspend fun hasConfirmationEvidence(id: PhotoId): Boolean = transactions.read {
        head(id.documentId) != null || hasRevisions(id.documentId) || catalog.exists(id.name)
    }

    suspend fun preview(candidate: ImportCandidate): ImportPreview = storagePhase(StoragePhase.IMPORT_PREVIEW) {
        val bytes = staging.read(candidate)
        // Earlier versions could write the catalog before finishing the first revision.
        val existing = transactions.read { catalogPhoto(candidate.photoId) }
        previewJpeg(candidate, bytes, existing)
    }

    suspend fun confirmPreview(
        preview: ImportPreview, confirmation: PhotoConfirmation, copySource: CaptureSource? = null,
    ): OpenDocument {
        require(confirmation.permits(preview.sourceDamaged))
        if (copySource != null) {
            require(confirmation != PhotoConfirmation.AUTOMATIC)
            stageCaptureCopy(copySource, preview.candidate)
        }
        return confirm(preview.candidate, preview.photo)
    }

    suspend fun confirm(candidate: ImportCandidate, photo: StoredPhoto): OpenDocument {
        require(photo.id == candidate.photoId && photo.sha256 == candidate.digest)
        val metadata = SnapshotCodec.encode(DocumentSnapshot(emptyList(), true, photo))
        transactions.write {
            check(staging.rejectedCapture(candidate.photoId) == null)
            val existing = catalog.find(candidate.photoId.name)
            if (existing == null) {
                check(candidate in staging.candidates())
                catalog.insert(PhotoImportRow(candidate.photoId.name, metadata))
            } else check(existing.metadata.contentEquals(metadata))
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
            if (head(id.documentId) != null || hasRevisions(id.documentId) || catalog.exists(id.name)) {
                throw ImportValidationFailure(ImportRejection.CAMERA_SOURCE_PROTECTED)
            }
            check(!captures.hasOutput(id))
            val current = captureCandidate(id)
            check(current == null || current == candidate)
            if (current != null) staging.read(current)
            staging.remove(candidate)
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
