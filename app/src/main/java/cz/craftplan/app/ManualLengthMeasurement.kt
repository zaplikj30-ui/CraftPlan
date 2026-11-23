package cz.craftplan.app

import java.math.BigInteger

internal sealed interface OriginalLengthUnit {
    val version: Int get() = 1

    data class Single(val unit: LengthUnit) : OriginalLengthUnit
    data object FeetAndInches : OriginalLengthUnit
}

internal enum class LengthSource { MANUAL }

@ConsistentCopyVisibility
internal data class ManualLengthMeasurement private constructor(
    val length: ExactLengthMm,
    val originalInput: String,
    val originalUnit: OriginalLengthUnit,
) {
    val source = LengthSource.MANUAL

    companion object {
        const val MAX_INPUT_LENGTH = 96
        // Anchored ASCII grammar. Spaces can separate parts; they cannot group digits.
        private const val DIGITS = "[0-9]{1,18}"
        private const val DECIMAL = "$DIGITS(?:[.,]$DIGITS)?"
        private const val INCH_NUMBER = "(?:${DIGITS} +)?$DIGITS/$DIGITS|$DECIMAL"
        private val feetAndInches = Regex("($DECIMAL) *(?:ft|') *($INCH_NUMBER) *(?:in|\")")
        private val single = Regex("($INCH_NUMBER) *(mm|cm|m|in|ft|'|\")?")
        private val fraction = Regex("(?:($DIGITS) +)?($DIGITS)/($DIGITS)")

        /**
         * Null means invalid input. A dot or comma is a decimal mark, never a grouping mark.
         * With an explicit unit, use the text alone or supply the same unit as the picker.
         */
        fun parse(input: String, selectedUnit: LengthUnit? = null): ManualLengthMeasurement? {
            if (input.length > MAX_INPUT_LENGTH || ('.' in input && ',' in input)) return null
            val text = input.trim(' ')
            return try {
                val compound = feetAndInches.matchEntire(text)
                if (compound != null) {
                    if (selectedUnit != null) return null
                    val (feet, inches) = compound.destructured
                    val mm = millimeters(feet, LengthUnit.FT) + millimeters(inches, LengthUnit.IN)
                    return ManualLengthMeasurement(
                        ExactLengthMm.fromMillimeters(mm), input, OriginalLengthUnit.FeetAndInches,
                    )
                }
                val (number, suffix) = single.matchEntire(text)?.destructured ?: return null
                val explicitUnit = when (suffix) {
                    "" -> null
                    "'" -> LengthUnit.FT
                    "\"" -> LengthUnit.IN
                    else -> LengthUnit.entries.first { it.symbol == suffix }
                }
                if (selectedUnit != null && explicitUnit != null && selectedUnit != explicitUnit) return null
                val unit = explicitUnit ?: selectedUnit ?: return null
                if ('/' in number && unit != LengthUnit.IN) return null
                val mm = millimeters(number, unit)
                ManualLengthMeasurement(ExactLengthMm.fromMillimeters(mm), input, OriginalLengthUnit.Single(unit))
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun millimeters(number: String, unit: LengthUnit): LengthFraction {
            val parts = fraction.matchEntire(number)
            val magnitude = if (parts != null) {
                val (wholeText, numeratorText, denominatorText) = parts.destructured
                val properOrSimple = LengthFraction.of(BigInteger(numeratorText), BigInteger(denominatorText))
                if (wholeText.isEmpty()) properOrSimple
                else {
                    require(BigInteger(numeratorText) < BigInteger(denominatorText))
                    LengthFraction.of(BigInteger(wholeText), BigInteger.ONE) + properOrSimple
                }
            } else {
                LengthFraction.decimal(number.replace(',', '.'))
            }
            return magnitude * LengthFraction.of(unit.mmNumerator, unit.mmDenominator)
        }
    }
}
