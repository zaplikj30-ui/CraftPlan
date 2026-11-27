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
    fun testAppearanceCheckboxTracksOverrideAndRemovingOnePreservesOthers() {
        val global = DimensionAppearance()
        val other = dimension.copy(id = UUID.randomUUID())
        val foreign = dimension.copy(photoId = PhotoId(UUID.randomUUID().toString()))
        // Checking the box creates an override even before changing any setting.
        val checked = DimensionTestAppearances().withOverride(dimension, global)
        assertEquals(global, checked.values[dimension.id])
        val changed = global.copy(geometryPercent = 160)
        val otherStyle = global.copy(labelPercent = 175)
        val both = checked.withOverride(dimension, changed).withOverride(other, otherStyle)
        assertEquals(changed, both.values[dimension.id])
        assertEquals(both, both.withoutOverride(foreign))
        val unchecked = both.withoutOverride(dimension)
        val newGlobal = global.copy(transparent = !global.transparent)
        assertEquals(null, unchecked.values[dimension.id])
        assertEquals(newGlobal, unchecked.effective(dimension, newGlobal))
        assertEquals(otherStyle, unchecked.effective(other, newGlobal))
        assertEquals(unchecked, unchecked.withoutOverride(dimension))
        assertEquals(DimensionTestAppearances(), unchecked.withoutOverride(other))
    }

    @Test
    fun testAppearancesIsolateAllSettingsByPhotoAndDimensionAndResetToCurrentGlobal() {
        val other = dimension.copy(id = UUID.randomUUID())
        // Even a matching dimension UUID must not leak a test style across photos.
        val anotherPhoto = dimension.copy(photoId = PhotoId(UUID.randomUUID().toString()))
        val global = DimensionAppearance()
        val local = DimensionAppearance(false, true, 150, 200, 37)
        val localOther = DimensionAppearance(true, false, 50, 250, 10)
        val comparisons = DimensionTestAppearances().withOverride(dimension, local)
            .withOverride(other, localOther)
        assertEquals(local, comparisons.effective(dimension, global))
        assertEquals(localOther, comparisons.effective(other, global))
        assertEquals(global, comparisons.effective(anotherPhoto, global))
        val newGlobal = global.copy(labelPercent = 115, inlineDragToleranceDegrees = 24)
        assertEquals(local, comparisons.effective(dimension, newGlobal))
        assertEquals(newGlobal, comparisons.effective(anotherPhoto, newGlobal))
        assertEquals(newGlobal, DimensionTestAppearances().effective(dimension, newGlobal))
        val switched = comparisons.withOverride(anotherPhoto, localOther)
        assertEquals(newGlobal, switched.effective(dimension, newGlobal))
        assertEquals(localOther, switched.effective(anotherPhoto, newGlobal))
        assertEquals(local, comparisons.effective(dimension, global))
    }

    @Test
    fun automaticAppearanceSizeInterpolatesInDpAndClampsAtTrialLimits() {
        for (width in listOf(0, 320, 360, 411, 480)) {
            assertEquals(1f, automaticDimensionSizeFactor(width), 0f)
        }
        assertEquals(1.075f, automaticDimensionSizeFactor(540), 0.00001f)
        assertEquals(1.15f, automaticDimensionSizeFactor(600), 0.00001f)
        assertEquals(1.225f, automaticDimensionSizeFactor(720), 0.00001f)
        assertEquals(1.3f, automaticDimensionSizeFactor(840), 0.00001f)
        assertEquals(1.3f, automaticDimensionSizeFactor(1200), 0.00001f)
        assertTrue((320..1200).zipWithNext().all { (a, b) ->
            automaticDimensionSizeFactor(a) <= automaticDimensionSizeFactor(b)
        })
    }

    @Test
    fun recalibratedReferenceAndLegacyPreferences() {
        val reference = DimensionAppearance()
        assertTrue(reference.inline)
        assertTrue(reference.transparent)
        assertEquals(100, reference.geometryPercent)
        assertEquals(100, reference.labelPercent)
        assertEquals(22, reference.inlineDragToleranceDegrees)
        assertEquals(100, rebaseLegacyDimensionSize(110, DIMENSION_GEOMETRY_BASE_PERCENT))
        assertEquals(100, rebaseLegacyDimensionSize(120, DIMENSION_LABEL_BASE_PERCENT))
        assertEquals(100, rebaseLegacyDimensionSize(100, DIMENSION_GEOMETRY_BASE_PERCENT))
        assertEquals(100, rebaseLegacyDimensionSize(100, DIMENSION_LABEL_BASE_PERCENT))
        assertEquals(150, rebaseLegacyDimensionSize(165, DIMENSION_GEOMETRY_BASE_PERCENT))
        assertEquals(150, rebaseLegacyDimensionSize(180, DIMENSION_LABEL_BASE_PERCENT))
        assertEquals(50, rebaseLegacyDimensionSize(50, DIMENSION_LABEL_BASE_PERCENT))
        for (old in 55..250 step 5) {
            if (old == 100) continue
            for (base in listOf(DIMENSION_GEOMETRY_BASE_PERCENT, DIMENSION_LABEL_BASE_PERCENT)) {
                val rebased = rebaseLegacyDimensionSize(old, base)
                assertTrue(rebased in 50..250 && rebased % 5 == 0)
                assertTrue(kotlin.math.abs(rebased * base / 100f - old) <= 5.01f)
            }
        }
    }

    @Test
    fun manualAppearanceSizesAreIndependentBoundedAndResetOnlySizes() {
        for (size in 50..250 step 5) assertEquals(size, dimensionSizePercent(size))
        assertEquals(50, dimensionSizePercent(Int.MIN_VALUE))
        assertEquals(250, dimensionSizePercent(Int.MAX_VALUE))
        assertEquals(115, dimensionSizePercent(113))
        assertEquals(110, dimensionSizePercent(112))
        for (automatic in listOf(1f, 1.15f, 1.3f)) {
            val defaults = DimensionAppearance()
            assertEquals(automatic * 1.10f, defaults.geometryFactor(automatic), 0.00001f)
            assertEquals(automatic * 1.20f, defaults.labelFactor(automatic), 0.00001f)
            for (geometry in 50..250 step 5) {
                for (label in 50..250 step 5) {
                    val appearance = DimensionAppearance(false, true, geometry, label)
                    assertEquals(automatic * 1.10f * geometry / 100f, appearance.geometryFactor(automatic), 0.00001f)
                    assertEquals(automatic * 1.20f * label / 100f, appearance.labelFactor(automatic), 0.00001f)
                    assertEquals(DimensionAppearance(false, true), appearance.resetSizes())
                }
            }
        }
    }

    @Test
    fun largeVisibleTicksRemainDirectlyHittableWithoutExpandingIndirectTolerance() {
        val start = EditorPoint(0f, 0f)
        val end = EditorPoint(500f, 0f)
        val scale = 2f * DimensionAppearance(geometryPercent = 250).geometryFactor(1.3f)
        val visible = EditorPoint(0f, 50f)
        assertTrue(dimensionStrokeTargets(visible, start, end, 1f, 2f, 1f)
            .none { it.hit == DimensionHit.START })
        val target = dimensionStrokeTargets(visible, start, end, 1f, scale, 1f)
            .first { it.hit == DimensionHit.START }
        assertTrue(target.direct)
        assertFalse(target.gripCore)
        assertTrue(dimensionStrokeTargets(EditorPoint(10f, 50f), start, end, 1f, scale, 1f)
            .none { it.hit == DimensionHit.START })
        val ordinary = dimensionStrokeTargets(EditorPoint(0f, 30f), start, end, 1f, 0.8f, 1f)
            .first { it.hit == DimensionHit.START }
        assertFalse(ordinary.direct)
        assertTrue(dimensionStrokeTargets(EditorPoint(0f, 31f), start, end, 1f, 0.8f, 1f)
            .none { it.hit == DimensionHit.START })
    }

    @Test
    fun largestAppearanceLabelStillClearsShortDimensionEndpoints() {
        val appearance = DimensionAppearance(geometryPercent = 250, labelPercent = 250)
        val factor = appearance.geometryFactor(1.3f)
        val geometry = dimensionLabelGeometry(1f, 60f, 18f, false, false).centeredOnLine()
        val scale = 0.6f * appearance.labelFactor(1.3f)
        val clearance = dimensionHandleRadius(factor, 0.8f, 0.8f, 2.95f)
        val placement = placeDimensionLabel(geometry, 20f, 0f, scale, false, true, clearance, 2f)
        val (left, _) = geometry.horizontalBounds(scale)
        assertTrue(placement.distance + left >= 20f + clearance)
        assertTrue(placement.bodySpans!!.all { it.start.isFinite() && it.end.isFinite() })
    }

    @Test
    fun rulerTouchGrowsThreeDpPerSideWithoutChangingVisibleGeometryOrOtherLabels() {
        for (density in listOf(1f, 2f, 3.5f)) {
            val original = dimensionLabelGeometry(density, null, 18f * density, false, true)
            val note = dimensionLabelGeometry(density, 60f * density, 18f * density, true, false)
            val measured = dimensionLabelGeometry(density, 60f * density, 18f * density, false, true)
            assertEquals(LabelRect(-24f * density, -36f * density,
                24f * density, -2f * density), original.rulerTouch.let {
                LabelRect(it.left + 3f * density, it.top + 3f * density,
                    it.right - 3f * density, it.bottom - 3f * density)
            })
            assertEquals(LabelRect(-14f * density, -17f * density,
                14f * density, -7f * density), original.ruler)
            assertEquals(LabelRect(15f * density, -36f * density,
                91f * density, -2f * density), note.textTouch)
            assertEquals(LabelRect(-38f * density, -36f * density,
                38f * density, -2f * density), measured.textTouch)
            assertEquals(LabelRect(16f * density, -32f * density,
                47f * density, density), original.notebookTouch)
            for (geometry in listOf(original, note, original.centeredOnLine(), note.centeredOnLine())) {
                val ruler = requireNotNull(geometry.ruler)
                for (scale in listOf(0.2f, 0.8f, 1f, 3.2f)) {
                    val touch = geometry.rulerTouch
                    val left = minOf(touch.left, ruler.left * scale - (ruler.left - touch.left))
                    val right = maxOf(touch.right, ruler.right * scale + (touch.right - ruler.right))
                    val y = ruler.centerY * scale
                    assertTrue(requireNotNull(ruler.hit(left + density, y, scale, touch)).distance > 0f)
                    assertTrue(requireNotNull(ruler.hit(right - density, y, scale, touch)).distance > 0f)
                    assertNull(ruler.hit(left - density, y, scale, touch))
                    assertNull(ruler.hit(right + density, y, scale, touch))
                    val top = minOf(touch.top, ruler.top * scale - (ruler.top - touch.top))
                    val bottom = maxOf(touch.bottom, ruler.bottom * scale + (touch.bottom - ruler.bottom))
                    assertTrue(requireNotNull(ruler.hit(0f, top + density, scale, touch)).distance > 0f)
                    assertTrue(requireNotNull(ruler.hit(0f, bottom - density, scale, touch)).distance > 0f)
                    assertNull(ruler.hit(0f, top - density, scale, touch))
                    assertNull(ruler.hit(0f, bottom + density, scale, touch))
                    assertEquals(0f, requireNotNull(ruler.hit(0f, y, scale, touch)).distance, 0f)
                }
            }
        }
    }

    @Test
    fun inlineDragUsesTwentyTwoDegreesInBothDirectionsAndAtAnyOrientation() {
        for (orientation in listOf(0.0, 90.0, 42.0, -53.0, 180.0)) {
            fun vector(angle: Double, length: Float): EditorPoint {
                val radians = Math.toRadians(angle)
                return EditorPoint((kotlin.math.cos(radians) * length).toFloat(),
                    (kotlin.math.sin(radians) * length).toFloat())
            }
            val axis = vector(orientation, 100f)
            for (direction in listOf(0.0, 180.0)) {
                for (offset in listOf(-21.99, -17.5, 0.0, 17.5, 21.99)) {
                    assertEquals(InlineLabelDragMode.LABEL, resolveInlineLabelDrag(
                        null, axis, vector(orientation + direction + offset, 30f), 7f, false))
                }
                for (offset in listOf(-90.0, -22.5, -22.01, 22.01, 22.5, 90.0)) {
                    assertEquals(InlineLabelDragMode.BODY, resolveInlineLabelDrag(
                        null, axis, vector(orientation + direction + offset, 30f), 7f, false))
                }
            }
        }
    }

    @Test
    fun inlineDragWaitsForThresholdThenKeepsFirstModeUntilNextGesture() {
        val axis = EditorPoint(100f, 0f)
        assertNull(resolveInlineLabelDrag(null, axis, EditorPoint(0f, 6f), 7f, false))
        assertNull(resolveInlineLabelDrag(null, axis, EditorPoint(7f, 0f), 7f, false))
        assertNull(resolveInlineLabelDrag(null, EditorPoint(0f, 0f), EditorPoint(30f, 0f), 7f, false))
        val label = resolveInlineLabelDrag(null, axis, EditorPoint(12f, 2f), 7f, false)
        val body = resolveInlineLabelDrag(null, axis, EditorPoint(2f, 12f), 7f, false)
        assertEquals(InlineLabelDragMode.LABEL, label)
        assertEquals(InlineLabelDragMode.BODY, body)
        assertEquals(label, resolveInlineLabelDrag(label, axis, EditorPoint(0f, 80f), 7f, false))
        assertEquals(body, resolveInlineLabelDrag(body, axis, EditorPoint(80f, 0f), 7f, false))
        assertEquals(InlineLabelDragMode.BODY, resolveInlineLabelDrag(
            null, axis, EditorPoint(30f, 0f), 7f, true))
        assertNull(resolveInlineLabelDrag(null, axis, EditorPoint(2f, 0f), 7f, true))
    }

    @Test
    fun adjustableInlineDragToleranceUsesEveryDegreeInBothDirections() {
        for (tolerance in MIN_INLINE_DRAG_TOLERANCE_DEGREES..MAX_INLINE_DRAG_TOLERANCE_DEGREES) {
            for (orientation in listOf(0.0, 90.0, 42.0, -53.0, 180.0)) {
                fun vector(angle: Double): EditorPoint {
                    val radians = Math.toRadians(angle)
                    return EditorPoint((kotlin.math.cos(radians) * 100).toFloat(),
                        (kotlin.math.sin(radians) * 100).toFloat())
                }
                val axis = vector(orientation)
                for (direction in listOf(0.0, 180.0)) {
                    for (side in listOf(-1.0, 1.0)) {
                        val inside = vector(orientation + direction + side * (tolerance - 0.01))
                        val outside = vector(orientation + direction + side * (tolerance + 0.01))
                        assertEquals(InlineLabelDragMode.LABEL, resolveInlineLabelDrag(
                            null, axis, inside, 7f, false, tolerance))
                        assertEquals(InlineLabelDragMode.BODY, resolveInlineLabelDrag(
                            null, axis, outside, 7f, false, tolerance))
                    }
                }
            }
        }
    }

    @Test
    fun adjustableInlineDragTolerancePreservesThresholdLocksAndSizePreferences() {
        val axis = EditorPoint(100f, 0f)
        assertEquals(10, clampInlineDragTolerance(Int.MIN_VALUE))
        assertEquals(40, clampInlineDragTolerance(Int.MAX_VALUE))
        assertEquals(22, DimensionAppearance().inlineDragToleranceDegrees)
        val appearance = DimensionAppearance(false, true, 150, 200, 37)
        assertEquals(DimensionAppearance(false, true, 100, 100, 37), appearance.resetSizes())
        for (degrees in 10..40) {
            assertEquals(degrees, clampInlineDragTolerance(degrees))
            assertNull(resolveInlineLabelDrag(null, axis, EditorPoint(7f, 0f), 7f, false, degrees))
            assertEquals(InlineLabelDragMode.BODY, resolveInlineLabelDrag(
                null, axis, EditorPoint(30f, 0f), 7f, true, degrees))
            assertEquals(InlineLabelDragMode.LABEL, resolveInlineLabelDrag(
                InlineLabelDragMode.LABEL, axis, EditorPoint(0f, 80f), 7f, false, degrees))
            assertEquals(InlineLabelDragMode.BODY, resolveInlineLabelDrag(
                InlineLabelDragMode.BODY, axis, EditorPoint(80f, 0f), 7f, false, degrees))
        }
    }

    @Test
    fun centeredDecorationKeepsSizeAndTouchMargins() {
        val above = dimensionLabelGeometry(2f, 80f, 36f, false, true)
        val inline = above.centeredOnLine()
        val text = requireNotNull(inline.text)
        val oldText = requireNotNull(above.text)
        val touch = requireNotNull(inline.textTouch)
        assertEquals(0f, text.centerY, 0f)
        assertEquals(oldText.bottom - oldText.top, text.bottom - text.top, 0f)
        assertEquals(0f, requireNotNull(inline.notebook).centerY, 0f)
        assertEquals(0f, requireNotNull(text.hit(0f, 0f, 0.6f, touch)).distance, 0f)
        assertTrue(text.hit(0f, text.top * 0.6f - 1f, 0.6f, touch) != null)
    }

    @Test
    fun inlinePlacementLeavesRealGapAndProtectsEndpoints() {
        val geometry = dimensionLabelGeometry(1f, 60f, 18f, false, true).centeredOnLine()
        val (left, right) = geometry.horizontalBounds(1f)
        for (reversed in listOf(false, true)) {
            for (offset in listOf(-0.92f, 0f, 0.92f)) {
                val p = placeDimensionLabel(geometry, 200f, offset, 1f, reversed, true, 16f, 4f)
                val center = if (reversed) 200f - p.distance else p.distance
                assertTrue(center + left >= 20f - 0.0001f)
                assertTrue(center + right <= 180f + 0.0001f)
                val spans = requireNotNull(p.bodySpans)
                assertEquals(2, spans.size)
                val start = EditorPoint(0f, 0f)
                val end = EditorPoint(200f, 0f)
                val atLabel = EditorPoint(p.distance, 0f)
                assertFalse(dimensionStrokeTargets(atLabel, start, end, 1f, bodySpans = spans)
                    .any { it.hit == DimensionHit.BODY && it.direct })
                assertTrue(dimensionStrokeTargets(start, start, end, 1f, bodySpans = spans)
                    .any { it.hit == DimensionHit.START && it.gripCore })
            }
        }
    }

    @Test
    fun shortLabelsUseReadableOutsideSideAndAboveKeepsOriginalPosition() {
        for (geometry in listOf(
            dimensionLabelGeometry(1f, 140f, 18f, false, false),
            dimensionLabelGeometry(1f, null, 18f, false, true),
            dimensionLabelGeometry(1f, 140f, 18f, true, false),
        )) {
            val inline = geometry.centeredOnLine()
            val (left, right) = inline.horizontalBounds(1f)
            for (reversed in listOf(false, true)) {
                val length = 20f
                val p = placeDimensionLabel(inline, length, 0f, 1f, reversed, true, 12f, 4f)
                val center = if (reversed) length - p.distance else p.distance
                assertTrue(center + left >= length + 16f - 0.0001f)
                assertEquals(1, requireNotNull(p.bodySpans).size)
                val other = placeDimensionLabel(inline, length, if (reversed) 0.5f else -0.5f,
                    1f, reversed, true, 12f, 4f)
                val otherCenter = if (reversed) length - other.distance else other.distance
                assertTrue(otherCenter + right <= -16f)
            }
            val above = placeDimensionLabel(geometry, 20f, 0.5f, 1f, false, false, 12f, 4f)
            assertEquals(15f, above.distance, 0f)
            assertNull(above.bodySpans)
        }
    }

    @Test
    fun movingOutsideLabelCannotJumpAcrossDimensionOrChangeAnchors() {
        val geometry = dimensionLabelGeometry(1f, 140f, 18f, false, false).centeredOnLine()
        for (reversed in listOf(false, true)) {
            val axis = EditorPoint(if (reversed) -20f else 20f, 0f)
            val initial = dimension.copy(labelT = 0f)
            val p = placeDimensionLabel(geometry, 20f, initial.labelT, 1f, reversed, true, 12f, 4f)
            assertEquals(initial, initial.shiftPlacedLabel(axis, EditorPoint(-6f, 0f), false, p))
            val moved = initial.shiftPlacedLabel(axis, EditorPoint(6f, 0f), false, p)
            assertEquals(if (reversed) -0.6f else 0.6f, moved.labelT, 0.00001f)
            assertEquals(initial.start, moved.start)
            assertEquals(initial.end, moved.end)
            assertEquals(initial, initial.shiftPlacedLabel(axis, EditorPoint(6f, 0f), true, p))
            val otherSide = initial.copy(labelT = if (reversed) 0.5f else -0.5f)
            val other = placeDimensionLabel(geometry, 20f, otherSide.labelT, 1f, reversed, true, 12f, 4f)
            val limited = otherSide.shiftPlacedLabel(axis, EditorPoint(100f, 0f), false, other)
            assertTrue(if (reversed) limited.labelT > 0f else limited.labelT < 0f)
        }
    }

    @Test
    fun proportionalZoomKeepsInlinePlacementWhenClearanceAlsoFits() {
        val geometry = dimensionLabelGeometry(1f, 60f, 18f, false, false).centeredOnLine()
        val a = placeDimensionLabel(geometry, 200f, 0.3f, 1f, false, true, 16f, 4f)
        val b = placeDimensionLabel(geometry, 400f, 0.3f, 2f, false, true, 32f, 8f)
        assertEquals(2f * a.distance, b.distance, 0.0001f)
        assertEquals(2f * a.dragTravel, b.dragTravel, 0.0001f)
    }

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
    fun labelGeometryScalesRulerNotebookTextAndSpacingTogether() {
        for (density in listOf(1f, 2f)) {
            val unmeasured = dimensionLabelGeometry(density, null, 18f * density, false, true)
            val ruler = requireNotNull(unmeasured.ruler)
            val notebook = requireNotNull(unmeasured.notebook)
            assertEquals(28f * density, ruler.right - ruler.left, 0f)
            assertEquals(15f * density, notebook.right - notebook.left, 0f)
            for (scale in listOf(0.8f, 1f, 1.2f)) {
                assertEquals(10f * density * scale, (notebook.left - ruler.right) * scale, 0.0001f)
                assertEquals(0f, requireNotNull(ruler.hit(ruler.right * scale, -12f * density * scale,
                    scale, unmeasured.rulerTouch)).distance, 0f)
                assertEquals(0f, requireNotNull(notebook.hit(notebook.right * scale, -15f * density * scale,
                    scale, requireNotNull(unmeasured.notebookTouch))).distance, 0f)
                assertTrue(ruler.hit(23f * density, -12f * density, scale, unmeasured.rulerTouch) != null)
                assertTrue(requireNotNull(ruler.hit(23f * density, -12f * density,
                    scale, unmeasured.rulerTouch)).distance > 0f)
                assertNull(ruler.hit(60f * density, -12f * density, scale, unmeasured.rulerTouch))
                assertTrue(notebook.hit(notebook.right + 7f * density, -15f * density,
                    scale, requireNotNull(unmeasured.notebookTouch)) != null)
                assertTrue(unmeasured.cullingRadius * scale >=
                    kotlin.math.hypot(notebook.right * scale, notebook.top * scale) - 0.0001f)
                val visibleNote = dimensionLabelGeometry(density, 60f * density, 18f * density, true, false)
                val note = requireNotNull(visibleNote.text)
                assertEquals(4f * density * scale,
                    (note.left - requireNotNull(visibleNote.ruler).right) * scale, 0.0001f)
                assertEquals(0f, requireNotNull(note.hit(note.right * scale, -10f * density * scale,
                    scale, requireNotNull(visibleNote.textTouch))).distance, 0f)
                assertNull(visibleNote.notebook)
                val measured = dimensionLabelGeometry(density, 60f * density, 18f * density, false, true)
                assertNull(measured.ruler)
                assertEquals(5f * density * scale,
                    (requireNotNull(measured.notebook).left - requireNotNull(measured.text).right) * scale, 0.0001f)
            }
        }
    }

    @Test
    fun rulerAndNeighborsGrowProportionallyWithPhoto() {
        for (density in listOf(1f, 2f)) {
            val hidden = dimensionLabelGeometry(density, null, 18f * density, false, true)
            val shown = dimensionLabelGeometry(density, 60f * density, 18f * density, true, false)
            val measured = dimensionLabelGeometry(density, 60f * density, 18f * density, false, true)
            for ((scale, expected) in listOf(0.075f to 0.1f, 0.3f to 0.4f,
                0.6f to 0.8f, 1.2f to 1.6f, 2.4f to 3.2f, 4.8f to 6.4f)) {
                for (geometry in listOf(hidden, shown)) {
                    val ruler = requireNotNull(geometry.ruler)
                    val neighbor = requireNotNull(geometry.text ?: geometry.notebook)
                    val offset = geometry.rulerNeighborOffset(scale)
                    assertEquals(expected, geometry.rulerScale(scale), 0.00001f)
                    assertEquals(28f * density * expected, (ruler.right - ruler.left) * expected, 0.0001f)
                    assertEquals((neighbor.left - ruler.right) * scale,
                        neighbor.left * scale + offset - ruler.right * expected, 0.0001f)
                    val actualScale = geometry.rulerScale(scale)
                    assertEquals(0f, requireNotNull(ruler.hit(ruler.right * actualScale,
                        -12f * density * actualScale, actualScale, geometry.rulerTouch)).distance, 0f)
                    assertTrue(ruler.hit(23f * density, -12f * density,
                        actualScale, geometry.rulerTouch) != null)
                    assertTrue(geometry.cullingRadius * geometry.cullingScale(scale) >=
                        kotlin.math.hypot(ruler.right * actualScale, ruler.top * actualScale) - 0.0001f)
                    val touch = requireNotNull(geometry.textTouch ?: geometry.notebookTouch)
                    val point = neighbor.centerX * scale + offset
                    assertEquals(0f, requireNotNull(neighbor.hit(point - offset,
                        (neighbor.top + neighbor.bottom) / 2f * scale, scale, touch)).distance, 0f)
                    assertTrue(geometry.cullingRadius * geometry.cullingScale(scale) >=
                        kotlin.math.hypot(neighbor.right * scale + offset, neighbor.top * scale) - 0.0001f)
                }
                assertEquals(0f, measured.rulerNeighborOffset(scale), 0f)
                assertEquals(maxOf(1f, scale), measured.cullingScale(scale), 0f)
            }
        }
    }

    @Test
    fun smallerFitLabelsReduceEncroachmentWithoutChangingLabelAnchorOrHitMinimum() {
        val geometry = dimensionLabelGeometry(1f, 60f, 18f, false, false)
        val bounds = requireNotNull(geometry.text)
        val touch = requireNotNull(geometry.textTouch)
        // A neighboring parallel line 18dp above the anchor used to cross the
        // label rectangle at fit (0.8x); the new 0.6x rectangle fits below it.
        assertTrue(-18f in (bounds.top * 0.8f)..(bounds.bottom * 0.8f))
        assertTrue(-18f < bounds.top * 0.6f)
        assertEquals(0f, bounds.centerX, 0f)
        assertEquals(0f, requireNotNull(bounds.hit(0f, -10f, 0.6f, touch)).distance, 0f)
        assertTrue(bounds.hit(34f, -10f, 0.6f, touch) != null)
        assertNull(bounds.hit(70f, -10f, 0.6f, touch))
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
    fun visualHandleEnclosesTickOutlineAtEveryZoomWithoutChangingHitGeometry() {
        for (progress in listOf(0f, 0.5f, 1f)) {
            val strokeScale = 0.8f + 1.2f * progress
            val tickScale = 0.8f + 0.2f * progress
            val outline = 2.95f + 1.05f * progress
            val segments = FloatArray(28)
            writeDimensionSegments(segments, 300f, strokeScale, tickScale)
            val tick = kotlin.math.abs(segments[5])
            val halfOutline = outline * strokeScale / 2f
            val corner = kotlin.math.hypot(tick + halfOutline, halfOutline)
            val radius = dimensionHandleRadius(1f, strokeScale, tickScale, outline)
            assertEquals(1.5f, radius - 0.7f - corner, 0.0001f)
            assertEquals(radius * 2f, dimensionHandleRadius(2f, strokeScale, tickScale, outline), 0.0001f)
            val targets = dimensionStrokeTargets(EditorPoint(0f, 30f),
                EditorPoint(0f, 0f), EditorPoint(300f, 0f), 1f, strokeScale, tickScale)
            assertTrue(targets.any { it.hit == DimensionHit.START })
            assertTrue(dimensionStrokeTargets(EditorPoint(0f, 31f),
                EditorPoint(0f, 0f), EditorPoint(300f, 0f), 1f, strokeScale, tickScale).none { it.hit == DimensionHit.START })
        }
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
