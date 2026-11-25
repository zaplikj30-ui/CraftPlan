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

internal class InkDraftController(
    private val scope: CoroutineScope,
    private val recognize: suspend (String, List<List<PenPoint>>) -> String? = InkModels::recognize,
    private val settleDelayMs: Long = 650,
) {
    private var work: Job? = null
    private var revision = 0L
    private var target: InkDraftTarget? = null
    private var completedStrokes = emptyList<List<PenPoint>>()
    var preview by mutableStateOf<List<List<PenPoint>>>(emptyList())
        private set

    val hasPendingInput: Boolean get() = preview.isNotEmpty() || completedStrokes.isNotEmpty()

    fun start(next: InkDraftTarget): Long {
        val previousStrokes = completedStrokes.takeIf { target == next && it.size < 24 }.orEmpty()
        interrupt()
        target = next
        completedStrokes = previousStrokes
        return revision
    }

    fun previewStroke(ticket: Long, points: List<PenPoint>) {
        if (revision == ticket) preview = completedStrokes + listOf(points)
    }

    fun finish(
        ticket: Long,
        points: List<PenPoint>,
        current: () -> InkDraftTarget?,
        apply: (TextFieldValue) -> Unit,
        failed: () -> Unit,
    ) {
        val captured = target?.takeIf { ticket == revision } ?: return
        completedStrokes = completedStrokes + listOf(points)
        preview = completedStrokes
        val request = completedStrokes
        work = scope.launch {
            try {
                delay(settleDelayMs) // Natural pauses between strokes belong to the same recognition.
                val candidate = try {
                    withTimeoutOrNull(5_000) { recognize(captured.language, request) }
                } catch (_: CancellationException) {
                    // A canceled SDK task can fail without cancellation of this coroutine.
                    currentCoroutineContext().ensureActive()
                    null
                } catch (_: Exception) {
                    null
                } catch (_: LinkageError) {
                    null
                }
                if (ticket == revision && captured == current()) {
                    val next = candidate?.let { insertInkResult(captured.field, captured.input, it) }
                    clearInk()
                    if (next != null) apply(next) else failed()
                }
            } finally {
                if (ticket == revision) clearInk()
            }
        }
    }

    fun cancel(ticket: Long) {
        if (ticket == revision) interrupt()
    }

    fun interrupt() {
        revision++
        work?.cancel()
        clearInk()
    }

    private fun clearInk() {
        work = null
        target = null
        completedStrokes = emptyList()
        preview = emptyList()
    }

    fun previewFor(field: DimensionField): List<List<PenPoint>> {
        val strokes = preview
        return if (target?.field == field) strokes else emptyList()
    }
}

internal fun insertInkResult(field: DimensionField, value: TextFieldValue, candidate: String): TextFieldValue? {
    if (candidate.isBlank() || candidate.any { it == '\r' || it == '\n' || it == '\u0000' }) return null
    val start = value.selection.min
    val end = value.selection.max
    val limit = when (field) {
        DimensionField.VALUE -> ManualLengthMeasurement.MAX_INPUT_LENGTH
        DimensionField.NOTE -> MAX_DIMENSION_NOTE_LENGTH
    }
    // Separate independently recognized words only when appending to a note.
    // Do not change selected text, punctuation, existing whitespace or exact numeric input.
    val separateWords = field == DimensionField.NOTE && start == end && end == value.text.length &&
        value.text.lastOrNull()?.isLetterOrDigit() == true && candidate.first().isLetterOrDigit() &&
        (value.text.last().isLetter() || candidate.first().isLetter())
    val insertion = if (separateWords) " $candidate" else candidate
    if (insertion.length > limit - (value.text.length - (end - start))) return null
    val text = value.text.replaceRange(start, end, insertion)
    return TextFieldValue(text, TextRange(start + insertion.length))
}

internal fun canFinishPenStroke(action: Int?, canceled: Boolean, pointerCount: Int): Boolean =
    action == MotionEvent.ACTION_UP && !canceled && pointerCount == 1

/**
 * Hover never produces a press. For actual presses, the child field still gets regular taps.
 * Only motion beyond pen slop is consumed, leaving cursor placement to BasicTextField.
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
        val ticket = if (enabled && down.type == PointerType.Stylus) actions.begin() else null
        if (ticket == null) actions.otherContact()
        val pen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
        val missingModel = !enabled && down.type == PointerType.Stylus
        val ownsPenMotion = ticket != null || missingModel || (pen && editable)
        val origin = down.position
        // Claim in-field pen writing before the editable Compose field can start IME handwriting.
        val slop = if (ownsPenMotion) minOf(3.dp.toPx(), viewConfiguration.handwritingSlop)
            else viewConfiguration.touchSlop
        val points = mutableListOf(PenPoint(origin.x, origin.y, down.uptimeMillis))
        var moved = false
        var delivered = false
        var rejectedMultitouch = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val raw = event.motionEvent
                val canceled = raw == null || raw.actionMasked == MotionEvent.ACTION_CANCEL ||
                    raw.flags and MotionEvent.FLAG_CANCELED != 0
                if (canceled) break
                if (event.changes.size != 1 && !rejectedMultitouch) {
                    rejectedMultitouch = true
                    if (ticket != null) actions.cancel(ticket)
                }
                if (rejectedMultitouch) {
                    // Do not let subsequent movement of the rejected pen reach Compose's IME recognizer.
                    if (ownsPenMotion) change.consume()
                    if (!change.pressed || !ownsPenMotion) break
                    continue
                }
                val position = change.position
                if (!position.x.isFinite() || !position.y.isFinite()) break
                val distance = (position - origin).getDistance()
                val contactTime = change.uptimeMillis - down.uptimeMillis
                if (!moved && contactTime >= viewConfiguration.longPressTimeoutMillis) break
                if (distance > slop) moved = true
                if ((ticket != null || missingModel) && (position.x !in 0f..size.width.toFloat() ||
                        position.y !in 0f..size.height.toFloat())
                ) {
                    if (moved) change.consume()
                    break
                }
                if (!change.pressed) {
                    val validUp = canFinishPenStroke(raw.actionMasked, false, event.changes.size)
                    if (validUp && moved && ticket != null && points.size >= 2) {
                        change.consume()
                        actions.finish(ticket, points.toList())
                        delivered = true
                    } else if (validUp && moved && missingModel) {
                        change.consume()
                        actions.missingModel()
                    } else if (validUp && !moved && !editable) {
                        actions.tap()
                    } else if (moved && ownsPenMotion) {
                        change.consume()
                    }
                    break
                }
                if (moved && !ownsPenMotion) {
                    break
                }
                if (ticket != null && points.size < 256) {
                    val previous = points.last()
                    if ((position - Offset(previous.x, previous.y)).getDistance() >= 1f) {
                        points += PenPoint(position.x, position.y, change.uptimeMillis)
                    }
                }
                if (moved && ownsPenMotion) {
                    change.consume()
                    if (ticket != null) actions.preview(ticket, points.toList())
                }
            }
        } finally {
            if (!delivered && ticket != null) actions.cancel(ticket)
        }
    }
}

internal data class InkFieldCallbacks(
    val begin: () -> Long?,
    val preview: (Long, List<PenPoint>) -> Unit,
    val finish: (Long, List<PenPoint>) -> Unit,
    val cancel: (Long) -> Unit,
    val tap: () -> Unit,
    val otherContact: () -> Unit,
    val missingModel: () -> Unit,
)
