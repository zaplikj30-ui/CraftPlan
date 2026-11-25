package cz.craftplan.app

import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class InkDraftTarget(
    val draftId: UUID,
    val field: DimensionField,
    val language: String,
    val input: TextFieldValue,
)

/** A retained, bounded batch of handwriting. Contacts and recognition jobs
 * have separate identities. Cancellation of one contact never discards the
 * completed batch; only successful insertion or explicit discard clears it. */
internal class InkDraftController(
    private val scope: CoroutineScope,
    private val recognize: suspend (String, List<List<PenPoint>>) -> String? = InkModels::recognize,
    private val settleDelayMs: Long = 650,
) {
    private data class Binding(
        val owner: Any,
        val current: (DimensionField) -> InkDraftTarget?,
        val apply: (DimensionField, TextFieldValue) -> Unit,
        val failed: () -> Unit,
    )
    private var binding: Binding? = null
    private var work: Job? = null
    private var revision = 0L
    private var target: InkDraftTarget? = null
    private var completedStrokes = emptyList<List<PenPoint>>()
    private var contact: Long? = null
    private var candidate: String? = null
    var preview by mutableStateOf<List<List<PenPoint>>>(emptyList())
        private set
    var failed by mutableStateOf(false)
        private set
    var hasPendingInput by mutableStateOf(false)
        private set
    var pendingField by mutableStateOf<DimensionField?>(null)
        private set

    val pendingLanguage: String? get() = target?.language

    fun pendingInput(field: DimensionField): TextFieldValue? =
        target?.takeIf { it.field == field }?.input

    fun attach(
        owner: Any,
        current: (DimensionField) -> InkDraftTarget?,
        apply: (DimensionField, TextFieldValue) -> Unit,
        failed: () -> Unit,
    ) {
        binding = Binding(owner, current, apply, failed)
        deliver()
    }

    fun detach(owner: Any) {
        if (binding?.owner === owner) binding = null
    }

    fun start(next: InkDraftTarget): Long? {
        // Do not replace another target, an incomplete contact or a full batch.
        // The UI will offer waiting/retry instead of discarding old handwriting.
        if (contact != null || (target != null && target != next) ||
            completedStrokes.size >= MAX_STROKES) return null
        revision++
        work?.cancel()
        work = null
        candidate = null
        target = next
        pendingField = next.field
        failed = false
        preview = completedStrokes
        hasPendingInput = true
        contact = revision
        return revision
    }

    fun previewStroke(ticket: Long, points: List<PenPoint>) {
        if (contact == ticket) preview = completedStrokes + listOf(points)
    }

    fun finish(ticket: Long, points: List<PenPoint>) {
        if (contact != ticket) return
        contact = null
        if (points.isNotEmpty()) completedStrokes = completedStrokes + listOf(points)
        preview = completedStrokes
        if (completedStrokes.isEmpty()) clearInk() else schedule()
    }

    // Only the unfinished contact is canceled (palm/system cancellation or a tap).
    fun cancel(ticket: Long) {
        if (contact != ticket) return
        contact = null
        preview = completedStrokes
        if (completedStrokes.isEmpty()) clearInk() else schedule()
    }

    fun retry() {
        if (contact == null && completedStrokes.isNotEmpty()) {
            candidate = null
            schedule()
        }
    }

    private fun schedule() {
        val captured = target ?: return
        val request = completedStrokes
        val ticket = ++revision
        failed = false
        work?.cancel()
        work = scope.launch {
            delay(settleDelayMs)
            val result = try {
                withTimeoutOrNull(5_000) { recognize(captured.language, request) }
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
                null
            } catch (_: Exception) {
                null
            } catch (_: LinkageError) {
                null
            }
            if (ticket != revision || contact != null) return@launch
            work = null
            candidate = result
            if (result.isNullOrBlank()) markFailed() else deliver()
        }
    }

    private fun markFailed() {
        failed = true
        binding?.failed?.invoke()
    }

    private fun deliver() {
        val captured = target ?: return
        val result = candidate ?: return
        val active = binding ?: return // Rotation: retain the candidate for the new UI.
        if (contact != null) return
        val current = active.current(captured.field)
        if (current != captured) {
            markFailed()
            return
        }
        val next = insertInkResult(captured.field, captured.input, result)
        if (next == null) {
            markFailed()
            return
        }
        clearInk()
        active.apply(captured.field, next)
    }

    /** Explicit cancellation of the entire draft or user-approved discard only. */
    fun interrupt() {
        revision++
        work?.cancel()
        clearInk()
    }

    private fun clearInk() {
        work = null
        target = null
        pendingField = null
        contact = null
        candidate = null
        completedStrokes = emptyList()
        preview = emptyList()
        failed = false
        hasPendingInput = false
    }

    fun previewFor(field: DimensionField): List<List<PenPoint>> =
        if (pendingField == field) preview else emptyList()

    companion object {
        internal const val MAX_STROKES = 128
    }
}

internal enum class PenContactKind { TAP, HOLD, INK }

/** Stationary holds tolerate jitter; tiny marks in an active batch are ink. */
internal fun classifyPenContact(
    pending: Boolean, durationMs: Long, displacementDp: Float, pathDp: Float,
): PenContactKind = when {
    displacementDp > 8f || pathDp > 16f -> PenContactKind.INK
    pending -> PenContactKind.INK
    durationMs >= PEN_HOLD_MS -> PenContactKind.HOLD
    displacementDp >= 1.5f || pathDp >= 3f -> PenContactKind.INK
    else -> PenContactKind.TAP
}

internal fun insertInkResult(field: DimensionField, value: TextFieldValue, candidate: String): TextFieldValue? {
    if (candidate.isBlank() || candidate.any { it == '\r' || it == '\n' || it == '\u0000' }) return null
    val start = value.selection.min
    val end = value.selection.max
    val limit = when (field) {
        DimensionField.VALUE -> ManualLengthMeasurement.MAX_INPUT_LENGTH
        DimensionField.NOTE -> MAX_DIMENSION_NOTE_LENGTH
    }
    // Notes separate words; values only separate a number from a following word.
    // Never insert spaces between digits, into decimals/fractions or selections.
    val append = start == end && end == value.text.length
    val previous = value.text.lastOrNull()
    val first = candidate.first()
    val separateWords = append && previous?.isLetterOrDigit() == true && first.isLetterOrDigit() &&
        (if (field == DimensionField.NOTE) previous.isLetter() || first.isLetter()
        else previous.isDigit() && first.isLetter() && first !in "xX")
    val insertion = if (separateWords) " $candidate" else candidate
    if (insertion.length > limit - (value.text.length - (end - start))) return null
    val text = value.text.replaceRange(start, end, insertion)
    return TextFieldValue(text, TextRange(start + insertion.length))
}

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
                    raw.flags and MotionEvent.FLAG_CANCELED != 0 || event.changes.size != 1) break
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
