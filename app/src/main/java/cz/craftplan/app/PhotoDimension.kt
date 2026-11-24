package cz.craftplan.app

import java.util.UUID

internal enum class PhotoId {
    SYNTHETIC_WORKBENCH,
}

/** In-memory editor coordinates relative to the canonical photo. */
internal data class PhotoAnchor(val u: Float, val v: Float) {
    init {
        require(u.isFinite() && v.isFinite())
    }
}

internal enum class MeasurementState {
    UNMEASURED,
    MEASURED,
}

internal data class PhotoDimension(
    val id: UUID,
    val photoId: PhotoId,
    val start: PhotoAnchor,
    val end: PhotoAnchor,
    val measurement: ManualLengthMeasurement? = null,
    val displayUnit: LengthUnit = LengthUnit.MM,
    /** Optional visible text placed after the measured number and unit. */
    val labelSuffix: String = "",
    /** Visible label when no numerical measurement has been assigned. */
    val textLabel: String? = null,
    /** Note text and the editor's note visibility are independent. */
    val note: String = "",
    /** "Nic" hides the unit in the label, without deleting the exact measurement. */
    val showUnit: Boolean = true,
    /** Relative displacement from the midpoint along the rendered dimension. */
    val labelT: Float = 0f,
) {
    val measurementState: MeasurementState
        get() = if (measurement == null) MeasurementState.UNMEASURED else MeasurementState.MEASURED

    fun visibleNote(showNotes: Boolean): String? = note.takeIf { showNotes && it.isNotBlank() }

    fun displayLabel(): String? {
        val measured = measurement
        if (measured == null) return textLabel?.takeIf { it.isNotBlank() }
        val shown = measured.length.displayNumberForUi(displayUnit)
        return listOfNotNull(
            if (shown.approximate) "≈" + shown.text else shown.text,
            displayUnit.symbol.takeIf { showUnit },
            labelSuffix.takeIf { it.isNotBlank() },
        ).joinToString(" ")
    }

    fun withManualLength(input: String, selectedUnit: LengthUnit? = null): PhotoDimension {
        val validated = ManualLengthMeasurement.parse(input, selectedUnit) ?: return this
        return copy(measurement = validated, labelSuffix = "", textLabel = null)
    }

    init {
        require(start != end)
        require(labelT in -0.92f..0.92f)
    }
}

internal sealed interface DimensionPlacement {
    data object Idle : DimensionPlacement
    data class Ready(val photoId: PhotoId) : DimensionPlacement {
        fun finishDrag(start: PhotoAnchor, end: PhotoAnchor?): Complete? =
            if (end == null || end == start) null
            else Complete(PhotoDimension(UUID.randomUUID(), photoId, start, end))
    }

    data class Complete(val dimension: PhotoDimension) : DimensionPlacement
}
