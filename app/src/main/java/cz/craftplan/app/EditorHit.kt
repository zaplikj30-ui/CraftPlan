package cz.craftplan.app

import java.util.UUID

/** Shape-independent touch arbitration. Distances and tolerances are in screen pixels. */
/** Keep input type separate from gesture meaning; identical initial ergonomics until device tests. */
internal enum class EditorPointerKind { FINGER, STYLUS, ERASER, MOUSE, OTHER }

internal data class EditorInputProfile(
    val kind: EditorPointerKind,
    val hitDensity: Float,
    val editDragThreshold: Float,
)

internal fun editorInputProfile(kind: EditorPointerKind, density: Float): EditorInputProfile =
    EditorInputProfile(kind, density, 7f * density)

internal enum class EditorHitPart { ENDPOINT, LABEL, NOTE, BODY }

internal data class EditorHitScore(
    val ownerId: UUID,
    val part: EditorHitPart,
    val distance: Float,
    val labelCenterDistance: Float = 0f,
    val direct: Boolean = distance == 0f,
)

private fun EditorHitPart.priority(): Int = when (this) {
    EditorHitPart.ENDPOINT -> 0
    EditorHitPart.LABEL, EditorHitPart.NOTE -> 1
    EditorHitPart.BODY -> 2
}

/** Direct geometry beats any expanded-only target. Selection breaks exact geometric ties. */
internal fun <T> chooseEditorHit(
    candidates: List<T>, selectedId: UUID?, density: Float, scoreOf: (T) -> EditorHitScore,
): T? {
    if (!density.isFinite() || density <= 0f) return null
    val scored = candidates.map { it to scoreOf(it) }.filter {
        it.second.distance.isFinite() && it.second.distance >= 0f &&
            it.second.labelCenterDistance.isFinite() && it.second.labelCenterDistance >= 0f
    }
    return scored.minWithOrNull(
        compareBy<Pair<T, EditorHitScore>> { !it.second.direct }
            .thenBy { it.second.distance }
            .thenBy { it.second.part.priority() }
            .thenBy { it.second.labelCenterDistance }
            .thenBy { it.second.ownerId != selectedId },
    )?.first
}
