package cz.craftplan.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    private val recognize: suspend (String, List<List<PenPoint>>, InkRecognitionHints) -> String? = InkModels::recognize,
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
    private var recognitionHints = InkRecognitionHints("")
    private var completedStrokes = emptyList<List<PenPoint>>()
    private var contact: Long? = null
    private var candidate: String? = null
    private var literalDot = false
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

    fun start(next: InkDraftTarget, area: InkWritingArea? = null): Long? {
        // Do not replace another target, an incomplete contact or a full batch.
        // The UI will offer waiting/retry instead of discarding old handwriting.
        if (literalDot || contact != null || (target != null && target != next) ||
            completedStrokes.size >= MAX_STROKES) return null
        revision++
        work?.cancel()
        work = null
        candidate = null
        if (target == null) recognitionHints = InkRecognitionHints(inkPreContext(next.input), area)
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

    /** Explicit stationary dot in a field. Keep the same captured target,
     * selection, limit and failure/retry contract as recognized handwriting. */
    fun finishDot(ticket: Long, points: List<PenPoint>) {
        if (contact != ticket || completedStrokes.isNotEmpty() || points.isEmpty()) return
        contact = null
        completedStrokes = listOf(points)
        preview = completedStrokes
        literalDot = true
        candidate = "."
        deliver()
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
            if (literalDot) deliver() else {
                candidate = null
                schedule()
            }
        }
    }

    private fun schedule() {
        val captured = target ?: return
        val request = completedStrokes
        val hints = recognitionHints
        val ticket = ++revision
        failed = false
        work?.cancel()
        work = scope.launch {
            delay(settleDelayMs)
            val result = try {
                withTimeoutOrNull(5_000) { recognize(captured.language, request, hints) }
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
        recognitionHints = InkRecognitionHints("")
        pendingField = null
        contact = null
        candidate = null
        literalDot = false
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

internal enum class PenContactKind { TAP, HOLD, INK, WAIT, DOT }

/** Stationary holds tolerate jitter; tiny marks in an active batch are ink. */
internal fun classifyPenContact(
    pending: Boolean, durationMs: Long, displacementDp: Float, pathDp: Float,
): PenContactKind = when {
    displacementDp > 8f || pathDp > 16f -> PenContactKind.INK
    pending && durationMs >= PEN_HOLD_MS && displacementDp < 1.5f && pathDp < 3f -> PenContactKind.WAIT
    pending -> PenContactKind.INK
    durationMs >= PEN_HOLD_MS -> PenContactKind.HOLD
    // A brief first contact tolerates small slips and digitizer jitter.
    // Existing ink and strong motion above retain their original sensitivity.
    durationMs <= 120L -> if (displacementDp >= 6f || pathDp >= 12f)
        PenContactKind.INK else PenContactKind.TAP
    displacementDp >= 1.5f || pathDp >= 3f -> PenContactKind.INK
    else -> PenContactKind.TAP
}

/** Only a real release inside a field may turn a stationary first contact
 * into a literal dot. A tap on any other dialog control stays a tap. */
internal fun classifyFieldPenRelease(
    pending: Boolean, writing: Boolean, canWrite: Boolean,
    durationMs: Long, displacementDp: Float, pathDp: Float,
): PenContactKind {
    if (writing) return PenContactKind.INK
    val kind = classifyPenContact(pending, durationMs, displacementDp, pathDp)
    return if (!pending && canWrite && kind == PenContactKind.TAP &&
        durationMs in 200L..400L && displacementDp < 1.5f && pathDp < 3f)
        PenContactKind.DOT else kind
}

internal fun inkPreContext(input: TextFieldValue): String {
    val tail = input.text.take(input.selection.min).takeLast(20)
    // Do not split a supplementary Unicode character at the truncation point.
    return if (tail.firstOrNull()?.isLowSurrogate() == true) tail.drop(1) else tail
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
    // Pre-context can make the SDK infer a leading space. Keep the existing
    // append rules for numeric fragments and punctuation; never change
    // whitespace inside a result or a replacement of selected text.
    val trimmed = candidate.trimStart(' ')
    val firstTrimmed = trimmed.firstOrNull()
    val adjusted = if (append && previous != null && firstTrimmed != null &&
        (previous == ' ' ||
            (previous.isDigit() && (firstTrimmed.isDigit() || firstTrimmed == '/')) ||
            (previous in ".,/" && firstTrimmed.isDigit()) || firstTrimmed in ".,"))
        trimmed else candidate
    val first = adjusted.first()
    val separateWords = append && previous?.isLetterOrDigit() == true && first.isLetterOrDigit() &&
        (if (field == DimensionField.NOTE) previous.isLetter() || first.isLetter()
        else previous.isDigit() && first.isLetter() &&
            !(first in "xX" && (adjusted.length == 1 || adjusted.drop(1).trimStart().firstOrNull()?.isDigit() == true)))
    val insertion = if (separateWords) " $adjusted" else adjusted
    if (insertion.length > limit - (value.text.length - (end - start))) return null
    val text = value.text.replaceRange(start, end, insertion)
    return TextFieldValue(text, TextRange(start + insertion.length))
}

