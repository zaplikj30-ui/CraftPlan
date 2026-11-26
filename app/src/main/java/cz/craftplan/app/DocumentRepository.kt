package cz.craftplan.app

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException

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
internal class UncertainDocumentSave(cause: Exception) : IOException("Save must be verified before retry", cause)

internal class DocumentRepository(
    private val transactions: DocumentTransactions,
    private val photos: PrivatePhotoStore,
    private val syncStorage: () -> Unit,
) {
    suspend fun open(): OpenDocument {
        val exists = transactions.read { checkedHead(allowRecovery = true) != null }
        if (!exists) transactions.write {
            if (checkedHead() == null) {
                insertHead(DocumentHead(SYNTHETIC_DOCUMENT_ID, UUID.randomUUID().toString(), null, null))
            }
        }
        return transactions.read {
            val head = checkNotNull(checkedHead(allowRecovery = true))
            val latest = head.revisionId
            if (latest == null) {
                OpenDocument(head.version(), DocumentSnapshot(emptyList(), true), photos.readBundled())
            } else {
                val current = if (isUuid(latest) && latest != head.previousRevisionId) revision(latest) else null
                val snapshot = validatedSnapshot(current, head, latest)
                    ?.takeIf { current?.previousRevisionId == head.previousRevisionId }
                if (snapshot != null) {
                    OpenDocument(head.version(), snapshot, photos.readAndSync())
                } else {
                    val visited = hashSetOf(latest)
                    var candidate = head.previousRevisionId
                    while (candidate != null && visited.add(candidate)) {
                        val older = revision(candidate) ?: break
                        if (!belongsTo(older, head, candidate)) break
                        val recovery = validatedSnapshot(older, head, candidate)
                        if (recovery != null) return@read OpenDocument(
                            DocumentVersion(head.storageGeneration, candidate), recovery,
                            photos.readAndSync(), recovered = true,
                        )
                        candidate = older.previousRevisionId
                    }
                    throw IOException("No valid confirmed revision can be opened")
                }
            }
        }
    }

    suspend fun save(request: DocumentSave): DocumentAck {
        require(request.editGeneration > 0 && isUuid(request.operationId) && isUuid(request.targetId))
        require(request.targetId != request.base.revisionId)
        val bytes = SnapshotCodec.encode(request.snapshot)
        require(SnapshotCodec.decode(bytes) == request.snapshot)
        val proposed = DocumentRevision(
            request.targetId, SYNTHETIC_DOCUMENT_ID, request.base.storageGeneration,
            request.base.revisionId, request.operationId, request.editGeneration, sha256(bytes), bytes,
        )

        // Resolve a possible previous commit before preparing or writing anything.
        val committed = transactions.read { inspect(proposed) }
        if (!committed && request.base.revisionId == null) photos.prepare() else photos.readAndSync()

        try {
            if (!committed) transactions.write {
                // Recheck inside the serialized writer transaction, including after media preparation.
                if (!inspect(proposed)) {
                    insertRevision(proposed)
                    check(publish(proposed.documentId, proposed.storageGeneration,
                        proposed.previousRevisionId, proposed.revisionId) == 1)
                }
            }
            syncStorage()
            check(transactions.read { inspect(proposed) })
            photos.readConfirmed()
        } catch (error: CancellationException) {
            throw error
        } catch (error: DocumentConflict) {
            throw error
        } catch (error: Exception) {
            throw UncertainDocumentSave(error)
        }
        return DocumentAck(request.operationId, request.editGeneration,
            DocumentVersion(request.base.storageGeneration, request.targetId))
    }

    private suspend fun DocumentDao.inspect(proposed: DocumentRevision): Boolean {
        val head = checkedHead() ?: throw DocumentConflict()
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
            if (validatedSnapshot(previous, head, id) == null ||
                previous?.previousRevisionId != head.previousRevisionId
            ) throw IOException("Current revision failed integrity validation")
        }
        return false
    }

    private suspend fun DocumentDao.checkedHead(allowRecovery: Boolean = false): DocumentHead? {
        val head = head(SYNTHETIC_DOCUMENT_ID)
        if (head?.revisionId == null && hasRevisions(SYNTHETIC_DOCUMENT_ID)) {
            throw IOException("Missing revision pointer")
        }
        if (head != null && (head.documentId != SYNTHETIC_DOCUMENT_ID || !isUuid(head.storageGeneration) ||
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

    private fun validatedSnapshot(row: DocumentRevision?, head: DocumentHead, revisionId: String): DocumentSnapshot? {
        if (row == null || !belongsTo(row, head, revisionId) ||
            !isUuid(row.saveOperationId) || row.editGeneration <= 0 ||
            (row.previousRevisionId != null && (!isUuid(row.previousRevisionId) || row.previousRevisionId == revisionId)) ||
            sha256(row.snapshot) != row.snapshotDigest
        ) return null
        return try {
            SnapshotCodec.decode(row.snapshot)
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun matches(known: DocumentRevision, proposed: DocumentRevision): Boolean =
        known.revisionId == proposed.revisionId && known.documentId == proposed.documentId &&
            known.storageGeneration == proposed.storageGeneration && known.previousRevisionId == proposed.previousRevisionId &&
            known.saveOperationId == proposed.saveOperationId && known.editGeneration == proposed.editGeneration &&
            known.snapshotDigest == proposed.snapshotDigest && known.snapshot.contentEquals(proposed.snapshot)

    private fun isUuid(value: String): Boolean =
        value.length == 36 && try {
            UUID.fromString(value).toString() == value
        } catch (_: IllegalArgumentException) {
            false
        }
}
