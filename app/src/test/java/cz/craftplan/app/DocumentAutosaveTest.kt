package cz.craftplan.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun DocumentSave.ack() =
        DocumentAck(operationId, editGeneration, base.copy(revisionId = targetId))
}
