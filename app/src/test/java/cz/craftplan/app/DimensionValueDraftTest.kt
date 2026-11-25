package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensionValueDraftTest {
    private val dimension = PhotoDimension(
        UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH, PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f),
    )

    @Test
    fun conversionExampleDoesNotDependOnOrAlterTheOriginalMeasurement() {
        val original = dimension.withManualLength("123 mm").copy(note = "poznámka")
        val draft = DimensionValueDraft(original)
        val converted = draft.selectUnit(LengthUnit.CM)
        val locked = draft.selectUnit(LengthUnit.CM, keepNumber = true)

        assertEquals("123", draft.input)
        assertEquals("12,3", converted.input)
        assertEquals("123", locked.input)
        assertSame(original.measurement, converted.confirm(original)!!.measurement)
        val reinterpreted = locked.confirm(original)!!
        assertEquals("1230", reinterpreted.measurement!!.length.displayNumber(LengthUnit.MM))
        assertEquals(original.note, reinterpreted.note)
        assertEquals("123", original.measurement!!.length.displayNumber(LengthUnit.MM))
    }

    @Test
    fun micrometerConversionKeepsExactLengthAndOriginalInput() {
        val original = dimension.withManualLength("9665056 mm")
        val converted = DimensionValueDraft(original).selectUnit(LengthUnit.CM)
        assertEquals("966505,6", converted.input)
        val micrometers = converted.selectUnit(LengthUnit.UM)
        assertEquals("9665056000", micrometers.input)
        assertSame(original.measurement, micrometers.confirm(original)!!.measurement)
        assertEquals("966505,6", micrometers.selectUnit(LengthUnit.CM).input)
        assertEquals("9665056 mm", original.measurement!!.originalInput)
    }

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
        assertEquals("0,84667", draft.input)
        val confirmed = draft.confirm(source)!!
        assertSame(source.measurement, confirmed.measurement)
        assertEquals(LengthUnit.CM, confirmed.displayUnit)
        assertEquals("1/3", draft.selectUnit(LengthUnit.IN).input)
        assertEquals("0,84667", DimensionValueDraft(confirmed).input)
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
        for (input in listOf("1/0 in", "1.2,3", "1000000001", "1000000001mm popis", "1".repeat(97))) {
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
        assertEquals("12,5", converted.input)
        assertEquals(ManualLengthMeasurement.parse("1,25", LengthUnit.CM), converted.confirm(dimension)?.measurement)
    }

    @Test
    fun keepingNumberHonorsExplicitUnitsAndRejectsOutOfRangeLengths() {
        val original = dimension.withManualLength("850 mm")
        val draft = DimensionValueDraft(original).type("850 mm")
            .selectUnit(LengthUnit.CM, keepNumber = true)
        assertEquals("850 mm", draft.input)
        assertEquals("850 mm", draft.confirm(original)!!.displayLabel())
        assertEquals(LengthUnit.MM, draft.confirm(original)!!.displayUnit)
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
        assertEquals("1,0625 in převýšení", original.displayLabel())
        assertNull(DimensionValueDraft(dimension).type("1/0 poznámka").confirm(dimension))
        assertNull(DimensionValueDraft(original).withNote("změna").confirm(original.copy(note = "jinde")))
    }

    @Test
    fun repeatingConversionKeepsRationalSourceAndVisibleSuffix() {
        val draft = DimensionValueDraft(dimension).type("1/3 in pracovní deska").selectUnit(LengthUnit.CM)
        assertEquals("0,84667 pracovní deska", draft.input)
        val changed = draft.type("0,84667 montáž").withNote("Přeměřit")
        val converted = changed.selectUnit(LengthUnit.MM).withUnitVisibility(false).confirm(dimension)!!
        assertEquals("≈8,4667 montáž", converted.displayLabel())
        assertEquals("1/3 in", converted.measurement?.originalInput)
        assertEquals("Přeměřit", converted.note)
    }

    @Test
    fun numericSuffixConvertsOnlyTheFirstLengthAndPreservesOtherNumbers() {
        val first = DimensionValueDraft(dimension).type("1000 500 × 600, 12 otvorů")
        val converted = first.selectUnit(LengthUnit.M)
        assertEquals("1 500 × 600, 12 otvorů", converted.input)
        val measured = converted.confirm(dimension)!!
        assertEquals("1000", measured.measurement!!.length.displayNumber(LengthUnit.MM))
        assertEquals("500 × 600, 12 otvorů", measured.labelSuffix)
        assertEquals("1 m 500 × 600, 12 otvorů", measured.displayLabel())
        assertEquals("1000 500 × 600, 12 otvorů", converted.selectUnit(LengthUnit.MM).input)

        val twoNumbers = DimensionValueDraft(dimension).type("1000 500")
        assertEquals("1 500", twoNumbers.selectUnit(LengthUnit.M).input)
        assertEquals("500", twoNumbers.confirm(dimension)!!.labelSuffix)

        val mixed = DimensionValueDraft(dimension).type("1 1/2 in").selectUnit(LengthUnit.MM)
        assertEquals("38,1", mixed.input)
        assertEquals("38.1", mixed.confirm(dimension)!!.measurement!!.length.displayNumber(LengthUnit.MM))
        for (bad in listOf("1 3/2 in", "1 1/0 in", "1 234 mm")) {
            assertNull(bad, DimensionValueDraft(dimension).type(bad).confirm(dimension))
        }
    }

    @Test
    fun invalidLengthBlocksConversionAndPreservesDraftUntilCorrected() {
        val draft = DimensionValueDraft(dimension).type("1000000001 500 × 600")
        val rejected = draft.selectUnit(LengthUnit.M)
        assertSame(draft, rejected)
        assertEquals(LengthUnit.MM, rejected.unit)
        assertNull(rejected.confirm(dimension))

        val corrected = rejected.type("1000000000 500 × 600").selectUnit(LengthUnit.M)
        assertEquals("1000000 500 × 600", corrected.input)
        assertEquals("1000000000", corrected.confirm(dimension)!!.measurement!!.length.displayNumber(LengthUnit.MM))
    }

    @Test
    fun roundedMetricDisplayNeverReplacesExactRationalOrSuffix() {
        val draft = DimensionValueDraft(dimension).type("1/3 in deska 500 × 600")
            .selectUnit(LengthUnit.MM)
        assertEquals("8,4667 deska 500 × 600", draft.input)
        assertTrue(draft.hasApproximateDisplay())
        val labeled = draft.confirm(dimension)!!
        assertEquals("≈8,4667 mm deska 500 × 600", labeled.displayLabel())
        assertEquals(LengthMmEncoding.Rational("127", "15"), labeled.measurement!!.length.encoding)
        assertEquals("1/3 in", labeled.measurement.originalInput)

        val reopened = DimensionValueDraft(labeled)
        assertTrue(reopened.hasApproximateDisplay())
        assertEquals("1/3 deska 500 × 600", reopened.selectUnit(LengthUnit.IN).input)
        assertEquals("8,4668", reopened.type("8,4668").confirm(labeled)!!
            .measurement!!.length.displayNumber(LengthUnit.MM).replace('.', ','))
    }

    @Test
    fun invalidUnitOrExpressionCannotBeReclassifiedAsVisibleSuffix() {
        val original = dimension.withManualLength("850 mm").copy(note = "Ponechat")
        val draft = DimensionValueDraft(original).selectUnit(LengthUnit.CM, keepNumber = true)
        assertEquals("850 mm popis", draft.type("850 mm popis").confirm(original)!!.displayLabel())
        for (bad in listOf("3 ft 1/0 in", "12 MM", " ".repeat(96) + "9")) {
            assertNull(bad, draft.type(bad).confirm(original))
        }
        val edited = DimensionValueDraft(original).withNote("Nová poznámka").confirm(original)!!
        assertSame(original.measurement, edited.measurement)
        assertEquals("850 mm", edited.displayLabel())
        assertNull(DimensionValueDraft(original).withNote("a".repeat(MAX_DIMENSION_NOTE_LENGTH + 1)).confirm(original))
    }

    @Test
    fun symbolsAndExpressionsStayTextWithoutImplicitLengthOrConversion() {
        for (label in listOf("4649%", "4649 %", "25 %", "15 m²", "15m²", "8 m³",
            "45°", "100 + 50", "12−3", "12 x 2", "12 × 2", "12 ÷ 2", "12 = 2",
            "12Ø", "12 +", "85 + zz", "12 kg", "R")) {
            val draft = DimensionValueDraft(dimension).type(label)
            assertTrue(label, draft.isTextOnly())
            for (converted in listOf(draft, draft.selectUnit(LengthUnit.M), draft.selectUnit(LengthUnit.CM, true))) {
                val confirmed = requireNotNull(converted.confirm(dimension))
                assertNull(label, confirmed.measurement)
                assertEquals(label, confirmed.displayLabel())
                assertEquals(label, DimensionValueDraft(confirmed).input)
                assertEquals(MeasurementState.UNMEASURED, confirmed.measurementState)
            }
        }
    }

    @Test
    fun explicitlyTypedLengthWinsAndDiameterMarkerSurvivesConversion() {
        val cm = DimensionValueDraft(dimension).type("125 cm")
        assertEquals(LengthUnit.CM, cm.effectiveDisplayUnit())
        assertEquals("125 cm", cm.confirm(dimension)!!.displayLabel())
        assertEquals("1250 mm v otvoru", DimensionValueDraft(dimension).type("1250 v otvoru").confirm(dimension)!!.displayLabel())
        assertEquals("1250", cm.confirm(dimension)!!.measurement!!.length.displayNumber(LengthUnit.MM))
        assertEquals("1250 mm", cm.selectUnit(LengthUnit.MM).confirm(dimension)!!.displayLabel())
        assertEquals("2,5 m", DimensionValueDraft(dimension).type("2,5 m").confirm(dimension)!!.displayLabel())
        val hidden = DimensionValueDraft(dimension).withUnitVisibility(false).type("125 cm")
        assertEquals("125 cm", hidden.confirm(dimension)!!.displayLabel())
        assertEquals("125", hidden.withUnitVisibility(false).confirm(dimension)!!.displayLabel())
        val diameter = DimensionValueDraft(dimension).type("Ø 50 mm").confirm(dimension)!!
        assertEquals("Ø 50 mm", diameter.displayLabel())
        assertEquals("Ø 50", DimensionValueDraft(diameter).input)
        val converted = DimensionValueDraft(diameter).selectUnit(LengthUnit.CM).confirm(diameter)!!
        assertSame(diameter.measurement, converted.measurement)
        assertEquals("Ø 5 cm", converted.displayLabel())
        val textual = DimensionValueDraft(diameter).type("25 %").confirm(diameter)!!
        assertNull(textual.measurement)
        assertEquals("", textual.labelPrefix)
        assertEquals("25 %", textual.displayLabel())
    }

    @Test
    fun blankAndZeroReopenDifferentlyAndKeepNotesAndAnchors() {
        val blank = DimensionValueDraft(dimension).confirm(dimension)!!
        assertEquals(MeasurementState.UNMEASURED, blank.measurementState)
        val originallyMeasured = dimension.withManualLength("45 mm").copy(note = "parapet")
        val zero = DimensionValueDraft(originallyMeasured).type("0").confirm(originallyMeasured)!!
        assertEquals("0", DimensionValueDraft(zero).input)
        assertEquals("0 mm", zero.displayLabel())
        val cleared = DimensionValueDraft(zero).type("").confirm(zero)!!
        assertEquals("", DimensionValueDraft(cleared).input)
        assertNull(cleared.displayLabel())
        assertEquals(zero.copy(measurement = null), cleared)
        assertEquals("parapet", cleared.visibleNote(true))
        assertNull(cleared.visibleNote(false))
        assertNull(blank.visibleNote(true))
        assertEquals("parapet", DimensionValueDraft(cleared).note)
    }
}
