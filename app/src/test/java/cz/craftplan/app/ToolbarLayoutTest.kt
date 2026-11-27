package cz.craftplan.app

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarLayoutTest {
    @Test
    fun defaultMatchesTheExistingTwoRows() {
        assertEquals(
            "settings,undo,redo,delete,label-lock|fit,notes,deselect,dimension-move",
            ToolbarLayout.Default.toPreference(),
        )
        assertEquals(ToolbarLayout.Default, ToolbarLayout.fromPreference(null))
    }

    @Test
    fun sameRowOrderAndCrossRowMovesOfSettingsSurvivePreferenceRoundTrip() {
        val moved = ToolbarLayout.Default
            .move(ToolbarIcon.DELETE, ToolbarDrop(ToolbarRow.MAIN, ToolbarIcon.UNDO))
            .move(ToolbarIcon.SETTINGS, ToolbarDrop(ToolbarRow.EXTRA, ToolbarIcon.NOTES))
            .move(ToolbarIcon.DIMENSION_MOVE, ToolbarDrop(ToolbarRow.EXTRA, ToolbarIcon.FIT))
        assertEquals(
            "delete,undo,redo,label-lock|dimension-move,fit,settings,notes,deselect",
            moved.toPreference(),
        )
        assertEquals(moved, ToolbarLayout.fromPreference(moved.toPreference()))
        assertEquals(moved, moved.move(ToolbarIcon.REDO, ToolbarDrop(ToolbarRow.EXTRA, ToolbarIcon.UNDO)))
        assertEquals(moved, moved.move(ToolbarIcon.REDO, ToolbarDrop(ToolbarRow.MAIN, ToolbarIcon.REDO)))
    }

    @Test
    fun ignoresUnknownAndDuplicateIdsAndAddsNewIconsInTheirDefaultRow() {
        val loaded = ToolbarLayout.fromPreference("notes,future,notes,undo|undo,settings")
        assertEquals("notes,undo,redo,delete,label-lock|settings,fit,deselect,dimension-move", loaded.toPreference())
        assertEquals(ToolbarLayout.Default, ToolbarLayout.fromPreference("partial"))
    }

    @Test
    fun allIconsCanMoveOffOneRowAndReturnToIt() {
        val default = ToolbarLayout.Default
        val oneRow = default.icons(ToolbarRow.MAIN).fold(default) { order, icon ->
            order.move(icon, ToolbarDrop(ToolbarRow.EXTRA))
        }
        assertTrue(oneRow.icons(ToolbarRow.MAIN).isEmpty())
        assertEquals(oneRow, ToolbarLayout.fromPreference(oneRow.toPreference()))
        val movedBack = oneRow.move(ToolbarIcon.SETTINGS, ToolbarDrop(ToolbarRow.MAIN))
        assertEquals(listOf(ToolbarIcon.SETTINGS), movedBack.icons(ToolbarRow.MAIN))
        assertEquals(ToolbarIcon.entries.size, ToolbarRow.entries.sumOf { movedBack.icons(it).size })
    }

    @Test
    fun dropUsesTheDestinationRowAndSlotCentersIncludingOffscreenSlots() {
        val row = Rect(100f, 0f, 220f, 50f)
        val rows = mapOf(ToolbarRow.EXTRA to row)
        val slots = mapOf(
            ToolbarIcon.FIT to Rect(65f, 0f, 115f, 50f),
            ToolbarIcon.NOTES to Rect(115f, 0f, 165f, 50f),
            ToolbarIcon.DESELECT to Rect(165f, 0f, 215f, 50f),
            ToolbarIcon.DIMENSION_MOVE to Rect(215f, 0f, 265f, 50f),
        )
        fun preview(x: Float, y: Float) =
            toolbarDropPreview(ToolbarLayout.Default, ToolbarIcon.SETTINGS, Offset(x, y), rows, slots, false)

        assertNull(preview(60f, 25f))
        assertNull(preview(160f, 51f))
        assertEquals(ToolbarDrop(ToolbarRow.EXTRA, ToolbarIcon.NOTES), preview(100f, 25f)?.drop)
        assertEquals(ToolbarDrop(ToolbarRow.EXTRA, ToolbarIcon.DIMENSION_MOVE), preview(218f, 25f)?.drop)
        assertNull(toolbarDropPreview(ToolbarLayout.Default, ToolbarIcon.SETTINGS, Offset(160f, 25f), rows, emptyMap(), false))
    }

    @Test
    fun dropOnEmptyRowAndRightToLeftOrder() {
        val row = Rect(0f, 0f, 100f, 50f)
        val default = ToolbarLayout.Default
        val empty = default.icons(ToolbarRow.EXTRA).fold(default) { order, icon ->
            order.move(icon, ToolbarDrop(ToolbarRow.MAIN))
        }
        val rows = mapOf(ToolbarRow.EXTRA to row)
        assertEquals(
            ToolbarDropPreview(ToolbarDrop(ToolbarRow.EXTRA), 100f, row),
            toolbarDropPreview(empty, ToolbarIcon.SETTINGS, Offset(50f, 25f), rows, emptyMap(), true),
        )
        val two = empty.move(ToolbarIcon.SETTINGS, ToolbarDrop(ToolbarRow.EXTRA))
            .move(ToolbarIcon.FIT, ToolbarDrop(ToolbarRow.EXTRA))
        val slots = mapOf(ToolbarIcon.SETTINGS to Rect(50f, 0f, 100f, 50f), ToolbarIcon.FIT to Rect(0f, 0f, 50f, 50f))
        assertEquals(
            ToolbarDrop(ToolbarRow.EXTRA, ToolbarIcon.FIT),
            toolbarDropPreview(two, ToolbarIcon.REDO, Offset(48f, 25f), rows, slots, true)?.drop,
        )
    }

    @Test
    fun platformCanceledReleaseCannotCommitADrop() {
        assertTrue(toolbarNativeCanceled(MotionEvent.ACTION_CANCEL, 0))
        assertTrue(toolbarNativeCanceled(MotionEvent.ACTION_UP, MotionEvent.FLAG_CANCELED))
        assertFalse(toolbarNativeCanceled(MotionEvent.ACTION_UP, 0))
    }

    @Test
    fun holdYieldsToClickDragScrollAndMultipleFingersBeforeTimeout() {
        val start = Offset(30f, 20f)
        fun pointer(id: Long = 1L, position: Offset = start, pressed: Boolean = true) =
            PointerInputChange(PointerId(id), 10L, position, pressed, 1f, 0L, start, id == 1L, false)
        fun canWait(vararg changes: PointerInputChange) =
            canWaitForToolbarHold(PointerEvent(changes.toList()), PointerId(1L), start, IntSize(100, 50), 8f)
        assertTrue(canWait(pointer()))
        assertTrue(canWait(pointer(position = Offset(35f, 24f))))
        assertFalse(canWait(pointer(pressed = false)))
        assertFalse(canWait(pointer(position = Offset(38f, 20f))))
        assertFalse(canWait(pointer(position = Offset(30f, 12f))))
        assertFalse(canWait(pointer().apply { consume() }))
        assertFalse(canWait(pointer(), pointer(id = 2L)))
        val up = pointer(pressed = false)
        assertEquals(up, singleToolbarPointer(PointerEvent(listOf(up)), PointerId(1L)))
        up.consume()
        assertNull(singleToolbarPointer(PointerEvent(listOf(up)), PointerId(1L)))
        assertNull(singleToolbarPointer(PointerEvent(listOf(pointer(), pointer(id = 2L))), PointerId(1L)))
        assertFalse(toolbarDragPastSlop(start, start, 8f))
        assertTrue(toolbarDragPastSlop(start, Offset(30f, 28f), 8f))
    }
}
