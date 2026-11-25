package cz.craftplan.app

/** A parsed label has either an exact measurement with optional visible text, or only text. */
internal sealed interface DimensionEntry {
    data class Measured(
        val value: ManualLengthMeasurement,
        val suffix: String,
        val displayUnit: LengthUnit,
        val prefix: String = "",
        val explicitlyTypedUnit: Boolean = false,
    ) : DimensionEntry {
        fun inputText(unit: LengthUnit): String = listOf(prefix, value.length.displayNumberForUi(unit).text, suffix)
            .filter(String::isNotBlank).joinToString(" ")
    }
    data class TextOnly(val text: String) : DimensionEntry
}

private fun isLabelSuffix(text: String): Boolean {
    if (text.firstOrNull()?.isDigit() == true) {
        // Another number may start the description, but not an invalid mixed fraction.
        val firstToken = text.takeWhile { !it.isWhitespace() }
        if ('/' in firstToken) return false
        val parts = text.trim().split(Regex("\\s+"))
        // Preserve rejection of ambiguous digit grouping / an extra explicit measurement.
        if (parts.size == 2 && parts[0].all(Char::isDigit) &&
            LengthUnit.entries.any { it.symbol.equals(parts[1], ignoreCase = true) }
        ) return false
        return true
    }
    if (text.firstOrNull()?.isLetter() != true) return false
    val firstWord = text.takeWhile(Char::isLetter)
    // Recognized unit tokens and arithmetic must not rescue a failed numerical parse.
    return LengthUnit.entries.none { it.symbol.equals(firstWord, ignoreCase = true) } &&
        !(firstWord.equals("x", ignoreCase = true) && text.drop(firstWord.length).trimStart()
            .firstOrNull()?.isDigit() == true)
}

// These are display annotations, not length units or calculations. The exact
// length parser is deliberately unchanged.
private val nonLengthUnit = Regex(
    """^(?:[µμmcdk]?m[²³23]|%|‰|°|kg|g|mg|l|ml|cl|dl|Pa|bar|W|kW|V|A|Hz)(?=$|[\s.,;:])""",
)
private val annotationNumber = Regex("""^[0-9]+(?:[.,][0-9]+)?""")
private fun isNonLengthTail(text: String): Boolean {
    val tail = text.trimStart()
    return tail.isNotEmpty() && (
        nonLengthUnit.containsMatchIn(tail) ||
        tail.first() in "%‰°+−-×÷=±*<>≤≥!?:;()[]{}Ø" ||
        Regex("""^[xX](?=\s*[0-9])""").containsMatchIn(tail)
    )
}

private fun isExplicitTextLabel(text: String): Boolean {
    if (!text.first().isDigit()) return true
    val number = annotationNumber.find(text)?.value ?: return false
    val tail = text.drop(number.length)
    // Do not turn a malformed number, fraction, or overflowing bare length
    // into a successful length (or silently rescue it as a label).
    if (isNonLengthTail(tail)) return true
    if (tail.isNotEmpty() && !tail.first().isWhitespace() && tail.first().isLetter()) {
        // Attached descriptive text is a label, but an actual length unit
        // remains subject to exact validation.
        val firstWord = tail.takeWhile(Char::isLetter)
        return LengthUnit.entries.none { firstWord.equals(it.symbol, ignoreCase = true) }
    }
    return false
}

