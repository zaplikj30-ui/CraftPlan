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
    val gripCore: Boolean = false,
)

internal fun dimensionStrokeTargets(
    point: EditorPoint, start: EditorPoint, end: EditorPoint, density: Float, strokeScale: Float = 1f,
    tickScale: Float = 1f,
): List<DimensionStrokeTarget> {
    val axis = end - start
    val length = hypot(axis.x, axis.y)
    if (!length.isFinite() || length <= 0f || !density.isFinite() || density <= 0f) return emptyList()
    val ux = axis.x / length
    val uy = axis.y / length
    val t = ((point.x - start.x) * ux + (point.y - start.y) * uy) / length
    // Direct hits follow the drawn strokes, but keep the established grip core,
    // expanded touch tolerances and endpoint/body partition at device density.
    val strokeDensity = density * strokeScale
    val strokeRadius = strokeDensity
    val tick = dimensionTickHalfLength(strokeDensity, tickScale)
    val arrow = minOf(9f * strokeDensity, length / 4f)
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
        // Keep the visible handle center separate from the broader touch tolerance.
        result += DimensionStrokeTarget(
            hit, distance, centerDistance, distance <= strokeRadius, gripCore = centerDistance <= 4f * density,
        )
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

/** Baseline label geometry, scaled as a single unit in the Canvas. */
internal data class LabelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    /** Preserve the baseline hit zone and margins while ranking the actual scaled rectangle. */
    fun hit(x: Float, y: Float, scale: Float, touch: LabelRect): LabelHitDistance? {
        val l = left * scale
        val t = top * scale
        val r = right * scale
        val b = bottom * scale
        val expanded = x in (l - (left - touch.left))..(r + (touch.right - right)) &&
            y in (t - (top - touch.top))..(b + (touch.bottom - bottom))
        if (!expanded && !touch.contains(x, y)) return null
        return labelRectangleDistance(x, y, l, t, r, b)
    }
}

internal data class DimensionLabelGeometry(
    val ruler: LabelRect?,
    val text: LabelRect?,
    val notebook: LabelRect?,
    val rulerTouch: LabelRect,
    val textTouch: LabelRect?,
    val notebookTouch: LabelRect?,
) {
    /** Only the ruler has a readability floor; the common label scale remains unchanged. */
    fun rulerScale(labelScale: Float): Float = maxOf(0.95f, labelScale)

    /** Screen-pixel offset for adjacent note/notebook, retaining their original gap. */
    fun rulerNeighborOffset(labelScale: Float): Float =
        (ruler?.right ?: 0f) * (rulerScale(labelScale) - labelScale)

    // Includes every visible corner even when the decoration rotates.
    // The ruler floor is below 1x, and shifting its neighbors by the extra
    // half-width cannot put their right edge beyond their 1x baseline extent.
    val cullingRadius: Float = listOfNotNull(ruler, text, notebook).maxOfOrNull {
        hypot(maxOf(kotlin.math.abs(it.left), kotlin.math.abs(it.right)),
            maxOf(kotlin.math.abs(it.top), kotlin.math.abs(it.bottom)))
    } ?: 0f
}

internal fun dimensionLabelGeometry(
    density: Float, textWidth: Float?, labelHeight: Float, besideRuler: Boolean, notebook: Boolean,
): DimensionLabelGeometry {
    val height = maxOf(36f * density, labelHeight + 12f * density)
    val ruler = if (textWidth == null || besideRuler)
        LabelRect(-14f * density, -17f * density, 14f * density, -7f * density) else null
    val text = textWidth?.let {
        val half = it / 2f + 5f * density
        val center = if (besideRuler) 18f * density + half else 0f
        val bottom = -5f * density
        LabelRect(center - half, bottom - labelHeight - 4f * density, center + half, bottom)
    }
    val notebookRect = if (notebook) {
        val left = (textWidth?.div(2f) ?: (14f * density)) + 10f * density
        LabelRect(left, -24f * density, left + 15f * density, -7f * density)
    } else null
    return DimensionLabelGeometry(
        ruler, text, notebookRect,
        LabelRect(-24f * density, -height, 24f * density, -2f * density),
        text?.let { LabelRect(it.left - 3f * density, -height, it.right + 3f * density, -2f * density) },
        notebookRect?.let { LabelRect(it.left - 8f * density, -32f * density, it.right + 8f * density, density) },
    )
}

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

