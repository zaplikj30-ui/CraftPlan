package cz.craftplan.app

import java.util.UUID

internal enum class PhotoId {
    SYNTHETIC_WORKBENCH,
}

/** Coordinates in the canonical photo, with (0, 0) at its upper-left corner. */
internal data class PhotoAnchor(val u: Float, val v: Float) {
    init {
        require(u in 0f..1f && v in 0f..1f)
    }
}

internal enum class MeasurementState {
    UNMEASURED,
}

internal data class PhotoDimension(
    val id: UUID,
    val photoId: PhotoId,
    val start: PhotoAnchor,
    val end: PhotoAnchor,
) {
    val measurementState = MeasurementState.UNMEASURED

    init {
        require(start != end)
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
