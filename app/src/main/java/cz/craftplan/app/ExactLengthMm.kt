package cz.craftplan.app

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

internal enum class LengthUnit(val symbol: String, val mmNumerator: Long, val mmDenominator: Long = 1) {
    MM("mm", 1),
    CM("cm", 10),
    M("m", 1000),
    IN("in", 127, 5),
    FT("ft", 1524, 5),
}

internal sealed interface LengthMmEncoding {
    val tag: String

    data class Decimal(val value: String) : LengthMmEncoding {
        override val tag = "decimal-mm"
    }

    data class Rational(val numerator: String, val denominator: String) : LengthMmEncoding {
        override val tag = "rational-mm"
    }
}

/** Presentation only. The stored millimeter value remains exact. */
internal data class DisplayLengthNumber(val text: String, val approximate: Boolean)

@ConsistentCopyVisibility
internal data class ExactLengthMm private constructor(val encoding: LengthMmEncoding) {
    /** Converts for display without rewriting the authoritative millimeter value. */
    fun displayNumber(unit: LengthUnit): String {
        val mm = when (val value = encoding) {
            is LengthMmEncoding.Decimal -> LengthFraction.decimal(value.value)
            is LengthMmEncoding.Rational -> LengthFraction.of(
                BigInteger(value.numerator), BigInteger(value.denominator),
            )
        }
        val converted = mm * LengthFraction.of(unit.mmDenominator, unit.mmNumerator)
        return if (converted.terminatingScale() == null) {
            "${converted.numerator}/${converted.denominator}"
        } else {
            converted.toDecimal().toPlainString()
        }
    }

    /** Rounded metric preview only; never rewrite the exact rational [encoding]. */
    fun displayNumberForUi(unit: LengthUnit): DisplayLengthNumber {
        val exact = displayNumber(unit)
        if (unit == LengthUnit.IN || unit == LengthUnit.FT || '/' !in exact) {
            return DisplayLengthNumber(exact, false)
        }
        val (numerator, denominator) = exact.split('/')
        // Four fractional millimeter places, adapted to the selected metric unit.
        val decimals = when (unit) {
            LengthUnit.MM -> 4
            LengthUnit.CM -> 5
            LengthUnit.M -> 7
            else -> error("Not a metric unit")
        }
        val rounded = BigDecimal(numerator).divide(
            BigDecimal(denominator), decimals, RoundingMode.HALF_UP,
        )
        // A nonzero exact value must never be shown as 0.
        if (rounded.signum() == 0) return DisplayLengthNumber(exact, false)
        return DisplayLengthNumber(
            rounded.stripTrailingZeros().toPlainString().replace('.', ','), true,
        )
    }

    companion object {
        fun fromMillimeters(mm: LengthFraction): ExactLengthMm {
            require(mm.numerator <= bounded(mm.denominator * BigInteger.valueOf(1_000_000_000)))
            val scale = mm.terminatingScale()
            val encoding = if (scale != null) {
                require(scale <= 12)
                val decimal = mm.toDecimal()
                require(decimal.precision() <= 18)
                LengthMmEncoding.Decimal(decimal.toPlainString())
            } else {
                val numerator = mm.numerator.toString()
                val denominator = mm.denominator.toString()
                require(numerator.length <= 32 && denominator.length <= 32)
                LengthMmEncoding.Rational(numerator, denominator)
            }
            return ExactLengthMm(encoding)
        }
    }
}

/** Positive-denominator arithmetic for manual length input and display conversion. */
internal class LengthFraction private constructor(val numerator: BigInteger, val denominator: BigInteger) {
    operator fun plus(other: LengthFraction): LengthFraction = of(
        bounded(numerator * other.denominator) + bounded(other.numerator * denominator),
        bounded(denominator * other.denominator),
    )

    operator fun times(other: LengthFraction): LengthFraction =
        of(bounded(numerator * other.numerator), bounded(denominator * other.denominator))

    fun terminatingScale(): Int? {
        var rest = denominator
        val twos = rest.lowestSetBit
        rest = rest.shiftRight(twos)
        var fives = 0
        while (rest.mod(FIVE) == BigInteger.ZERO) {
            rest /= FIVE
            fives++
        }
        return if (rest == BigInteger.ONE) maxOf(twos, fives) else null
    }

    fun toDecimal(): BigDecimal =
        BigDecimal(numerator).divide(BigDecimal(denominator)).stripTrailingZeros()

    companion object {
        private val FIVE = BigInteger.valueOf(5)

        fun of(numerator: Long, denominator: Long): LengthFraction =
            of(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator))

        fun of(numerator: BigInteger, denominator: BigInteger): LengthFraction {
            require(numerator.signum() >= 0 && denominator.signum() > 0)
            val divisor = bounded(numerator).gcd(bounded(denominator))
            return LengthFraction(numerator / divisor, denominator / divisor)
        }

        fun decimal(ascii: String): LengthFraction {
            require(ascii.length <= 37)
            val decimal = BigDecimal(ascii)
            require(decimal.scale() in 0..18)
            return of(decimal.unscaledValue(), bounded(BigInteger.TEN.pow(decimal.scale())))
        }
    }
}

private fun bounded(value: BigInteger): BigInteger {
    require(value.bitLength() <= 256)
    return value
}
