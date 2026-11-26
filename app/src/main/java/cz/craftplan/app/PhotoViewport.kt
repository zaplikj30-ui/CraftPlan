package cz.craftplan.app

import kotlin.math.hypot
import kotlin.math.log2

/**
 * Maps image coordinates to the viewport for display. Lengths and document coordinates
 * are independent of this transform.
 */
internal class PhotoViewport(private val imageWidth: Int, private val imageHeight: Int) {
    init {
        require(imageWidth > 0 && imageHeight > 0)
    }

    private var width = 0
    private var height = 0
    private var photoFitScale = 1f
    private var contentFitScale = 1f
    private var minU = 0f
    private var maxU = 1f
    private var minV = 0f
    private var maxV = 1f
    private var handleMargin = 0f

    fun updateContentBounds(dimension: PhotoDimension?, handleMargin: Float) {
        updateContentBounds(listOfNotNull(dimension), handleMargin)
    }

    fun updateContentBounds(dimensions: List<PhotoDimension>, handleMargin: Float) {
        minU = 0f
        maxU = 1f
        minV = 0f
        maxV = 1f
        for ((_, _, start, end) in dimensions) {
            minU = minOf(minU, start.u, end.u)
            maxU = maxOf(maxU, start.u, end.u)
            minV = minOf(minV, start.v, end.v)
            maxV = maxOf(maxV, start.v, end.v)
        }
        this.handleMargin = handleMargin
        if (width > 0 && height > 0) contentFitScale = contentFit().scale
        transform(0f, 0f, 0f, 0f)
    }

    var scale = 1f
        private set
    var left = 0f
        private set
    var top = 0f
        private set

    private val dimensionZoomProgress: Float
        get() = log2((scale / photoFitScale).coerceIn(1f, MAX_ZOOM)) / log2(MAX_ZOOM)

    /** Visual stroke size only: at photo fit 0.8x, at maximum zoom the unchanged 2x. */
    val dimensionStrokeScale: Float
        get() = 0.8f + 1.2f * dimensionZoomProgress

    /** Additional shortening of the end ticks at fit, disappearing at maximum zoom. */
    val dimensionTickScale: Float
        get() = 0.8f + 0.2f * dimensionZoomProgress

    /** Yellow stroke width relative to stroke density; unchanged at maximum zoom. */
    val dimensionFillWidth: Float
        get() = 1.7f + 0.3f * dimensionZoomProgress

    /** Black under-stroke: at fit, 15% less yellow and 25% more border per side.
     *  The total width stays almost unchanged (2.95 instead of 3), and is 4 at maximum zoom.
     */
    val dimensionOutlineWidth: Float
        get() = 2.95f + 1.05f * dimensionZoomProgress

    /** Photo-relative decoration scale: 9sp nominal at a 360dp photo long edge.
     *  Unlike fit-relative zoom, this preserves proportions across viewport resize,
     *  different bitmap resolutions and fitting annotations outside the photo.
     */
    fun dimensionLabelScale(density: Float): Float =
        0.6f * (maxOf(imageWidth, imageHeight) * scale / (360f * density))

    /** Destination edges are in the same space as the normalized anchors. */
    val photoRight: Float get() = left + imageWidth * scale
    val photoBottom: Float get() = top + imageHeight * scale

    fun fit(width: Int, height: Int) {
        this.width = width
        this.height = height
        if (width <= 0 || height <= 0) return

        updatePhotoFitScale()
        val fit = contentFit()
        contentFitScale = fit.scale
        scale = fit.scale
        left = (width / 2.0 - fit.centerX * scale).toFloat()
        top = (height / 2.0 - fit.centerY * scale).toFloat()
    }

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (this.width <= 0 || this.height <= 0) {
            fit(width, height)
            return
        }

