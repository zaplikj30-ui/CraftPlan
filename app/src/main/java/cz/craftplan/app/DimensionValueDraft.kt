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
        fun inputText(unit: LengthUnit): String {
            val number = value.length.displayNumberForUi(unit).text
            return listOf(prefix, number, suffix).filter(String::isNotBlank).joinToString(" ")
        }
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
    if (isLiteralCalculationLabel(trimmed)) return DimensionEntry.TextOnly(trimmed)
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
    private val resultPrefix: String? = null,
    private val computedTextLabel: String? = null,
    private val lastValueExpression: String? = null,
    private val lastNoteExpression: String? = null,
) {
    val targetId get() = target.id

    constructor(dimension: PhotoDimension) : this(
        dimension,
        listOfNotNull(dimension.equationPrefix, dimension.textLabel ?: dimension.measurement?.let {
            val number = DimensionEntry.Measured(it, dimension.labelSuffix, dimension.displayUnit, dimension.labelPrefix)
                .inputText(dimension.displayUnit)
            if (dimension.equationPrefix != null && (it.calculationRounded ||
                    it.length.displayNumberForUi(dimension.displayUnit).approximate)) "≈" + number else number
        }).joinToString(" "),
        dimension.displayUnit,
        dimension.measurement?.let { DimensionEntry.Measured(it, dimension.labelSuffix, dimension.displayUnit, dimension.labelPrefix) },
        note = dimension.note,
        showUnit = dimension.showUnit,
        resultPrefix = dimension.equationPrefix,
        computedTextLabel = dimension.textLabel.takeIf { dimension.equationPrefix != null },
        // Only replay a directly calculated, unconverted measured equation.
        lastValueExpression = dimension.equationPrefix?.takeIf { prefix ->
            dimension.measurement?.let { m ->
                m.source == LengthSource.CALCULATED &&
                    m.originalUnit == OriginalLengthUnit.Single(dimension.displayUnit) &&
                    prefix.endsWith(formatCalculatedExpression(m.originalInput))
            } == true
        },
    )

    fun type(text: String): DimensionValueDraft {
        if (text == input) return this
        // Only the suffix after a previously executed equals is authoritative.
        // Editing it never evaluates the expression or revives an older result.
        val prefix = resultPrefix
        if (prefix != null && '=' in text) {
            val nextPrefix = text.substring(0, text.lastIndexOf('=') + 1)
            val result = text.substring(nextPrefix.length)
            // An edited result is a new manual value, not an old approximation.
            val cleaned = if (result != input.substring(prefix.length) || nextPrefix != prefix)
                removeResultApproximation(text) else text
            val explicit = (parseDimensionEntry(cleaned.substring(nextPrefix.length).trim(), unit)
                as? DimensionEntry.Measured)?.explicitlyTypedUnit == true
            return DimensionValueDraft(target, cleaned, unit, null, note, showUnit || explicit, nextPrefix,
                lastNoteExpression = lastNoteExpression)
        }
        val preserved = if (prefix != null) null else unchangedEntry?.let { entry ->
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
        return DimensionValueDraft(target, text, unit, preserved, note, showUnit || explicit,
            lastNoteExpression = lastNoteExpression)
    }

    /** A new equals appends a rounded result; no live evaluation during edits. */
    fun calculate(expression: String, places: Int = DEFAULT_CALCULATION_PLACES): DimensionValueDraft? {
        if (!expression.endsWith('=')) return null
        val segment = findCalculationSegment(expression)
        val result = roundedCalculation(segment.expression, places) ?: return null
        val prefix = segment.prefix + formatCalculatedExpression(segment.expression)
        val text = prefix + " " + result.markedNumber
        if (text.length > ManualLengthMeasurement.MAX_INPUT_LENGTH) return null
        // A contextual calculation (including a continuation of its result)
        // is descriptive: never replace a preceding measured length with it.
        val firstEquals = expression.indexOf('=')
        val firstSegment = if (firstEquals == expression.lastIndex) segment
            else findCalculationSegment(expression.substring(0, firstEquals + 1))
        val contextPrefix = when {
            firstSegment.contextual -> firstSegment.prefix
            segment.contextual -> segment.prefix
            else -> null
        }
        if (contextPrefix != null) {
            val previous = contextPrefix.trimEnd()
            // A prior executed equation cannot be silently replaced by a separate
            // calculation. Keep the already established measure if unambiguous.
            if ('=' in previous && (unchangedEntry != null || target.measurement != null)) return null
            val measuredPrefix = if ('=' !in previous) {
                val existing = unchangedEntry?.let { entry ->
                    val number = entry.copy(suffix = "").inputText(entry.displayUnit)
                    if (previous == number) entry.copy(suffix = "")
                    else if (previous.startsWith("$number ")) {
                        val suffix = previous.removePrefix("$number ").trim()
                        entry.copy(suffix = suffix).takeIf { isLabelSuffix(suffix) && !isNonLengthTail(suffix) }
                    } else null
                }
                existing ?: parseDimensionEntry(previous, unit) as? DimensionEntry.Measured
            } else null
            val suffix = text.substring(contextPrefix.length)
            val entry = measuredPrefix?.copy(suffix =
                listOf(measuredPrefix.suffix, suffix).filter(String::isNotBlank).joinToString(" "))
            return DimensionValueDraft(target, text, unit, entry, note, showUnit,
                computedTextLabel = if (entry == null) text else null,
                lastValueExpression = prefix, lastNoteExpression = lastNoteExpression)
        }
        val measured = ManualLengthMeasurement.calculate(segment.expression, unit, places)
        if (measured == null) {
            if (!result.number.startsWith('-') && !(result.approximate && result.number == "0")) return null
            return DimensionValueDraft(target, text, unit, null, note, showUnit, prefix, result.markedNumber,
                prefix, lastNoteExpression)
        }
        return DimensionValueDraft(target, text, unit, DimensionEntry.Measured(measured, "", unit),
            note, showUnit, prefix, lastValueExpression = prefix, lastNoteExpression = lastNoteExpression)
    }

    fun calculateNote(expression: String, places: Int = DEFAULT_CALCULATION_PLACES): DimensionValueDraft? {
        if (!expression.endsWith('=')) return null
        val segment = findCalculationSegment(expression)
        val result = roundedCalculation(segment.expression, places) ?: return null
        val prefix = segment.prefix + formatCalculatedExpression(segment.expression)
        val text = prefix + " " + result.markedNumber
        if (text.length > MAX_DIMENSION_NOTE_LENGTH) return null
        return DimensionValueDraft(target, input, unit, unchangedEntry, text, showUnit, resultPrefix,
            computedTextLabel, lastValueExpression, prefix)
    }

    /** Replay only results that were automatically generated and not manually edited.
     * No other saved dimension is affected. Both fields change together or not at all. */
    fun recalculate(places: Int): DimensionValueDraft? {
        val value = lastValueExpression?.let { calculate(it, places) ?: return null } ?: this
        return lastNoteExpression?.let { value.calculateNote(it, places) ?: return null } ?: value
    }

    fun editNote(text: String): DimensionValueDraft {
        val oldEquals = note.lastIndexOf('=')
        val newEquals = text.lastIndexOf('=')
        val cleaned = if (oldEquals >= 0 && newEquals >= 0 &&
            note.substring(oldEquals + 1) != text.substring(newEquals + 1) &&
            note.substring(oldEquals + 1).trimStart().startsWith('≈'))
            removeResultApproximation(text) else text
        return withNote(cleaned)
    }

    fun withNote(text: String): DimensionValueDraft =
        if (text == note) this else DimensionValueDraft(
            target, input, unit, unchangedEntry, text, showUnit, resultPrefix, computedTextLabel,
            lastValueExpression = lastValueExpression,
        )

    fun withUnitVisibility(visible: Boolean): DimensionValueDraft =
        if (visible == showUnit) this else DimensionValueDraft(
            target, input, unit, unchangedEntry, note, visible, resultPrefix, computedTextLabel,
            lastValueExpression, lastNoteExpression,
        )

    fun selectUnit(newUnit: LengthUnit, keepNumber: Boolean = false): DimensionValueDraft {
        val parsed = entry()
        if (parsed is DimensionEntry.TextOnly) return this // No length to convert or reinterpret.
        if (newUnit == effectiveDisplayUnit() && (keepNumber || unchangedEntry != null)) return withUnitVisibility(true)
        if (newUnit == effectiveDisplayUnit() && !showUnit) return withUnitVisibility(true)
        if (keepNumber) {
            // Keep the raw input. An explicitly typed length unit still wins.
            val number = resultPrefix?.let { input.removePrefix(it).trimStart().removePrefix("≈") } ?: input
            val text = resultPrefix?.let { "$it $number" } ?: number
            return DimensionValueDraft(target, text, newUnit, null, note, true, resultPrefix,
                lastNoteExpression = lastNoteExpression)
        }
        if (input.isNotBlank() && parsed == null) return this
        val measured = (parsed as? DimensionEntry.Measured)?.copy(displayUnit = newUnit, explicitlyTypedUnit = false)
        // Keep the original equation and identify the old unit before appending
        // its converted equivalent, rather than silently changing the equality.
        val prefix = if (resultPrefix != null && parsed is DimensionEntry.Measured &&
            newUnit != parsed.displayUnit) {
            val oldNumber = parsed.inputText(parsed.displayUnit)
            val mark = if (parsed.value.calculationRounded ||
                parsed.value.length.displayNumberForUi(parsed.displayUnit).approximate) "≈" else ""
            "$resultPrefix $mark$oldNumber ${parsed.displayUnit.symbol} ="
        } else resultPrefix
        val converted = measured?.inputText(newUnit)
        val marker = if (measured != null && (measured.value.calculationRounded ||
            measured.value.length.displayNumberForUi(newUnit).approximate)) "≈" else ""
        val text = if (prefix != null && converted != null) "$prefix $marker$converted" else converted ?: input
        if (text.length > ManualLengthMeasurement.MAX_INPUT_LENGTH) return this
        return DimensionValueDraft(target, text, newUnit, measured, note, true, prefix,
            lastNoteExpression = lastNoteExpression)
    }

    fun hasApproximateDisplay(): Boolean =
        computedTextLabel?.startsWith('≈') == true || unchangedEntry?.let {
            it.value.calculationRounded || it.value.length.displayNumberForUi(unit).approximate
        } == true

    fun isTextOnly(): Boolean = entry() is DimensionEntry.TextOnly

    fun effectiveDisplayUnit(): LengthUnit = (entry() as? DimensionEntry.Measured)?.displayUnit ?: unit

    private val parsedEntry by lazy {
        unchangedEntry ?: computedTextLabel?.let { DimensionEntry.TextOnly(it) } ?: if (resultPrefix != null) {
            // An emptied result is incomplete, not a measurement of zero and
            // not a request to evaluate again. Manual replacements are exact.
            val result = input.removePrefix(resultPrefix).trim().removePrefix("≈")
            // An unexecuted continuation must not masquerade as a measured length.
            parseDimensionEntry(result, unit)
        } else parseDimensionEntry(input, unit)
    }
    private fun entry(): DimensionEntry? = parsedEntry

    fun confirm(current: PhotoDimension): PhotoDimension? {
        if (target.id != current.id || target.photoId != current.photoId ||
            target.measurement != current.measurement || target.displayUnit != current.displayUnit ||
            target.labelSuffix != current.labelSuffix || target.labelPrefix != current.labelPrefix ||
            target.textLabel != current.textLabel ||
            target.note != current.note || target.showUnit != current.showUnit ||
            target.equationPrefix != current.equationPrefix
        ) return null
        if (note.length > MAX_DIMENSION_NOTE_LENGTH || input.length > ManualLengthMeasurement.MAX_INPUT_LENGTH) return null
        return when (val entry = entry()) {
            is DimensionEntry.Measured -> current.copy(
                measurement = entry.value, displayUnit = entry.displayUnit,
                labelPrefix = entry.prefix, labelSuffix = entry.suffix, textLabel = null, note = note, showUnit = showUnit,
                equationPrefix = resultPrefix,
            )
            is DimensionEntry.TextOnly -> current.copy(
                measurement = null, displayUnit = unit, labelPrefix = "", labelSuffix = "",
                textLabel = entry.text, note = note, showUnit = showUnit, equationPrefix = resultPrefix,
            )
            null -> if (input.isBlank()) current.copy(
                measurement = null, displayUnit = unit, labelPrefix = "", labelSuffix = "",
                textLabel = null, note = note, showUnit = showUnit, equationPrefix = null,
            ) else null
        }
    }
}

internal const val MAX_DIMENSION_NOTE_LENGTH = 2000
