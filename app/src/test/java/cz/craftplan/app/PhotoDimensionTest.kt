package cz.craftplan.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun releasingOutsideTheVisiblePhotoDoesNotCompleteADimension() {
        val viewport = PhotoViewport(1200, 800)
        viewport.fit(600, 600)
        val ready = DimensionPlacement.Ready(PhotoId.SYNTHETIC_WORKBENCH)
        val start = requireNotNull(viewport.toPhotoAnchor(150f, 300f))
        assertNull(ready.finishDrag(start, viewport.dragEnd(start, 150f, 90f, touchSlop = 8)))

        viewport.transform(300f, 300f, 300f, 300f, factor = 2f)
        assertNull(ready.finishDrag(start, viewport.dragEnd(start, 610f, 300f, touchSlop = 8)))
    }

    @Test
    fun emptyMarginsAndUnlaidOutViewportCannotCreateAnchors() {
        val viewport = PhotoViewport(1200, 800)
        assertNull(viewport.toPhotoAnchor(0f, 0f))

        viewport.fit(600, 600)
        assertNull(viewport.toPhotoAnchor(300f, 99f))
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
}
