package cz.craftplan.app

import java.math.BigInteger
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ExactLengthTest {
    @Test
    fun canonicalLengthsUseExactDecimalOrReducedRationalMillimeters() {
        val examples = mapOf(
            "850,5 mm" to LengthMmEncoding.Decimal("850.5"),
            "85 cm" to LengthMmEncoding.Decimal("850"),
            "1.2 m" to LengthMmEncoding.Decimal("1200"),
            "1.25 in" to LengthMmEncoding.Decimal("31.75"),
            "3 ft" to LengthMmEncoding.Decimal("914.4"),
            "1/16 in" to LengthMmEncoding.Decimal("1.5875"),
            "1/3 in" to LengthMmEncoding.Rational("127", "15"),
            "2/6 in" to LengthMmEncoding.Rational("127", "15"),
            "1 1/2 in" to LengthMmEncoding.Decimal("38.1"),
            "3 ft 4 1/2 in" to LengthMmEncoding.Decimal("1028.7"),
            "3' 4 1/2\"" to LengthMmEncoding.Decimal("1028.7"),
        )
        for ((input, expected) in examples) {
            assertEquals(input, expected, ManualLengthMeasurement.parse(input)?.length?.encoding)
        }
    }

    @Test
    fun recordsOriginalTextUnitAndManualSourceIndependentlyOfDisplay() {
        val text = " 85,00 "
        val measurement = requireNotNull(ManualLengthMeasurement.parse(text, LengthUnit.CM))
        val length = measurement.length
        assertEquals(text, measurement.originalInput)
        assertEquals(OriginalLengthUnit.Single(LengthUnit.CM), measurement.originalUnit)
        assertEquals(LengthSource.MANUAL, measurement.source)
        assertEquals("850", length.displayNumber(LengthUnit.MM))
        assertEquals("85", length.displayNumber(LengthUnit.CM))
        assertEquals("4250/127", length.displayNumber(LengthUnit.IN))
        assertEquals("0.85", length.displayNumber(LengthUnit.M))
        assertEquals(LengthMmEncoding.Decimal("850"), length.encoding)
        assertEquals(length, measurement.length)

        val compound = "3 ft 4 1/2 in"
        val compoundMeasurement = requireNotNull(ManualLengthMeasurement.parse(compound))
        assertEquals(compound, compoundMeasurement.originalInput)
        assertEquals(OriginalLengthUnit.FeetAndInches, compoundMeasurement.originalUnit)
        val third = requireNotNull(ManualLengthMeasurement.parse("1/3 in")).length
        assertEquals("1/3", third.displayNumber(LengthUnit.IN))
        assertEquals("1/36", third.displayNumber(LengthUnit.FT))
    }

    @Test
    fun explicitZeroAndAbsenceStayDistinctAndGeometryCopiesPreserveMeasurement() {
        val empty = PhotoDimension(
            UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
            PhotoAnchor(0.1f, 0.2f), PhotoAnchor(0.6f, 0.2f),
        )
        val measured = empty.withManualLength("0.0000", LengthUnit.MM)
        assertNull(empty.measurement)
        assertEquals(MeasurementState.UNMEASURED, empty.measurementState)
        assertEquals(MeasurementState.MEASURED, measured.measurementState)
        assertEquals(LengthMmEncoding.Decimal("0"), measured.measurement?.length?.encoding)
        assertEquals(empty.start, measured.start)
        assertEquals(empty.end, measured.end)

        val exact = measured.withManualLength("1/3 in")
        for (hit in DimensionHit.entries) {
            val edited = exact.move(hit, 0.1f, 0.2f)
            assertSame(exact.measurement, edited.measurement)
            assertEquals(empty.id, edited.id)
            assertEquals(empty.photoId, edited.photoId)
        }
    }

    @Test
    fun syntaxAndNumericLimitsRejectWithoutReplacingTheLastValidLength() {
        val dimension = PhotoDimension(
            UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH, PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f),
        ).withManualLength("85 cm")
        val invalid = listOf(
            "", "85", "-1 mm", "+1 mm", "1e3 mm", "NaN mm", "1 dm", "1 234 mm",
            "1,234.56 mm", "1.234.56 mm", "1/0 in", "1/3 cm", "1 3/2 in", "４ mm",
            "2 in 3 ft", "1 ft 2 in 3 in", "1 ft 4", "3.1 ft 4,2 in",
            "0000000000000000001 mm", " ".repeat(93) + "1 mm",
            "1000000000.1 mm", "0.0000000000001 mm", "1234567.123456789012 mm",
            "3.000000000000000001 ft 1/999999999999999989 in",
        )
        for (input in invalid) {
            assertNull(input, ManualLengthMeasurement.parse(input))
            assertSame(input, dimension, dimension.withManualLength(input))
        }
        assertSame(dimension, dimension.withManualLength("85 cm", LengthUnit.MM))
        assertEquals(
            LengthMmEncoding.Decimal("1000000000"),
            ManualLengthMeasurement.parse("1000000000 mm")?.length?.encoding,
        )
        assertEquals(
            LengthMmEncoding.Decimal("0.000000000001"),
            ManualLengthMeasurement.parse("0.000000000001 mm")?.length?.encoding,
        )
        assertEquals(
            LengthMmEncoding.Decimal("123456.123456789012"),
            ManualLengthMeasurement.parse("123456.123456789012 mm")?.length?.encoding,
        )
    }

    @Test
    fun roundedMetricFractionIsPresentationOnlyAndExactZeroIsNotInvented() {
        val third = ManualLengthMeasurement.parse("1/3 in")!!.length
        assertEquals(DisplayLengthNumber("8,4667", true), third.displayNumberForUi(LengthUnit.MM))
        assertEquals(DisplayLengthNumber("0,84667", true), third.displayNumberForUi(LengthUnit.CM))
        assertEquals(DisplayLengthNumber("0,0084667", true), third.displayNumberForUi(LengthUnit.M))
        assertEquals(DisplayLengthNumber("1/3", false), third.displayNumberForUi(LengthUnit.IN))
        assertEquals("127/15", third.displayNumber(LengthUnit.MM))
        assertEquals(LengthMmEncoding.Rational("127", "15"), third.encoding)
        assertTrue(third.displayNumberForUi(LengthUnit.MM).approximate)
    }

    @Test
    fun oversizedArithmeticIsRejectedBeforeReduction() {
        val large = BigInteger.ONE.shiftLeft(130)
        val one = BigInteger.ONE
        assertThrows(IllegalArgumentException::class.java) {
            val left = LengthFraction.of(large, one)
            val right = LengthFraction.of(one, large)
            // Both terms fit separately, but forming their sum requires a product over 256 bits.
            left + right
        }
        assertThrows(IllegalArgumentException::class.java) {
            val oversized = BigInteger.ONE.shiftLeft(256)
            LengthFraction.of(oversized, oversized)
        }
    }
}
