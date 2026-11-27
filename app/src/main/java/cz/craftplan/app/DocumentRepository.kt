package cz.craftplan.app

import java.io.IOException
import java.util.UUID

internal interface DocumentTransactions {
    suspend fun <T> read(block: suspend DocumentDao.() -> T): T
    suspend fun <T> write(block: suspend DocumentDao.() -> T): T
}

internal data class DocumentVersion(val storageGeneration: String, val revisionId: String?)

internal data class OpenDocument(
    val version: DocumentVersion,
    val snapshot: DocumentSnapshot,
    val photoBytes: ByteArray,
    val recovered: Boolean = false,
    val recoveredFromMedia: Boolean = false,
)

internal data class DocumentSave(
    val base: DocumentVersion,
    val editGeneration: Long,
    val snapshot: DocumentSnapshot,
    val operationId: String = UUID.randomUUID().toString(),
    val targetId: String = UUID.randomUUID().toString(),
)

internal data class DocumentAck(val operationId: String, val editGeneration: Long, val version: DocumentVersion)

internal class DocumentConflict : IOException("The confirmed document changed")

/** This attempt stopped in deterministic snapshot validation, before accessing storage. */
internal class DocumentSnapshotRejected(cause: StorageFailure) : IOException("Snapshot rejected before write", cause)

internal class UncertainDocumentSave(cause: Exception) : IOException("Save must be verified before retry", cause)

