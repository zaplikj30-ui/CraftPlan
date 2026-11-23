package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun outsideAnchorsExtendOnlyTheRequiredDirectionsAtMaximumZoom() {
        val viewport = PhotoViewport(800, 800)
        viewport.fit(400, 400)
        val dimension = PhotoDimension(
            UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
            PhotoAnchor(-0.75f, 0.25f), PhotoAnchor(0.75f, 1.75f),
        )
        viewport.updateContentBounds(dimension, 24f)
        viewport.transform(200f, 200f, 200f, 200f, factor = 8f)
        viewport.transform(0f, 0f, 9000f, -9000f)

        assertEquals(376f, viewport.screenX(dimension.start), 0.001f)
        assertEquals(24f, viewport.screenY(dimension.end), 0.001f)

        viewport.transform(0f, 0f, -9000f, 9000f)
        assertEquals(-3000f, viewport.left, 0.001f)
        assertEquals(200f, viewport.top, 0.001f)

        viewport.transform(0f, 0f, 9000f, -9000f)
        viewport.updateContentBounds(dimension.copy(start = PhotoAnchor(0.25f, 0.25f)), 24f)
        assertEquals(200f, viewport.left, 0.001f)
        assertEquals(24f, viewport.screenY(dimension.end), 0.001f)
        viewport.updateContentBounds(null, 24f)
        assertEquals(-3000f, viewport.top, 0.001f)
    }

    @Test
    fun heldContactTracksAutopanAndTheTemporaryRangeIsFinite() {
        val viewport = PhotoViewport(400, 400)
        viewport.fit(400, 400)
        val contact = requireNotNull(viewport.clampContact(403f, 200f))
        val initial = requireNotNull(viewport.toPhotoAnchor(contact.x, contact.y))

        repeat(10) { viewport.transform(0f, 0f, -100f, 0f, extendingForDrag = true) }
        val current = requireNotNull(viewport.toPhotoAnchor(contact.x, contact.y))
        assertEquals(2.5f, current.u, 0.001f)
        assertEquals(contact, viewport.screenPoint(current))
        assertEquals(1f, initial.u, 0.001f)
        assertNull(viewport.clampContact(Float.NaN, 200f))

        viewport.updateContentBounds(null, 24f)
        assertEquals(-200f, viewport.left, 0.001f)
    }

    @Test
    fun endpointEditUsesTheMovedViewportWithoutMovingTheFixedAnchor() {
        val viewport = PhotoViewport(400, 400)
        viewport.fit(400, 400)
        val dimension = PhotoDimension(
            UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
            PhotoAnchor(0.25f, 0.5f), PhotoAnchor(0.75f, 0.5f),
        )
        val downAnchor = requireNotNull(viewport.toPhotoAnchor(295f, 200f))
        viewport.transform(0f, 0f, -300f, 0f, extendingForDrag = true)

        val contact = EditorPoint(380f, 200f)
        val edited = viewport.moveDimension(dimension, DimensionHit.END, contact - viewport.screenPoint(downAnchor))
        assertEquals(385f, viewport.screenX(edited.end), 0.001f)
        assertEquals(dimension.start, edited.start)
        assertEquals(dimension.id, edited.id)
    }

    @Test
    fun eachFinishedEdgeDragAllowsAnotherWithoutDriftingAfterRelease() {
        val viewport = PhotoViewport(400, 400)
        viewport.fit(400, 400)
        var dimension = PhotoDimension(
            UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
            PhotoAnchor(0.25f, 0.5f), PhotoAnchor(0.75f, 0.5f),
        )
        viewport.updateContentBounds(dimension, 24f)
        repeat(3) {
            val startOffset = viewport.left
            viewport.transform(0f, 0f, -9000f, 0f, extendingForDrag = true)
            val limit = viewport.left
            viewport.transform(0f, 0f, -9000f, 0f, extendingForDrag = true)
            assertEquals(limit, viewport.left, 0f)
            assertTrue(limit < startOffset)

            val edge = requireNotNull(viewport.toPhotoAnchor(380f, 200f))
            assertTrue(edge.u > dimension.end.u)
            dimension = dimension.copy(end = edge)
            viewport.updateContentBounds(dimension, 24f)
            assertEquals(limit, viewport.left, 0f)
            assertEquals(380f, viewport.screenX(dimension.end), 0.001f)
        }
        assertTrue(viewport.left + 400f < 0f)
        viewport.transform(0f, 0f, 9000f, 0f)
        assertEquals(200f, viewport.left, 0.001f)
        assertEquals(PhotoAnchor(0.25f, 0.5f), dimension.start)
    }
}
