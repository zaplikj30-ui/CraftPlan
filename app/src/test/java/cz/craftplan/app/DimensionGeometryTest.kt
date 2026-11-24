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
    fun visibleStrokeAndExpandedEndpointAreSeparateCandidates() {
        val start = EditorPoint(0f, 0f)
        val end = EditorPoint(100f, 0f)
        val near = dimensionStrokeTargets(EditorPoint(16f, 0f), start, end, 1f, false)
        assertTrue(near.any { it.hit == DimensionHit.START && !it.direct })
        assertTrue(near.any { it.hit == DimensionHit.BODY && it.direct })
        val tick = dimensionStrokeTargets(EditorPoint(0f, 10f), start, end, 1f, false)
        assertTrue(tick.any { it.hit == DimensionHit.START && it.direct })
        val short = dimensionStrokeTargets(EditorPoint(20f, 0f), start, EditorPoint(40f, 0f), 1f, true)
        assertEquals(listOf(DimensionHit.BODY), short.map { it.hit })
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
    fun releasingThePanFingerDoesNotFinishOrCancelTheHeldDimension() {
        assertEquals(HeldPointerRelease.END_PAN, heldPointerRelease(7, 3, 3, canceled = false))
        assertEquals(HeldPointerRelease.END_PAN, heldPointerRelease(7, 3, 3, canceled = true))
        assertEquals(HeldPointerRelease.IGNORE, heldPointerRelease(7, 3, 4, canceled = false))
        assertEquals(HeldPointerRelease.FINISH, heldPointerRelease(7, 3, 7, canceled = false))
        assertEquals(HeldPointerRelease.CANCEL, heldPointerRelease(7, 3, 7, canceled = true))
    }

    @Test
    fun everyPointOnLongShortVerticalAndDiagonalDimensionsIsSelectable() {
        val start = EditorPoint(50f, 70f)
        for (end in listOf(EditorPoint(650f, 70f), EditorPoint(90f, 70f), EditorPoint(50f, 670f), EditorPoint(450f, 370f))) {
            val axis = end - start
            for (i in 0..200) {
                val t = i / 200f
                val point = EditorPoint(start.x + t * axis.x, start.y + t * axis.y)
                assertTrue("Missing hit at t=$t on $start to $end", hitDimension(point, start, end, 1f) != null)
            }
            assertEquals(DimensionHit.START, hitDimension(start, start, end, 1f))
            assertEquals(DimensionHit.END, hitDimension(end, start, end, 1f))
        }
        assertEquals(DimensionHit.BODY, hitDimension(EditorPoint(100f, 80f), start, EditorPoint(650f, 70f), 1f))
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
    fun workspaceDotsMoveWithPanInBothDirectionsAndDoNotOverflow() {
        assertEquals(0f, workspaceGridOffset(0f, 44f), 0f)
        assertEquals(10f, workspaceGridOffset(10f, 44f), 0.0001f)
        assertEquals(34f, workspaceGridOffset(-10f, 44f), 0.0001f)
        assertEquals(24f, workspaceGridOffset(-20f, 44f), 0.0001f)
        assertEquals(0f, workspaceGridOffset(Float.NaN, 44f), 0f)
        assertEquals(0f, workspaceGridOffset(20f, 0f), 0f)
    }

    @Test
    fun verticalAutoPanGraduallyTiltsBeforeTheScreenEdgeAndStaysStraightInTheMiddle() {
        val height = 800
        val maxSpeed = 440f
        val edge = verticalAutoPanEdgeSize(height, 1f, isLandscape = false)
        assertEquals(260f, edge, 0f)
        assertEquals(0f, edgePanSpeed(400f, height, edge, maxSpeed), 0f)
        assertEquals(0f, edgePanSpeed(261f, height, edge, maxSpeed), 0f)
        assertEquals(0f, edgePanSpeed(260f, height, edge, maxSpeed), 0f)
        assertEquals(220f, edgePanSpeed(130f, height, edge, maxSpeed), 0.0001f)
        assertEquals(440f, edgePanSpeed(0f, height, edge, maxSpeed), 0f)
        assertEquals(-220f, edgePanSpeed(670f, height, edge, maxSpeed), 0.0001f)
        assertEquals(-440f, edgePanSpeed(800f, height, edge, maxSpeed), 0f)
        assertEquals(137.6f, verticalAutoPanEdgeSize(320, 1f, isLandscape = false), 0.001f)
        assertEquals(520f, verticalAutoPanEdgeSize(1600, 2f, isLandscape = false), 0f)
        assertEquals(180f, verticalAutoPanEdgeSize(800, 1f, isLandscape = true), 0f)
        assertEquals(96f, verticalAutoPanEdgeSize(320, 1f, isLandscape = true), 0f)
        assertEquals(0f, verticalAutoPanEdgeSize(0, 1f, isLandscape = false), 0f)
        assertEquals(0f, verticalAutoPanEdgeSize(800, Float.NaN, isLandscape = true), 0f)
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