internal class DocumentRepository(
    private val transactions: DocumentTransactions,
    private val photos: PrivatePhotoStore,
    private val syncStorage: () -> Unit,
) {
    private val photo = photos.photo
    private val documentId = photo.id.documentId

    fun verifyPublishedMedia() { photos.readAndSync() }

    suspend fun open(): OpenDocument {
        val exists = transactions.read { checkedHead(documentId, allowRecovery = true) != null }
        if (!exists) transactions.write {
            if (checkedHead(documentId) == null) {
                insertHead(DocumentHead(documentId, UUID.randomUUID().toString(), null, null))
            }
        }
        return transactions.read {
            val head = checkNotNull(checkedHead(documentId, allowRecovery = true))
            val latest = head.revisionId
            if (latest == null) {
                OpenDocument(head.version(), DocumentSnapshot(emptyList(), true, photo), photos.readBundled())
            } else {
                openConfirmedRevision(head, latest, photo.id, expectedPhoto = photo) { photos.readAndSync() }
            }
        }
    }

    suspend fun save(request: DocumentSave): DocumentAck {
        val proposed = try {
            storagePhase(StoragePhase.SNAPSHOT) {
                require(request.editGeneration > 0 && isUuid(request.operationId) && isUuid(request.targetId))
                require(request.targetId != request.base.revisionId)
                require(request.snapshot.photo == photo)
                val bytes = SnapshotCodec.encode(request.snapshot)
                require(SnapshotCodec.decode(bytes) == request.snapshot)
                DocumentRevision(
                    request.targetId, documentId, request.base.storageGeneration,
                    request.base.revisionId, request.operationId, request.editGeneration, sha256(bytes), bytes,
                )
            }
        } catch (error: StorageFailure) {
            // Encoding and decoding in this block operate solely on an in-memory buffer.
            val cause = error.cause
            if (cause is IllegalArgumentException || cause is IOException) throw DocumentSnapshotRejected(error)
            throw error
        }

        // Resolve a possible previous commit before preparing or writing anything.
        val committed = verifyOutcome(StoragePhase.REVISION_CHECK) { transactions.read { inspect(proposed) } }
        if (!committed) {
            verifyOutcome(StoragePhase.REVISION_WRITE, preparingPhoto = true) {
                transactions.write {
                    if (!inspect(proposed)) {
                        if (request.base.revisionId == null) photos.prepareForNewRevision() else photos.readAndSync()
                        insertRevision(proposed)
                        check(publish(proposed.documentId, proposed.storageGeneration,
                            proposed.previousRevisionId, proposed.revisionId) == 1)
                    }
                }
            }
        }

        return verifyOutcome(StoragePhase.REVISION_CONFIRM) {
            if (committed) photos.readAndSync()
            storagePhase(StoragePhase.DB_DIRECTORY_SYNC) { syncStorage() }
            check(transactions.read { inspect(proposed) })
            photos.readConfirmed()
            DocumentAck(request.operationId, request.editGeneration,
                DocumentVersion(request.base.storageGeneration, request.targetId))
        }
    }

    private suspend fun <T> verifyOutcome(
        phase: StoragePhase,
        preparingPhoto: Boolean = false,
        block: suspend () -> T,
    ): T = try {
        storagePhase(phase) { block() }
    } catch (error: StorageFailure) {
        // In the writer block, all photo barriers precede the first revision mutation.
        // A known committed operation must keep uncertain status if re-verification fails.
        if (preparingPhoto && error.phase.photoPreparation) throw error
        throw UncertainDocumentSave(error)
    }

    private suspend fun DocumentDao.inspect(proposed: DocumentRevision): Boolean {
        val head = checkedHead(documentId) ?: throw DocumentConflict()
        if (head.storageGeneration != proposed.storageGeneration) throw DocumentConflict()
        val known = operation(proposed.saveOperationId)
        if (known != null) {
            if (!matches(known, proposed) || head.revisionId != known.revisionId ||
                head.previousRevisionId != known.previousRevisionId
            ) throw DocumentConflict()
            return true
        }
        if (head.revisionId != proposed.previousRevisionId || revision(proposed.revisionId) != null) {
            throw DocumentConflict()
        }
        head.revisionId?.let { id ->
            val previous = revision(id)
            if (validatedSnapshot(previous, head, id, photo.id, expectedPhoto = photo) == null ||
                previous?.previousRevisionId != head.previousRevisionId
            ) throw IOException("Current revision failed integrity validation")
        }
        return false
    }

    private fun matches(known: DocumentRevision, proposed: DocumentRevision): Boolean =
        known.revisionId == proposed.revisionId && known.documentId == proposed.documentId &&
            known.storageGeneration == proposed.storageGeneration && known.previousRevisionId == proposed.previousRevisionId &&
            known.saveOperationId == proposed.saveOperationId && known.editGeneration == proposed.editGeneration &&
            known.snapshotDigest == proposed.snapshotDigest && known.snapshot.contentEquals(proposed.snapshot)

    companion object {
        /** Reopen an already published import without consulting its staging catalog. */
        suspend fun openConfirmed(
            transactions: DocumentTransactions,
            photoId: PhotoId,
            repository: (StoredPhoto) -> DocumentRepository,
        ): OpenDocument = openConfirmedMedia(transactions, photoId) { repository(it).photos.readAndSync() }

        suspend fun openConfirmedMedia(
            transactions: DocumentTransactions,
            photoId: PhotoId,
            readMedia: (StoredPhoto) -> ByteArray,
        ): OpenDocument = transactions.read {
            val head = checkedHead(photoId.documentId, allowRecovery = true)
                ?: throw IOException("Missing document head")
            val latest = head.revisionId ?: throw IOException("Document has no confirmed revision")
            openConfirmedRevision(head, latest, photoId, readMedia = readMedia)
        }
    }
}

