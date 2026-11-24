package cz.craftplan.app

/** In-memory history of confirmed geometry/values. Snapshots share immutable dimension objects. */
@ConsistentCopyVisibility
internal data class DimensionHistory private constructor(
    private val past: List<List<PhotoDimension>>,
    private val future: List<List<PhotoDimension>>,
) {
    constructor() : this(emptyList(), emptyList())

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun record(before: DimensionScene, after: DimensionScene): DimensionHistory {
        if (before.dimensions == after.dimensions) return this
        return DimensionHistory((past + listOf(before.dimensions)).takeLast(MAX_HISTORY), emptyList())
    }

    fun undo(scene: DimensionScene): Pair<DimensionScene, DimensionHistory>? {
        val restored = past.lastOrNull() ?: return null
        return scene.restoreDimensions(restored) to DimensionHistory(past.dropLast(1), future + listOf(scene.dimensions))
    }

    fun redo(scene: DimensionScene): Pair<DimensionScene, DimensionHistory>? {
        val restored = future.lastOrNull() ?: return null
        return scene.restoreDimensions(restored) to DimensionHistory(past + listOf(scene.dimensions), future.dropLast(1))
    }

    private fun DimensionScene.restoreDimensions(restored: List<PhotoDimension>) = copy(
        dimensions = restored,
        selectedId = selectedId?.takeIf { id -> restored.any { it.id == id } },
    )

    private companion object {
        const val MAX_HISTORY = 30
    }
}
