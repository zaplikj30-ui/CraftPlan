package cz.craftplan.app

import android.graphics.Bitmap
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException

internal data class ImportPreview(
    val candidate: ImportCandidate, val photo: StoredPhoto, val bitmap: Bitmap, val sourceDamaged: Boolean,
)
internal data class PhotoList(val confirmed: List<PhotoId>, val pending: List<ImportCandidate>)

internal class PhotoImports(
    private val directory: File,
    private val syncDirectory: (File) -> Unit,
    private val staging: ImportStaging,
    private val transactions: DocumentTransactions,
    private val catalog: PhotoImportDao,
    private val repository: (StoredPhoto, () -> ByteArray) -> DocumentRepository,
) {
    suspend fun list(): PhotoList {
        val confirmed = transactions.read { catalog.committedIds().map(::PhotoId) }
        val committed = confirmed.toHashSet()
        return PhotoList(confirmed, staging.candidates().filterNot { it.photoId in committed })
    }

    fun receive(input: InputStream): ImportCandidate = storagePhase(StoragePhase.IMPORT_STAGE) {
        staging.receive(input)
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
            if (!verified.recovered) removeRedundantStage(candidate)
        }
    }

    suspend fun open(id: PhotoId): OpenDocument {
        val photo = transactions.read {
            check(head(id.documentId)?.revisionId != null)
            requireNotNull(catalogPhoto(id))
        }
        return existingRepository(photo).open().also { verified ->
            if (!verified.recovered) removeRedundantStage(ImportCandidate(photo.id, photo.sha256))
        }
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
