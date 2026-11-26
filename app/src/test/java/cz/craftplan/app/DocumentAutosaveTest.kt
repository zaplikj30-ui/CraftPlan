package cz.craftplan.app

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAutosaveTest {
    private val snapshot = DocumentSnapshot(emptyList(), true)
    private val version = DocumentVersion("storage", "r0")

    @Test
    fun startingAndCancelingAnEditDoesNotEnqueueAnything() {
        val session = DocumentAutosave(version)
        session.beginEdit()
        assertFalse(session.hasPendingChanges)
        assertNull(session.nextRequest())
        assertEquals(version, session.confirmedVersion)
    }

    @Test
    fun lateAckAdvancesThePredecessorWithoutConfirmingNewerContent() {
        val session = DocumentAutosave(version)
        session.finishedEdit(snapshot)
        val first = checkNotNull(session.nextRequest())
        session.beginEdit()
        val newer = snapshot.copy(showNotes = false)
        session.finishedEdit(newer)
        assertSame(first, session.nextRequest())
        val firstAck = first.ack()
        assertTrue(session.acknowledge(firstAck))
        assertTrue(session.hasPendingChanges)
        val next = checkNotNull(session.nextRequest())
        assertEquals(firstAck.version, next.base)
        assertEquals(newer, next.snapshot)
        assertFalse(session.acknowledge(firstAck))
        assertFalse(session.acknowledge(next.ack().copy(version = version)))
        assertTrue(session.hasPendingChanges)
        assertTrue(session.acknowledge(next.ack()))
        assertFalse(session.hasPendingChanges)
    }

    @Test
    fun aDefinitivelyRejectedRequestWaitsForACompletedCorrectionAndCannotAcknowledgeIt() {
        val session = DocumentAutosave(version)
        session.finishedEdit(snapshot)
        val rejected = checkNotNull(session.nextRequest())
        assertFalse(session.recordFailure(rejected, snapshotRejected()))
        assertFalse(session.acknowledge(rejected.ack()))
        session.beginEdit()
        assertFalse(session.hasCorrectedSnapshot)
        assertSame(rejected, session.nextRequest()) // Retrying without a correction keeps the request.
        assertFalse(session.acknowledge(rejected.ack()))
        assertFalse(session.recordFailure(rejected, snapshotRejected()))

        val corrected = snapshot.copy(showNotes = false)
        session.finishedEdit(corrected)
        assertTrue(session.hasCorrectedSnapshot)
        val replacement = checkNotNull(session.nextRequest())
        assertEquals(version, replacement.base)
        assertEquals(corrected, replacement.snapshot)
        assertNotEquals(rejected.operationId, replacement.operationId)
        assertNotEquals(rejected.targetId, replacement.targetId)
        assertFalse(session.recordFailure(rejected, snapshotRejected()))
        assertFalse(session.acknowledge(rejected.ack()))
        assertTrue(session.hasPendingChanges)
        assertTrue(session.acknowledge(replacement.ack()))
        assertFalse(session.hasPendingChanges)
    }

    @Test
    fun uncertaintyConflictAndOtherErrorsKeepIdentityEvenIfALaterAttemptFailsPreflight() {
        val failures = listOf(
            UncertainDocumentSave(IOException("Injected loss of ACK")) to DocumentSaveStatus.UNCERTAIN,
            DocumentConflict() to DocumentSaveStatus.CONFLICT,
            StorageFailure(StoragePhase.PHOTO_STAGE_SYNC, IOException("Injected sync failure")) to DocumentSaveStatus.FAILED,
        )
        for ((error, status) in failures) {
            val session = DocumentAutosave(version)
            session.finishedEdit(snapshot)
            val first = checkNotNull(session.nextRequest())
            session.finishedEdit(snapshot.copy(showNotes = false))
            assertFalse(session.recordFailure(first, error))
            assertSame(first, session.nextRequest())
            assertFalse(session.recordFailure(first, snapshotRejected()))
            assertEquals(status, session.failureStatus)
            assertSame(first, session.nextRequest())
            assertEquals(version, session.confirmedVersion)
            assertTrue(session.hasPendingChanges)
        }
    }

    private fun snapshotRejected() =
        DocumentSnapshotRejected(StorageFailure(StoragePhase.SNAPSHOT, IllegalArgumentException()))

    private fun DocumentSave.ack() =
        DocumentAck(operationId, editGeneration, base.copy(revisionId = targetId))
}