internal fun readableAngle(axis: EditorPoint): Float =
    readableDegrees(Math.toDegrees(atan2(axis.y.toDouble(), axis.x.toDouble())).toFloat())

internal fun readableDegrees(degrees: Float): Float = when {
    degrees > 90f -> degrees - 180f
    degrees <= -90f -> degrees + 180f
    else -> degrees
}

/** Conservative screen bounds; an intersection can be a false positive, never a clipped decoration. */
internal fun boundsMayBeVisible(
    x1: Float, y1: Float, x2: Float, y2: Float, padding: Float, width: Int, height: Int,
): Boolean =
    maxOf(x1, x2) + padding >= 0f && minOf(x1, x2) - padding <= width &&
        maxOf(y1, y2) + padding >= 0f && minOf(y1, y2) - padding <= height

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
 * New dimensions use the established broad vertical edge zones.
 * Editing an existing endpoint needs a narrower zone for precise adjustments.
 */
internal fun verticalAutoPanEdgeSize(
    height: Int, density: Float, isLandscape: Boolean, editingEndpoint: Boolean = false,
): Float {
    if (height <= 0 || !density.isFinite() || density <= 0f) return 0f
    if (editingEndpoint) return minOf(height / 2f, 48f * density)
    // Keep the established activation distance for drawing in each orientation.
    val portion = if (isLandscape) 0.30f else 0.43f
    val maxDp = if (isLandscape) 180f else 260f
    return minOf(height * portion, maxDp * density)
}

/** Only endpoint editing uses the measured fixed toolbar as its lower edge. */
internal fun verticalAutoPanSpeed(
    y: Float, height: Int, safeTopPx: Int, fixedToolbarTopPx: Int?, density: Float,
    isLandscape: Boolean, editingEndpoint: Boolean,
): Float {
    val top = safeTopPx.coerceIn(0, height.coerceAtLeast(0))
    val bottom = if (editingEndpoint) (fixedToolbarTopPx ?: height).coerceIn(top, height.coerceAtLeast(top))
        else height.coerceAtLeast(top)
    val usableHeight = bottom - top
    val edge = verticalAutoPanEdgeSize(usableHeight, density, isLandscape, editingEndpoint)
    return edgePanSpeed(y - top, usableHeight, edge, 440f * density)
}

/** Signed camera speed. The photo moves toward the middle of the view. */
internal fun edgePanSpeed(position: Float, size: Int, edgeSize: Float, maxSpeed: Float): Float {
    val edge = minOf(edgeSize, size / 2f)
    if (edge <= 0f || !position.isFinite()) return 0f
    val near = ((edge - position) / edge).coerceIn(0f, 1f)
    val far = ((position - (size - edge)) / edge).coerceIn(0f, 1f)
    return (near - far) * maxSpeed
}

private fun dimensionTickHalfLength(strokeDensity: Float, tickScale: Float): Float =
    11f * strokeDensity * tickScale

/** Visual ring encloses the same tick geometry including its square outline caps.
 *  The 2.2dp margin is a 1.5dp clear gap plus half the 1.4dp ring stroke.
 *  This is deliberately not used by hit testing.
 */
internal fun dimensionHandleRadius(
    density: Float, strokeScale: Float, tickScale: Float, outlineWidth: Float,
): Float {
    val strokeDensity = density * strokeScale
    val halfOutline = outlineWidth * strokeDensity / 2f
    return hypot(dimensionTickHalfLength(strokeDensity, tickScale) + halfOutline, halfOutline) + 2.2f * density
}

/** Seven independent segments in the same order as the individually drawn strokes. */
internal fun writeDimensionSegments(segments: FloatArray, length: Float, density: Float, tickScale: Float = 1f) {
    val tick = dimensionTickHalfLength(density, tickScale)
    val arrow = minOf(9f * density, length / 4f)
    val wing = arrow / 2f
    fun line(offset: Int, x1: Float, y1: Float, x2: Float, y2: Float) {
        segments[offset] = x1
        segments[offset + 1] = y1
        segments[offset + 2] = x2
        segments[offset + 3] = y2
    }
    line(0, 0f, 0f, length, 0f)
    line(4, 0f, -tick, 0f, tick)
    line(8, length, -tick, length, tick)
    line(12, 0f, 0f, arrow, -wing)
    line(16, length, 0f, length - arrow, -wing)
    line(20, 0f, 0f, arrow, wing)
    line(24, length, 0f, length - arrow, wing)
}
