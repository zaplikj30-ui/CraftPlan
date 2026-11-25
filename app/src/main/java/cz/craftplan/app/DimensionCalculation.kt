package cz.craftplan.app

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Exact, bounded arithmetic for the editable value field. No evaluation of arbitrary code. */
@ConsistentCopyVisibility
internal data class DimensionCalculation private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger,
) {
    operator fun plus(other: DimensionCalculation) = of(
        bound(bound(numerator * other.denominator) + bound(other.numerator * denominator)),
        bound(denominator * other.denominator),
    )
    operator fun minus(other: DimensionCalculation) = this + of(-other.numerator, other.denominator)
    operator fun times(other: DimensionCalculation) = of(
        bound(numerator * other.numerator), bound(denominator * other.denominator),
    )
    operator fun div(other: DimensionCalculation) = of(
        bound(numerator * other.denominator), bound(denominator * other.numerator),
    )

    fun rounded(places: Int): DisplayLengthNumber {
        require(places in 0..12)
        val number = BigDecimal(numerator).divide(BigDecimal(denominator), places, RoundingMode.HALF_UP)
        val exact = number.unscaledValue() * denominator == numerator * BigInteger.TEN.pow(places)
        return DisplayLengthNumber(number.stripTrailingZeros().toPlainString().replace('.', ','), !exact)
    }

    fun editableText(): String {
        val exact = try {
            BigDecimal(numerator).divide(BigDecimal(denominator)).stripTrailingZeros().toPlainString()
                .replace('.', ',')
        } catch (_: ArithmeticException) {
            "$numerator/$denominator"
        }
        require(exact.length <= ManualLengthMeasurement.MAX_INPUT_LENGTH)
        return exact
    }

    companion object {
        private fun bound(value: BigInteger): BigInteger {
            require(value.bitLength() <= 256)
            return value
        }
        private fun of(n: BigInteger, d: BigInteger): DimensionCalculation {
            require(d.signum() != 0)
            val divisor = bound(n).gcd(bound(d))
            val sign = BigInteger.valueOf(d.signum().toLong())
            return DimensionCalculation(n / divisor * sign, d / divisor * sign)
        }
        fun decimal(text: String): DimensionCalculation {
            val decimal = BigDecimal(text.replace(',', '.'))
            return of(decimal.unscaledValue(), bound(BigInteger.TEN.pow(decimal.scale())))
        }
    }
}

private val calculationCharacters = Regex("""[0-9., +\-−×xX*÷/]+""")
private val calculationNumber = Regex("""[0-9]{1,18}(?:[.,][0-9]{1,18})?""")

/** Returns null for non-math, malformed input, zero division, or resource limits. */
internal fun evaluateDimensionCalculation(input: String): DimensionCalculation? {
    if (input.length > ManualLengthMeasurement.MAX_INPUT_LENGTH || !input.endsWith('=')) return null
    val body = input.dropLast(1)
    if (!calculationCharacters.matches(body)) return null
    return try {
        val parser = CalculationParser(body)
        parser.parse()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}

private class CalculationParser(private val text: String) {
    private var index = 0
    private var terms = 0
    private fun skipSpace() { while (text.getOrNull(index) == ' ') index++ }
    private fun next(): Char? { skipSpace(); return text.getOrNull(index) }
    private fun number(): DimensionCalculation {
        require(++terms <= 24)
        val sign = when (next()) {
            '-', '−' -> { index++; "-" }
            '+' -> { index++; "" }
            else -> ""
        }
        skipSpace()
        val token = calculationNumber.find(text, index)
        require(token != null && token.range.first == index)
        index = token.range.last + 1
        return DimensionCalculation.decimal(sign + token.value)
    }
    private fun product(): DimensionCalculation {
        var result = number()
        while (true) {
            val operator = next()
            if (operator !in listOf('×', 'x', 'X', '*', '÷', '/')) return result
            index++
            val right = number()
            result = if (operator == '÷' || operator == '/') result / right else result * right
        }
    }
    fun parse(): DimensionCalculation {
        var result = product()
        while (true) {
            val operator = next() ?: break
            require(operator == '+' || operator == '-' || operator == '−')
            index++
            val right = product()
            result = if (operator == '+') result + right else result - right
        }
        require(index == text.length)
        return result
    }
}

/** An expression deliberately retained with a literal equals is never a length. */
internal fun isLiteralCalculationLabel(input: String): Boolean =
    input.endsWith('=') && calculationCharacters.matches(input.dropLast(1))

internal const val DEFAULT_CALCULATION_PLACES = 3

/** The last segment after an earlier equals is the next calculation.
 * A note can also start an expression after a colon or semicolon. */
internal fun calculationSegment(input: String, note: Boolean = false): Pair<String, String> {
    val beforeEquals = input.dropLast(1)
    val previousEquals = beforeEquals.lastIndexOf('=')
    val separator = if (note) maxOf(previousEquals, beforeEquals.lastIndexOf(':'), beforeEquals.lastIndexOf(';'))
        else previousEquals
    val start = separator + 1
    val raw = input.substring(start)
    val leading = raw.takeWhile { it == ' ' }
    val marker = if (raw.drop(leading.length).startsWith('≈')) 1 else 0
    return input.substring(0, start) + leading to raw.drop(leading.length + marker)
}

/** An insertion of a final equals, not backspace, selection or cursor movement. */
internal fun requestsDimensionCalculation(
    previous: String, next: String, enabled: Boolean, note: Boolean = false,
): Boolean = enabled && previous != next && next.endsWith('=') &&
    next.count { it == '=' } > previous.count { it == '=' } &&
    isLiteralCalculationLabel(calculationSegment(next, note).second)

/** Replayable result of a single equals action. The typed expression remains
 * editable; the rounded decimal is the authoritative new calculation result. */
internal data class RoundedCalculation(
    val expression: String,
    val number: String,
    val approximate: Boolean,
    val places: Int,
) {
    val markedNumber: String get() = (if (approximate) "≈" else "") + number
}

internal fun roundedCalculation(expression: String, places: Int): RoundedCalculation? {
    if (places !in 0..12) return null
    val result = evaluateDimensionCalculation(expression) ?: return null
    val shown = result.rounded(places)
    // Never disguise a nonzero length as a measured zero; keep a marked text
    // result instead, and let the user raise precision or type their own number.
    if (shown.text.length > ManualLengthMeasurement.MAX_INPUT_LENGTH) return null
    return RoundedCalculation(expression, shown.text, shown.approximate, places)
}
