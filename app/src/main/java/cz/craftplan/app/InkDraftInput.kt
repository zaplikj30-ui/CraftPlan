package cz.craftplan.app

import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
        val stylus = down.type == PointerType.Stylus
        // The dialog's Initial-pass owner already claimed this continuation.
        val actions = callbacks()
        if (stylus && down.isConsumed) {
            actions.diagnostics?.penGesture(PenDiagnosticStage.FIELD_ALREADY_OWNED,
                actions.field, eventMs = down.uptimeMillis, consumed = true)
            return@awaitEachGesture
        }
        val pending = actions.pending()
        if (stylus) down.consume()
        val ticket = if (enabled && stylus) actions.begin() else null
        if (enabled && stylus && ticket == null && pending) actions.blocked()
        if (stylus) actions.diagnostics?.penGesture(PenDiagnosticStage.FIELD_DOWN, actions.field,
            eventMs = down.uptimeMillis, pending = pending, enabled = enabled, ticket = ticket != null)
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
                    actions.diagnostics?.penGesture(PenDiagnosticStage.FIELD_RELEASE, actions.field,
                        eventMs = change.uptimeMillis, pending = pending, enabled = enabled, ticket = ticket != null,
                        kind = kind, durationMs = duration, displacementDp = maximum / density,
                        pathDp = travel / density, rawAction = raw.actionMasked)
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
            if (stylus && !delivered) actions.diagnostics?.penGesture(PenDiagnosticStage.FIELD_ABORT,
                actions.field, eventMs = down.uptimeMillis, pending = pending, enabled = enabled,
                ticket = ticket != null, displacementDp = maximum / density, pathDp = travel / density)
        }
    }
}

// 32 dp is approximately 5 mm at nominal Android density, not a physical calibration.
internal const val INK_START_MARGIN_DP = 32f

internal data class DialogInkStart(val field: DimensionField, val continuing: Boolean)

/** Actual field hits keep their existing cursor/hold/ink handler. A new contact
 * in the margin belongs to the nearest visible field (active field breaks ties).
 * Once a batch exists, its captured field wins everywhere in the dialog. */
internal fun dialogInkStart(
    position: Offset,
    pending: DimensionField?,
    preferred: DimensionField,
    valueBounds: Rect?,
    noteBounds: Rect?,
    marginPx: Float,
    visibleKeypadBounds: Rect? = null,
): DialogInkStart? {
    if (!position.x.isFinite() || !position.y.isFinite()) return null
    if (pending != null) return DialogInkStart(pending, continuing = true)
    // A new batch must never claim a key. Use the current clipped keyboard's
    // top edge across the dialog, not a fixed offset from the note field.
    if (visibleKeypadBounds != null && visibleKeypadBounds.width > 0f &&
        visibleKeypadBounds.height > 0f && position.y >= visibleKeypadBounds.top) return null
    val fields = listOf(
        DimensionField.VALUE to valueBounds,
        DimensionField.NOTE to noteBounds,
    ).mapNotNull { (field, bounds) ->
        bounds?.takeIf { it.width > 0f && it.height > 0f }?.let { field to it }
    }
    if (fields.any { (_, bounds) -> bounds.contains(position) }) return null
    val nearest = fields.map { (field, bounds) ->
        val dx = maxOf(bounds.left - position.x, 0f, position.x - bounds.right)
        val dy = maxOf(bounds.top - position.y, 0f, position.y - bounds.bottom)
        field to (dx * dx + dy * dy)
    }.filter { (_, squared) -> squared <= marginPx * marginPx }
        .minWithOrNull(compareBy<Pair<DimensionField, Float>> { it.second }
            .thenBy { if (it.first == preferred) 0 else 1 })
    return nearest?.let { DialogInkStart(it.first, continuing = false) }
}

internal data class DialogInkContact(
    val continuing: Boolean,
    val enabled: Boolean,
    val actions: InkFieldCallbacks,
)

