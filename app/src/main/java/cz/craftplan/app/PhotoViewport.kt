package cz.craftplan.app

import kotlin.math.hypot

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
    private var dimension: PhotoDimension? = null
    private var handleMargin = 0f

    fun updateContentBounds(dimension: PhotoDimension?, handleMargin: Float) {
        this.dimension = dimension
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
        val start = dimension?.start
        val end = dimension?.end
        val minU = minOf(0f, start?.u ?: 0f, end?.u ?: 0f)
        val maxU = maxOf(1f, start?.u ?: 1f, end?.u ?: 1f)
        val minV = minOf(0f, start?.v ?: 0f, end?.v ?: 0f)
        val maxV = maxOf(1f, start?.v ?: 1f, end?.v ?: 1f)
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
        val active = dimension
        val requestedLeft = (toX - (fromX - left) * ratio).toFloat()
        val requestedTop = (toY - (fromY - top) * ratio).toFloat()
        if (!requestedLeft.isFinite() || !requestedTop.isFinite()) return
        left = boundedOffset(
            requestedLeft, width, imageWidth * nextScale,
            active?.start?.u, active?.end?.u, extendingForDrag,
        )
        top = boundedOffset(
            requestedTop, height, imageHeight * nextScale,
            active?.start?.v, active?.end?.v, extendingForDrag,
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
        start: Float?, end: Float?, extendingForDrag: Boolean,
    ): Float {
        val visible = minOf(viewportSize.toFloat(), imageSize) / 2f
        val margin = minOf(handleMargin, viewportSize / 2f)
        val nearAnchor = minOf(start ?: 0f, end ?: 0f)
        val farAnchor = maxOf(start ?: 1f, end ?: 1f)
        var lower = visible - imageSize
        var upper = viewportSize - visible
        if (nearAnchor < 0f) upper = maxOf(upper, viewportSize - margin - nearAnchor * imageSize)
        if (farAnchor > 1f) lower = minOf(lower, margin - farAnchor * imageSize)

        // While a dimension is held, the user controls how far to extend the canvas.
        // Do not impose a per-gesture screen-width stop. Normal panning stays bounded.
        if (extendingForDrag) return offset
        return offset.coerceIn(lower, upper)
    }

    private companion object {
        const val MAX_ZOOM = 8f
    }
}
