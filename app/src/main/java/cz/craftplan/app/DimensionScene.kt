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

    fun deleteSelected(): DimensionScene {
        val selected = find(selectedId) ?: return this
        return copy(dimensions = dimensions.filterNot { it.id == selected.id }, selectedId = null)
    }

    private fun accepts(dimension: PhotoDimension): Boolean = dimension.photoId == PhotoId.SYNTHETIC_WORKBENCH
}

internal data class DimensionTarget(
    val dimension: PhotoDimension,
    val hit: DimensionHit,
    val label: Boolean,
    val distance: Float,
    // Distance to the center of the visible label across its baseline, for overlapping labels.
    val labelCenterDistance: Float = 0f,
)

internal fun orderedDimensionTargets(
    targets: List<DimensionTarget>, selectedId: UUID?, endpointTolerance: Float,
): List<DimensionTarget> {
    val ends = targets.filter { it.hit != DimensionHit.BODY }.sortedBy { it.distance }
    val favored = ends.firstOrNull {
        it.dimension.id == selectedId && it.distance <= ends.first().distance + endpointTolerance
    }
    // A large hit area is only a candidate: prefer the actual nearest visible element.
    val body = targets.filter { it.hit == DimensionHit.BODY }
        .sortedWith(compareBy<DimensionTarget> { it.distance }
            .thenByDescending { it.label }
            .thenBy { it.labelCenterDistance }
            .thenByDescending { it.dimension.id == selectedId })
    return if (favored == null) ends + body else listOf(favored) + (ends - favored) + body
}

/** Only genuinely ambiguous hits cycle, never merely overlapping generous touch areas. */
internal fun ambiguousDimensionTargets(
    ordered: List<DimensionTarget>, tolerance: Float,
): List<DimensionTarget> {
    val first = ordered.firstOrNull() ?: return emptyList()
    return ordered.filter {
        (it.hit == DimensionHit.BODY) == (first.hit == DimensionHit.BODY) &&
            it.label == first.label &&
            kotlin.math.abs(it.distance - first.distance) <= tolerance &&
            (!first.label || kotlin.math.abs(it.labelCenterDistance - first.labelCenterDistance) <= tolerance)
    }
}

internal data class DimensionTapDecision(val target: DimensionTarget, val openValue: Boolean, val cycle: DimensionTapCycle?)

/** Coincident lines cycle; tapping their label opens the selected member without a long press. */
internal fun decideDimensionTap(
    ordered: List<DimensionTarget>, selectedId: UUID?, previous: DimensionTapCycle?,
    point: EditorPoint, density: Float,
): DimensionTapDecision? {
    val preferred = ordered.firstOrNull() ?: return null
    val candidates = ambiguousDimensionTargets(ordered, density)
    if (preferred.hit == DimensionHit.BODY && preferred.label) {
        val chosen = candidates.firstOrNull { it.dimension.id == selectedId }
        if (chosen != null || candidates.size == 1) {
            return DimensionTapDecision(chosen ?: preferred, true, null)
        }
        // First ambiguous label tap selects; the next ordinary tap opens that value.
        return DimensionTapDecision(preferred, false, null)
    }
    val ids = candidates.map { it.dimension.id }
    val cycle = previous?.next(point, ids, 7f * density) ?: DimensionTapCycle(point, ids, 0)
    return DimensionTapDecision(candidates.first { it.dimension.id == cycle.candidates[cycle.index] }, false, cycle)
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
