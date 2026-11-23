package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensionGeometryTest {
    private val dimension = PhotoDimension(
        UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH,
        PhotoAnchor(0.25f, 0.25f), PhotoAnchor(0.75f, 0.5f),
    )

    @Test
    fun shortDimensionReservesTheMiddleForMovingBothEndpoints() {
        val start = EditorPoint(0f, 0f)
        val end = EditorPoint(40f, 0f)
        assertEquals(DimensionHit.START, hitDimension(EditorPoint(10f, 12f), start, end, 1f))
        assertEquals(DimensionHit.END, hitDimension(EditorPoint(30f, 12f), start, end, 1f))
        assertEquals(DimensionHit.BODY, hitDimension(EditorPoint(20f, 12f), start, end, 1f))
        assertNull(hitDimension(EditorPoint(-25f, 25f), start, end, 1f))
    }

    @Test
    fun tappingAndSmallMotionStayBelowDragThreshold() {
        val down = EditorPoint(10f, 20f)
        assertFalse(passesDragThreshold(down, EditorPoint(17f, 20f), 7f))
        assertTrue(passesDragThreshold(down, EditorPoint(16f, 25f), 7f))
    }

    @Test
    fun endDragAtZoomPreservesGrabOffsetAndIdentity() {
        val viewport = PhotoViewport(800, 800)
        viewport.fit(400, 400)
        viewport.transform(200f, 200f, 200f, 200f, factor = 2f)
        val moved = viewport.moveDimension(dimension, DimensionHit.START, EditorPoint(100f, 40f) - EditorPoint(20f, 0f))
        assertEquals(0.35f, moved.start.u, 0.00001f)
        assertEquals(0.30f, moved.start.v, 0.00001f)
        assertEquals(dimension.end, moved.end)
        assertEquals(dimension.id, moved.id)
        assertEquals(dimension.photoId, moved.photoId)
        assertEquals(MeasurementState.UNMEASURED, moved.measurementState)
        assertEquals(dimension, dimension.move(DimensionHit.START, 0.5f, 0.25f))
    }

    @Test
    fun wholeDimensionMovesTogetherAcrossThePhotoEdge() {
        val moved = dimension.move(DimensionHit.BODY, 0.5f, -0.5f)
        assertEquals(PhotoAnchor(0.75f, -0.25f), moved.start)
        assertEquals(PhotoAnchor(1.25f, 0f), moved.end)
        assertEquals(dimension.id, moved.id)
    }

    @Test
    fun endpointCanPassThePhotoEdgeButStopsAtTheWorkspaceEdge() {
        val viewport = PhotoViewport(800, 400)
        viewport.fit(400, 400)
        val outside = viewport.moveDimension(dimension, DimensionHit.START, EditorPoint(0f, -65f))
        assertEquals(0.25f, outside.start.u, 0.00001f)
        assertEquals(-0.075f, outside.start.v, 0.00001f)
        assertEquals(dimension.end, outside.end)

        val edge = viewport.moveDimension(dimension, DimensionHit.START, EditorPoint(0f, -200f))
        assertEquals(PhotoAnchor(0.25f, -0.5f), edge.start)
        assertEquals(dimension.end, edge.end)
        assertEquals(dimension, viewport.moveDimension(dimension, DimensionHit.START, EditorPoint(Float.NaN, 0f)))
    }

    @Test
    fun movingTheBodyThroughWorkspaceKeepsBothEndpointsAtTheSameOffset() {
        val viewport = PhotoViewport(800, 400)
        viewport.fit(400, 400)
        val move = EditorPoint(0f, -100f)
        val moved = viewport.moveDimension(dimension, DimensionHit.BODY, move)

        assertEquals(PhotoAnchor(0.25f, -0.25f), moved.start)
        assertEquals(PhotoAnchor(0.75f, 0f), moved.end)
        assertEquals(move, viewport.screenPoint(moved.start) - viewport.screenPoint(dimension.start))
        assertEquals(move, viewport.screenPoint(moved.end) - viewport.screenPoint(dimension.end))
    }

    @Test
    fun cancelDiscardsDraftAndOnlyExistingDimensionEditsHandOffToPinch() {
        assertEquals(DimensionInterruption.CANCEL, dimensionInterruption(true, false, false))
        assertEquals(DimensionInterruption.CANCEL, dimensionInterruption(true, true, false))
        assertEquals(DimensionInterruption.CANCEL, dimensionInterruption(false, true, true))
        assertEquals(DimensionInterruption.HAND_OFF_TO_PINCH, dimensionInterruption(false, true, false))
        assertEquals(DimensionInterruption.CONTINUE, dimensionInterruption(false, false, false))
    }

    @Test
    fun reversingTheLineKeepsTheRulerReadable() {
        assertEquals(45f, readableAngle(EditorPoint(10f, 10f)), 0.001f)
        assertEquals(45f, readableAngle(EditorPoint(-10f, -10f)), 0.001f)
        assertEquals(90f, readableAngle(EditorPoint(0f, -10f)), 0.001f)
    }

    @Test
    fun magnifierKeepsItsCornerBetweenSwitchThresholds() {
        assertTrue(magnifierOnRight(44f, 100, null))
        assertTrue(magnifierOnRight(55f, 100, true))
        assertFalse(magnifierOnRight(56f, 100, true))
        assertFalse(magnifierOnRight(46f, 100, false))
        assertTrue(magnifierOnRight(44f, 100, false))
    }

    @Test
    fun autoPanSpeedIsSymmetricAndActiveOnlyNearAnEdge() {
        assertEquals(0f, edgePanSpeed(200f, 400, 48f, 440f), 0f)
        assertEquals(220f, edgePanSpeed(24f, 400, 48f, 440f), 0f)
        assertEquals(-220f, edgePanSpeed(376f, 400, 48f, 440f), 0f)
        assertEquals(440f, edgePanSpeed(0f, 400, 48f, 440f), 0f)
        assertEquals(-440f, edgePanSpeed(400f, 400, 48f, 440f), 0f)
        assertEquals(0f, edgePanSpeed(15f, 30, 48f, 440f), 0f)
    }
}
