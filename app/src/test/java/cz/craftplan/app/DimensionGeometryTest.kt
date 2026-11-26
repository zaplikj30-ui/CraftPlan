package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertArrayEquals
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

    // Exercise the same geometry and resolver as PhotoEditorView, not a legacy hit-test.
    private fun hitDimension(point: EditorPoint, start: EditorPoint, end: EditorPoint, density: Float): DimensionHit? =
        preferredDimensionTarget(
            dimensionStrokeTargets(point, start, end, density).map {
                DimensionTarget(dimension, it.hit, false, it.distance, it.centerDistance, it.direct)
            }, null, density,
        )?.hit

    @Test
    fun batchedStrokePreservesTicksAndArrowheadsForLongAndShortDimensions() {
        val reused = FloatArray(28)
        writeDimensionSegments(reused, 80f, 2f)
        assertArrayEquals(
            floatArrayOf(0f, 0f, 80f, 0f, 0f, -22f, 0f, 22f, 80f, -22f, 80f, 22f,
                0f, 0f, 18f, -9f, 80f, 0f, 62f, -9f, 0f, 0f, 18f, 9f, 80f, 0f, 62f, 9f),
            reused, 0f,
        )
        writeDimensionSegments(reused, 8f, 1f)
        assertArrayEquals(
            floatArrayOf(0f, 0f, 8f, 0f, 0f, -11f, 0f, 11f, 8f, -11f, 8f, 11f,
                0f, 0f, 2f, -1f, 8f, 0f, 6f, -1f, 0f, 0f, 2f, 1f, 8f, 0f, 6f, 1f),
            reused, 0f,
        )
    }

    @Test
    fun largerStrokesKeepAnchorsAndDirectHitsAlignedWithoutExpandingTouchZones() {
        val segments = FloatArray(28)
        writeDimensionSegments(segments, 100f, 2f)
        assertEquals(0f, segments[0], 0f)
        assertEquals(100f, segments[2], 0f)
        assertEquals(-22f, segments[5], 0f)
        assertEquals(22f, segments[7], 0f)
        val start = EditorPoint(0f, 0f)
        val end = EditorPoint(100f, 0f)
        fun targets(point: EditorPoint, scale: Float) = dimensionStrokeTargets(point, start, end, 1f, scale)
        assertTrue(targets(EditorPoint(0f, 22f), 2f).any { it.hit == DimensionHit.START && it.direct })
        assertFalse(targets(EditorPoint(0f, 22f), 1f).any { it.direct })
        assertTrue(targets(EditorPoint(18f, 9f), 2f).any { it.hit == DimensionHit.START && it.direct })
        assertTrue(targets(EditorPoint(4f, 0f), 2f).any { it.gripCore })
        assertFalse(targets(EditorPoint(5f, 0f), 2f).any { it.gripCore })
        assertTrue(targets(EditorPoint(0f, 32f), 2f).isEmpty())
        assertTrue(targets(EditorPoint(50f, 21f), 2f).isEmpty())
        assertEquals(listOf(DimensionHit.BODY), targets(EditorPoint(50f, 0f), 2f).map { it.hit })
        writeDimensionSegments(segments, 8f, 2f)
        assertEquals(2f, segments[14], 0f) // short arrows still cap at one quarter of length
    }

    @Test
    fun shorterFitTicksChangeOnlyVisibleTickGeometryAndNotTouchEligibility() {
        val original = FloatArray(28)
        val shortened = FloatArray(28)
        writeDimensionSegments(original, 100f, 0.8f)
        writeDimensionSegments(shortened, 100f, 0.8f, tickScale = 0.8f)
        for (index in original.indices) {
            val expected = if (index in listOf(5, 7, 9, 11)) original[index] * 0.8f else original[index]
            assertEquals(expected, shortened[index], 0.00001f)
        }
        val start = EditorPoint(0f, 0f)
        val end = EditorPoint(100f, 0f)
        val point = EditorPoint(0f, 8.6f)
        val before = dimensionStrokeTargets(point, start, end, 1f, 0.8f)
        val after = dimensionStrokeTargets(point, start, end, 1f, 0.8f, 0.8f)
        assertEquals(before.map { it.hit }, after.map { it.hit })
        assertTrue(before.any { it.hit == DimensionHit.START && it.direct })
        assertFalse(after.any { it.hit == DimensionHit.START && it.direct })
        assertTrue(dimensionStrokeTargets(EditorPoint(0f, shortened[7]), start, end, 1f, 0.8f, 0.8f)
            .any { it.hit == DimensionHit.START && it.direct })
    }

    @Test
    fun allPointerTypesStartFromTheEstablishedGestureProfile() {
        for (kind in EditorPointerKind.entries) {
            val profile = editorInputProfile(kind, 2f)
            assertEquals(kind, profile.kind)
            assertEquals(2f, profile.hitDensity, 0f)
            assertEquals(14f, profile.editDragThreshold, 0f)
        }
    }

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
        val near = dimensionStrokeTargets(EditorPoint(16f, 0f), start, end, 1f)
        assertTrue(near.any { it.hit == DimensionHit.START && !it.direct })
        assertTrue(near.any { it.hit == DimensionHit.BODY && it.direct })
        val tick = dimensionStrokeTargets(EditorPoint(0f, 10f), start, end, 1f)
        assertTrue(tick.any { it.hit == DimensionHit.START && it.direct })
        val short = dimensionStrokeTargets(EditorPoint(20f, 0f), start, EditorPoint(40f, 0f), 1f)
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
    fun pinchDiscardsEndpointBodyLabelAndDrawingPreviewsAndOwnsRemainingFinger() {
        for (target in listOf("start", "end", "body", "label", "empty workspace", "new dimension")) {
            val stream = EditorTouchStream()
            var preview: String? = target
            var cancellations = 0
            stream.begin()
            stream.beginPinch { preview = null; cancellations++ }
            assertNull(preview)
            assertTrue(stream.pinchOnly)
            stream.beginPinch { cancellations++ }
            assertEquals(1, cancellations)
            stream.begin()
            assertFalse(stream.pinchOnly)
        }
    }

    @Test
    fun tinyDiagonalDimensionReservesItsCenterAndUsesAReachableDragThreshold() {
        val start = EditorPoint(0f, 0f)
        val end = EditorPoint(8f, 8f)
        assertEquals(DimensionHit.START, hitDimension(start, start, end, 1f))
        assertEquals(DimensionHit.BODY, hitDimension(EditorPoint(4f, 4f), start, end, 1f))
        assertEquals(DimensionHit.END, hitDimension(end, start, end, 1f))
        val threshold = dimensionDragThreshold(start.distanceTo(end), 1f, 7f)
        assertEquals(3f, threshold, 0f)
        assertFalse(passesDragThreshold(start, EditorPoint(2f, 0f), threshold))
        assertTrue(passesDragThreshold(start, EditorPoint(4f, 0f), threshold))
        assertEquals(7f, dimensionDragThreshold(100f, 1f, 7f), 0f)
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
    fun fingerAndStylusCanDistinguishAndMoveAVeryShortDimensionAtMaximumZoom() {
        val viewport = PhotoViewport(400, 400)
        viewport.fit(400, 400)
        viewport.transform(200f, 200f, 200f, 200f, factor = 100f)
        val original = dimension.copy(start = PhotoAnchor(0.5f, 0.5f), end = PhotoAnchor(0.501f, 0.5f))
        val start = viewport.screenPoint(original.start)
        val end = viewport.screenPoint(original.end)
        val middle = EditorPoint((start.x + end.x) / 2f, start.y)
        for (kind in listOf(EditorPointerKind.FINGER, EditorPointerKind.STYLUS)) {
            val profile = editorInputProfile(kind, 1f)
            assertEquals(DimensionHit.START, hitDimension(start, start, end, profile.hitDensity))
            assertEquals(DimensionHit.END, hitDimension(end, start, end, profile.hitDensity))
            assertEquals(DimensionHit.BODY, hitDimension(middle, start, end, profile.hitDensity))
            val threshold = dimensionDragThreshold(start.distanceTo(end), profile.hitDensity, profile.editDragThreshold)
            val release = EditorPoint(middle.x, middle.y + threshold + 2f)
            assertTrue(passesDragThreshold(middle, release, threshold))
            val moved = viewport.moveDimension(original, DimensionHit.BODY, release - middle)
            assertEquals(original.id, moved.id)
            assertTrue(moved.start.v > original.start.v)
            assertEquals(moved.start.v, moved.end.v, 0f)
        }
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
    fun endpointEditingUsesNarrowVerticalEdgeInBothOrientations() {
        for (landscape in listOf(false, true)) {
            val height = if (landscape) 400 else 800
            val edge = verticalAutoPanEdgeSize(height, 1f, landscape, editingEndpoint = true)
            assertEquals(48f, edge, 0f)
            assertEquals(0f, edgePanSpeed(48f, height, edge, 440f), 0f)
            assertEquals(0f, edgePanSpeed(height - 48f, height, edge, 440f), 0f)
            assertEquals(0f, edgePanSpeed(100f, height, edge, 440f), 0f)
            assertEquals(0f, edgePanSpeed(height - 100f, height, edge, 440f), 0f)
            assertEquals(220f, edgePanSpeed(24f, height, edge, 440f), 0.0001f)
            assertEquals(-220f, edgePanSpeed(height - 24f, height, edge, 440f), 0.0001f)
            assertEquals(440f, edgePanSpeed(0f, height, edge, 440f), 0f)
            assertEquals(-440f, edgePanSpeed(height.toFloat(), height, edge, 440f), 0f)
            assertTrue(verticalAutoPanEdgeSize(height, 1f, landscape) > edge)
            assertEquals(96f, verticalAutoPanEdgeSize(height * 2, 2f, landscape, editingEndpoint = true), 0f)
        }
        assertEquals(15f, verticalAutoPanEdgeSize(30, 1f, true, editingEndpoint = true), 0f)
        assertEquals(0f, verticalAutoPanEdgeSize(0, 1f, false, editingEndpoint = true), 0f)
        assertEquals(0f, verticalAutoPanEdgeSize(800, Float.NaN, false, editingEndpoint = true), 0f)
    }

    @Test
    fun endpointAutoPanUsesFixedToolbarTopRatherThanScreenBottom() {
        for (landscape in listOf(false, true)) {
            val height = if (landscape) 400 else 800
            val top = 24
            val bottom = height - 74
            fun speed(y: Float, editing: Boolean = true, toolbar: Int? = bottom) =
                verticalAutoPanSpeed(y, height, top, toolbar, 1f, landscape, editing)
            assertEquals(440f, speed(top.toFloat()), 0f)
            assertEquals(0f, speed(top + 48f), 0f)
            assertEquals(0f, speed(bottom - 48f), 0f)
            assertEquals(-220f, speed(bottom - 24f), 0.0001f)
            assertEquals(-440f, speed(bottom.toFloat()), 0f)
            assertEquals(-440f, speed(height.toFloat()), 0f)
            assertEquals(0f, speed(bottom - 24f, toolbar = null), 0f)
            assertEquals(speed(bottom - 24f, false, null), speed(bottom - 24f, false), 0f)
            assertEquals(0f, speed(top.toFloat(), toolbar = top), 0f)
        }
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
