package cz.craftplan.app

import android.view.MotionEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkDraftInputTest {
    private val stroke = listOf(PenPoint(1f, 1f, 10), PenPoint(10f, 15f, 20))

    @Test
    fun insertsUnmodifiedTextInSelectionAndRejectsOversizeOrMultiline() {
        val value = TextFieldValue("99", TextRange(0, 2))
        assertEquals("1/3", insertInkResult(DimensionField.VALUE, value, "1/3")?.text)
        assertEquals("1,25", insertInkResult(DimensionField.VALUE, value, "1,25")?.text)
        assertEquals("1.25", insertInkResult(DimensionField.VALUE, value, "1.25")?.text)
        assertEquals("Žluťoučký 2", insertInkResult(DimensionField.NOTE, TextFieldValue(), "Žluťoučký 2")?.text)
        assertNull(insertInkResult(DimensionField.VALUE, value, "2\n3"))
        assertNull(insertInkResult(DimensionField.NOTE, TextFieldValue(), ""))
        assertNull(insertInkResult(DimensionField.VALUE, value, "0".repeat(ManualLengthMeasurement.MAX_INPUT_LENGTH + 1)))
    }

    @Test
    fun separateNoteWordsAtEndWithoutChangingNumericOrSelectedText() {
        fun append(text: String, candidate: String, field: DimensionField = DimensionField.NOTE) =
            insertInkResult(field, TextFieldValue(text, TextRange(text.length)), candidate)
        val note = append("Levá strana", "okna")
        assertEquals("Levá strana okna", note?.text)
        assertEquals(TextRange(16), note?.selection)
        assertEquals("Levá strana okna", append("Levá strana ", "okna")?.text)
        assertEquals("okno,", append("okno", ",")?.text)
        assertEquals("1250", append("12", "50")?.text)
        assertEquals("1250", append("12", "50", DimensionField.VALUE)?.text)
        assertEquals("1/3", append("1/", "3", DimensionField.VALUE)?.text)
        assertEquals("aXc", insertInkResult(DimensionField.NOTE, TextFieldValue("abc", TextRange(1, 2)), "X")?.text)
        assertEquals("aXbc", insertInkResult(DimensionField.NOTE, TextFieldValue("abc", TextRange(1)), "X")?.text)
        assertNull(append("a".repeat(MAX_DIMENSION_NOTE_LENGTH - 1), "b"))
    }

    @Test
    fun onlyRealUncanceledSinglePointerReleaseCanFinishStroke() {
        assertTrue(canFinishPenStroke(MotionEvent.ACTION_UP, canceled = false, pointerCount = 1))
        assertFalse(canFinishPenStroke(MotionEvent.ACTION_UP, canceled = true, pointerCount = 1))
        assertFalse(canFinishPenStroke(MotionEvent.ACTION_CANCEL, canceled = false, pointerCount = 1))
        assertFalse(canFinishPenStroke(MotionEvent.ACTION_POINTER_UP, canceled = false, pointerCount = 2))
        assertFalse(canFinishPenStroke(null, canceled = false, pointerCount = 1))
    }

    @Test(timeout = 3000)
    fun delayedResultIsRejectedAfterNewerInputOrTargetChange(): Unit = runBlocking {
        val original = InkDraftTarget(UUID.randomUUID(), DimensionField.VALUE, "cs", TextFieldValue("123"))
        for (change in listOf(
            original.copy(input = TextFieldValue("124")),
            original.copy(input = TextFieldValue("123", TextRange(2))),
            original.copy(field = DimensionField.NOTE),
            original.copy(language = "en"),
            original.copy(draftId = UUID.randomUUID()),
        )) {
            val started = CompletableDeferred<Unit>()
            val result = CompletableDeferred<String>()
            val controller = InkDraftController(this, recognize = { _, _ ->
                started.complete(Unit)
                withContext(NonCancellable) { result.await() }
            }, settleDelayMs = 0)
            var current = original
            val applied = mutableListOf<TextFieldValue>()
            controller.finish(controller.start(original), stroke, { current }, { applied += it }, {})
            started.await()
            current = change
            result.complete("4")
            // Wait until the pending preview is discarded.
            while (controller.preview.isNotEmpty()) kotlinx.coroutines.yield()
            assertTrue(applied.isEmpty())
        }
    }

    @Test(timeout = 3000)
    fun sdkCancellationClearsThePendingDraft() = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.VALUE, "cs", TextFieldValue())
        val failed = CompletableDeferred<Unit>()
        val controller = InkDraftController(this, recognize = { _, _ ->
            throw kotlinx.coroutines.CancellationException("SDK task canceled")
        }, settleDelayMs = 0)
        controller.finish(controller.start(target), stroke, { target }, { error("Draft changed") }, { failed.complete(Unit) })
        failed.await()
        assertFalse(controller.hasPendingInput)
    }

    @Test(timeout = 3000)
    fun nextStrokeBatchesAndInterruptionRejectsAnOlderSdkCallback(): Unit = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.NOTE, "cs", TextFieldValue())
        val firstStarted = CompletableDeferred<Unit>()
        val firstResult = CompletableDeferred<String>()
        var calls = 0
        val applied = CompletableDeferred<TextFieldValue>()
        val controller = InkDraftController(this, recognize = { _, strokes ->
            if (++calls == 1) {
                firstStarted.complete(Unit)
                withContext(NonCancellable) { firstResult.await() }
            } else {
                assertEquals(listOf(stroke, stroke), strokes)
                "číslo"
            }
        }, settleDelayMs = 0)
        controller.finish(controller.start(target), stroke, { target }, { applied.complete(it) }, {})
        firstStarted.await()
        assertTrue(controller.hasPendingInput)
        val nextTicket = controller.start(target)
        assertEquals(listOf(stroke), controller.preview)
        controller.previewStroke(nextTicket, stroke)
        assertEquals(listOf(stroke, stroke), controller.preview)
        controller.finish(nextTicket, stroke, { target }, { applied.complete(it) }, {})
        assertEquals("číslo", applied.await().text)
        assertFalse(controller.hasPendingInput)
        firstResult.complete("starý výsledek")
    }
}
