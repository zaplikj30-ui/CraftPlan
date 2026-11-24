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


internal enum class ToolbarGripOutcome {
    TAP, UP, DOWN, BELOW_VERTICAL_THRESHOLD, NON_VERTICAL, INVALID,
    POINTER_MISSING, CONSUMED, MULTI_POINTER, STREAM_CANCELED,
}

internal fun toolbarGripReleaseReason(dx: Float, dy: Float, maxTravel: Float, threshold: Float): ToolbarGripOutcome {
    if (!dx.isFinite() || !dy.isFinite() || !maxTravel.isFinite() || maxTravel < 0f ||
        !threshold.isFinite() || threshold <= 0f) return ToolbarGripOutcome.INVALID
    if (maxOf(abs(dx), abs(dy), maxTravel) < threshold) return ToolbarGripOutcome.TAP
    if (abs(dy) < threshold) return ToolbarGripOutcome.BELOW_VERTICAL_THRESHOLD
    if (abs(dy) <= abs(dx)) return ToolbarGripOutcome.NON_VERTICAL
    return if (dy < 0f) ToolbarGripOutcome.UP else ToolbarGripOutcome.DOWN
}

/** Same release policy; the diagnostic reason is also the source of the decision. */
internal fun toolbarGripAfterRelease(expanded: Boolean, dx: Float, dy: Float, maxTravel: Float, threshold: Float): Boolean =
    when (toolbarGripReleaseReason(dx, dy, maxTravel, threshold)) {
        ToolbarGripOutcome.TAP -> !expanded
        ToolbarGripOutcome.UP -> true
        ToolbarGripOutcome.DOWN -> false
        else -> expanded
    }

/** Restore the window center inside a layout already padded by the safe drawing insets. */
internal fun toolbarHintCenterOffset(leftInset: Dp, rightInset: Dp): Dp = (rightInset - leftInset) / 2
