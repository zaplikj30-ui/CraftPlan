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
    val direct: Boolean = distance == 0f,
    val notebook: Boolean = false,
)

internal fun preferredDimensionTarget(
    targets: List<DimensionTarget>, selectedId: UUID?, density: Float,
): DimensionTarget? = chooseEditorHit(targets, selectedId, density) {
    EditorHitScore(
        it.dimension.id,
        when {
            it.hit != DimensionHit.BODY -> EditorHitPart.ENDPOINT
            it.notebook -> EditorHitPart.NOTE
            it.label -> EditorHitPart.LABEL
            else -> EditorHitPart.BODY
        },
        it.distance,
        it.labelCenterDistance,
        it.direct,
    )
}

internal data class DimensionTapDecision(val target: DimensionTarget, val openValue: Boolean)

/** First tap selects. Only a value label on the already selected object opens its dialog. */
internal fun decideDimensionTap(target: DimensionTarget?, selectedId: UUID?): DimensionTapDecision? =
    target?.let {
        DimensionTapDecision(it, it.dimension.id == selectedId && it.hit == DimensionHit.BODY && it.label)
    }