/** Only an unambiguous exact length becomes a measurement. */
internal fun parseDimensionEntry(input: String, unit: LengthUnit): DimensionEntry? {
    if (input.isBlank() || input.length > ManualLengthMeasurement.MAX_INPUT_LENGTH ||
        input.any { it == '\r' || it == '\n' || it == '\u0000' }) return null
    val trimmed = input.trim()
    val prefix = if (trimmed.startsWith("Ø")) "Ø" else ""
    val body = if (prefix.isEmpty()) trimmed else trimmed.drop(1).trimStart()
    if (body.isEmpty()) return DimensionEntry.TextOnly(trimmed)

    fun parseNumber(number: String): DimensionEntry.Measured? {
        val explicit = ManualLengthMeasurement.parse(number)
        if (explicit != null) {
            val display = (explicit.originalUnit as? OriginalLengthUnit.Single)?.unit ?: unit
            return DimensionEntry.Measured(explicit, "", display, prefix, explicitlyTypedUnit = true)
        }
        val implicit = ManualLengthMeasurement.parse(number, unit) ?: return null
        return DimensionEntry.Measured(implicit, "", unit, prefix)
    }

    parseNumber(body)?.let { return it }
    // Longest prefix first, including explicit units and compound imperial lengths.
    for (i in body.indices.reversed()) {
        if (!body[i].isWhitespace()) continue
        val number = body.substring(0, i).trimEnd()
        val suffix = body.substring(i).trim()
        if (suffix.isEmpty()) continue
        parseNumber(number)?.let {
            if (isNonLengthTail(suffix)) return DimensionEntry.TextOnly(trimmed)
            return if (isLabelSuffix(suffix)) it.copy(suffix = suffix) else null
        }
    }
    return if (isExplicitTextLabel(body)) DimensionEntry.TextOnly(trimmed) else null
}

