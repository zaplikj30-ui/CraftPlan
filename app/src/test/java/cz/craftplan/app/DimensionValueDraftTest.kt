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
        for (input in listOf("", "1/0 in", "1.2,3", "85 + zz", "1000000001", "1".repeat(97))) {
            assertNull(input, draft.type(input).confirm(original))
        }
        assertNull(draft.confirm(original.copy(id = UUID.randomUUID())))
        assertNull(draft.confirm(original.withManualLength("900 mm")))
        assertEquals("850 mm", original.measurement!!.originalInput)
    }

    @Test
    fun keepingNumberChangesMeasurementOnlyAfterConfirmation() {
        val original = dimension.withManualLength("850 mm")
        val draft = DimensionValueDraft(original).selectUnit(LengthUnit.CM, keepNumber = true)
        assertEquals("850", draft.input)
        assertEquals(LengthMmEncoding.Decimal("850"), original.measurement!!.length.encoding)
        assertEquals("850 mm", original.measurement.originalInput)

        val confirmed = draft.confirm(original)!!
        val corrected = ManualLengthMeasurement.parse("850", LengthUnit.CM)!!
        assertEquals(LengthMmEncoding.Decimal("8500"), corrected.length.encoding)
        assertEquals(original.copy(measurement = corrected, displayUnit = LengthUnit.CM), confirmed)
    }

    @Test
    fun keepsTypedTextAndConvertsCorrectedDraftExactlyWhenSwitchedBackToConversion() {
        val draft = DimensionValueDraft(dimension).type("1,25")
            .selectUnit(LengthUnit.CM, keepNumber = true)
        assertEquals("1,25", draft.input)
        val converted = draft.selectUnit(LengthUnit.MM)
        assertEquals("12.5", converted.input)
        assertEquals(ManualLengthMeasurement.parse("1,25", LengthUnit.CM), converted.confirm(dimension)?.measurement)
    }

    @Test
    fun keepingNumberRejectsConflictingUnitsAndOutOfRangeLengths() {
        val original = dimension.withManualLength("850 mm")
        val draft = DimensionValueDraft(original).type("850 mm")
            .selectUnit(LengthUnit.CM, keepNumber = true)
        assertEquals("850 mm", draft.input)
        assertNull(draft.confirm(original))
        assertNull(draft.type("100000001").confirm(original))
        assertEquals(LengthMmEncoding.Decimal("8500"), draft.type("850").confirm(original)?.measurement?.length?.encoding)
    }
    @Test
    fun mixedVisibleTextAndExactValueStaySeparate() {
        val numeric = DimensionValueDraft(dimension).type("1250 výška pracovní desky")
            .withNote("Přeměřit po montáži").confirm(dimension)!!
        assertEquals(MeasurementState.MEASURED, numeric.measurementState)
        assertEquals("1250", numeric.measurement!!.length.displayNumber(LengthUnit.MM))
        assertEquals("1250 mm výška pracovní desky", numeric.displayLabel())
        assertEquals("Přeměřit po montáži", numeric.note)
        assertEquals("1250 výška pracovní desky", DimensionValueDraft(numeric).input)

        val noUnit = DimensionValueDraft(numeric).withUnitVisibility(false).confirm(numeric)!!
        assertEquals("1250 výška pracovní desky", noUnit.displayLabel())
        assertEquals(numeric.measurement, noUnit.measurement)
        assertEquals(LengthUnit.MM, noUnit.displayUnit)
    }

    @Test
    fun textOnlyAndNoteDoNotInventMeasurements() {
        val text = DimensionValueDraft(dimension).type("PŘEMĚŘIT")
            .withNote("Soukromá poznámka").confirm(dimension)!!
        assertNull(text.measurement)
        assertEquals(MeasurementState.UNMEASURED, text.measurementState)
        assertEquals("PŘEMĚŘIT", text.displayLabel())
        assertEquals("Soukromá poznámka", text.note)
        val noteOnly = DimensionValueDraft(dimension).withNote("poznámka").confirm(dimension)!!
        assertNull(noteOnly.displayLabel())
        assertNull(noteOnly.measurement)
        assertEquals("poznámka", noteOnly.note)
    }

    @Test
    fun mixedFractionsConvertAndBadNumericExpressionsAreRejected() {
        val original = DimensionValueDraft(dimension).type("1 1/16 převýšení")
            .selectUnit(LengthUnit.IN, keepNumber = true).confirm(dimension)!!
        assertEquals("26.9875", original.measurement!!.length.displayNumber(LengthUnit.MM))
        assertEquals("1.0625 in převýšení", original.displayLabel())
        for (bad in listOf("12%", "12 + 2", "12−3", "1/0 poznámka")) {
            assertNull(bad, DimensionValueDraft(dimension).type(bad).confirm(dimension))
        }
        assertNull(DimensionValueDraft(original).withNote("změna").confirm(original.copy(note = "jinde")))
    }
}
