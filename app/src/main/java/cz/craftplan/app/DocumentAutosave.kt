package cz.craftplan.app

/** Main-thread session state. Only a snapshot rejected before any possible
 * publication can give way to a newer finished edit without an ACK. */
internal class DocumentAutosave(version: DocumentVersion) {
    var confirmedVersion = version
        private set
    private var editGeneration = 0L
    private var latest: Pair<Long, DocumentSnapshot>? = null
    private var outstanding: DocumentSave? = null
    private var failure = Failure.NONE

    private enum class Failure(val status: DocumentSaveStatus?) {
        NONE(null),
        REJECTED_BEFORE_WRITE(DocumentSaveStatus.FAILED),
        KEEP_OPERATION(DocumentSaveStatus.FAILED),
        UNCERTAIN(DocumentSaveStatus.UNCERTAIN),
        CONFLICT(DocumentSaveStatus.CONFLICT),
    }

    val hasPendingChanges get() = latest != null
    val failureStatus get() = failure.status
    val hasCorrectedSnapshot: Boolean
        get() {
            val request = outstanding ?: return false
            val generation = latest?.first ?: return false
            return failure == Failure.REJECTED_BEFORE_WRITE && generation > request.editGeneration
        }

    fun beginEdit() {
        editGeneration++
    }

    fun finishedEdit(snapshot: DocumentSnapshot) {
        beginEdit()
        latest = editGeneration to snapshot
    }

    fun nextRequest(): DocumentSave? {
        outstanding?.let { if (!hasCorrectedSnapshot) return it }
        val (generation, snapshot) = latest ?: return null
        failure = Failure.NONE
        return DocumentSave(confirmedVersion, generation, snapshot).also { outstanding = it }
    }

    /** Returns whether a newer edit is already ready to replace this request. */
    fun recordFailure(request: DocumentSave, error: Exception): Boolean {
        if (outstanding !== request) return false
        failure = when {
            error is DocumentConflict -> Failure.CONFLICT
            error is UncertainDocumentSave -> Failure.UNCERTAIN
            failure == Failure.UNCERTAIN || failure == Failure.CONFLICT -> failure
            error is DocumentSnapshotRejected && failure != Failure.KEEP_OPERATION -> Failure.REJECTED_BEFORE_WRITE
            else -> Failure.KEEP_OPERATION
        }
        return hasCorrectedSnapshot
    }

    fun acknowledge(ack: DocumentAck): Boolean {
        val request = outstanding ?: return false
        if (failure == Failure.REJECTED_BEFORE_WRITE) return false
        if (ack.operationId != request.operationId || ack.editGeneration != request.editGeneration ||
            ack.version.storageGeneration != request.base.storageGeneration ||
            ack.version.revisionId != request.targetId
        ) return false

        confirmedVersion = ack.version
        outstanding = null
        failure = Failure.NONE
        if (latest?.first == ack.editGeneration) latest = null
        return true
    }
}

internal enum class DocumentSaveStatus { SAVING, SAVED, EDITING, FAILED, UNCERTAIN, CONFLICT, RECOVERED }
