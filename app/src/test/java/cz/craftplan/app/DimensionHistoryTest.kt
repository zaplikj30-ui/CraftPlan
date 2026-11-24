package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DimensionHistoryTest {
    @Test
    fun undoAndRedoEachCompletedChangeAndPreserveTheOtherDimension() {
        val a = dimension(1)
        val b = dimension(2).withManualLength("1/3 in")
        val created = DimensionScene().prepareSingle().create(a).prepareSingle().create(b)
        val edit = DimensionValueDraft(b).selectUnit(LengthUnit.UM).withNote("Přeměřit").confirm(b)!!
        val measured = created.edit(b, edit)
        val movedEnd = edit.move(DimensionHit.START, 0.15f, 0f)
        val endpoint = measured.edit(edit, movedEnd)
        val movedBody = movedEnd.move(DimensionHit.BODY, 0f, 0.1f)
        val body = endpoint.edit(movedEnd, movedBody)
        val shifted = movedBody.shiftLabel(EditorPoint(50f, 0f), EditorPoint(10f, 0f), false)
        val label = body.edit(movedBody, shifted)
        val deleted = label.deleteSelected()
        assertEquals(listOf(a), deleted.dimensions)
        val snapshots = listOf(
            DimensionScene(), DimensionScene().prepareSingle().create(a), created, measured, endpoint, body, label, deleted,
        )
        var history = snapshots.zipWithNext().fold(DimensionHistory()) { log, (before, after) ->
            log.record(before, after)
        }
        var scene = deleted
        for (expected in snapshots.dropLast(1).asReversed()) {
            val (previous, log) = history.undo(scene)!!
            assertEquals(expected.dimensions, previous.dimensions)
            scene = previous
            history = log
        }
        assertFalse(history.canUndo)
        for (expected in snapshots.drop(1)) {
            val (next, log) = history.redo(scene)!!
            assertEquals(expected.dimensions, next.dimensions)
            scene = next
            history = log
        }
        assertFalse(history.canRedo)
        assertSame(a, scene.find(a.id))
    }

    @Test
    fun canceledOrUnchangedActionsKeepRedoAndNewEditDiscardsIt() {
        val a = dimension(1)
        val base = DimensionScene().prepareSingle().create(a)
        val edited = base.edit(a, a.copy(note = "První"))
        val history = DimensionHistory().record(base, edited)
        val (rewound, rewoundHistory) = history.undo(edited)!!
        assertSame(rewoundHistory, rewoundHistory.record(rewound, rewound.select(null).toggleRepeat()))
        assertSame(rewoundHistory, rewoundHistory.record(rewound, rewound.edit(a, a)))
        val next = rewound.edit(a, a.copy(note = "Druhá"))
        assertNull(rewoundHistory.record(rewound, next).redo(next))
        val unselected = rewound.select(null)
        assertSame(unselected, unselected.deleteSelected())
    }

    @Test
    fun historyKeepsOnlyTheMostRecentHundredChanges() {
        val original = dimension(1)
        var scene = DimensionScene().prepareSingle().create(original)
        var history = DimensionHistory()
        repeat(110) { index ->
            val current = scene.find(original.id)!!
            val next = scene.edit(current, current.copy(note = "Změna ${index + 1}"))
            history = history.record(scene, next)
            scene = next
        }
        repeat(100) {
            val (previous, log) = history.undo(scene)!!
            scene = previous
            history = log
        }
        assertEquals("Změna 10", scene.find(original.id)?.note)
        assertFalse(history.canUndo)
    }

    private fun dimension(number: Long) = PhotoDimension(
        UUID(0, number), PhotoId.SYNTHETIC_WORKBENCH, PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f),
    )
}
