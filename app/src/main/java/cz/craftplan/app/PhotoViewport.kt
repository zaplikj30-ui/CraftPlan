package cz.craftplan.app

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
