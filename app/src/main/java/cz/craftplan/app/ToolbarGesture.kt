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
    SHARED_TAP, SHARED_HORIZONTAL, SHARED_LONG_PRESS,
}

internal fun toolbarGripReleaseReason(dx: Float, dy: Float, maxTravel: Float, threshold: Float): ToolbarGripOutcome {
    if (!dx.isFinite() || !dy.isFinite() || !maxTravel.isFinite() || maxTravel < 0f ||
        !threshold.isFinite() || threshold <= 0f) return ToolbarGripOutcome.INVALID
    if (maxOf(abs(dx), abs(dy), maxTravel) < threshold) return ToolbarGripOutcome.TAP
    if (abs(dy) < threshold) return ToolbarGripOutcome.BELOW_VERTICAL_THRESHOLD
    if (abs(dy) <= abs(dx)) return ToolbarGripOutcome.NON_VERTICAL
    return if (dy < 0f) ToolbarGripOutcome.UP else ToolbarGripOutcome.DOWN
}

/** Same release policy; only the separate side grip retains tap-to-toggle. */
internal fun toolbarGripAfterRelease(
    expanded: Boolean, dx: Float, dy: Float, maxTravel: Float, threshold: Float,
    tapToggles: Boolean = true,
): Boolean =
    when (toolbarGripReleaseReason(dx, dy, maxTravel, threshold)) {
        ToolbarGripOutcome.TAP -> if (tapToggles) !expanded else expanded
        ToolbarGripOutcome.UP -> true
        ToolbarGripOutcome.DOWN -> false
        else -> expanded
    }

/** Actual top grip bounds in the common Compose root, including its workspace overlap. */
internal data class ToolbarTopGripRegion(
    val left: Float, val top: Float, val right: Float, val bottom: Float, val surfaceTop: Float,
) {
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom

    fun inWorkspace(y: Float): Boolean = y < surfaceTop

    fun canTrack(x: Float, y: Float, editorPriority: Boolean): Boolean =
        contains(x, y) && (!inWorkspace(y) || !editorPriority)
}

internal enum class ToolbarSharedDecision { WAIT, VERTICAL, HORIZONTAL, LONG_PRESS, INVALID }

/** Only the first decisive direction may claim the shared area. No tap or hold is claimed. */
internal fun toolbarSharedDecision(
    dx: Float, dy: Float, threshold: Float, elapsedMs: Long, longPressMs: Long,
): ToolbarSharedDecision {
    if (!dx.isFinite() || !dy.isFinite() || !threshold.isFinite() || threshold <= 0f ||
        elapsedMs < 0 || longPressMs <= 0) return ToolbarSharedDecision.INVALID
    if (elapsedMs >= longPressMs) return ToolbarSharedDecision.LONG_PRESS
    if (maxOf(abs(dx), abs(dy)) < threshold) return ToolbarSharedDecision.WAIT
    return if (abs(dy) > abs(dx)) ToolbarSharedDecision.VERTICAL else ToolbarSharedDecision.HORIZONTAL
}

/** The strip is inside the surface, immediately below the dedicated top grip. */
internal fun toolbarSharedContains(x: Float, y: Float, width: Float, gripWidth: Float,
    gripOverlap: Float, extension: Float,
): Boolean {
    val half = minOf(width, gripWidth) / 2f
    return x >= width / 2f - half && x < width / 2f + half &&
        y >= gripOverlap && y < gripOverlap + extension
}

/** Restore the window center inside a layout already padded by the safe drawing insets. */
internal fun toolbarHintCenterOffset(leftInset: Dp, rightInset: Dp): Dp = (rightInset - leftInset) / 2
