package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoViewportTest {
    @Test
    fun photoDestinationMatchesCanonicalAnchorsAtFitZoomAndReopen() {
        for (orientation in 1..8) {
            val (width, height) = photoDimensions(4001, 3001, orientation)
            val viewport = PhotoViewport(width, height)
            val reopened = PhotoViewport(width, height)
            viewport.resize(800, 720)
            reopened.resize(800, 720)
            assertEquals(viewport.photoRight, reopened.photoRight, 0f)
            assertEquals(viewport.photoBottom, reopened.photoBottom, 0f)
            assertCanonicalDestination(viewport, width, height)
            viewport.transform(400f, 360f, 425f, 345f, factor = 2f)
            assertCanonicalDestination(viewport, width, height)
            viewport.resize(720, 800)
            assertCanonicalDestination(viewport, width, height)
        }
    }

    private fun assertCanonicalDestination(viewport: PhotoViewport, width: Int, height: Int) {
        val corner = viewport.screenPoint(PhotoAnchor(1f, 1f))
        assertEquals(corner.x, viewport.photoRight, 0.001f)
        assertEquals(corner.y, viewport.photoBottom, 0.001f)
        val destinationWidth = viewport.photoRight - viewport.left
        val destinationHeight = viewport.photoBottom - viewport.top
        assertEquals(width * viewport.scale, destinationWidth, 0.001f)
        assertEquals(height * viewport.scale, destinationHeight, 0.001f)
        // The same fractional point of a reduced texture maps to the anchor,
        // even when sampling rounds the two bitmap axes differently.
        val bitmapWidth = (width + 3) / 4
        val bitmapHeight = (height + 3) / 4
        val texelX = bitmapWidth / 3
        val texelY = bitmapHeight / 2
        val anchor = PhotoAnchor(texelX.toFloat() / bitmapWidth, texelY.toFloat() / bitmapHeight)
        val point = viewport.screenPoint(anchor)
        assertEquals(viewport.left + anchor.u * destinationWidth, point.x, 0.001f)
        assertEquals(viewport.top + anchor.v * destinationHeight, point.y, 0.001f)
        val hit = requireNotNull(viewport.toPhotoAnchor(point.x, point.y))
        assertEquals(anchor.u, hit.u, 0.00001f)
        assertEquals(anchor.v, hit.v, 0.00001f)
    }

    @Test
    fun strokeScaleIsBoundedAndContinuousAcrossZoomAndFit() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)
        for (step in 0..6) {
            if (step > 0) viewport.transform(300f, 300f, 300f, 300f, factor = 2f)
            assertEquals(0.8f + 0.2f * step, viewport.dimensionStrokeScale, 0.00001f)
            assertEquals(0.8f + 0.2f * step / 6f, viewport.dimensionTickScale, 0.00001f)
            val progress = step / 6f
            assertEquals(1.7f + 0.3f * progress, viewport.dimensionFillWidth, 0.00001f)
            assertEquals(2.95f + 1.05f * progress, viewport.dimensionOutlineWidth, 0.00001f)
            assertEquals(0.625f + 0.375f * progress,
                (viewport.dimensionOutlineWidth - viewport.dimensionFillWidth) / 2f, 0.00001f)
        }
        viewport.transform(300f, 300f, 300f, 300f, factor = 100f)
        assertEquals(32f, viewport.scale, 0.001f)
        assertEquals(2f, viewport.dimensionStrokeScale, 0f)
        assertEquals(1f, viewport.dimensionTickScale, 0f)
        assertEquals(4f, viewport.dimensionOutlineWidth, 0f)
        assertEquals(2f, viewport.dimensionFillWidth, 0f)
        viewport.fit(600, 600)
        viewport.transform(300f, 300f, 300f, 300f, factor = 3f)
        assertTrue(viewport.dimensionStrokeScale in 1f..1.2f)
        viewport.fit(600, 600)
        assertEquals(0.8f, viewport.dimensionStrokeScale, 0f)
        assertEquals(0.8f, viewport.dimensionTickScale, 0f)
        assertEquals(2.95f, viewport.dimensionOutlineWidth, 0f)
        assertEquals(1.7f, viewport.dimensionFillWidth, 0f)
    }

    @Test
    fun labelTextCounterScalesFromEighteenToTwelveSp() {
        val viewport = PhotoViewport(1000, 1000)
        viewport.fit(500, 500)
        assertEquals(18f, viewport.dimensionLabelSp, 0f)
        for (step in 1..6) {
            viewport.transform(250f, 250f, 250f, 250f, 2f)
            assertEquals(18f - step, viewport.dimensionLabelSp, 0.00001f)
        }
        viewport.transform(250f, 250f, 250f, 250f, 2f)
        assertEquals(12f, viewport.dimensionLabelSp, 0f)
        for (step in 5 downTo 0) {
            viewport.transform(250f, 250f, 250f, 250f, 0.5f)
            assertEquals(18f - step, viewport.dimensionLabelSp, 0.00001f)
        }
        viewport.transform(250f, 250f, 250f, 250f, 0.1f)
        assertEquals(18f, viewport.dimensionLabelSp, 0f)
        viewport.fit(500, 500)
        assertEquals(18f, viewport.dimensionLabelSp, 0f)
    }

    @Test
    fun maximumZoomKeepsAnchorMappingAcrossPanRotationAndFit() {
        val viewport = PhotoViewport(4001, 3001)
        val anchor = PhotoAnchor(0.5f, 0.5f)
        viewport.fit(1080, 1920)
        viewport.transform(540f, 960f, 540f, 960f, factor = 100f)
        assertEquals(2f, viewport.dimensionStrokeScale, 0.00001f)
        fun check(x: Float, y: Float) {
            val point = viewport.screenPoint(anchor)
            assertEquals(x, point.x, 0.05f)
            assertEquals(y, point.y, 0.05f)
            val recovered = requireNotNull(viewport.toPhotoAnchor(x, y))
            assertEquals(anchor.u, recovered.u, 0.000001f)
            assertEquals(anchor.v, recovered.v, 0.000001f)
        }
        check(540f, 960f)
        viewport.transform(540f, 960f, 600f, 900f)
        check(600f, 900f)
        viewport.transform(600f, 900f, 540f, 960f)
        viewport.resize(1920, 1080)
        check(960f, 540f)
        viewport.fit(1920, 1080)
        check(960f, 540f)
    }

    @Test
    fun fitIncludesAnchorsFromDifferentDimensionsAfterAResize() {
        val viewport = PhotoViewport(400, 400)
        val left = PhotoDimension(
            UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
            PhotoAnchor(-1f, 0.25f), PhotoAnchor(0.25f, 0.25f),
        )
        val right = left.copy(id = UUID.randomUUID(), start = PhotoAnchor(0.75f, 0.75f), end = PhotoAnchor(2f, 0.75f))
        viewport.updateContentBounds(listOf(left, right), 24f)
        viewport.fit(400, 400)
        assertEquals(24f, viewport.screenX(left.start), 0.001f)
        assertEquals(376f, viewport.screenX(right.end), 0.001f)
        viewport.resize(700, 320)
        assertTrue(viewport.contains(viewport.screenX(left.start), viewport.screenY(left.start)))
        assertTrue(viewport.contains(viewport.screenX(right.end), viewport.screenY(right.end)))
    }

    @Test
    fun openingFitsThePhotoAndBothAnchorsInEveryDirection() {
        val cases = listOf(
            PhotoAnchor(-1f, 0.5f) to PhotoAnchor(2f, 0.5f),
            PhotoAnchor(0.5f, -1f) to PhotoAnchor(0.5f, 2f),
            PhotoAnchor(-1f, 2f) to PhotoAnchor(2f, -1f),
            PhotoAnchor(0.25f, 0.75f) to PhotoAnchor(8f, -6f),
        )
        for ((start, end) in cases) {
            val viewport = PhotoViewport(1200, 800)
            val dimension = PhotoDimension(UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH, start, end)
            viewport.updateContentBounds(dimension, 24f)
            viewport.resize(600, 480)

            val points = listOf(PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f), start, end)
                .map(viewport::screenPoint)
            assertTrue(points.all { it.x in 23.999f..576.001f && it.y in 23.999f..456.001f })
            assertEquals(300f, (points.minOf { it.x } + points.maxOf { it.x }) / 2f, 0.001f)
            assertEquals(240f, (points.minOf { it.y } + points.maxOf { it.y }) / 2f, 0.001f)

            val initialScale = viewport.scale
            viewport.transform(300f, 240f, 300f, 240f, factor = 0.1f)
            assertEquals(initialScale, viewport.scale, 0f)
        }
    }

    @Test
    fun openingWithAnInsideDimensionUsesTheOriginalPhotoFit() {
        val viewport = PhotoViewport(1200, 800)
        viewport.updateContentBounds(
            PhotoDimension(
                UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
                PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f),
            ),
            24f,
        )
        viewport.resize(600, 600)

        assertEquals(0.5f, viewport.scale, 0f)
        assertEquals(0f, viewport.left, 0f)
        assertEquals(100f, viewport.top, 0f)
    }

    @Test
    fun aLongDimensionFitsButDoesNotLowerTheOriginalMaximumZoom() {
        val viewport = PhotoViewport(1200, 800)
        val far = PhotoAnchor(1_000_000f, 0.5f)
        viewport.updateContentBounds(
            PhotoDimension(UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH, PhotoAnchor(0f, 0f), far),
            24f,
        )
        viewport.resize(600, 480)

        assertEquals(24f, viewport.left, 0.001f)
        assertEquals(576f, viewport.screenX(far), 0.001f)
        viewport.transform(300f, 240f, 300f, 240f, factor = 100_000_000f)
        assertEquals(32f, viewport.scale, 0.001f)
    }

    @Test
    fun editingContentAndResizingPreserveAHandSetView() {
        val viewport = PhotoViewport(800, 800)
        val original = PhotoDimension(
            UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
            PhotoAnchor(-1f, 0.25f), PhotoAnchor(2f, 0.5f),
        )
        viewport.updateContentBounds(original, 24f)
        viewport.resize(400, 400)
        viewport.transform(200f, 200f, 225f, 205f, factor = 1.2f)
        val scale = viewport.scale
        val left = viewport.left
        val top = viewport.top
        viewport.updateContentBounds(original.copy(end = PhotoAnchor(1f, 0.5f)), 24f)

        assertEquals(scale, viewport.scale, 0f)
        assertEquals(left, viewport.left, 0f)
        assertEquals(top, viewport.top, 0f)
        val focus = viewport.toPhotoAnchor(200f, 200f)
        viewport.resize(420, 440)
        val resizedFocus = requireNotNull(viewport.toPhotoAnchor(210f, 220f))
        assertEquals(requireNotNull(focus).u, resizedFocus.u, 0.0001f)
        assertEquals(focus.v, resizedFocus.v, 0.0001f)
    }

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
        assertEquals(32f, viewport.scale, 0.001f)

        viewport.fit(1200, 600)
        assertEquals(0.75f, viewport.scale, 0.001f)
        assertEquals(150f, viewport.left, 0.001f)
        assertEquals(0f, viewport.top, 0.001f)
    }

    @Test
    fun outsideAnchorsExtendOnlyTheRequiredDirectionsAtEightfoldZoom() {
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
    fun heldContactTracksAutopanBeyondThePreviousOneScreenLimit() {
        val viewport = PhotoViewport(400, 400)
        viewport.fit(400, 400)
        val contact = requireNotNull(viewport.clampContact(403f, 200f))
        val initial = requireNotNull(viewport.toPhotoAnchor(contact.x, contact.y))

        repeat(20) { viewport.transform(0f, 0f, -100f, 0f, extendingForDrag = true) }
        val current = requireNotNull(viewport.toPhotoAnchor(contact.x, contact.y))
        assertEquals(-1400f, viewport.left, 0.001f)
        assertEquals(4.5f, current.u, 0.001f)
        assertEquals(contact, viewport.screenPoint(current))
        assertEquals(1f, initial.u, 0.001f)
        assertNull(viewport.clampContact(Float.NaN, 200f))

        viewport.updateContentBounds(null, 24f)
        assertEquals(-200f, viewport.left, 0.001f)
    }

    @Test
    fun heldWorkspaceBoundaryIsStableAcrossTouchesAndBothAxes() {
        val viewport = PhotoViewport(400, 400)
        viewport.fit(400, 400)

        repeat(50) { viewport.transform(0f, 0f, -100f, -100f, extendingForDrag = true) }
        assertEquals(-1400f, viewport.left, 0.001f)
        assertEquals(-1400f, viewport.top, 0.001f)

        // A new gesture cannot enlarge the allowed workspace by resetting a counter.
        repeat(20) { viewport.transform(0f, 0f, -100f, -100f, extendingForDrag = true) }
        assertEquals(-1400f, viewport.left, 0.001f)
        assertEquals(-1400f, viewport.top, 0.001f)

        repeat(40) { viewport.transform(0f, 0f, 100f, 100f, extendingForDrag = true) }
        assertEquals(1400f, viewport.left, 0.001f)
        assertEquals(1400f, viewport.top, 0.001f)
        viewport.updateContentBounds(null, 24f)
        assertEquals(200f, viewport.left, 0.001f)
        assertEquals(200f, viewport.top, 0.001f)
    }

    @Test
    fun confirmedDimensionExtendsTheWorkspaceBoundaryWithoutChangingItsAnchors() {
        val viewport = PhotoViewport(400, 400)
        viewport.fit(400, 400)
        val dimension = PhotoDimension(
            UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
            PhotoAnchor(0.25f, 0.5f), PhotoAnchor(4f, 0.5f),
        )
        viewport.updateContentBounds(dimension, 24f)
        viewport.transform(0f, 0f, -10000f, 0f, extendingForDrag = true)
        assertEquals(-2776f, viewport.left, 0.001f)
        assertEquals(PhotoAnchor(4f, 0.5f), dimension.end)
        assertEquals(4f, (viewport.screenX(dimension.end) - viewport.left) / (400f * viewport.scale), 0.001f)
        viewport.transform(0f, 0f, 10000f, 0f, extendingForDrag = true)
        assertEquals(1400f, viewport.left, 0.001f)
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
            viewport.transform(0f, 0f, -500f, 0f, extendingForDrag = true)
            val movedOffset = viewport.left
            assertEquals(startOffset - 500f, movedOffset, 0f)

            val edge = requireNotNull(viewport.toPhotoAnchor(380f, 200f))
            assertTrue(edge.u > dimension.end.u)
            dimension = dimension.copy(end = edge)
            viewport.updateContentBounds(dimension, 24f)
            assertEquals(movedOffset, viewport.left, 0f)
            assertEquals(380f, viewport.screenX(dimension.end), 0.001f)
        }
        assertTrue(viewport.left + 400f < 0f)
        viewport.transform(0f, 0f, 9000f, 0f)
        assertEquals(200f, viewport.left, 0.001f)
        assertEquals(PhotoAnchor(0.25f, 0.5f), dimension.start)
    }
}
