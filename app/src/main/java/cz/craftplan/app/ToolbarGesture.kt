package cz.craftplan.app

import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/** Delta is positive in the expansion direction. The platform already applies touch slop. */
internal fun toolbarExpansionAfterDrag(expanded: Boolean, delta: Float, threshold: Float): Boolean =
    when {
        !delta.isFinite() || !threshold.isFinite() || threshold <= 0f -> expanded
        delta >= threshold -> true
        delta <= -threshold -> false
        else -> expanded
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
