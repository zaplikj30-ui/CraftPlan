package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DimensionValueDraftTest {
    private val dimension = PhotoDimension(
        UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH, PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f),
    )

    @Test
    fun confirmsZeroAndPreservesLatestGeometry() {
        val moved = dimension.copy(end = PhotoAnchor(1.2f, 1f))
        val confirmed = DimensionValueDraft(dimension).type("0").confirm(moved)!!
        assertEquals(moved.copy(measurement = ManualLengthMeasurement.parse("0", LengthUnit.MM)), confirmed)
        assertEquals(MeasurementState.MEASURED, confirmed.measurementState)
        assertNull(dimension.measurement)
    }

    @Test
    fun unitSwitchPreservesOriginalAndDoesNotReparseMetricFraction() {
        val source = dimension.withManualLength(" 1/3 in ")
        val draft = DimensionValueDraft(source).selectUnit(LengthUnit.CM)
        assertEquals("127/150", draft.input)
        val confirmed = draft.confirm(source)!!
        assertSame(source.measurement, confirmed.measurement)
        assertEquals(LengthUnit.CM, confirmed.displayUnit)
        assertEquals("1/3", draft.selectUnit(LengthUnit.IN).input)
        assertEquals("127/150", DimensionValueDraft(confirmed).input)
    }

    @Test
    fun enteredNumbersAndExplicitUnitsConvertWithoutLosingProvenance() {
        val original = DimensionValueDraft(dimension).type("850").selectUnit(LengthUnit.CM)
        assertEquals("85", original.input)
        assertEquals("850", original.confirm(dimension)!!.measurement!!.originalInput)
        val edited = original.type("95,5").confirm(dimension)!!
        assertEquals(LengthMmEncoding.Decimal("955"), edited.measurement!!.length.encoding)
        assertEquals(OriginalLengthUnit.Single(LengthUnit.CM), edited.measurement.originalUnit)

        val compound = DimensionValueDraft(dimension).type("3' 4 1/2\"").confirm(dimension)!!
        assertEquals(OriginalLengthUnit.FeetAndInches, compound.measurement!!.originalUnit)
        assertEquals("1028.7", compound.measurement.length.displayNumber(compound.displayUnit))
    }

    @Test
    fun rejectsInvalidAndStaleConfirmation() {
        val original = dimension.withManualLength("850 mm")
        val draft = DimensionValueDraft(original).type("950")
        for (input in listOf("", "1/0 in", "1.2,3", "85 zz", "1000000001", "1".repeat(97))) {
            assertNull(input, draft.type(input).confirm(original))
        }
        assertNull(draft.confirm(original.copy(id = UUID.randomUUID())))
        assertNull(draft.confirm(original.withManualLength("900 mm")))
        assertEquals("850 mm", original.measurement!!.originalInput)
    }
}
