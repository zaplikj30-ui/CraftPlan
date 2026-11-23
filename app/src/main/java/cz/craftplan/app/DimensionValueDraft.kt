package cz.craftplan.app

/** A parsed label has either an exact measurement with optional visible text, or only text. */
internal sealed interface DimensionEntry {
    data class Measured(val value: ManualLengthMeasurement, val suffix: String) : DimensionEntry
    data class TextOnly(val text: String) : DimensionEntry
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
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.length > ManualLengthMeasurement.MAX_INPUT_LENGTH) return null
    val parseNumber: (String) -> ManualLengthMeasurement? = { number ->
        if (requireSelectedUnit) ManualLengthMeasurement.parse(number, unit)
        else ManualLengthMeasurement.parse(number) ?: ManualLengthMeasurement.parse(number, unit)
    }
    parseNumber(trimmed)?.let { return DimensionEntry.Measured(it, "") }
    // Try the longest numerical prefix first, so imperial compound fractions remain intact.
    for (i in trimmed.indices.reversed()) {
        if (!trimmed[i].isWhitespace()) continue
        val prefix = trimmed.substring(0, i).trimEnd()
        val suffix = trimmed.substring(i).trimStart()
        if (suffix.firstOrNull()?.isLetter() != true) continue
        parseNumber(prefix)?.let { return DimensionEntry.Measured(it, suffix) }
    }
    return if (trimmed.first().isLetter()) DimensionEntry.TextOnly(trimmed) else null
}

/** A pending edit. Only confirmation changes the dimension or its note. */
internal class DimensionValueDraft private constructor(
    private val target: PhotoDimension,
    val input: String,
    val unit: LengthUnit,
    private val unchangedSource: ManualLengthMeasurement?,
    private val requireSelectedUnit: Boolean = false,
    val note: String = "",
    val showUnit: Boolean = true,
) {
    constructor(dimension: PhotoDimension) : this(
        dimension,
        dimension.textLabel ?: dimension.measurement?.length?.displayNumber(dimension.displayUnit)
            ?.let { number -> listOf(number, dimension.labelSuffix).filter { it.isNotBlank() }.joinToString(" ") }
            .orEmpty(),
        dimension.displayUnit,
        dimension.measurement,
        note = dimension.note,
        showUnit = dimension.showUnit,
    )

    fun type(text: String): DimensionValueDraft =
        if (text == input) this else DimensionValueDraft(
            target, text, unit, null, requireSelectedUnit, note, showUnit,
        )

    fun withNote(text: String): DimensionValueDraft =
        if (text == note) this else DimensionValueDraft(
            target, input, unit, unchangedSource, requireSelectedUnit, text, showUnit,
        )

    fun withUnitVisibility(visible: Boolean): DimensionValueDraft =
        if (visible == showUnit) this else DimensionValueDraft(
            target, input, unit, unchangedSource, requireSelectedUnit, note, visible,
        )

    fun selectUnit(newUnit: LengthUnit, keepNumber: Boolean = false): DimensionValueDraft {
        if (newUnit == unit && showUnit) return this
        if (newUnit == unit) return withUnitVisibility(true)
        if (keepNumber) {
            // Conflicting explicit suffixes must be fixed by the user.
            return DimensionValueDraft(target, input, newUnit, null, true, note, true)
        }
        val entry = parseDimensionEntry(input, unit, requireSelectedUnit)
        val source = unchangedSource ?: (entry as? DimensionEntry.Measured)?.value
        val suffix = (entry as? DimensionEntry.Measured)?.suffix.orEmpty()
        val converted = source?.length?.displayNumber(newUnit)?.let {
            listOf(it, suffix).filter(String::isNotBlank).joinToString(" ")
        } ?: input
        return DimensionValueDraft(
            target, converted, newUnit, source,
            requireSelectedUnit && source == null, note, true,
        )
    }

    fun validated(): ManualLengthMeasurement? = unchangedSource
        ?: (parseDimensionEntry(input, unit, requireSelectedUnit) as? DimensionEntry.Measured)?.value

    fun confirm(current: PhotoDimension): PhotoDimension? {
        if (target.id != current.id || target.photoId != current.photoId ||
            target.measurement != current.measurement || target.displayUnit != current.displayUnit ||
            target.labelSuffix != current.labelSuffix || target.textLabel != current.textLabel ||
            target.note != current.note || target.showUnit != current.showUnit
        ) return null
        if (note.length > 2000) return null
        // The original exact source wins when only presentation or notes have changed.
        val entry = parseDimensionEntry(input, unit, requireSelectedUnit)
        return when (entry) {
            is DimensionEntry.Measured -> current.copy(
                measurement = unchangedSource ?: entry.value, displayUnit = unit,
                labelSuffix = entry.suffix, textLabel = null, note = note, showUnit = showUnit,
            )
            is DimensionEntry.TextOnly -> current.copy(
                measurement = null, displayUnit = unit, labelSuffix = "",
                textLabel = entry.text, note = note, showUnit = showUnit,
            )
            null -> if (input.isBlank() && note.isNotBlank()) current.copy(
                measurement = null, displayUnit = unit, labelSuffix = "",
                textLabel = null, note = note, showUnit = showUnit,
            ) else null
        }
    }
}
