package cz.craftplan.app

import kotlin.math.hypot

/**
 * Maps image coordinates to the viewport for display. Lengths and document coordinates
 * are independent of this transform.
 */
internal class PhotoViewport(private val imageWidth: Int, private val imageHeight: Int) {
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
        left = boundedOffset(0f, width, imageWidth * scale)
        top = boundedOffset(0f, height, imageHeight * scale)
    }

    fun transform(fromX: Float, fromY: Float, toX: Float, toY: Float, factor: Float = 1f) {
        if (width <= 0 || height <= 0 || !factor.isFinite() || factor <= 0f) return

        val nextScale = (scale * factor).coerceIn(fitScale, fitScale * MAX_ZOOM)
        val ratio = nextScale / scale
        left = boundedOffset(toX - (fromX - left) * ratio, width, imageWidth * nextScale)
        top = boundedOffset(toY - (fromY - top) * ratio, height, imageHeight * nextScale)
        scale = nextScale
    }

    fun toPhotoAnchor(x: Float, y: Float): PhotoAnchor? {
        if (width <= 0 || height <= 0 || x !in 0f..width.toFloat() || y !in 0f..height.toFloat()) {
            return null
        }

        val u = (x - left) / (imageWidth * scale)
        val v = (y - top) / (imageHeight * scale)
        return if (u in 0f..1f && v in 0f..1f) PhotoAnchor(u, v) else null
    }

    fun dragEnd(start: PhotoAnchor, x: Float, y: Float, touchSlop: Int): PhotoAnchor? {
        val end = toPhotoAnchor(x, y) ?: return null
        val distance = hypot(x - screenX(start), y - screenY(start))
        return end.takeIf { distance >= touchSlop }
    }

    fun screenX(anchor: PhotoAnchor): Float = left + anchor.u * (imageWidth * scale)

    fun screenY(anchor: PhotoAnchor): Float = top + anchor.v * (imageHeight * scale)

    fun screenPoint(anchor: PhotoAnchor) = EditorPoint(screenX(anchor), screenY(anchor))

    fun moveDimension(dimension: PhotoDimension, hit: DimensionHit, delta: EditorPoint): PhotoDimension =
        dimension.move(hit, delta.x / (imageWidth * scale), delta.y / (imageHeight * scale))

    private fun boundedOffset(offset: Float, viewportSize: Int, imageSize: Float): Float =
        if (imageSize <= viewportSize) {
            (viewportSize - imageSize) / 2f
        } else {
            offset.coerceIn(viewportSize - imageSize, 0f)
        }

    private companion object {
        const val MAX_ZOOM = 8f
    }
}