/** A pending edit. Only confirmation changes the dimension or its note. */
internal class DimensionValueDraft private constructor(
    private val target: PhotoDimension,
    val input: String,
    val unit: LengthUnit,
    private val unchangedEntry: DimensionEntry.Measured?,
    val note: String = "",
    val showUnit: Boolean = true,
) {
    val targetId get() = target.id

    constructor(dimension: PhotoDimension) : this(
        dimension,
        dimension.textLabel ?: dimension.measurement?.length?.displayNumberForUi(dimension.displayUnit)?.text
            ?.let { number -> listOf(dimension.labelPrefix, number, dimension.labelSuffix).filter { it.isNotBlank() }.joinToString(" ") }
            .orEmpty(),
        dimension.displayUnit,
        dimension.measurement?.let { DimensionEntry.Measured(it, dimension.labelSuffix, dimension.displayUnit, dimension.labelPrefix) },
        note = dimension.note,
        showUnit = dimension.showUnit,
    )

    fun type(text: String): DimensionValueDraft {
        if (text == input) return this
        val preserved = unchangedEntry?.let { entry ->
            val number = if (entry.suffix.isEmpty()) input.trim()
                else input.trim().removeSuffix(entry.suffix).trimEnd()
            val trimmed = text.trim()
            if (text.length > ManualLengthMeasurement.MAX_INPUT_LENGTH ||
                text.any { it == '\r' || it == '\n' || it == '\u0000' }) null
            else if (trimmed == number) entry.copy(suffix = "")
            else if (trimmed.startsWith(number) && trimmed.getOrNull(number.length)?.isWhitespace() == true) {
                val suffix = trimmed.drop(number.length).trimStart()
                entry.copy(suffix = suffix).takeIf { isLabelSuffix(suffix) && !isNonLengthTail(suffix) }
            } else null
        }
        val explicit = preserved == null &&
            (parseDimensionEntry(text, unit) as? DimensionEntry.Measured)?.explicitlyTypedUnit == true
        return DimensionValueDraft(target, text, unit, preserved, note, showUnit || explicit)
    }

    /** Replace only the draft; the user must still confirm the dimension. */
    fun calculate(expression: String): DimensionValueDraft? {
        val result = evaluateDimensionCalculation(expression) ?: return null
        val text = try { result.editableText() } catch (_: IllegalArgumentException) { return null }
        if (result.numerator.signum() < 0) {
            return DimensionValueDraft(target, text, unit, null, note, showUnit)
        }
        val measured = ManualLengthMeasurement.calculate(expression, unit) ?: return null
        return DimensionValueDraft(
            target, text, unit, DimensionEntry.Measured(measured, "", unit), note, showUnit,
        )
    }

    fun withNote(text: String): DimensionValueDraft =
        if (text == note) this else DimensionValueDraft(
            target, input, unit, unchangedEntry, text, showUnit,
        )

    fun withUnitVisibility(visible: Boolean): DimensionValueDraft =
        if (visible == showUnit) this else DimensionValueDraft(
            target, input, unit, unchangedEntry, note, visible,
        )

    fun selectUnit(newUnit: LengthUnit, keepNumber: Boolean = false): DimensionValueDraft {
        val parsed = entry()
        if (parsed is DimensionEntry.TextOnly) return this // No length to convert or reinterpret.
        if (newUnit == effectiveDisplayUnit() && (keepNumber || unchangedEntry != null)) return withUnitVisibility(true)
        if (newUnit == effectiveDisplayUnit() && !showUnit) return withUnitVisibility(true)
        if (keepNumber) {
            // A calculated exact fraction may not be valid ordinary metric input.
            // Reinterpret only an explicitly retained exact number, not the old expression.
            if (parsed is DimensionEntry.Measured && parsed.value.source == LengthSource.CALCULATED &&
                '/' in input && parsed.prefix.isEmpty()) {
                val number = input.removeSuffix(parsed.suffix).trim()
                val measured = ManualLengthMeasurement.calculate("$number=", newUnit) ?: return this
                return DimensionValueDraft(
                    target, input, newUnit, parsed.copy(value = measured, displayUnit = newUnit), note, true,
                )
            }
            // Keep the raw input. An explicitly typed length unit still wins.
            return DimensionValueDraft(target, input, newUnit, null, note, true)
        }
        if (input.isNotBlank() && parsed == null) return this
        val measured = (parsed as? DimensionEntry.Measured)?.copy(displayUnit = newUnit, explicitlyTypedUnit = false)
        return DimensionValueDraft(
            target, measured?.inputText(newUnit) ?: input, newUnit, measured, note, true,
        )
    }

    fun hasApproximateDisplay(): Boolean =
        unchangedEntry?.let {
            it.value.length.displayNumberForUi(unit).approximate && input == it.inputText(unit)
        } == true

    fun isTextOnly(): Boolean = entry() is DimensionEntry.TextOnly

    fun effectiveDisplayUnit(): LengthUnit = (entry() as? DimensionEntry.Measured)?.displayUnit ?: unit

    private val parsedEntry by lazy { unchangedEntry ?: parseDimensionEntry(input, unit) }
    private fun entry(): DimensionEntry? = parsedEntry

    fun confirm(current: PhotoDimension): PhotoDimension? {
        if (target.id != current.id || target.photoId != current.photoId ||
            target.measurement != current.measurement || target.displayUnit != current.displayUnit ||
            target.labelSuffix != current.labelSuffix || target.labelPrefix != current.labelPrefix ||
            target.textLabel != current.textLabel ||
            target.note != current.note || target.showUnit != current.showUnit
        ) return null
        if (note.length > MAX_DIMENSION_NOTE_LENGTH) return null
        return when (val entry = entry()) {
            is DimensionEntry.Measured -> current.copy(
                measurement = entry.value, displayUnit = entry.displayUnit,
                labelPrefix = entry.prefix, labelSuffix = entry.suffix, textLabel = null, note = note, showUnit = showUnit,
            )
            is DimensionEntry.TextOnly -> current.copy(
                measurement = null, displayUnit = unit, labelPrefix = "", labelSuffix = "",
                textLabel = entry.text, note = note, showUnit = showUnit,
            )
            null -> if (input.isBlank()) current.copy(
                measurement = null, displayUnit = unit, labelPrefix = "", labelSuffix = "",
                textLabel = null, note = note, showUnit = showUnit,
            ) else null
        }
    }
}

internal const val MAX_DIMENSION_NOTE_LENGTH = 2000
