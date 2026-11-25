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
    }

    @Test
    fun resultRemainsDraftAndExactFractionHasReplayableProvenance() {
        val draft = DimensionValueDraft(dimension).type("1÷3=")
        val calculated = requireNotNull(draft.calculate(draft.input))
        assertEquals("1/3", calculated.input)
        assertFalse(calculated.hasApproximateDisplay())
        assertNull(dimension.measurement)
        val confirmed = calculated.confirm(dimension)!!
        assertEquals(LengthSource.CALCULATED, confirmed.measurement!!.source)
        assertEquals("1÷3=", confirmed.measurement.originalInput)
        assertEquals(LengthMmEncoding.Rational("1", "3"), confirmed.measurement.length.encoding)
        assertEquals(confirmed.measurement, ManualLengthMeasurement.calculate("1÷3=", LengthUnit.MM))
        val converted = calculated.selectUnit(LengthUnit.CM).confirm(dimension)!!
        assertEquals(confirmed.measurement, converted.measurement)
        val reopened = DimensionValueDraft(confirmed)
        assertEquals(confirmed.measurement, reopened.confirm(confirmed)!!.measurement)
        val changed = calculated.type("25").confirm(dimension)!!
        assertEquals(LengthSource.MANUAL, changed.measurement!!.source)
        assertEquals("25", changed.measurement.length.displayNumber(LengthUnit.MM))
    }

    @Test
    fun failureAndNegativeResultsDoNotInventLength() {
        val original = dimension.withManualLength("50 mm")
        val draft = DimensionValueDraft(original).type("1÷0=")
        assertNull(draft.calculate(draft.input))
        assertEquals("1÷0=", draft.input)
        assertEquals("50 mm", original.displayLabel())
        assertNull(draft.calculate("1000000001+0="))
        assertNull(draft.calculate("1/10000000000000="))
        val negative = draft.calculate("1-2=")!!.confirm(original)!!
        assertEquals("-1", negative.displayLabel())
        assertNull(negative.measurement)
        val zero = draft.calculate("1-1=")!!.confirm(original)!!
        assertEquals("0 mm", zero.displayLabel())
    }
}
