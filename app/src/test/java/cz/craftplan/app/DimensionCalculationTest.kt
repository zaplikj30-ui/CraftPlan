package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class DimensionCalculationTest {
    private val dimension = PhotoDimension(
        UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH, PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f),
    )

    @Test
    fun exactArithmeticAndStandardPrecedence() {
        for ((expression, result) in mapOf(
            "100+50=" to "150", "100−50=" to "50", "100×50=" to "5000",
            "100÷50=" to "2", "2+3*4=" to "14", "20/5*2=" to "8",
            "0,1+0.2=" to "0,3", "1÷3=" to "1/3", "1/3*3=" to "1",
            "2-5=" to "-3", "2*-3+7=" to "1", "5-5=" to "0",
        )) {
            assertEquals(expression, result, evaluateDimensionCalculation(expression)?.editableText())
        }
    }

    @Test
    fun malformedOrUnboundedInputIsRejected() {
        for (input in listOf("100+50", "1÷0=", "1/0=", "1+=", "1.2,3=", "1 2=",
            "1+2==", "1 cm+2 mm=", "1e3=", "", "1".repeat(19) + "=",
            List(25) { "1" }.joinToString("+") + "=",
            List(6) { "999999999999999999" }.joinToString("*") + "=")) {
            assertNull(input, evaluateDimensionCalculation(input))
        }
    }

    @Test
    fun onlyNewFinalEqualsInMathTriggersWhenEnabled() {
        assertTrue(requestsDimensionCalculation("100+50", "100+50=", true))
        assertFalse(requestsDimensionCalculation("100+50", "100+50=", false))
        assertFalse(requestsDimensionCalculation("100+", "100+50", true))
        assertFalse(requestsDimensionCalculation("100+50=", "100+50=", true))
        assertFalse(requestsDimensionCalculation("25%", "25%=", true))
        assertFalse(requestsDimensionCalculation("12 mm", "12 mm=", true))
        for (label in listOf("1/3=", "1/0=", "100+50=")) {
            val literal = DimensionValueDraft(dimension).type(label).confirm(dimension)!!
            assertNull(literal.measurement)
            assertEquals(label, literal.displayLabel())
        }
    }

    @Test
    fun fullEquationAndRoundedResultSurviveReopenAndManualEdit() {
        val draft = DimensionValueDraft(dimension).calculate("100 ÷ 3 =", 2)!!
        assertEquals("100 ÷ 3 = ≈33,33", draft.input)
        assertTrue(draft.hasApproximateDisplay())
        val confirmed = draft.confirm(dimension)!!
        assertEquals("100 ÷ 3 = ≈33,33 mm", confirmed.displayLabel())
        assertEquals(LengthSource.CALCULATED, confirmed.measurement!!.source)
        assertEquals("33.33", confirmed.measurement.length.displayNumber(LengthUnit.MM))
        assertEquals(confirmed.measurement, ManualLengthMeasurement.calculate("100 ÷ 3 =", LengthUnit.MM, 2))
        assertEquals(draft.input, DimensionValueDraft(confirmed).input)
        val edited = draft.type("100 ÷ 3 = ≈34")
        assertEquals("100 ÷ 3 = 34", edited.input)
        assertEquals("100 ÷ 3 = 34 mm", edited.confirm(dimension)!!.displayLabel())
        assertNull(draft.type("100 ÷ 3 =").confirm(dimension))
        val next = draft.calculate(draft.input + " × 3 =", 2)!!
        assertEquals("100 ÷ 3 = ≈33,33 × 3 = 99,99", next.input)
        assertEquals("99.99", next.confirm(dimension)!!.measurement!!.length.displayNumber(LengthUnit.MM))
    }

    @Test
    fun noteAndConversionPreserveFullEquation() {
        val draft = DimensionValueDraft(dimension).calculate("100 + 50 =", 3)!!
        assertEquals("100 + 50 = 150 mm", draft.confirm(dimension)!!.displayLabel())
        val cm = draft.selectUnit(LengthUnit.CM)
        assertEquals("100 + 50 = 150 mm = 15", cm.input)
        assertEquals("100 + 50 = 150 mm = 15 cm", cm.confirm(dimension)!!.displayLabel())
        val note = draft.calculateNote("Výpočet: 100 / 3 =", 2)!!
        assertEquals("Výpočet: 100 ÷ 3 = ≈33,33", note.note)
        assertEquals("Výpočet: 100 ÷ 3 = 34", note.editNote("Výpočet: 100 ÷ 3 = ≈34").note)
        assertEquals(draft.input, note.input)
        assertFalse(requestsDimensionCalculation("100+50= 150", "100+50=", true))
        assertTrue(requestsDimensionCalculation("100+50", "100+50=", true))
        assertTrue(requestsDimensionCalculation("Výpočet: 100/3", "Výpočet: 100/3=", true, note = true))
        assertEquals("≈0", roundedCalculation("1/3=", 0)!!.markedNumber)
        assertNull(ManualLengthMeasurement.calculate("1/3=", LengthUnit.MM, 0))
    }

    @Test
    fun contextualExpressionsNormalizeOnlyMathAndPreservePriorMeasurement() {
        val draft = DimensionValueDraft(dimension)
        val label = draft.calculate("Kuchyň 40x50=", 3)!!
        assertEquals("Kuchyň 40 × 50 = 2000", label.input)
        assertNull(label.confirm(dimension)!!.measurement)
        val measured = draft.calculate("46641 text 40x50=", 3)!!
        assertEquals("46641 text 40 × 50 = 2000", measured.input)
        assertEquals("46641", measured.confirm(dimension)!!.measurement!!.length.displayNumber(LengthUnit.MM))
        val chained = measured.calculate(measured.input + " / 2=")!!
        assertEquals("46641 text 40 × 50 = 2000 ÷ 2 = 1000", chained.input)
        assertEquals("46641", chained.confirm(dimension)!!.measurement!!.length.displayNumber(LengthUnit.MM))
        val chainedLabel = label.calculate(label.input + " / 2=")!!
        assertNull(chainedLabel.confirm(dimension)!!.measurement)
        assertEquals("Kuchyň 40 × 50 = 2000 ÷ 2 = 1000", chainedLabel.confirm(dimension)!!.displayLabel())
        assertEquals("Kuchyň 100 ÷ 5 = 20", draft.calculateNote("Kuchyň 100/5=")!!.note)
        assertEquals("123 40 × 50 = 2000", draft.calculateNote("123 40X50=")!!.note)
        assertTrue(requestsDimensionCalculation("Text 10/0", "Text 10/0=", true))
        assertNull(draft.calculateNote("Text 10/0="))
        assertFalse(requestsDimensionCalculation("12 mm", "12 mm=", true))
        assertNull(draft.calculateNote("1+ 2/0="))
        assertEquals("2 × −3 + 7 =", formatCalculatedExpression("2*-3+7="))
    }

    @Test
    fun precisionReplaysOnlyUneditedCalculatedResults() {
        val draft = DimensionValueDraft(dimension).calculate("100/3=", 2)!!
            .calculateNote("Text 40/3=", 2)!!
        val changed = draft.recalculate(4)!!
        assertEquals("100 ÷ 3 = ≈33,3333", changed.input)
        assertEquals("Text 40 ÷ 3 = ≈13,3333", changed.note)
        val manual = draft.type("100 ÷ 3 = 34").editNote("Text 40 ÷ 3 = 14")
        assertEquals(manual.input, manual.recalculate(4)!!.input)
        assertEquals(manual.note, manual.recalculate(4)!!.note)
        val chain = draft.calculate(draft.input + " × 3 =", 2)!!
        assertEquals("100 ÷ 3 = ≈33,33 × 3 = 99,99", chain.recalculate(4)!!.input)
        val context = DimensionValueDraft(dimension).calculate("46641 text 100/3=", 2)!!
        assertEquals("46641 text 100 ÷ 3 = ≈33,3333", context.recalculate(4)!!.input)
        assertEquals("46641", context.recalculate(4)!!.confirm(dimension)!!.measurement!!.length.displayNumber(LengthUnit.MM))
    }

    @Test
    fun explicitEqualsFromImeAndKeypadUsesSameTrigger() {
        val before = "kuchyň 40x50"
        val after = "$before="
        assertTrue(requestsCalculationFromEdit(before, after, after.length, after.length, true, true))
        assertTrue(requestsCalculationFromEdit(before, "$after ", after.length + 1, after.length + 1, true, true))
        assertFalse(requestsCalculationFromEdit(after, after, after.length, after.length, true, true))
        assertFalse(requestsCalculationFromEdit(before, after, 1, after.length, true, true))
        assertFalse(requestsCalculationFromEdit(before, after, after.length, after.length, false, true))
        assertEquals("kuchyň 40 × 50 = 2000", DimensionValueDraft(dimension).calculateNote(after)!!.note)
    }

    @Test
    fun roundedNoteMarkerSurvivesIncrementalContinuationButNotManualReplacement() {
        var draft = DimensionValueDraft(dimension).calculateNote("100/3=", 0)!!
        for (key in listOf(" ", "×", "3")) draft = draft.editNote(draft.note + key)
        assertEquals("100 ÷ 3 = ≈33 ×3", draft.note)
        val chained = draft.calculateNote(draft.note + "=", 0)!!
        assertEquals("100 ÷ 3 = ≈33 × 3 = 99", chained.note)
        val initial = DimensionValueDraft(dimension).calculateNote("100/3=", 0)!!
        assertEquals("100 ÷ 3 = 34", initial.editNote("100 ÷ 3 = ≈34").note)
        assertEquals("100 ÷ 3 = 333", initial.editNote("100 ÷ 3 = ≈333").note)
    }

    @Test
    fun failureAndNegativeResultsDoNotInventLength() {
        val original = dimension.withManualLength("50 mm")
        val draft = DimensionValueDraft(original).type("1÷0=")
        assertNull(draft.calculate(draft.input))
        assertEquals("1÷0=", draft.input)
        assertEquals("50 mm", original.displayLabel())
        assertNull(draft.calculate("1000000001+0="))
        assertNull(draft.calculate("1/10000000000000=", 13))
        val negative = draft.calculate("1-2=")!!.confirm(original)!!
        assertEquals("1 − 2 = -1", negative.displayLabel())
        assertNull(negative.measurement)
        val zero = draft.calculate("1-1=")!!.confirm(original)!!
        assertEquals("1 − 1 = 0 mm", zero.displayLabel())
    }
}