/** Ancestor of every dialog control. Active-batch stylus DOWN is consumed in
 * Initial before children, for the entire contact, even if the batch is full.
 * Near-field starts claim only actual writing movement, not ordinary button
 * taps/holds. The handler is stable across recognition and recomposition. */
internal fun Modifier.inkDialogInput(
    diagnostics: EditorDiagnostics?,
    route: (Offset) -> DialogInkContact?,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.type != PointerType.Stylus || down.isConsumed) return@awaitEachGesture
        diagnostics?.penGesture(PenDiagnosticStage.ROOT_DOWN, eventMs = down.uptimeMillis)
        val routed = route(down.position)
        diagnostics?.penGesture(PenDiagnosticStage.ROOT_ROUTE, routed?.actions?.field,
            eventMs = down.uptimeMillis, enabled = routed?.enabled, continuing = routed?.continuing)
        if (routed == null) return@awaitEachGesture
        val actions = routed.actions
        var owned = routed.continuing
        var ticket: Long? = null
        var delivered = false
        var rejected = false
        val origin = down.position
        var last = origin
        var maximum = 0f
        var travel = 0f
        val points = PenStrokeBuffer(PenPoint(origin.x, origin.y, down.uptimeMillis))
        try {
            if (owned) {
                down.consume()
                ticket = actions.begin()
                if (ticket == null) actions.blocked()
            }
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (owned) change.consume()
                val raw = event.motionEvent
                if (raw == null || raw.actionMasked == MotionEvent.ACTION_CANCEL ||
                    raw.flags and MotionEvent.FLAG_CANCELED != 0) break
                if (event.changes.size != 1) {
                    // A rejected continuation cannot fall through to a control.
                    if (!owned) break
                    ticket?.let(actions.cancel)
                    ticket = null
                    rejected = true
                }
                if (rejected) {
                    if (!change.pressed) break
                    continue
                }
                val position = change.position
                if (!position.x.isFinite() || !position.y.isFinite()) break
                maximum = maxOf(maximum, (position - origin).getDistance())
                travel += (position - last).getDistance()
                last = position
                points.append(PenPoint(position.x, position.y, change.uptimeMillis))
                // A near-field stroke, once claimed, cannot turn into a hold.
                val kind = classifyPenContact(owned,
                    change.uptimeMillis - down.uptimeMillis, maximum / density, travel / density)
                if (!owned && kind == PenContactKind.INK) {
                    owned = true
                    change.consume()
                    if (routed.enabled) {
                        ticket = actions.begin()
                        if (ticket == null) actions.blocked()
                    }
                }
                if (!change.pressed) {
                    diagnostics?.penGesture(PenDiagnosticStage.ROOT_RELEASE, actions.field,
                        eventMs = change.uptimeMillis, enabled = routed.enabled, ticket = ticket != null,
                        kind = kind, durationMs = change.uptimeMillis - down.uptimeMillis,
                        displacementDp = maximum / density, pathDp = travel / density,
                        rawAction = raw.actionMasked, consumed = owned, continuing = routed.continuing)
                    if (!owned || !canFinishPenStroke(raw.actionMasked, false, event.changes.size)) break
                    val activeTicket = ticket
                    if (kind == PenContactKind.INK) {
                        if (activeTicket != null) {
                            actions.finish(activeTicket, points.snapshot())
                            delivered = true
                        } else if (!routed.enabled) actions.missingModel()
                    } else if (kind == PenContactKind.WAIT) {
                        actions.blocked()
                    }
                    break
                }
                val activeTicket = ticket
                if (owned && activeTicket != null && kind == PenContactKind.INK)
                    actions.preview(activeTicket, points.snapshot())
            }
        } finally {
            if (!delivered) ticket?.let(actions.cancel)
            if (owned && !delivered) diagnostics?.penGesture(PenDiagnosticStage.ROOT_ABORT,
                actions.field, eventMs = down.uptimeMillis, ticket = ticket != null,
                displacementDp = maximum / density, pathDp = travel / density,
                continuing = routed.continuing)
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
    val diagnostics: EditorDiagnostics? = null,
    val field: DimensionField? = null,
)
