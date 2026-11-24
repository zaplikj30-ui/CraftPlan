package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DimensionSceneTest {
    @Test
    fun labelMovesOnlyAlongItsOwnAxisAndLockLeavesGeometryEditable() {
        val original = dimension(1).withManualLength("1/16 in")
        val other = dimension(2).withManualLength("0 mm")
        val axis = EditorPoint(100f, 100f)
        assertSame(original, original.shiftLabel(axis, EditorPoint(10f, -10f), locked = false))
        assertSame(original, original.shiftLabel(axis, EditorPoint(20f, 20f), locked = true))
        val shifted = original.shiftLabel(axis, EditorPoint(20f, 20f), locked = false)
        assertEquals(0.4f, shifted.labelT, 0.00001f)
        assertEquals(original, shifted.copy(labelT = 0f))
        assertEquals(0.92f, shifted.shiftLabel(axis, axis, locked = false).labelT, 0f)
        val moved = shifted.move(DimensionHit.START, 0.1f, 0f)
        assertEquals(shifted.labelT, moved.labelT, 0f)
        val scene = DimensionScene(listOf(original, other)).edit(original, shifted)
        assertSame(other, scene.find(other.id))
    }

    @Test
    fun repeatAppendsWithoutSelectionAndClosesOrTogglesSafely() {
        val repeat = DimensionScene().toggleRepeat()
        val two = repeat.create(dimension(1)).create(dimension(2))
        assertEquals(DimensionTool.REPEAT, two.tool)
        assertNull(two.selectedId)
        val selected = two.select(dimension(1).id)
        assertEquals(DimensionTool.REPEAT, selected.tool)
        assertEquals(dimension(1).id, selected.selectedId)
        assertEquals(DimensionTool.SELECT, selected.toggleRepeat().tool)
        assertEquals(DimensionTool.SELECT, selected.closePhoto().tool)
        assertEquals(two.dimensions, selected.closePhoto().dimensions)
        val single = two.prepareSingle().create(dimension(3))
        assertEquals(DimensionTool.SELECT, single.tool)
        assertEquals(dimension(3).id, single.selectedId)
    }

    @Test
    fun shortTapTogglesActiveModesAndOneShotStopsAfterCreation() {
        val ready = DimensionScene().tapDrawingTool()
        assertEquals(DimensionTool.SINGLE, ready.tool)
        assertEquals(DimensionTool.SELECT, ready.tapDrawingTool().tool)
        assertEquals(DimensionTool.SELECT, ready.create(dimension(1)).tool)
        val repeat = ready.toggleRepeat()
        assertEquals(DimensionTool.REPEAT, repeat.create(dimension(1)).tool)
        assertEquals(DimensionTool.SELECT, repeat.tapDrawingTool().tool)
    }

    @Test
    fun rulerMovesWithUndoAndKeepsItsPositionWhenAssignedRealZero() {
        val original = dimension(1)
        val axis = EditorPoint(80f, 0f)
        assertSame(original, original.shiftLabel(axis, axis, locked = true))
        val moved = original.shiftLabel(axis, EditorPoint(20f, 15f), locked = false)
        assertEquals(0.5f, moved.labelT, 0f)
        assertEquals(MeasurementState.UNMEASURED, moved.measurementState)
        assertEquals(original, moved.copy(labelT = original.labelT))
        val measured = moved.withManualLength("0 mm")
        assertEquals(moved.labelT, measured.labelT, 0f)
        assertEquals(moved, measured.copy(measurement = null))
        val before = DimensionScene(listOf(original))
        val after = before.edit(original, moved)
        val (undone, history) = DimensionHistory().record(before, after).undo(after)!!
        assertEquals(before, undone)
        assertEquals(after, history.redo(undone)?.first)
    }

    @Test
    fun projectingTicksAndArrowTipsBeatTheHaloOfNearbySelectedEndpoint() {
        val upper = dimension(1)
        val lower = dimension(2)
        val startX = 12f
        for ((point, winner, selected) in listOf(
            Triple(EditorPoint(startX, 10f), upper, lower),
            Triple(EditorPoint(startX, 37f), lower, upper),
            Triple(EditorPoint(startX + 7f, 16.5f), upper, lower),
        )) {
            val candidates = listOf(upper to 20f, lower to 27f).flatMap { (dimension, y) ->
                dimensionStrokeTargets(point, EditorPoint(startX, y), EditorPoint(100f, y), 1f).map {
                    DimensionTarget(dimension, it.hit, false, it.distance, it.centerDistance, it.direct)
                }
            }
            assertEquals(winner.id, preferredDimensionTarget(candidates, selected.id, 1f)?.dimension?.id)
        }
    }

    @Test
    fun independentCreationAndEditsUseStableIdentity() {
        val first = dimension(0).withManualLength("1/3 in").copy(note = "První")
        val items = (1..200).map(::dimension)
        val scene = (listOf(first) + items).fold(DimensionScene()) { state, item ->
            state.copy(tool = DimensionTool.SINGLE).create(item)
        }
        assertEquals(201, scene.dimensions.size)
        assertEquals(items.last().id, scene.selectedId)
        assertEquals(DimensionTool.SELECT, scene.tool)
        val draft = DimensionValueDraft(first).withNote("Upravená")
        val updated = draft.confirm(scene.find(draft.targetId)!!)!!
        val edited = scene.edit(first, updated)
        assertEquals("Upravená", edited.find(first.id)?.note)
        assertSame(first.measurement, edited.find(first.id)?.measurement)
        assertEquals(items, edited.dimensions.drop(1))
        assertSame(edited, edited.edit(first, first.copy(note = "Zastaralá")))
        assertSame(edited, edited.edit(updated, items.first()))
        assertNull(edited.select(null).selectedId)
    }

    @Test
    fun endpointWinsAndSelectionBreaksOnlyGeometricTies() {
        val label = DimensionTarget(dimension(0), DimensionHit.BODY, true, 0f)
        val nearest = DimensionTarget(dimension(1), DimensionHit.END, false, 9f)
        val selected = DimensionTarget(dimension(2), DimensionHit.START, false, 15f)
        val targets = listOf(nearest, selected)
        assertEquals(nearest, preferredDimensionTarget(targets, selected.dimension.id, 1f))
        val tied = selected.copy(distance = nearest.distance)
        assertEquals(tied, preferredDimensionTarget(listOf(nearest, tied), tied.dimension.id, 1f))
        assertEquals(nearest, preferredDimensionTarget(targets, selected.dimension.id, 0.5f))
        assertEquals(label, preferredDimensionTarget(targets + label, selected.dimension.id, 1f))
        val directEnd = nearest.copy(distance = 0f, direct = true)
        assertEquals(directEnd, preferredDimensionTarget(listOf(label, directEnd), null, 1f))
    }

    @Test
    fun selectedHandleCenterResistsACrossingLineButDoesNotCaptureItsExposedPart() {
        val selected = dimension(1)
        val crossing = dimension(2)
        fun targets(
            point: EditorPoint,
            crossingStart: EditorPoint = EditorPoint(3f, -50f),
            crossingEnd: EditorPoint = EditorPoint(3f, 50f),
        ): List<DimensionTarget> {
            val selectedStroke = dimensionStrokeTargets(point, EditorPoint(0f, 0f), EditorPoint(100f, 0f), 1f)
            val crossingStroke = dimensionStrokeTargets(point, crossingStart, crossingEnd, 1f)
            return listOf(selected to selectedStroke, crossing to crossingStroke).flatMap { (owner, stroke) ->
                stroke.map { DimensionTarget(owner, it.hit, false, it.distance, it.centerDistance, it.direct, gripCore = it.gripCore) }
            }
        }
        val atGrip = targets(EditorPoint(3f, 1.1f))
        assertEquals(crossing.id, preferredDimensionTarget(atGrip, null, 1f)?.dimension?.id)
        val held = preferredDimensionTarget(atGrip, selected.id, 1f)
        assertEquals(selected.id, held?.dimension?.id)
        assertEquals(DimensionHit.START, held?.hit)
        assertEquals(crossing.id, preferredDimensionTarget(targets(EditorPoint(3f, 10f)), selected.id, 1f)?.dimension?.id)
        val nearForeignTip = targets(EditorPoint(3f, 1.1f), EditorPoint(2.8f, 1.1f), EditorPoint(100f, 1.1f))
        assertEquals(crossing.id, preferredDimensionTarget(nearForeignTip, selected.id, 1f)?.dimension?.id)
    }

    @Test
    fun nearbyParallelLinesAndLabelsPreferActualNearestElement() {
        val upper = dimension(1)
        val lower = dimension(2)
        for ((point, expected) in listOf(EditorPoint(50f, 34f) to upper, EditorPoint(50f, 42f) to lower)) {
            val hits = listOf(
                DimensionTarget(upper, DimensionHit.BODY, false,
                    distanceToDimensionLine(point, EditorPoint(0f, 30f), EditorPoint(100f, 30f))),
                DimensionTarget(lower, DimensionHit.BODY, false,
                    distanceToDimensionLine(point, EditorPoint(0f, 44f), EditorPoint(100f, 44f))),
            )
            assertEquals(expected, preferredDimensionTarget(hits, upper.id, 1f)?.dimension)
        }
        val direct = DimensionTarget(lower, DimensionHit.BODY, true, 0f, 2f)
        val fringe = DimensionTarget(upper, DimensionHit.BODY, false, 5f)
        assertEquals(direct, preferredDimensionTarget(listOf(fringe, direct), upper.id, 1f))
        val selectedFringe = DimensionTarget(upper, DimensionHit.BODY, true, 0.5f, 2f)
        assertEquals(direct, preferredDimensionTarget(listOf(selectedFringe, direct), upper.id, 1f))
        val directLine = fringe.copy(distance = 0f, direct = true)
        val wideEnd = DimensionTarget(lower, DimensionHit.END, false, 12f)
        assertEquals(directLine, preferredDimensionTarget(listOf(wideEnd, directLine), lower.id, 1f))
    }

    @Test
    fun shortDimensionCenterAndRulerAreSeparateTargets() {
        val dimension = dimension(1)
        val start = EditorPoint(0f, 0f)
        val end = EditorPoint(12f, 0f)
        fun target(x: Float, y: Float): DimensionTarget? {
            val stroke = dimensionStrokeTargets(EditorPoint(x, y), start, end, 1f).map {
                DimensionTarget(dimension, it.hit, false, it.distance, it.centerDistance, it.direct)
            }
            val ruler = labelRectangleDistance(x - 6f, y, -14f, -17f, 14f, -7f)
            return preferredDimensionTarget(
                stroke + DimensionTarget(dimension, DimensionHit.BODY, true, ruler.distance, ruler.centerDistance),
                dimension.id, 1f,
            )
        }
        assertEquals(DimensionHit.START, target(0f, 0f)?.hit)
        assertEquals(DimensionHit.END, target(12f, 0f)?.hit)
        assertEquals(DimensionHit.BODY, target(6f, 0f)?.hit)
        assertEquals(false, target(6f, 0f)?.label)
        assertEquals(true, target(6f, -12f)?.label)
    }

    @Test
    fun isolatedEndRemainsGrabbableButDirectForeignStrokeWins() {
        val a = dimension(1)
        val b = dimension(2)
        val end = DimensionTarget(a, DimensionHit.START, false, 10f)
        val ownLine = DimensionTarget(a, DimensionHit.BODY, false, 0f)
        val otherLine = DimensionTarget(b, DimensionHit.BODY, false, 0f)
        val ownLabel = DimensionTarget(a, DimensionHit.BODY, true, 0f)
        assertEquals(end, preferredDimensionTarget(listOf(end, ownLine), null, 1f))
        assertEquals(otherLine, preferredDimensionTarget(listOf(end, otherLine), a.id, 1f))
        assertEquals(ownLabel, preferredDimensionTarget(listOf(end, ownLine, ownLabel), a.id, 1f))
    }

    @Test
    fun overlappingLabelsPreferNearestCenterAndExactOverlapDoesNotCycle() {
        val a = dimension(1)
        val b = dimension(2)
        val upper = labelRectangleDistance(0f, -10f, -30f, -24f, 30f, -5f)
        val lower = labelRectangleDistance(0f, -15f, -30f, -24f, 30f, -5f)
        val labels = listOf(
            DimensionTarget(a, DimensionHit.BODY, true, upper.distance, upper.centerDistance),
            DimensionTarget(b, DimensionHit.BODY, true, lower.distance, lower.centerDistance),
        )
        assertEquals(b, preferredDimensionTarget(labels, a.id, 1f)?.dimension)
        val lines = listOf(DimensionTarget(a, DimensionHit.BODY, false, 0f), DimensionTarget(b, DimensionHit.BODY, false, 0f))
        repeat(10) {
            val hit = preferredDimensionTarget(lines, b.id, 1f)
            assertEquals(b, hit?.dimension)
            assertEquals(false, decideDimensionTap(hit, b.id)?.openValue)
        }
    }

    @Test
    fun onlyLabelOfAlreadySelectedDimensionOpensValue() {
        val a = dimension(1)
        val b = dimension(2)
        val label = DimensionTarget(a, DimensionHit.BODY, true, 0f)
        val body = label.copy(label = false)
        val end = label.copy(hit = DimensionHit.END, label = false)
        assertEquals(false, decideDimensionTap(label, null)?.openValue)
        assertEquals(false, decideDimensionTap(label, b.id)?.openValue)
        assertEquals(true, decideDimensionTap(label, a.id)?.openValue)
        assertEquals(false, decideDimensionTap(body, a.id)?.openValue)
        assertEquals(false, decideDimensionTap(end, a.id)?.openValue)
        assertNull(decideDimensionTap(null, a.id))
    }

    @Test
    fun crossingLinesUseFiniteSegmentDistance() {
        val horizontal = EditorPoint(100f, 50f)
        val vertical = EditorPoint(50f, 100f)
        val p = EditorPoint(65f, 53f)
        assertEquals(3f, distanceToDimensionLine(p, EditorPoint(0f, 50f), horizontal), 0.001f)
        assertEquals(15f, distanceToDimensionLine(p, EditorPoint(50f, 0f), vertical), 0.001f)
        assertEquals(5f, distanceToDimensionLine(EditorPoint(104f, 53f), EditorPoint(0f, 50f), horizontal), 0.001f)
    }

    private fun dimension(number: Int) = PhotoDimension(
        UUID(0, number.toLong()), PhotoId.SYNTHETIC_WORKBENCH, PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f),
    )
}
