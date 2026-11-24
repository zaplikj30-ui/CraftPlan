package cz.craftplan.app

import androidx.compose.ui.unit.Dp
import kotlin.math.abs

private fun toolbarDragIntent(delta: Float, threshold: Float): Boolean? =
    when {
        !delta.isFinite() || !threshold.isFinite() || threshold <= 0f -> null
        delta >= threshold -> true
        delta <= -threshold -> false
        else -> null
    }

/** Delta is positive in the expansion direction. The platform already applies touch slop. */
internal fun toolbarExpansionAfterDrag(expanded: Boolean, delta: Float, threshold: Float): Boolean =
    toolbarDragIntent(delta, threshold) ?: expanded

/** A left drag collapses both rows; a right drag opens only the horizontal row. */
internal fun toolbarHorizontalExpansionAfterDrag(
    expanded: Boolean,
    extraExpanded: Boolean,
    delta: Float,
    threshold: Float,
): Pair<Boolean, Boolean> = when (toolbarDragIntent(delta, threshold)) {
    true -> true to extraExpanded
    false -> false to false
    null -> expanded to extraExpanded
}


/** A tap toggles; a vertical drag sets the state once on release. Travel beyond slop is never a tap. */
internal fun toolbarGripAfterRelease(expanded: Boolean, dx: Float, dy: Float, maxTravel: Float, threshold: Float): Boolean {
    if (!dx.isFinite() || !dy.isFinite() || !maxTravel.isFinite() || maxTravel < 0f ||
        !threshold.isFinite() || threshold <= 0f) return expanded
    if (maxOf(abs(dx), abs(dy), maxTravel) < threshold) return !expanded
    if (abs(dy) < threshold || abs(dy) <= abs(dx)) return expanded
    return toolbarExpansionAfterDrag(expanded, -dy, threshold)
}

/** Restore the window center inside a layout already padded by the safe drawing insets. */
internal fun toolbarHintCenterOffset(leftInset: Dp, rightInset: Dp): Dp = (rightInset - leftInset) / 2
