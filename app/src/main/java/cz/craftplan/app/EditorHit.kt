package cz.craftplan.app

import java.util.UUID
import kotlin.math.abs

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

private fun EditorHitPart.isLabel(): Boolean = this == EditorHitPart.LABEL || this == EditorHitPart.NOTE

/** Direct geometry beats any expanded-only target. Selection breaks near ties within the same
 * hit class. Stable front-to-back input order is the last tie-break; no cycling. */
internal fun <T> chooseEditorHit(
    candidates: List<T>, selectedId: UUID?, density: Float, scoreOf: (T) -> EditorHitScore,
): T? {
    if (!density.isFinite() || density <= 0f) return null
    val scored = candidates.map { it to scoreOf(it) }.filter {
        it.second.distance.isFinite() && it.second.distance >= 0f &&
            it.second.labelCenterDistance.isFinite() && it.second.labelCenterDistance >= 0f
    }
    val nearest = scored.minWithOrNull(
        compareBy<Pair<T, EditorHitScore>> { !it.second.direct }
            .thenBy { it.second.distance }
            .thenBy { it.second.part.priority() }
            .thenBy { it.second.labelCenterDistance },
    ) ?: return null
    val best = nearest.second
    val tolerance = if (best.part == EditorHitPart.ENDPOINT) 7f * density else density
    val selected = scored.filter { (_, score) ->
        score.ownerId == selectedId && score.part == best.part && score.direct == best.direct &&
            score.distance <= best.distance + tolerance &&
            (!best.part.isLabel() ||
                abs(score.labelCenterDistance - best.labelCenterDistance) <= density)
    }.minWithOrNull(compareBy<Pair<T, EditorHitScore>> { it.second.distance }
        .thenBy { it.second.labelCenterDistance })
    return (selected ?: nearest).first
}
