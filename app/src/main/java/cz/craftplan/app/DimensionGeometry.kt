package cz.craftplan.app

import kotlin.math.atan2
import kotlin.math.hypot

internal data class EditorPoint(val x: Float, val y: Float) {
    operator fun minus(other: EditorPoint) = EditorPoint(x - other.x, y - other.y)
    fun distanceTo(other: EditorPoint): Float = hypot(x - other.x, y - other.y)
}

internal enum class DimensionHit {
    START, END, BODY,
}

/** Screen-space distance to the finite line segment, not to its generous hit zone. */
internal fun distanceToDimensionLine(point: EditorPoint, start: EditorPoint, end: EditorPoint): Float {
    val axis = end - start
    val squared = axis.x * axis.x + axis.y * axis.y
    if (squared <= 0f) return point.distanceTo(start)
    val relative = point - start
    val t = ((relative.x * axis.x + relative.y * axis.y) / squared).coerceIn(0f, 1f)
    return point.distanceTo(EditorPoint(start.x + t * axis.x, start.y + t * axis.y))
}

/** All eligible stroke parts are retained until the shared resolver runs. */
internal data class DimensionStrokeTarget(
    val hit: DimensionHit,
    val distance: Float,
    val centerDistance: Float,
    val direct: Boolean,
)

internal fun dimensionStrokeTargets(
    point: EditorPoint, start: EditorPoint, end: EditorPoint, density: Float,
): List<DimensionStrokeTarget> {
    val axis = end - start
    val length = hypot(axis.x, axis.y)
    if (!length.isFinite() || length <= 0f || !density.isFinite() || density <= 0f) return emptyList()
    val ux = axis.x / length
    val uy = axis.y / length
    val t = ((point.x - start.x) * ux + (point.y - start.y) * uy) / length
    val strokeRadius = density
    val tick = 11f * density
    val arrow = minOf(9f * density, length / 4f)
    fun from(center: EditorPoint, along: Float, across: Float) =
        EditorPoint(center.x + ux * along - uy * across, center.y + uy * along + ux * across)
    val result = mutableListOf<DimensionStrokeTarget>()
    fun endpoint(hit: DimensionHit, center: EditorPoint, direction: Float) {
        val centerDistance = point.distanceTo(center)
        if (centerDistance >= 31f * density) return
        val distance = minOf(
            distanceToDimensionLine(point, from(center, 0f, -tick), from(center, 0f, tick)),
            distanceToDimensionLine(point, center, from(center, direction * arrow, arrow / 2f)),
            distanceToDimensionLine(point, center, from(center, direction * arrow, -arrow / 2f)),
        )
        // The handle halo is feedback, not a painted endpoint stroke.
        result += DimensionStrokeTarget(hit, distance, centerDistance, distance <= strokeRadius)
    }
    if (t <= 0.27f) endpoint(DimensionHit.START, start, 1f)
    if (t >= 0.73f) endpoint(DimensionHit.END, end, -1f)
    val lineDistance = distanceToDimensionLine(point, start, end)
    if (lineDistance <= 20f * density) {
        result += DimensionStrokeTarget(DimensionHit.BODY, lineDistance, 0f, lineDistance <= strokeRadius)
    }
    return result
}

internal data class LabelHitDistance(val distance: Float, val centerDistance: Float)

/** Distance to a visible rectangle in the label's rotated local coordinates. */
internal fun labelRectangleDistance(
    along: Float, across: Float, left: Float, top: Float, right: Float, bottom: Float,
): LabelHitDistance = LabelHitDistance(
    hypot(maxOf(left - along, along - right, 0f), maxOf(top - across, across - bottom, 0f)),
    hypot(along - (left + right) / 2f, across - (top + bottom) / 2f),
)

internal fun PhotoDimension.move(hit: DimensionHit, du: Float, dv: Float): PhotoDimension {
    val movedStart = if (hit == DimensionHit.END) start else start.shift(du, dv) ?: return this
    val movedEnd = if (hit == DimensionHit.START) end else end.shift(du, dv) ?: return this
    return if (movedStart == movedEnd) this else copy(start = movedStart, end = movedEnd)
}

internal fun PhotoDimension.shiftLabel(axis: EditorPoint, delta: EditorPoint, locked: Boolean): PhotoDimension {
    if (locked) return this
    val lengthSquared = axis.x.toDouble() * axis.x + axis.y.toDouble() * axis.y
    val projected = (delta.x.toDouble() * axis.x + delta.y.toDouble() * axis.y) / lengthSquared
    if (!projected.isFinite()) return this
    val updated = (labelT + 2.0 * projected).coerceIn(-0.92, 0.92).toFloat()
    return if (updated == labelT) this else copy(labelT = updated)
}

private fun PhotoAnchor.shift(du: Float, dv: Float): PhotoAnchor? {
    val x = u + du
    val y = v + dv
    return if (x.isFinite() && y.isFinite()) PhotoAnchor(x, y) else null
}

/** After pinch starts, single-pointer edits and taps resume only on a new ACTION_DOWN. */
internal class EditorTouchStream {
    var pinchOnly = false
        private set

    fun begin() { pinchOnly = false }
    fun beginPinch(cancelPreview: () -> Unit) {
        if (pinchOnly) return
        pinchOnly = true
        cancelPreview()
    }
}

internal fun readableAngle(axis: EditorPoint): Float {
    val degrees = Math.toDegrees(atan2(axis.y.toDouble(), axis.x.toDouble())).toFloat()
    return when {
        degrees > 90f -> degrees - 180f
        degrees <= -90f -> degrees + 180f
        else -> degrees
    }
}

internal fun magnifierOnRight(x: Float, width: Int, previous: Boolean?): Boolean = when {
    x < width * 0.45f -> true
    x > width * 0.55f -> false
    else -> previous ?: (x <= width / 2f)
}

internal fun passesDragThreshold(down: EditorPoint, current: EditorPoint, threshold: Float): Boolean =
    down.distanceTo(current) > threshold

/** A short stroke can be deliberately dragged without first leaving its entire hit region. */
internal fun dimensionDragThreshold(span: Float, density: Float, usualThreshold: Float): Float =
    (span * 0.2f).coerceIn(3f * density, usualThreshold)

/** Screen-space dot grid phase following the workspace offset in either direction. */
internal fun workspaceGridOffset(workspaceOffset: Float, spacing: Float): Float {
    if (!workspaceOffset.isFinite() || !spacing.isFinite() || spacing <= 0f) return 0f
    return ((workspaceOffset % spacing) + spacing) % spacing
}

/**
 * Vertical auto-pan begins well before the very top/bottom of the editor.
 * The middle stays neutral so horizontal drags remain straight.
 */
internal fun verticalAutoPanEdgeSize(height: Int, density: Float, isLandscape: Boolean): Float {
    if (height <= 0 || !density.isFinite() || density <= 0f) return 0f
    // Orientation only adjusts activation distance; gesture mechanics stay identical.
    val portion = if (isLandscape) 0.30f else 0.43f
    val maxDp = if (isLandscape) 180f else 260f
    return minOf(height * portion, maxDp * density)
}

/** Signed camera speed. The photo moves toward the middle of the view. */
internal fun edgePanSpeed(position: Float, size: Int, edgeSize: Float, maxSpeed: Float): Float {
    val edge = minOf(edgeSize, size / 2f)
    if (edge <= 0f || !position.isFinite()) return 0f
    val near = ((edge - position) / edge).coerceIn(0f, 1f)
    val far = ((position - (size - edge)) / edge).coerceIn(0f, 1f)
    return (near - far) * maxSpeed
}
