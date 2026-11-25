package cz.craftplan.app

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
    fun dialogInkRoutesPendingBatchEverywhereAndOnlyNewWritingNearFields() {
        val value = Rect(10f, 10f, 110f, 30f)
        val note = Rect(10f, 50f, 110f, 70f)
        fun route(x: Float, y: Float, pending: DimensionField? = null,
                  preferred: DimensionField = DimensionField.VALUE) =
            dialogInkStart(Offset(x, y), pending, preferred, value, note, 20f)
        // Existing field-local tap/hold/ink stays in charge when no batch exists.
        assertNull(route(40f, 20f))
        assertNull(route(40f, 60f))
        assertEquals(DialogInkStart(DimensionField.VALUE, false), route(40f, 0f))
        assertEquals(DialogInkStart(DimensionField.NOTE, false), route(120f, 65f))
        assertEquals(DialogInkStart(DimensionField.NOTE, false),
            route(40f, 40f, preferred = DimensionField.NOTE))
        assertNull(route(130f, 90f)) // Outside the radial margin.
        assertNull(route(40f, 150f))
        for (point in listOf(Offset(40f, 60f), Offset(40f, 150f), Offset(300f, 2f))) {
            assertEquals(DialogInkStart(DimensionField.VALUE, true),
                route(point.x, point.y, pending = DimensionField.VALUE))
        }
        assertNull(dialogInkStart(Offset(40f, 20f), null, DimensionField.VALUE,
            Rect.Zero, null, 20f))
        assertNull(dialogInkStart(Offset(Float.NaN, 20f), DimensionField.NOTE,
            DimensionField.VALUE, value, note, 20f))
    }

    @Test
    fun keypadTopLimitsNewInkButNeverAnActiveBatch() {
        val value = Rect(10f, 10f, 110f, 30f)
        val note = Rect(10f, 40f, 110f, 60f)
        val portrait = Rect(0f, 64f, 120f, 200f)
        val landscape = Rect(35f, 64f, 90f, 160f)
        fun route(x: Float, y: Float, keypad: Rect?, pending: DimensionField? = null) =
            dialogInkStart(Offset(x, y), pending, DimensionField.VALUE,
                value, note, 32f, visibleKeypadBounds = keypad)
        for (keypad in listOf(portrait, landscape)) {
            assertEquals(DialogInkStart(DimensionField.VALUE, false), route(40f, 0f, keypad))
            assertEquals(DialogInkStart(DimensionField.NOTE, false), route(40f, 63.9f, keypad))
            assertNull(route(40f, 64f, keypad))
            assertNull(route(40f, 65f, keypad))
            assertNull(route(15f, 65f, keypad)) // Same boundary on narrow landscape keypad.
            for (field in DimensionField.entries) {
                assertEquals(DialogInkStart(field, true), route(40f, 65f, keypad, field))
                assertEquals(DialogInkStart(field, true), route(40f, 150f, keypad, field))
            }
        }
        // Hidden or fully clipped keypad does not restrict the original margin.
        for (keypad in listOf(null, Rect.Zero, Rect(0f, 64f, 120f, 64f))) {
            assertEquals(DialogInkStart(DimensionField.NOTE, false), route(40f, 65f, keypad))
        }
        // Boundary follows actual layout, rather than a fixed distance from the note.
        assertEquals(DialogInkStart(DimensionField.NOTE, false),
            route(40f, 65f, Rect(0f, 70f, 120f, 200f)))
        assertNull(route(40f, 65f, Rect(0f, 62f, 120f, 200f)))
    }

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
    fun longContinuousStrokeKeepsLatestPointBeyondOldLimitAndBoundedBudget() {
        val first = PenPoint(0f, 0f, 0L)
        val buffer = PenStrokeBuffer(first)
        for (i in 1..20_000) {
            val next = PenPoint(i.toFloat(), (i % 17).toFloat(), i.toLong())
            buffer.append(next)
            assertTrue(buffer.size <= PenStrokeBuffer.MAX_POINTS)
            if (i % 251 == 0 || i == 20_000) {
                val snapshot = buffer.snapshot()
                assertEquals(first, snapshot.first())
                assertEquals(next, snapshot.last())
                assertTrue(snapshot.zipWithNext().all { (a, b) -> a.timeMs < b.timeMs })
            }
        }
        val snapshot = buffer.snapshot()
        assertEquals((19_900..20_000).map { it.toLong() }, snapshot.takeLast(101).map { it.timeMs })
        buffer.append(PenPoint(20_000f, (20_000 % 17).toFloat(), 20_001L))
        assertEquals(snapshot, buffer.snapshot())
        buffer.append(PenPoint(20_001f, 0f, 20_002L))
        assertEquals(20_002L, buffer.snapshot().last().timeMs)
        assertEquals(20_000L, snapshot.last().timeMs)
    }

    @Test
    fun onlyRealUncanceledSinglePointerReleaseCanFinishStroke() {
        assertTrue(canFinishPenStroke(MotionEvent.ACTION_UP, canceled = false, pointerCount = 1))
        assertFalse(canFinishPenStroke(MotionEvent.ACTION_UP, canceled = true, pointerCount = 1))
        assertFalse(canFinishPenStroke(MotionEvent.ACTION_CANCEL, canceled = false, pointerCount = 1))
        assertFalse(canFinishPenStroke(MotionEvent.ACTION_POINTER_UP, canceled = false, pointerCount = 2))
        assertFalse(canFinishPenStroke(null, canceled = false, pointerCount = 1))
    }

    @Test
    fun shortInitialTapTolerancePreservesDeliberateInkAndActiveDots() {
        for ((duration, displacement, path) in listOf(
            Triple(33L, 2.7318637f, 3.1303012f),
            Triple(43L, 2.3653305f, 4.8653708f),
            Triple(40L, 5.054732f, 5.180271f),
            Triple(35L, 2.1118329f, 2.677018f),
            Triple(48L, 2.0519884f, 4.2577314f),
            Triple(27L, 3.0922985f, 3.09603f),
        )) {
            assertEquals(PenContactKind.TAP, classifyPenContact(false, duration, displacement, path))
            assertEquals(PenContactKind.INK, classifyPenContact(true, duration, displacement, path))
        }
        assertEquals(PenContactKind.TAP, classifyPenContact(false, 120, 5.99f, 11.99f))
        assertEquals(PenContactKind.INK, classifyPenContact(false, 120, 6f, 6f))
        assertEquals(PenContactKind.INK, classifyPenContact(false, 40, 4f, 12f))
        assertEquals(PenContactKind.INK, classifyPenContact(false, 121, 2f, 2f))
        assertEquals(PenContactKind.INK, classifyPenContact(false, 20, 9f, 9f))
        assertEquals(PenContactKind.HOLD, classifyPenContact(false, 800, 0.5f, 1f))
        assertEquals(PenContactKind.INK, classifyPenContact(true, 20, 0f, 0f))
    }

    @Test
    fun penClassificationAllowsJitterAndDotsWithoutTreatingWritingAsHold() {
        assertEquals(PenContactKind.HOLD, classifyPenContact(false, 900, 5f, 10f))
        assertEquals(PenContactKind.TAP, classifyPenContact(false, 80, 0.5f, 0.6f))
        assertEquals(PenContactKind.INK, classifyPenContact(true, 50, 0f, 0f))
        assertEquals(PenContactKind.WAIT, classifyPenContact(true, 900, 0f, 0f))
        // A claimed near-field stroke stays ink through a subsequent slow pause.
        assertEquals(PenContactKind.INK, classifyPenContact(false, 200, 2f, 2f))
        assertEquals(PenContactKind.INK, classifyPenContact(true, 1200, 2f, 2f))
        assertEquals(PenContactKind.INK, classifyPenContact(false, 1200, 9f, 12f))
        assertEquals(PenContactKind.INK, classifyPenContact(false, 1200, 5f, 20f))
        assertEquals(PenContactKind.INK, classifyPenContact(false, 200, 2f, 2f))
        assertEquals("455 Ahoj", insertInkResult(DimensionField.VALUE, TextFieldValue("455", TextRange(3)), "Ahoj")?.text)
        assertEquals("45520", insertInkResult(DimensionField.VALUE, TextFieldValue("455", TextRange(3)), "20")?.text)
        assertEquals("455x", insertInkResult(DimensionField.VALUE, TextFieldValue("455", TextRange(3)), "x")?.text)
        assertEquals("455 Xyl", insertInkResult(DimensionField.VALUE, TextFieldValue("455", TextRange(3)), "Xyl")?.text)
    }

    @Test(timeout = 3000)
    fun canceledContactPreservesEarlierStrokesAndAnotherFieldCannotReplaceTarget(): Unit = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.VALUE, "cs", TextFieldValue())
        val result = CompletableDeferred<TextFieldValue>()
        val controller = InkDraftController(this, recognize = { _, strokes ->
            assertEquals(listOf(stroke), strokes)
            "123"
        }, settleDelayMs = 0)
        controller.attach(Any(), { target }, { _, value -> result.complete(value) }, {})
        controller.finish(controller.start(target)!!, stroke)
        assertNull(controller.start(target.copy(field = DimensionField.NOTE)))
        val contact = controller.start(target)!!
        controller.cancel(contact)
        assertTrue(controller.hasPendingInput)
        assertEquals(listOf(stroke), controller.preview)
        assertEquals("123", result.await().text)
        assertFalse(controller.hasPendingInput)
    }

    @Test(timeout = 3000)
    fun recognitionSurvivesDetachAndDeliversOnceToNewUi(): Unit = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.NOTE, "cs", TextFieldValue())
        val started = CompletableDeferred<Unit>()
        val recognized = CompletableDeferred<String>()
        val applied = CompletableDeferred<TextFieldValue>()
        val controller = InkDraftController(this, recognize = { _, _ ->
            started.complete(Unit)
            recognized.await()
        }, settleDelayMs = 0)
        val old = Any()
        controller.attach(old, { target }, { _, _ -> error("Old UI") }, {})
        controller.finish(controller.start(target)!!, stroke)
        started.await()
        controller.detach(old)
        recognized.complete("nové")
        kotlinx.coroutines.yield()
        assertTrue(controller.hasPendingInput)
        controller.attach(Any(), { target }, { _, text -> applied.complete(text) }, {})
        assertEquals("nové", applied.await().text)
        assertFalse(controller.hasPendingInput)
    }

    @Test(timeout = 3000)
    fun recognitionUsesCapturedLanguageAndSelectionAcrossDetach() = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.VALUE, "cs",
            TextFieldValue("125", TextRange(1)))
        val controller = InkDraftController(this, recognize = { language, _ ->
            assertEquals("cs", language)
            "9"
        }, settleDelayMs = 0)
        controller.finish(controller.start(target)!!, stroke)
        assertEquals("cs", controller.pendingLanguage)
        assertEquals(target.input, controller.pendingInput(DimensionField.VALUE))
        val result = CompletableDeferred<TextFieldValue>()
        controller.attach(Any(), { target }, { _, value -> result.complete(value) }, {})
        assertEquals("1925", result.await().text)
    }

    @Test(timeout = 3000)
    fun nextStrokeAndDotRetainEarlierInkDespiteLateSdkResult(): Unit = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.NOTE, "cs", TextFieldValue())
        val firstStarted = CompletableDeferred<Unit>()
        val firstResult = CompletableDeferred<String>()
        val applied = CompletableDeferred<TextFieldValue>()
        val dot = listOf(PenPoint(12f, 15f, 40))
        var calls = 0
        val controller = InkDraftController(this, recognize = { _, strokes ->
            if (++calls == 1) {
                firstStarted.complete(Unit)
                withContext(NonCancellable) { firstResult.await() }
            } else {
                assertEquals(listOf(stroke, dot), strokes)
                "číslo."
            }
        }, settleDelayMs = 0)
        controller.attach(Any(), { target }, { _, text -> applied.complete(text) }, {})
        controller.finish(controller.start(target)!!, stroke)
        firstStarted.await()
        val ticket = controller.start(target)!!
        assertEquals(listOf(stroke), controller.preview)
        controller.finish(ticket, dot)
        assertEquals("číslo.", applied.await().text)
        firstResult.complete("starý výsledek")
        assertFalse(controller.hasPendingInput)
    }

    @Test(timeout = 3000)
    fun failureRetainsInkUntilRetryOrExplicitDiscard(): Unit = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.VALUE, "cs", TextFieldValue())
        val failure = CompletableDeferred<Unit>()
        val applied = CompletableDeferred<TextFieldValue>()
        var calls = 0
        val controller = InkDraftController(this, recognize = { _, strokes ->
            assertEquals(listOf(stroke), strokes)
            if (++calls == 1) null else "42"
        }, settleDelayMs = 0)
        controller.attach(Any(), { target }, { _, text -> applied.complete(text) }, { failure.complete(Unit) })
        controller.finish(controller.start(target)!!, stroke)
        failure.await()
        assertTrue(controller.failed)
        assertTrue(controller.hasPendingInput)
        assertEquals(listOf(stroke), controller.preview)
        controller.retry()
        assertEquals("42", applied.await().text)
        assertFalse(controller.hasPendingInput)
    }

    @Test(timeout = 3000)
    fun sdkCancellationRetainsPreviousStrokes() = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.VALUE, "cs", TextFieldValue())
        val failure = CompletableDeferred<Unit>()
        val controller = InkDraftController(this, recognize = { _, _ ->
            throw kotlinx.coroutines.CancellationException("SDK task canceled")
        }, settleDelayMs = 0)
        controller.attach(Any(), { target }, { _, _ -> error("Unexpected result") }, { failure.complete(Unit) })
        controller.finish(controller.start(target)!!, stroke)
        failure.await()
        assertTrue(controller.failed)
        assertTrue(controller.hasPendingInput)
        assertEquals(listOf(stroke), controller.preview)
        controller.interrupt()
    }

    @Test(timeout = 3000)
    fun staleTargetNeverReceivesTextAndRetainsInk(): Unit = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.VALUE, "cs", TextFieldValue("123"))
        val failure = CompletableDeferred<Unit>()
        val controller = InkDraftController(this, recognize = { _, _ -> "4" }, settleDelayMs = 0)
        controller.attach(Any(), { target.copy(input = TextFieldValue("124")) },
            { _, _ -> error("Stale result") }, { failure.complete(Unit) })
        controller.finish(controller.start(target)!!, stroke)
        failure.await()
        assertTrue(controller.hasPendingInput)
        assertEquals(listOf(stroke), controller.preview)
        controller.interrupt()
        assertFalse(controller.hasPendingInput)
    }
}
