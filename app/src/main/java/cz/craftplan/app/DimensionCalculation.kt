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

// A contextual suffix needs at least one binary operator. Do not reinterpret
// incomplete arithmetic or a fragment of an attached word/number as an expression.
private val contextualSyntax = Regex(
    """[ ]*[+−-]?[ ]*[0-9]{1,18}(?:[.,][0-9]{1,18})?(?:[ ]*[+−×xX*÷/-][ ]*[+−-]?[ ]*[0-9]{1,18}(?:[.,][0-9]{1,18})?)+[ ]*=""",
)

internal data class CalculationSegment(val prefix: String, val expression: String, val contextual: Boolean)

internal fun findCalculationSegment(input: String): CalculationSegment {
    if (!input.endsWith('=')) return CalculationSegment("", input, false)
    val before = input.dropLast(1)
    val previousEquals = before.lastIndexOf('=')
    var start = previousEquals + 1
    while (input.getOrNull(start) == ' ') start++
    if (input.getOrNull(start) == '≈') start++
    val raw = input.substring(start)
    // A whole expression or a chain uses the entire operand, including malformed
    // expressions (which must report an error, not silently evaluate a suffix).
    if (contextualSyntax.matches(raw) || (isLiteralCalculationLabel(raw) &&
            !Regex("""[0-9] +[0-9]""").containsMatchIn(raw))) {
        return CalculationSegment(input.substring(0, start), raw, false)
    }
    // Longest valid suffix, only at a clear separator. Limit search to the
    // existing arithmetic budget, even in a long note.
    val first = maxOf(start + 1, input.length - ManualLengthMeasurement.MAX_INPUT_LENGTH)
    for (i in first until input.lastIndex) {
        if (!input[i].isDigit() && input[i] !in "+−-") continue
        if (input[i - 1] !in " :;") continue
        val preceding = input.substring(start, i).trimEnd()
        if (preceding.isEmpty() || preceding.last() in "+−-×*÷/.,=") continue
        if (preceding.last() in "xX" && preceding.dropLast(1).trimEnd().lastOrNull()?.isDigit() == true) continue
        val candidate = input.substring(i)
        if (contextualSyntax.matches(candidate)) {
            return CalculationSegment(input.substring(0, i), candidate, true)
        }
    }
    return CalculationSegment(input.substring(0, start), raw, false)
}

@Suppress("UNUSED_PARAMETER")
internal fun calculationSegment(input: String, note: Boolean = false): Pair<String, String> =
    findCalculationSegment(input).let { it.prefix to it.expression }

/** Canonicalize only a successfully evaluated expression, never surrounding text.
 * Unary signs remain attached to their number. */
internal fun formatCalculatedExpression(expression: String): String {
    val tokens = Regex("""[0-9]+(?:[.,][0-9]+)?|[+−×xX*÷/=-]""").findAll(expression)
    val out = StringBuilder()
    var expectsNumber = true
    for (token in tokens) {
        val raw = token.value
        if (raw.first().isDigit()) {
            out.append(raw)
            expectsNumber = false
        } else {
            val symbol = when (raw) {
                "-", "−" -> "−"
                "x", "X", "*" -> "×"
                "/" -> "÷"
                else -> raw
            }
            if (expectsNumber && symbol in listOf("+", "−")) out.append(symbol)
            else {
                out.append(' ').append(symbol)
                if (symbol != "=") out.append(' ')
            }
            expectsNumber = true
        }
    }
    return out.toString()
}

/** An insertion of a final equals, not backspace, selection or cursor movement. */
internal fun requestsDimensionCalculation(
    previous: String, next: String, enabled: Boolean, note: Boolean = false,
): Boolean = enabled && previous != next && next.endsWith('=') &&
    next.count { it == '=' } > previous.count { it == '=' } &&
    isLiteralCalculationLabel(calculationSegment(next, note).second)

/** A final, newly inserted equals is explicit even if the IME still reports
 * an active composing region. Require the caret after it and never evaluate
 * selection changes or an unchanged final equals. */
internal fun requestsCalculationFromEdit(
    previous: String, next: String, selectionStart: Int, selectionEnd: Int,
    enabled: Boolean, note: Boolean = false,
): Boolean = selectionStart == selectionEnd && selectionEnd == next.length &&
    requestsDimensionCalculation(previous, next, enabled, note)

/** Preserve an earlier approximation while the user extends its visible result.
 * Only an edit to the actual rounded number removes its marker. */
internal fun editedApproximation(previous: String, next: String): String {
    val equals = previous.lastIndexOf('=')
    if (equals < 0 || !previous.substring(equals + 1).trimStart().startsWith('≈')) return next
    val marker = equals + 1 + previous.substring(equals + 1).takeWhile { it == ' ' }.length
    if (!next.startsWith(previous.substring(0, marker + 1))) return next
    val oldNumber = previous.substring(marker + 1)
    if (!Regex("""[-−]?[0-9]+(?:[.,][0-9]+)?""").matches(oldNumber)) return next
    val tail = next.substring(marker + 1)
    if (tail == oldNumber) return next
    if (tail.startsWith(oldNumber) &&
        tail.drop(oldNumber.length).trimStart().firstOrNull() in listOf('+', '-', '−', '×', 'x', 'X', '*', '÷', '/')) return next
    // Appending whitespace before the next operator is also not an edit.
    if (tail.startsWith(oldNumber) && tail.drop(oldNumber.length).isBlank()) return next
    return next.removeRange(marker, marker + 1)
}

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

/** Remove only the marker in the last result, not a previous rounded operand. */
internal fun removeResultApproximation(text: String): String {
    val equals = text.lastIndexOf('=')
    if (equals < 0) return text
    val marker = equals + 1 + text.substring(equals + 1).takeWhile { it == ' ' }.length
    return if (text.getOrNull(marker) == '≈') text.removeRange(marker, marker + 1) else text
}
