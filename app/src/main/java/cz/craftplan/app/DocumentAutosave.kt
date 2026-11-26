package cz.craftplan.app

/** Main-thread session state. A retry retains the exact pending operation even
 * when more finished edits arrive while the writer is busy or awaiting retry. */
internal class DocumentAutosave(version: DocumentVersion) {
    var confirmedVersion = version
        private set
    private var editGeneration = 0L
    private var latest: Pair<Long, DocumentSnapshot>? = null
    private var outstanding: DocumentSave? = null

    val hasPendingChanges get() = latest != null

    fun beginEdit() {
        editGeneration++
    }

    fun finishedEdit(snapshot: DocumentSnapshot) {
        beginEdit()
        latest = editGeneration to snapshot
    }

    fun nextRequest(): DocumentSave? {
        outstanding?.let { return it }
        val (generation, snapshot) = latest ?: return null
        return DocumentSave(confirmedVersion, generation, snapshot).also { outstanding = it }
    }

    fun acknowledge(ack: DocumentAck): Boolean {
        val request = outstanding ?: return false
        if (ack.operationId != request.operationId || ack.editGeneration != request.editGeneration ||
            ack.version.storageGeneration != request.base.storageGeneration ||
            ack.version.revisionId != request.targetId
        ) return false

        confirmedVersion = ack.version
        outstanding = null
        if (latest?.first == ack.editGeneration) latest = null
        return true
    }
}

internal enum class DocumentSaveStatus { SAVING, SAVED, EDITING, FAILED, UNCERTAIN, CONFLICT, RECOVERED }
