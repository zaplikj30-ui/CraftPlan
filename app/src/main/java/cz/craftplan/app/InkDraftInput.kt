package cz.craftplan.app

import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

internal fun canFinishPenStroke(action: Int?, canceled: Boolean, pointerCount: Int): Boolean =
    action == MotionEvent.ACTION_UP && !canceled && pointerCount == 1

/**
 * Stylus down is owned immediately so the native text toolbar cannot race ink.
 * Finger selection remains native. A stationary stylus release is classified
 * separately as a tap or intentional long hold; an active writing batch
 * also accepts small contacts such as dots.
 */
internal fun Modifier.inkFieldInput(
    enabled: Boolean,
    editable: Boolean,
    callbacks: () -> InkFieldCallbacks,
): Modifier = pointerInput(enabled, editable) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x !in 0f..size.width.toFloat() || down.position.y !in 0f..size.height.toFloat()) return@awaitEachGesture
        val actions = callbacks()
        val stylus = down.type == PointerType.Stylus
        val pending = actions.pending()
        if (stylus) down.consume()
        val ticket = if (enabled && stylus) actions.begin() else null
        if (enabled && stylus && ticket == null && pending) actions.blocked()
        val origin = down.position
        var last = origin
        var maximum = 0f
        var travel = 0f
        val points = PenStrokeBuffer(PenPoint(origin.x, origin.y, down.uptimeMillis))
        var delivered = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val raw = event.motionEvent
                if (raw == null || raw.actionMasked == MotionEvent.ACTION_CANCEL ||
                    raw.flags and MotionEvent.FLAG_CANCELED != 0) break
                if (event.changes.size != 1) {
                    // Preserve the previously completed batch and consume the rest
                    // of this rejected stylus contact, so native IME cannot take over.
                    if (ticket != null) actions.cancel(ticket)
                    delivered = true
                    if (stylus) {
                        change.consume()
                        while (change.pressed) {
                            val rejected = awaitPointerEvent(PointerEventPass.Initial)
                            val next = rejected.changes.firstOrNull { it.id == down.id } ?: break
                            next.consume()
                            if (!next.pressed) break
                        }
                    }
                    break
                }
                val position = change.position
                if (!position.x.isFinite() || !position.y.isFinite()) break
                maximum = maxOf(maximum, (position - origin).getDistance())
                travel += (position - last).getDistance()
                last = position
                val duration = change.uptimeMillis - down.uptimeMillis
                if (!stylus) {
                    // Native finger selection remains available; a stationary
                    // release also lets the dialog open the intended field.
                    if (!change.pressed && maximum <= viewConfiguration.touchSlop) {
                        if (duration >= viewConfiguration.longPressTimeoutMillis)
                            callbacks().fingerHold(origin)
                        else callbacks().tap()
                    }
                    if (!change.pressed || maximum > viewConfiguration.touchSlop) break
                    continue
                }
                change.consume()
                if (ticket != null) points.append(PenPoint(position.x, position.y, change.uptimeMillis))
                val kind = classifyPenContact(pending, duration,
                    maximum / density, travel / density)
                if (!change.pressed) {
                    if (!canFinishPenStroke(raw.actionMasked, false, event.changes.size)) break
                    when (kind) {
                        PenContactKind.INK -> if (ticket != null) {
                            // A single point is a legitimate dot, not a canceled stroke.
                            actions.finish(ticket, points.snapshot())
                            delivered = true
                        } else if (!enabled) actions.missingModel() else actions.blocked()
                        PenContactKind.HOLD -> {
                            if (ticket != null) actions.cancel(ticket)
                            delivered = true
                            actions.penHold(origin)
                        }
                        PenContactKind.TAP -> {
                            if (ticket != null) actions.cancel(ticket)
                            delivered = true
                            actions.penTap(origin, change.uptimeMillis)
                        }
                        PenContactKind.WAIT -> {
                            if (ticket != null) actions.cancel(ticket)
                            delivered = true
                            actions.blocked()
                        }
                    }
                    break
                }
                if (ticket != null && kind == PenContactKind.INK) actions.preview(ticket, points.snapshot())
            }
        } finally {
            if (!delivered && ticket != null) actions.cancel(ticket)
        }
    }
}

internal const val PEN_HOLD_MS = 800L

internal data class InkFieldCallbacks(
    val begin: () -> Long?,
    val preview: (Long, List<PenPoint>) -> Unit,
    val finish: (Long, List<PenPoint>) -> Unit,
    val cancel: (Long) -> Unit,
    val tap: () -> Unit,
    val penTap: (Offset, Long) -> Unit = { _, _ -> },
    val penHold: (Offset) -> Unit = {},
    val fingerHold: (Offset) -> Unit = {},
    val pending: () -> Boolean,
    val blocked: () -> Unit,
    val missingModel: () -> Unit,
)
