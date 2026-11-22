package cz.craftplan.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PhotoDimensionTest {
    @Test
    fun placementCreatesOneUnmeasuredDimensionForThePhoto() {
        val photoId = PhotoId.SYNTHETIC_WORKBENCH
        val start = PhotoAnchor(0.25f, 0.5f)
        val end = PhotoAnchor(0.75f, 0.5f)
        val first = DimensionPlacement.AwaitingFirst(photoId)
        val second = first.place(start) as DimensionPlacement.AwaitingSecond

        assertSame(second, second.place(start))
        val complete = second.place(end) as DimensionPlacement.Complete
        assertEquals(photoId, complete.dimension.photoId)
        assertEquals(start, complete.dimension.start)
        assertEquals(end, complete.dimension.end)
        assertEquals(MeasurementState.UNMEASURED, complete.dimension.measurementState)
        val later = complete.place(PhotoAnchor(0.5f, 0.5f)) as DimensionPlacement.Complete
        assertSame(complete, later)
        assertEquals(complete.dimension.id, later.dimension.id)
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
