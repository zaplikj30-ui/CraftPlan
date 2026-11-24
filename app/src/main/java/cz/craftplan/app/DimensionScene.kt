package cz.craftplan.app

import java.util.UUID

internal enum class DimensionTool { SELECT, SINGLE, REPEAT }

internal data class DimensionScene(
    val dimensions: List<PhotoDimension> = emptyList(),
    val selectedId: UUID? = null,
    val tool: DimensionTool = DimensionTool.SELECT,
) {
    fun find(id: UUID?): PhotoDimension? = dimensions.firstOrNull { it.id == id }

    fun select(id: UUID?) = copy(selectedId = find(id)?.id)

    fun prepareSingle() = copy(tool = DimensionTool.SINGLE, selectedId = null)

    fun toggleRepeat() = if (tool == DimensionTool.REPEAT) copy(tool = DimensionTool.SELECT)
        else copy(tool = DimensionTool.REPEAT, selectedId = null)

    fun closePhoto() = copy(tool = DimensionTool.SELECT, selectedId = null)

    fun create(dimension: PhotoDimension): DimensionScene {
        if (tool == DimensionTool.SELECT || !accepts(dimension) || find(dimension.id) != null) return this
        val repeat = tool == DimensionTool.REPEAT
        return copy(
            dimensions = dimensions + dimension,
            selectedId = if (repeat) null else dimension.id,
            tool = if (repeat) DimensionTool.REPEAT else DimensionTool.SELECT,
        )
    }

    fun edit(original: PhotoDimension, updated: PhotoDimension): DimensionScene {
        if (updated == original || updated.id != original.id || !accepts(updated) || find(original.id) != original) return this
        return copy(dimensions = dimensions.map { if (it.id == original.id) updated else it })
    }

    private fun accepts(dimension: PhotoDimension): Boolean = dimension.photoId == PhotoId.SYNTHETIC_WORKBENCH
}

internal data class DimensionTarget(
    val dimension: PhotoDimension,
    val hit: DimensionHit,
    val label: Boolean,
    val distance: Float,
)

internal fun orderedDimensionTargets(
    targets: List<DimensionTarget>, selectedId: UUID?, endpointTolerance: Float,
): List<DimensionTarget> {
    val ends = targets.filter { it.hit != DimensionHit.BODY }.sortedBy { it.distance }
    val favored = ends.firstOrNull {
        it.dimension.id == selectedId && it.distance <= ends.first().distance + endpointTolerance
    }
    val body = targets.filter { it.hit == DimensionHit.BODY }
        .sortedWith(compareByDescending<DimensionTarget> { it.label }.thenByDescending { it.dimension.id == selectedId })
    return if (favored == null) ends + body else listOf(favored) + (ends - favored) + body
}

internal data class DimensionTapCycle(val point: EditorPoint, val candidates: List<UUID>, val index: Int) {
    fun next(at: EditorPoint, ids: List<UUID>, tolerance: Float): DimensionTapCycle {
        // Selection can reorder the preferred hit, so retain the first tap's order.
        if (at.distanceTo(point) > tolerance || ids.toSet() != candidates.toSet()) {
            return DimensionTapCycle(at, ids, 0)
        }
        return copy(index = (index + 1) % candidates.size)
    }
}