private suspend fun DocumentDao.openConfirmedRevision(
    head: DocumentHead,
    latest: String,
    photoId: PhotoId,
    expectedPhoto: StoredPhoto? = null,
    readMedia: (StoredPhoto) -> ByteArray,
): OpenDocument {
    var failedPhoto: StoredPhoto? = null
    var mediaFailure: StorageFailure? = null

    fun verifiedMedia(snapshot: DocumentSnapshot): ByteArray? {
        if (snapshot.photo == failedPhoto) return null
        return try {
            readMedia(snapshot.photo)
        } catch (error: StorageFailure) {
            val cause = error.cause
            if (error.phase != StoragePhase.PHOTO_VERIFY ||
                (cause !is MissingConfirmedPhoto && cause !is IllegalArgumentException)
            ) throw error
            failedPhoto = snapshot.photo
            mediaFailure = error
            null
        }
    }

    val current = if (isUuid(latest) && latest != head.previousRevisionId) revision(latest) else null
    val snapshot = validatedSnapshot(current, head, latest, photoId, expectedPhoto)
        ?.takeIf { current?.previousRevisionId == head.previousRevisionId }
    if (snapshot != null) {
        verifiedMedia(snapshot)?.let { return OpenDocument(head.version(), snapshot, it) }
    }

    val visited = hashSetOf(latest)
    var candidate = head.previousRevisionId
    while (candidate != null && visited.add(candidate)) {
        val older = revision(candidate) ?: break
        if (!belongsTo(older, head, candidate)) break
        val recovery = validatedSnapshot(older, head, candidate, photoId, expectedPhoto)
        if (recovery != null) {
            val bytes = verifiedMedia(recovery)
            if (bytes != null) return OpenDocument(
                DocumentVersion(head.storageGeneration, candidate), recovery, bytes, recovered = true,
            )
        }
        candidate = older.previousRevisionId
    }
    throw (mediaFailure ?: IOException("No valid confirmed revision can be opened"))
}

/** Called under the writer transaction. Incomplete revision evidence always protects the source. */
internal suspend fun DocumentDao.isProtectedRecoveryMedia(
    id: PhotoId, digest: String, verifyMedia: (StoredPhoto) -> Unit,
): Boolean {
    val head = checkedHead(id.documentId) ?: return false
    val total = revisionCount(id.documentId)
    var revisionId = head.revisionId
    var visited = 0L
    val verified = mutableSetOf<StoredPhoto>()
    while (revisionId != null) {
        if (visited >= total) return true
        val row = revision(revisionId) ?: return true
        val snapshot = validatedSnapshot(row, head, revisionId, id, expectedPhoto = null) ?: return true
        if (visited == 0L && row.previousRevisionId != head.previousRevisionId) return true
        if (snapshot.photo.sha256 == digest) return true
        if (verified.add(snapshot.photo)) verifyMedia(snapshot.photo)
        visited++
        revisionId = row.previousRevisionId
    }
    return visited != total
}

private suspend fun DocumentDao.checkedHead(documentId: String, allowRecovery: Boolean = false): DocumentHead? {
    val head = head(documentId)
    if (head?.revisionId == null && hasRevisions(documentId)) {
        throw IOException("Missing revision pointer")
    }
    if (head != null && (head.documentId != documentId || !isUuid(head.storageGeneration) ||
        (head.revisionId == null && head.previousRevisionId != null) ||
        (!allowRecovery && head.revisionId != null &&
            (!isUuid(head.revisionId) || head.revisionId == head.previousRevisionId)) ||
        (head.previousRevisionId != null && !isUuid(head.previousRevisionId))
    )) throw IOException("Invalid revision pointer")
    return head
}

private fun DocumentHead.version() = DocumentVersion(storageGeneration, revisionId)

private fun belongsTo(row: DocumentRevision, head: DocumentHead, revisionId: String): Boolean =
    row.revisionId == revisionId && row.documentId == head.documentId &&
        row.storageGeneration == head.storageGeneration

private fun validatedSnapshot(
    row: DocumentRevision?, head: DocumentHead, revisionId: String, photoId: PhotoId,
    expectedPhoto: StoredPhoto?,
): DocumentSnapshot? {
    if (row == null || !belongsTo(row, head, revisionId) ||
        !isUuid(row.saveOperationId) || row.editGeneration <= 0 ||
        (row.previousRevisionId != null && (!isUuid(row.previousRevisionId) || row.previousRevisionId == revisionId)) ||
        sha256(row.snapshot) != row.snapshotDigest
    ) return null
    return try {
        SnapshotCodec.decode(row.snapshot).takeIf {
            it.photo.id == photoId && (expectedPhoto == null || it.photo == expectedPhoto)
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }
}

private fun isUuid(value: String): Boolean =
    value.length == 36 && try {
        UUID.fromString(value).toString() == value
    } catch (_: IllegalArgumentException) {
        false
    }
