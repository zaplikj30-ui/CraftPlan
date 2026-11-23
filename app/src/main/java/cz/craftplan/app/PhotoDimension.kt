package cz.craftplan.app

import java.util.UUID

internal enum class PhotoId {
    SYNTHETIC_WORKBENCH,
}

/**
 * In-memory editor coordinates relative to the canonical photo. (0, 0) and (1, 1)
 * are its corners; the same axes extend into the workspace around it.
 */
internal data class PhotoAnchor(val u: Float, val v: Float) {
    init {
        require(u.isFinite() && v.isFinite())
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
