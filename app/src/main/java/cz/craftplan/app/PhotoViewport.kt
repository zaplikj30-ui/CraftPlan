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
    private var fitScale = 1f

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

        fitScale = minOf(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        scale = fitScale
        left = (width - imageWidth * scale) / 2f
        top = (height - imageHeight * scale) / 2f
    }

    fun transform(fromX: Float, fromY: Float, toX: Float, toY: Float, factor: Float = 1f) {
        if (width <= 0 || height <= 0 || !factor.isFinite() || factor <= 0f ||
            !fromX.isFinite() || !fromY.isFinite() || !toX.isFinite() || !toY.isFinite()
        ) return

        val nextScale = (scale * factor).coerceIn(fitScale, fitScale * MAX_ZOOM)
        val ratio = nextScale / scale
        left = boundedOffset(toX - (fromX - left) * ratio, width, imageWidth * nextScale)
        top = boundedOffset(toY - (fromY - top) * ratio, height, imageHeight * nextScale)
        scale = nextScale
    }

    fun contains(x: Float, y: Float): Boolean =
        width > 0 && height > 0 && x in 0f..width.toFloat() && y in 0f..height.toFloat()

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

    private fun boundedOffset(offset: Float, viewportSize: Int, imageSize: Float): Float {
        val visible = minOf(viewportSize.toFloat(), imageSize) / 2f
        return offset.coerceIn(visible - imageSize, viewportSize - visible)
    }

    private companion object {
        const val MAX_ZOOM = 8f
    }
}
