package cz.craftplan.app

/** A parsed label has either an exact measurement with optional visible text, or only text. */
internal sealed interface DimensionEntry {
    data class Measured(val value: ManualLengthMeasurement, val suffix: String) : DimensionEntry
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

/**
 * A numerical prefix must be a valid exact length. Free text follows it after whitespace.
 * Invalid expressions beginning with a number must never silently become measured values.
 */
internal fun parseDimensionEntry(
    input: String,
    unit: LengthUnit,
    requireSelectedUnit: Boolean = false,
): DimensionEntry? {
    if (input.isBlank() || input.length > ManualLengthMeasurement.MAX_INPUT_LENGTH) return null
    val parseNumber: (String) -> ManualLengthMeasurement? = { number ->
        if (requireSelectedUnit) ManualLengthMeasurement.parse(number, unit)
        else ManualLengthMeasurement.parse(number) ?: ManualLengthMeasurement.parse(number, unit)
    }
    parseNumber(input)?.let { return DimensionEntry.Measured(it, "") }
    // Try the longest numerical prefix first, so imperial compound fractions remain intact.
    for (i in input.indices.reversed()) {
        if (!input[i].isWhitespace()) continue
        val prefix = input.substring(0, i).trimEnd()
        val suffix = input.substring(i).trim()
        if (suffix.isEmpty()) continue
        parseNumber(prefix)?.let { return if (isLabelSuffix(suffix)) DimensionEntry.Measured(it, suffix) else null }
    }
    val trimmed = input.trim()
    return if (trimmed.first().isLetter()) DimensionEntry.TextOnly(trimmed) else null
}

/** A pending edit. Only confirmation changes the dimension or its note. */
internal class DimensionValueDraft private constructor(
    private val target: PhotoDimension,
    val input: String,
    val unit: LengthUnit,
    private val unchangedEntry: DimensionEntry.Measured?,
    private val requireSelectedUnit: Boolean = false,
    val note: String = "",
    val showUnit: Boolean = true,
) {
    val targetId get() = target.id

    constructor(dimension: PhotoDimension) : this(
        dimension,
        dimension.textLabel ?: dimension.measurement?.length?.displayNumberForUi(dimension.displayUnit)?.text
            ?.let { number -> listOf(number, dimension.labelSuffix).filter { it.isNotBlank() }.joinToString(" ") }
            .orEmpty(),
        dimension.displayUnit,
        dimension.measurement?.let { DimensionEntry.Measured(it, dimension.labelSuffix) },
        note = dimension.note,
        showUnit = dimension.showUnit,
    )

    fun type(text: String): DimensionValueDraft {
        if (text == input) return this
        val preserved = unchangedEntry?.let { entry ->
            val number = entry.value.length.displayNumberForUi(unit).text
            val trimmed = text.trim()
            if (text.length > ManualLengthMeasurement.MAX_INPUT_LENGTH) null
            else if (trimmed == number) entry.copy(suffix = "")
            else if (trimmed.startsWith(number) && trimmed.getOrNull(number.length)?.isWhitespace() == true) {
                val suffix = trimmed.drop(number.length).trimStart()
                entry.copy(suffix = suffix)
            } else null
        }
        return DimensionValueDraft(target, text, unit, preserved, requireSelectedUnit, note, showUnit)
    }

    fun withNote(text: String): DimensionValueDraft =
        if (text == note) this else DimensionValueDraft(
            target, input, unit, unchangedEntry, requireSelectedUnit, text, showUnit,
        )

    fun withUnitVisibility(visible: Boolean): DimensionValueDraft =
        if (visible == showUnit) this else DimensionValueDraft(
            target, input, unit, unchangedEntry, requireSelectedUnit, note, visible,
        )

    fun selectUnit(newUnit: LengthUnit, keepNumber: Boolean = false): DimensionValueDraft {
        if (newUnit == unit && showUnit && (keepNumber || unchangedEntry != null)) return this
        if (newUnit == unit && !showUnit) return withUnitVisibility(true)
        // Selecting the already visible unit still converts a newly typed explicit unit.
        if (keepNumber) {
            // Conflicting explicit suffixes must be fixed by the user.
            return DimensionValueDraft(target, input, newUnit, null, true, note, true)
        }
        val parsed = entry()
        if (input.isNotBlank() && parsed == null) return this // Reject silently broken conversion.
        val entry = parsed as? DimensionEntry.Measured
        val converted = entry?.value?.length?.displayNumberForUi(newUnit)?.text?.let {
            listOf(it, entry.suffix).filter(String::isNotBlank).joinToString(" ")
        } ?: input
        return DimensionValueDraft(
            target, converted, newUnit, entry,
            requireSelectedUnit && entry == null, note, true,
        )
    }

    fun hasApproximateDisplay(): Boolean =
        unchangedEntry?.value?.length?.displayNumberForUi(unit)?.approximate == true

    private fun entry(): DimensionEntry? = unchangedEntry?.let {
        if (it.suffix.isEmpty() || isLabelSuffix(it.suffix)) it else return null
    } ?: parseDimensionEntry(input, unit, requireSelectedUnit)

    fun confirm(current: PhotoDimension): PhotoDimension? {
        if (target.id != current.id || target.photoId != current.photoId ||
            target.measurement != current.measurement || target.displayUnit != current.displayUnit ||
            target.labelSuffix != current.labelSuffix || target.textLabel != current.textLabel ||
            target.note != current.note || target.showUnit != current.showUnit
        ) return null
        if (note.length > MAX_DIMENSION_NOTE_LENGTH) return null
        return when (val entry = entry()) {
            is DimensionEntry.Measured -> current.copy(
                measurement = entry.value, displayUnit = unit,
                labelSuffix = entry.suffix, textLabel = null, note = note, showUnit = showUnit,
            )
            is DimensionEntry.TextOnly -> current.copy(
                measurement = null, displayUnit = unit, labelSuffix = "",
                textLabel = entry.text, note = note, showUnit = showUnit,
            )
            null -> if (input.isBlank()) current.copy(
                measurement = null, displayUnit = unit, labelSuffix = "",
                textLabel = null, note = note, showUnit = showUnit,
            ) else null
        }
    }
}

internal const val MAX_DIMENSION_NOTE_LENGTH = 2000
