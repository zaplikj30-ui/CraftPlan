package cz.craftplan.app

/** A pending edit for one field. Only confirmation changes the dimension. */
internal class DimensionValueDraft private constructor(
    private val target: PhotoDimension,
    val input: String,
    val unit: LengthUnit,
    private val unchangedSource: ManualLengthMeasurement?,
) {
    constructor(dimension: PhotoDimension) : this(
        dimension,
        dimension.measurement?.length?.displayNumber(dimension.displayUnit).orEmpty(),
        dimension.displayUnit,
        dimension.measurement,
    )

    fun type(text: String): DimensionValueDraft =
        if (text == input) this else DimensionValueDraft(target, text, unit, null)

    fun selectUnit(newUnit: LengthUnit): DimensionValueDraft {
        if (newUnit == unit) return this
        val source = validated()
        return DimensionValueDraft(target, source?.length?.displayNumber(newUnit) ?: input, newUnit, source)
    }

    fun validated(): ManualLengthMeasurement? = unchangedSource
        // An explicit unit in the pasted text specifies the source unit.
        ?: ManualLengthMeasurement.parse(input)
        ?: ManualLengthMeasurement.parse(input, unit)

    fun confirm(current: PhotoDimension): PhotoDimension? {
        if (target.id != current.id || target.photoId != current.photoId ||
            target.measurement != current.measurement || target.displayUnit != current.displayUnit
        ) return null
        val source = validated() ?: return null
        return current.copy(measurement = source, displayUnit = unit)
    }
}
