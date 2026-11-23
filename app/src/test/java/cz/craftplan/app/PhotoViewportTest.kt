package cz.craftplan.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoViewportTest {
    @Test
    fun fitCentersTheImageInUnusedSpace() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)

        assertEquals(0.5f, viewport.scale, 0.001f)
        assertEquals(0f, viewport.left, 0.001f)
        assertEquals(100f, viewport.top, 0.001f)
    }

    @Test
    fun pinchKeepsTheSameImagePointUnderTheMovingFocus() {
        val viewport = PhotoViewport(800, 800)
        viewport.fit(400, 400)
        viewport.transform(fromX = 160f, fromY = 180f, toX = 180f, toY = 215f, factor = 2f)

        assertEquals(1f, viewport.scale, 0.001f)
        assertEquals(320f, (180f - viewport.left) / viewport.scale, 0.001f)
        assertEquals(360f, (215f - viewport.top) / viewport.scale, 0.001f)
    }

    @Test
    fun zoomedPanRetainsHalfTheViewportAndZoomOutRespectsTheNewBounds() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)
        viewport.transform(300f, 300f, 300f, 300f, factor = 2f)
        viewport.transform(300f, 300f, -1000f, 1400f)

        assertEquals(-900f, viewport.left, 0.001f)
        assertEquals(300f, viewport.top, 0.001f)

        viewport.transform(300f, 300f, 300f, 300f, factor = 0.1f)

        assertEquals(0.5f, viewport.scale, 0.001f)
        assertEquals(-300f, viewport.left, 0.001f)
        assertEquals(300f, viewport.top, 0.001f)
    }

    @Test
    fun panAtFitLeavesHalfOfEachPhotoAxisAvailableToGrab() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)
        viewport.transform(0f, 0f, 900f, -900f)

        assertEquals(300f, viewport.left, 0.001f)
        assertEquals(-200f, viewport.top, 0.001f)
        viewport.transform(0f, 0f, -900f, 900f)
        assertEquals(-300f, viewport.left, 0.001f)
        assertEquals(400f, viewport.top, 0.001f)
    }

    @Test
    fun malformedTransformDoesNotChangeTheViewport() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)
        viewport.transform(0f, 0f, Float.NaN, 120f)
        viewport.transform(0f, 0f, 120f, 120f, factor = Float.POSITIVE_INFINITY)

        assertEquals(0f, viewport.left, 0.001f)
        assertEquals(100f, viewport.top, 0.001f)
        assertEquals(0.5f, viewport.scale, 0.001f)
    }

    @Test
    fun zoomIsBoundedAndFitUsesNewViewDimensions() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)
        viewport.transform(300f, 300f, 300f, 300f, factor = 100f)
        assertEquals(4f, viewport.scale, 0.001f)

        viewport.fit(1200, 600)
        assertEquals(0.75f, viewport.scale, 0.001f)
        assertEquals(150f, viewport.left, 0.001f)
        assertEquals(0f, viewport.top, 0.001f)
    }
}
