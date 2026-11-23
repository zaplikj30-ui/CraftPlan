package cz.craftplan.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoDimensionTest {
    @Test
    fun completingDragUsesTheReleasePositionAndKeepsDimensionUnmeasured() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)
        val photoId = PhotoId.SYNTHETIC_WORKBENCH
        val ready = DimensionPlacement.Ready(photoId)
        val start = requireNotNull(viewport.toPhotoAnchor(150f, 300f))
        val end = viewport.dragEnd(start, 450f, 350f, touchSlop = 8)
        val dimension = requireNotNull(ready.finishDrag(start, end)).dimension

        assertEquals(photoId, dimension.photoId)
        assertEquals(PhotoAnchor(0.25f, 0.5f), dimension.start)
        assertEquals(PhotoAnchor(0.75f, 0.625f), dimension.end)
        assertEquals(MeasurementState.UNMEASURED, dimension.measurementState)
    }

    @Test
    fun tapsAndShortDragsDoNotCompleteADimension() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)
        val ready = DimensionPlacement.Ready(PhotoId.SYNTHETIC_WORKBENCH)
        val start = requireNotNull(viewport.toPhotoAnchor(150f, 300f))

        assertNull(ready.finishDrag(start, start))
        assertNull(ready.finishDrag(start, viewport.dragEnd(start, 150f, 300f, touchSlop = 8)))
        assertNull(ready.finishDrag(start, viewport.dragEnd(start, 154f, 304f, touchSlop = 8)))
        assertEquals(PhotoAnchor(0.27f, 0.5f), viewport.dragEnd(start, 162f, 300f, touchSlop = 8))
    }

    @Test
    fun releaseCanFinishInTheWorkspaceButNotOutsideTheEditor() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)
        val ready = DimensionPlacement.Ready(PhotoId.SYNTHETIC_WORKBENCH)
        val start = requireNotNull(viewport.toPhotoAnchor(150f, 300f))
        val dimension = requireNotNull(ready.finishDrag(start, viewport.dragEnd(start, 150f, 50f, touchSlop = 8))).dimension
        assertEquals(PhotoAnchor(0.25f, -0.125f), dimension.end)

        viewport.transform(300f, 300f, 300f, 300f, factor = 2f)
        assertNull(ready.finishDrag(start, viewport.dragEnd(start, 610f, 300f, touchSlop = 8)))
    }

    @Test
    fun onlyLaidOutWorkspaceAcceptsFiniteTouchCoordinates() {
        val viewport = PhotoViewport(1200, 800)
        assertNull(viewport.toPhotoAnchor(0f, 0f))

        viewport.fit(600, 600)
        assertEquals(PhotoAnchor(0.5f, -0.25f), viewport.toPhotoAnchor(300f, 0f))
        assertEquals(PhotoAnchor(0.5f, 1.25f), viewport.toPhotoAnchor(300f, 600f))
        assertNull(viewport.toPhotoAnchor(Float.NaN, 300f))
        assertNull(viewport.toPhotoAnchor(300f, Float.NEGATIVE_INFINITY))
        assertThrows(IllegalArgumentException::class.java) { PhotoAnchor(Float.POSITIVE_INFINITY, 0f) }
        assertNull(viewport.toPhotoAnchor(601f, 300f))
        assertEquals(PhotoAnchor(0f, 0f), viewport.toPhotoAnchor(0f, 100f))
        assertEquals(PhotoAnchor(1f, 1f), viewport.toPhotoAnchor(600f, 500f))
    }

    @Test
    fun anchorsProjectWithThePhotoAfterZoomAndPan() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)
        val start = requireNotNull(viewport.toPhotoAnchor(150f, 300f))
        val end = requireNotNull(viewport.toPhotoAnchor(375f, 350f))

        viewport.transform(300f, 300f, 300f, 300f, factor = 2f)
        viewport.transform(300f, 300f, 350f, 275f)

        assertEquals(50f, viewport.screenX(start), 0.001f)
        assertEquals(275f, viewport.screenY(start), 0.001f)
        assertEquals(500f, viewport.screenX(end), 0.001f)
        assertEquals(375f, viewport.screenY(end), 0.001f)
        assertEquals(start, viewport.toPhotoAnchor(viewport.screenX(start), viewport.screenY(start)))
        assertEquals(end, viewport.toPhotoAnchor(viewport.screenX(end), viewport.screenY(end)))
    }

    @Test
    fun anchorOutsideThePhotoRoundTripsAfterPanZoomAndRefit() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)
        val outside = requireNotNull(viewport.toPhotoAnchor(150f, 50f))
        assertEquals(PhotoAnchor(0.25f, -0.125f), outside)

        viewport.transform(300f, 300f, 300f, 470f, factor = 1.5f)
        val projected = viewport.screenPoint(outside)
        assertEquals(75f, projected.x, 0.001f)
        assertEquals(95f, projected.y, 0.001f)
        assertEquals(outside, viewport.toPhotoAnchor(projected.x, projected.y))

        viewport.fit(600, 900)
        val refitted = viewport.screenPoint(outside)
        assertEquals(outside, viewport.toPhotoAnchor(refitted.x, refitted.y))
    }
}
