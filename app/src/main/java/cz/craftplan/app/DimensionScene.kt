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

    fun tapDrawingTool() = if (tool == DimensionTool.SELECT) prepareSingle()
        else copy(tool = DimensionTool.SELECT)

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

    fun replaceSynthetic(batch: List<PhotoDimension>): DimensionScene {
        require(batch.all(SyntheticDimensions::owns))
        val manual = dimensions.filterNot(SyntheticDimensions::owns)
        return copy(dimensions = manual + batch, selectedId = selectedId?.takeIf { id -> manual.any { it.id == id } })
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
    val gripCore: Boolean = false,
)

internal fun preferredDimensionTarget(
    targets: List<DimensionTarget>, selectedId: UUID?, density: Float,
): DimensionTarget? {
    fun score(target: DimensionTarget) = EditorHitScore(
        target.dimension.id,
        when {
            target.hit != DimensionHit.BODY -> EditorHitPart.ENDPOINT
            target.notebook -> EditorHitPart.NOTE
            target.label -> EditorHitPart.LABEL
            else -> EditorHitPart.BODY
        },
        target.distance,
        target.labelCenterDistance,
        target.direct,
    )
    val best = chooseEditorHit(targets, selectedId, density, ::score) ?: return null
    // Protect the center of a visible selected handle against a crossing line or label.
    // A direct hit on a different endpoint still uses the established distance ordering.
    if (selectedId != null && (best.hit == DimensionHit.BODY || !best.direct)) {
        val endpoints = targets.filter {
            it.hit != DimensionHit.BODY && if (it.dimension.id == selectedId) it.gripCore else it.direct
        }
        val preferredEndpoint = chooseEditorHit(endpoints, selectedId, density, ::score)
        if (preferredEndpoint?.dimension?.id == selectedId) return preferredEndpoint
    }
    // Preserve the familiar end portion of an isolated dimension, but never let an expanded
    // endpoint intercept a direct label or a direct stroke of another object.
    if (best.hit == DimensionHit.BODY && !best.label &&
        targets.none { it.direct && (it.dimension.id != best.dimension.id || it.label) } &&
        (best.direct || targets.all { it.dimension.id == best.dimension.id })
    ) {
        return chooseEditorHit(targets.filter {
            it.dimension.id == best.dimension.id && it.hit != DimensionHit.BODY
        }, selectedId, density, ::score) ?: best
    }
    return best
}

internal data class DimensionTapDecision(val target: DimensionTarget, val openValue: Boolean)

/** First tap selects. Only a value label on the already selected object opens its dialog. */
internal fun decideDimensionTap(target: DimensionTarget?, selectedId: UUID?): DimensionTapDecision? =
    target?.let {
        DimensionTapDecision(it, it.dimension.id == selectedId && it.hit == DimensionHit.BODY && it.label)
    }