        val oldCenterX = this.width / 2f
        val oldCenterY = this.height / 2f
        this.width = width
        this.height = height
        updatePhotoFitScale()
        contentFitScale = contentFit().scale
        transform(oldCenterX, oldCenterY, width / 2f, height / 2f)
    }

    private fun updatePhotoFitScale() {
        photoFitScale = minOf(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
    }

    private data class ViewportFit(val scale: Float, val centerX: Double, val centerY: Double)

    private fun contentFit(): ViewportFit {
        if (minU == 0f && maxU == 1f && minV == 0f && maxV == 1f) {
            return ViewportFit(photoFitScale, imageWidth / 2.0, imageHeight / 2.0)
        }

        // Use Double for the bounds so distant normalized anchors can be fitted together.
        val left = minU.toDouble() * imageWidth
        val right = maxU.toDouble() * imageWidth
        val top = minV.toDouble() * imageHeight
        val bottom = maxV.toDouble() * imageHeight
        val margin = minOf(handleMargin, width / 4f, height / 4f)
        val fitScale = minOf(
            (width - 2.0 * margin) / (right - left),
            (height - 2.0 * margin) / (bottom - top),
        )
        return ViewportFit(fitScale.toFloat(), (left + right) / 2.0, (top + bottom) / 2.0)
    }

    fun transform(
        fromX: Float, fromY: Float, toX: Float, toY: Float, factor: Float = 1f,
        extendingForDrag: Boolean = false,
    ) {
        if (width <= 0 || height <= 0 || !factor.isFinite() || factor <= 0f ||
            !fromX.isFinite() || !fromY.isFinite() || !toX.isFinite() || !toY.isFinite()
        ) return

        // If an edit removes an overhang, keep a previously valid hand-set zoom
        // until the user zooms back into the new range.
        val minScale = minOf(scale, contentFitScale)
        val nextScale = (scale * factor).coerceIn(minScale, photoFitScale * MAX_ZOOM)
        val ratio = nextScale.toDouble() / scale
        val requestedLeft = (toX - (fromX - left) * ratio).toFloat()
        val requestedTop = (toY - (fromY - top) * ratio).toFloat()
        if (!requestedLeft.isFinite() || !requestedTop.isFinite()) return
        left = boundedOffset(
            requestedLeft, width, imageWidth * nextScale,
            minU, maxU, extendingForDrag,
        )
        top = boundedOffset(
            requestedTop, height, imageHeight * nextScale,
            minV, maxV, extendingForDrag,
        )
        scale = nextScale
    }

    fun contains(x: Float, y: Float): Boolean =
        width > 0 && height > 0 && x in 0f..width.toFloat() && y in 0f..height.toFloat()

    fun clampContact(x: Float, y: Float): EditorPoint? {
        if (width <= 0 || height <= 0 || !x.isFinite() || !y.isFinite()) return null
        return EditorPoint(x.coerceIn(0f, width.toFloat()), y.coerceIn(0f, height.toFloat()))
    }

    fun toPhotoAnchor(x: Float, y: Float): PhotoAnchor? {
        if (!contains(x, y)) return null
        return PhotoAnchor((x - left) / (imageWidth * scale), (y - top) / (imageHeight * scale))
    }

    fun dragEnd(start: PhotoAnchor, x: Float, y: Float, touchSlop: Int): PhotoAnchor? {
        val end = toPhotoAnchor(x, y) ?: return null
        val distance = hypot(x - screenX(start), y - screenY(start))
        return end.takeIf { distance >= touchSlop }
    }

    fun screenX(anchor: PhotoAnchor): Float = left + anchor.u * (imageWidth * scale)

    fun screenY(anchor: PhotoAnchor): Float = top + anchor.v * (imageHeight * scale)

    fun screenPoint(anchor: PhotoAnchor) = EditorPoint(screenX(anchor), screenY(anchor))

    fun moveDimension(dimension: PhotoDimension, hit: DimensionHit, delta: EditorPoint): PhotoDimension {
        if (width <= 0 || height <= 0 || !delta.x.isFinite() || !delta.y.isFinite()) return dimension

        // A grabbed endpoint retains its touch offset up to the edge of the editor.
        val movement = when (hit) {
            DimensionHit.START -> limitEndpointMovement(screenPoint(dimension.start), delta)
            DimensionHit.END -> limitEndpointMovement(screenPoint(dimension.end), delta)
            DimensionHit.BODY -> delta
        }
        return dimension.move(hit, movement.x / (imageWidth * scale), movement.y / (imageHeight * scale))
    }

    private fun limitEndpointMovement(point: EditorPoint, delta: EditorPoint): EditorPoint =
        EditorPoint(
            (point.x + delta.x).coerceIn(minOf(0f, point.x), maxOf(width.toFloat(), point.x)),
            (point.y + delta.y).coerceIn(minOf(0f, point.y), maxOf(height.toFloat(), point.y)),
        ) - point

    private fun boundedOffset(
        offset: Float, viewportSize: Int, imageSize: Float,
        nearAnchor: Float, farAnchor: Float, extendingForDrag: Boolean,
    ): Float {
        val visible = minOf(viewportSize.toFloat(), imageSize) / 2f
        val margin = minOf(handleMargin, viewportSize / 2f)
        var lower = visible - imageSize
        var upper = viewportSize - visible
        if (nearAnchor < 0f) upper = maxOf(upper, viewportSize - margin - nearAnchor * imageSize)
        if (farAnchor > 1f) lower = minOf(lower, margin - farAnchor * imageSize)

        // During a held dimension, allow extra workspace around the photo and already
        // committed dimension anchors. This is a content-based limit, not a per-gesture
        // distance limit: releasing and touching again cannot reset the boundary.
        if (extendingForDrag) {
            val reserve = viewportSize.toDouble() * WORKSPACE_RESERVE_SCREENS
            val minOffset = (lower.toDouble() - reserve).coerceAtLeast(-Float.MAX_VALUE.toDouble())
            val maxOffset = (upper.toDouble() + reserve).coerceAtMost(Float.MAX_VALUE.toDouble())
            return offset.toDouble().coerceIn(minOffset, maxOffset).toFloat()
        }
        return offset.coerceIn(lower, upper)
    }

    private companion object {
        const val MAX_ZOOM = 64f
        const val WORKSPACE_RESERVE_SCREENS = 3.0
    }
}
