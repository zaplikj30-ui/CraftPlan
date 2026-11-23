package cz.craftplan.app

internal enum class DimensionField { VALUE, NOTE }
internal enum class DimensionKeyboardMode { NUMERIC, SYSTEM }

internal data class DimensionKeyboard(
    val field: DimensionField = DimensionField.VALUE,
    val valueMode: DimensionKeyboardMode = DimensionKeyboardMode.NUMERIC,
    val noteMode: DimensionKeyboardMode = DimensionKeyboardMode.SYSTEM,
    val visible: Boolean = true,
) {
    val mode: DimensionKeyboardMode
        get() = if (this.field == DimensionField.VALUE) valueMode else noteMode

    val systemOpen: Boolean get() = visible && mode == DimensionKeyboardMode.SYSTEM

    fun open(field: DimensionField) = copy(field = field, visible = true)
    fun hide() = copy(visible = false)

    fun switch(): DimensionKeyboard {
        val next = if (mode == DimensionKeyboardMode.NUMERIC) DimensionKeyboardMode.SYSTEM else DimensionKeyboardMode.NUMERIC
        return if (field == DimensionField.VALUE) copy(valueMode = next, visible = true)
        else copy(noteMode = next, visible = true)
    }
}
