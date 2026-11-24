package cz.craftplan.app

import java.util.UUID
import kotlin.math.abs

/** Shape-independent touch arbitration. Distances and tolerances are in screen pixels. */
internal enum class EditorHitPart { ENDPOINT, LABEL, BODY }

internal data class EditorHitScore(
    val ownerId: UUID,
    val part: EditorHitPart,
    val distance: Float,
    val labelCenterDistance: Float = 0f,
)

/** Endpoint priority is preserved. Otherwise visible geometry wins; selection only breaks near ties.
 * The caller supplies candidates in stable front-to-back order. No tap cycling or per-frame work.
 */
internal fun <T> chooseEditorHit(
    candidates: List<T>, selectedId: UUID?, density: Float, scoreOf: (T) -> EditorHitScore,
): T? {
    val scored = candidates.map { it to scoreOf(it) }.filter {
        it.second.distance.isFinite() && it.second.distance >= 0f &&
            it.second.labelCenterDistance.isFinite() && it.second.labelCenterDistance >= 0f
    }
    val nearest = scored.minWithOrNull(
        compareBy<Pair<T, EditorHitScore>> { if (it.second.part == EditorHitPart.ENDPOINT) 0 else 1 }
            .thenBy { it.second.distance }
            .thenBy { if (it.second.part == EditorHitPart.LABEL) 0 else 1 }
            .thenBy { it.second.labelCenterDistance },
    ) ?: return null
    val best = nearest.second
    val tolerance = if (best.part == EditorHitPart.ENDPOINT) 7f * density else density
    val selected = scored.firstOrNull { (_, score) ->
        score.ownerId == selectedId && score.part == best.part &&
            score.distance <= best.distance + tolerance &&
            (best.part != EditorHitPart.LABEL ||
                ((best.distance > 0f || score.distance == 0f) &&
                    abs(score.labelCenterDistance - best.labelCenterDistance) <= density))
    }
    return (selected ?: nearest).first
}
