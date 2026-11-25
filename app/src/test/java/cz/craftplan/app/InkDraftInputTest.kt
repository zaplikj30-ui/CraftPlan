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
    fun standaloneCommaChecksSizeSlopeAndSingleStrokeAtAnyDensity() {
        fun line(dx: Float, dy: Float, density: Float = 1f) =
            listOf(PenPoint(0f, 0f, 0), PenPoint(dx * density, dy * density, 120))
        fun comma(dx: Float, dy: Float, density: Float = 1f) =
            isStandaloneInkComma(listOf(line(dx, dy, density)), density)
        assertTrue(comma(-9f, 12f))
        assertTrue(comma(9f, -12f))
        assertTrue(comma(-12f, 16f))
        assertTrue(comma(-9f, 12f, 3.5f))
        assertFalse(comma(-12.1f, 16f))
        assertFalse(comma(-1f, 12f))
        assertFalse(comma(-12f, 1f))
        assertFalse(comma(9f, 12f))
        assertFalse(comma(-2f, 2f))
        assertFalse(comma(-30f, 40f))
        assertFalse(isStandaloneInkComma(listOf(line(-9f, 12f), line(-9f, 12f)), 1f))
        assertFalse(isStandaloneInkComma(listOf(line(-9f, 12f)), null))
        assertFalse(isStandaloneInkComma(listOf(line(-9f, 12f)), 0f))
        assertFalse(isStandaloneInkComma(listOf(line(Float.NaN, 12f)), 1f))
        assertFalse(isStandaloneInkComma(listOf(listOf(PenPoint(0f, 0f, 0),
            PenPoint(-20f, 25f, 50), PenPoint(-9f, 12f, 100))), 1f))
        assertFalse(isStandaloneInkComma(listOf(listOf(PenPoint(0f, 0f, 0),
            PenPoint(-9f, 12f, 40), PenPoint(0f, 0f, 80), PenPoint(-9f, 12f, 120))), 1f))
    }

    @Test(timeout = 3000)
    fun shortDiagonalInsertsCommaAfterSlashWithoutRecognizerInBothFields() = runBlocking {
        for (field in DimensionField.entries) {
            val target = InkDraftTarget(UUID.randomUUID(), field, "cs",
                TextFieldValue("1/", TextRange(2)))
            val result = CompletableDeferred<TextFieldValue>()
            val controller = InkDraftController(this, recognize = { _, _, _ ->
                error("Local comma must not call recognizer")
            }, settleDelayMs = 0)
            controller.attach(Any(), { target }, { _, edited -> result.complete(edited) },
                { error("Unexpected failure") })
            controller.finish(controller.start(target, density = 1f)!!,
                listOf(PenPoint(0f, 0f, 0), PenPoint(-9f, 12f, 120)))
            assertEquals("1/,", result.await().text)
            assertFalse(controller.hasPendingInput)
        }
    }

    @Test(timeout = 3000)
    fun longerAndMultiStrokeInkRetainRecognizerAndOriginalContext() = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.VALUE, "cs",
            TextFieldValue("1", TextRange(1)))
        val short = listOf(PenPoint(0f, 0f, 0), PenPoint(-9f, 12f, 120))
        val long = listOf(PenPoint(0f, 0f, 0), PenPoint(-24f, 32f, 120))
        for (batch in listOf(listOf(long), listOf(short, short))) {
            val result = CompletableDeferred<TextFieldValue>()
            val controller = InkDraftController(this, recognize = { _, strokes, hints ->
                assertEquals(batch, strokes)
                assertEquals("1", hints.preContext)
                "/"
            }, settleDelayMs = 0)
            controller.attach(Any(), { target }, { _, edited -> result.complete(edited) },
                { error("Unexpected failure") })
            for (points in batch) controller.finish(controller.start(target, density = 1f)!!, points)
            assertEquals("1/", result.await().text)
        }
    }

    @Test
    fun dialogInkRoutesPendingBatchEverywhereAndOnlyNewWritingNearFields() {
        val value = Rect(10f, 10f, 110f, 30f)
        val note = Rect(10f, 50f, 110f, 70f)
        fun route(x: Float, y: Float, pending: DimensionField? = null) =
            dialogInkStart(Offset(x, y), pending, DimensionField.VALUE, value, note, 26f, 1f, 6f)
        assertNull(route(40f, 20f))
        assertNull(route(40f, 60f))
        assertEquals(DialogInkStart(DimensionField.VALUE, false), route(40f, 0f))
        assertEquals(DialogInkStart(DimensionField.NOTE, false), route(110.5f, 65f))
        assertNull(route(112f, 65f))
        assertEquals(DialogInkStart(DimensionField.NOTE, false), route(40f, 40f))
        assertNull(route(130f, 90f))
        assertNull(route(40f, 150f))
        for (point in listOf(Offset(40f, 60f), Offset(40f, 150f), Offset(300f, 2f))) {
            assertEquals(DialogInkStart(DimensionField.VALUE, true),
                route(point.x, point.y, pending = DimensionField.VALUE))
        }
        assertNull(dialogInkStart(Offset(40f, 20f), null, DimensionField.VALUE,
            Rect.Zero, null, 26f, 1f, 6f))
        assertNull(dialogInkStart(Offset(Float.NaN, 20f), DimensionField.NOTE,
            DimensionField.VALUE, value, note, 26f, 1f, 6f))
    }

    @Test
    fun keypadAndFieldEdgesLimitNewInkInPortraitAndLandscapeButNeverActiveBatch() {
        val value = Rect(10f, 40f, 110f, 60f)
        for (note in listOf(Rect(10f, 90f, 110f, 110f), Rect(130f, 40f, 230f, 60f))) {
            val top = note.bottom + 3f
            val keypad = Rect(0f, top, 250f, 200f)
            fun route(x: Float, y: Float, panel: Rect? = keypad, pending: DimensionField? = null) =
                dialogInkStart(Offset(x, y), pending, DimensionField.VALUE,
                    value, note, 26f, 1f, 6f, visibleKeypadBounds = panel)
            for ((field, bounds) in listOf(DimensionField.VALUE to value, DimensionField.NOTE to note)) {
                val x = bounds.center.x
                assertEquals(DialogInkStart(field, false), route(x, bounds.top - 10f))
                assertEquals(DialogInkStart(field, false), route(bounds.right + .5f, bounds.center.y))
                assertNull(route(bounds.right + 2f, bounds.center.y))
                assertNull(route(bounds.left - 2f, bounds.center.y))
                assertEquals(DialogInkStart(field, true), route(x, top + 10f, pending = field))
                assertEquals(DialogInkStart(field, false), route(x, bounds.bottom + 2f))
            }
            val x = note.center.x
            assertNull(route(x, top))
            assertNull(route(x, top + 1f))
            assertNull(route(x, top + 12f, panel = null))
            for (panel in listOf(null, Rect.Zero, Rect(0f, top, 250f, top))) {
                assertEquals(DialogInkStart(DimensionField.NOTE, false), route(x, top + 1f, panel))
            }
        }
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
        assertEquals("1250", append("12", " 50", DimensionField.VALUE)?.text)
        assertEquals("1250", append("12", " 50")?.text)
        assertEquals("12,5", append("12", " ,5", DimensionField.VALUE)?.text)
        assertEquals("okno,", append("okno", " ,")?.text)
        assertEquals("Levá strana", append("Levá", " strana")?.text)
        assertEquals("Levá strana", append("Levá ", " strana")?.text)
        assertEquals("a Xc", insertInkResult(DimensionField.NOTE,
            TextFieldValue("abc", TextRange(1, 2)), " X")?.text)
        assertEquals("1/3", append("1/", "3", DimensionField.VALUE)?.text)
        assertEquals("1/3", append("1/", " 3", DimensionField.VALUE)?.text)
        assertEquals("1/3", append("1", " /3", DimensionField.VALUE)?.text)
        assertEquals("1,3", append("1,", " 3", DimensionField.VALUE)?.text)
        assertEquals("1.3", append("1.", " 3", DimensionField.VALUE)?.text)
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
    fun recognitionContextUsesOnlyTextBeforeSelectionAndLastTwentyCharacters() {
        assertEquals("abc", inkPreContext(TextFieldValue("abcDEFghi", TextRange(3, 6))))
        assertEquals("abc", inkPreContext(TextFieldValue("abcDEFghi", TextRange(6, 3))))
        assertEquals("", inkPreContext(TextFieldValue("abc", TextRange(0))))
        assertEquals("56789012345678901234",
            inkPreContext(TextFieldValue("123456789012345678901234", TextRange(24))))
        assertEquals("1234567890123456789", inkPreContext(
            TextFieldValue("😀1234567890123456789", TextRange(21))))
    }

    @Test(timeout = 3000)
    fun recognitionHintsStayWithOriginalBatchAcrossNewStrokeAndReattach() = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.NOTE, "cs",
            TextFieldValue("Levá strana", TextRange(11)))
        val firstArea = InkWritingArea(400f, 54f)
        val result = CompletableDeferred<TextFieldValue>()
        val controller = InkDraftController(this, recognize = { _, strokes, hints ->
            assertEquals(2, strokes.size)
            assertEquals(InkRecognitionHints("Levá strana", firstArea), hints)
            ","
        }, settleDelayMs = 0)
        val old = Any()
        controller.attach(old, { target }, { _, _ -> error("Old UI") }, {})
        controller.finish(controller.start(target, firstArea)!!, stroke)
        controller.finish(controller.start(target, InkWritingArea(800f, 60f))!!, stroke)
        controller.detach(old)
        controller.attach(Any(), { target }, { _, edited -> result.complete(edited) }, {})
        assertEquals("Levá strana,", result.await().text)
    }

    @Test
    fun stationaryDotIsLimitedToFreshFieldReleaseAndDoesNotChangeTapsOrHolds() {
        fun kind(duration: Long, displacement: Float = 0.5f, path: Float = 0.7f,
                 pending: Boolean = false, writing: Boolean = false, enabled: Boolean = true) =
            classifyFieldPenRelease(pending, writing, enabled, duration, displacement, path)
        assertEquals(PenContactKind.TAP, kind(120))
        assertEquals(PenContactKind.TAP, kind(149, 3.9f, 8f))
        assertEquals(PenContactKind.DOT, kind(150))
        assertEquals(PenContactKind.DOT, kind(199))
        assertEquals(PenContactKind.DOT, kind(400))
        assertEquals(PenContactKind.DOT, kind(799))
        assertEquals(PenContactKind.HOLD, kind(800))
        assertEquals(PenContactKind.TAP, kind(60, 5.05f, 5.18f))
        assertEquals(PenContactKind.INK, kind(60, 4f, 12f))
        assertEquals(PenContactKind.DOT, kind(250, displacement = 2f, path = 2f))
        assertEquals(PenContactKind.DOT, kind(250, displacement = 4f, path = 20f))
        assertEquals(PenContactKind.INK, kind(250, displacement = 4.01f, path = 5f))
        assertEquals(PenContactKind.DOT, kind(165, 2.8171024f, 3.6262481f))
        assertEquals(PenContactKind.DOT, kind(449, 2.138846f, 2.369467f))
        assertEquals(PenContactKind.DOT, kind(747, 3.1505954f, 11.074846f))
        assertEquals(PenContactKind.DOT, kind(757, 0.75940275f, 2.1233227f))
        assertEquals(PenContactKind.TAP, classifyFieldPenContact(false, false, true, false, 250, 3f, 20f))
        assertEquals(PenContactKind.INK, classifyFieldPenContact(false, false, true, false, 250, 4.1f, 5f))
        assertEquals(PenContactKind.INK, classifyFieldPenContact(false, true, true, true, 400, 3f, 4f))
        assertEquals(PenContactKind.INK, kind(250, pending = true))
        assertEquals(PenContactKind.INK, kind(250, writing = true))
        assertEquals(PenContactKind.TAP, kind(250, enabled = false))
        assertEquals(PenContactKind.TAP, classifyPenContact(false, 250, 0.5f, 0.7f))
    }

    @Test
    fun explicitDotInsertsAtCapturedSelectionWithoutCallingRecognizer() = runBlocking {
        val initial = TextFieldValue("Text A", TextRange(4))
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.NOTE, "cs", initial)
        val controller = InkDraftController(this, recognize = { _, _, _ -> error("Unexpected recognition") })
        var result: TextFieldValue? = null
        controller.attach(Any(), { target }, { _, edited -> result = edited }, { error("Unexpected failure") })
        controller.finishDot(controller.start(target)!!, listOf(PenPoint(1f, 1f, 250)))
        assertEquals(TextFieldValue("Text. A", TextRange(5)), result)
        assertFalse(controller.hasPendingInput)
    }

    @Test
    fun staleExplicitDotRemainsPendingUntilRetryOrDiscard() = runBlocking {
        val target = InkDraftTarget(UUID.randomUUID(), DimensionField.VALUE, "cs",
            TextFieldValue("12", TextRange(2)))
        var live = target.copy(input = TextFieldValue("34", TextRange(2)))
        var result: TextFieldValue? = null
        val controller = InkDraftController(this, recognize = { _, _, _ -> error("Unexpected recognition") })
        controller.attach(Any(), { live }, { _, edited -> result = edited }, {})
        controller.finishDot(controller.start(target)!!, listOf(PenPoint(1f, 1f, 250)))
        assertTrue(controller.failed)
        assertTrue(controller.hasPendingInput)
        assertNull(controller.start(live))
        assertNull(result)
        live = target
        controller.retry()
        assertEquals(TextFieldValue("12.", TextRange(3)), result)
        assertFalse(controller.hasPendingInput)
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
        val controller = InkDraftController(this, recognize = { _, strokes, _ ->
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
        val controller = InkDraftController(this, recognize = { _, _, _ ->
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
        val controller = InkDraftController(this, recognize = { language, _, _ ->
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
        val controller = InkDraftController(this, recognize = { _, strokes, _ ->
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
        val controller = InkDraftController(this, recognize = { _, strokes, _ ->
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
        val controller = InkDraftController(this, recognize = { _, _, _ ->
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
        val controller = InkDraftController(this, recognize = { _, _, _ -> "4" }, settleDelayMs = 0)
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
