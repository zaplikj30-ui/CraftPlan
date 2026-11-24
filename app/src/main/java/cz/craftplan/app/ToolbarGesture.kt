package cz.craftplan.app

import androidx.compose.ui.unit.Dp

/** Delta is positive in the expansion direction. The platform already applies touch slop. */
internal fun toolbarExpansionAfterDrag(expanded: Boolean, delta: Float, threshold: Float): Boolean =
    when {
        !delta.isFinite() || !threshold.isFinite() || threshold <= 0f -> expanded
        delta >= threshold -> true
        delta <= -threshold -> false
        else -> expanded
    }


/** Restore the window center inside a layout already padded by the safe drawing insets. */
internal fun toolbarHintCenterOffset(leftInset: Dp, rightInset: Dp): Dp = (rightInset - leftInset) / 2
