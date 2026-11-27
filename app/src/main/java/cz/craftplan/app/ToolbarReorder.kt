package cz.craftplan.app

import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize

internal data class ToolbarDropPreview(val drop: ToolbarDrop, val markerX: Float, val rowBounds: Rect)

internal fun toolbarDropPreview(
    layout: ToolbarLayout, icon: ToolbarIcon, position: Offset,
    rows: Map<ToolbarRow, Rect>, slots: Map<ToolbarIcon, Rect>, rtl: Boolean,
): ToolbarDropPreview? {
    val (row, bounds) = rows.entries.firstOrNull { position in it.value } ?: return null
    val targets = layout.icons(row).filter { it != icon }
    // Until placement has reported a newly opened row, there is no reliable insertion position.
    if (targets.any { it !in slots }) return null
    val before = targets.firstOrNull {
        val center = slots.getValue(it).center.x
        if (rtl) position.x > center else position.x < center
    }
    val reference = before?.let(slots::getValue) ?: targets.lastOrNull()?.let(slots::getValue)
    val marker = when {
        reference == null -> if (rtl) bounds.right else bounds.left
        (before != null) != rtl -> reference.left
        else -> reference.right
    }
    return ToolbarDropPreview(ToolbarDrop(row, before), marker.coerceIn(bounds.left, bounds.right), bounds)
}

internal fun toolbarDragPastSlop(start: Offset, position: Offset, slop: Float): Boolean =
    (position - start).getDistanceSquared() >= slop * slop

internal class ToolbarReorder {
    var coordinates: LayoutCoordinates? = null
    val rows = mutableStateMapOf<ToolbarRow, Rect>()
    val slots = mutableStateMapOf<ToolbarIcon, Rect>()
    var icon by mutableStateOf<ToolbarIcon?>(null)
        private set
    var position by mutableStateOf<Offset?>(null)
        private set

    fun begin(source: ToolbarIcon) {
        icon = source
    }

    fun move(pointer: Offset) {
        position = pointer
    }

    fun clear() {
        position = null
        icon = null
    }

    fun preview(layout: ToolbarLayout, rtl: Boolean): ToolbarDropPreview? {
        val source = icon ?: return null
        val pointer = position ?: return null
        return toolbarDropPreview(layout, source, pointer, rows, slots, rtl)
    }
}

internal fun LayoutCoordinates.toolbarBounds(): Rect {
    val origin = positionInRoot()
    return Rect(origin, Size(size.width.toFloat(), size.height.toFloat()))
}

/**
 * Observe a potential hold without consuming it so clicks, scrolling and grip drags work as usual.
 * Once the hold wins, consume at Initial before any descendant can click or scroll.
 */
internal fun Modifier.reorderToolbar(
    state: PhotoEditorState, reorder: ToolbarReorder, rtl: Boolean, haptic: () -> Unit,
): Modifier = pointerInput(state, reorder, rtl, haptic) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        val coordinates = reorder.coordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
        val origin = coordinates.localToRoot(down.position)
        val row = reorder.rows.entries.firstOrNull { origin in it.value }?.key ?: return@awaitEachGesture
        val source = state.toolbarLayout.icons(row).firstOrNull {
            reorder.slots[it]?.contains(origin) == true
        } ?: return@awaitEachGesture
        val slop = viewConfiguration.touchSlop

        val interrupted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                // Skip Final of DOWN itself, which a normal clickable is allowed to consume.
                awaitPointerEvent(PointerEventPass.Initial)
                val final = awaitPointerEvent(PointerEventPass.Final)
                if (!canWaitForToolbarHold(final, down.id, down.position, size, slop)) break
            }
            true
        }
        if (interrupted == true) return@awaitEachGesture

        reorder.begin(source)
        try {
            haptic()
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = singleToolbarPointer(event, down.id)
                event.changes.forEach { it.consume() }
                if (change == null) break
                val current = reorder.coordinates?.takeIf { it.isAttached } ?: break
                val pointer = current.localToRoot(change.position)
                moved = moved || toolbarDragPastSlop(origin, pointer, slop)
                if (moved) reorder.move(pointer)
                if (!change.pressed) {
                    reorder.preview(state.toolbarLayout, rtl)?.let { state.moveToolbarIcon(source, it.drop) }
                    break
                }
            }
        } finally {
            reorder.clear()
        }
    }
}

internal fun canWaitForToolbarHold(
    event: PointerEvent,
    pointerId: PointerId,
    original: Offset,
    size: IntSize,
    slop: Float,
): Boolean {
    val change = singleToolbarPointer(event, pointerId) ?: return false
    return change.pressed && change.position.x in 0f..size.width.toFloat() && change.position.y in 0f..size.height.toFloat() &&
        !toolbarDragPastSlop(original, change.position, slop)
}

internal fun toolbarNativeCanceled(action: Int?, flags: Int): Boolean =
    action == MotionEvent.ACTION_CANCEL || flags and MotionEvent.FLAG_CANCELED != 0

@OptIn(ExperimentalComposeUiApi::class)
internal fun singleToolbarPointer(event: PointerEvent, pointerId: PointerId): PointerInputChange? {
    val raw = event.motionEvent
    if (toolbarNativeCanceled(raw?.actionMasked, raw?.flags ?: 0)) return null
    val pointer = event.changes.firstOrNull { it.id == pointerId } ?: return null
    return pointer.takeUnless {
        it.isConsumed || event.changes.any { other -> other.id != pointerId && other.pressed }
    }
}
