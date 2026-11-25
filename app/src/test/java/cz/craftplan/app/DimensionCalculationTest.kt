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
        assertEquals("Výpočet: 100 / 3 = ≈33,33", note.note)
        assertEquals("Výpočet: 100 / 3 = 34", note.editNote("Výpočet: 100 / 3 = ≈34").note)
        assertEquals(draft.input, note.input)
        assertFalse(requestsDimensionCalculation("100+50= 150", "100+50=", true))
        assertTrue(requestsDimensionCalculation("100+50", "100+50=", true))
        assertTrue(requestsDimensionCalculation("Výpočet: 100/3", "Výpočet: 100/3=", true, note = true))
        assertEquals("≈0", roundedCalculation("1/3=", 0)!!.markedNumber)
        assertNull(ManualLengthMeasurement.calculate("1/3=", LengthUnit.MM, 0))
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
        assertEquals("1-2= -1", negative.displayLabel())
        assertNull(negative.measurement)
        val zero = draft.calculate("1-1=")!!.confirm(original)!!
        assertEquals("1-1= 0 mm", zero.displayLabel())
    }
}
