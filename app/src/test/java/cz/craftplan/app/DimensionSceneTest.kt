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
    fun endpointWinsAndSelectedEndpointOnlyWinsWithinTolerance() {
        val label = DimensionTarget(dimension(0), DimensionHit.BODY, true, 0f)
        val nearest = DimensionTarget(dimension(1), DimensionHit.END, false, 9f)
        val selected = DimensionTarget(dimension(2), DimensionHit.START, false, 15f)
        val targets = listOf(nearest, selected)
        assertEquals(selected, preferredDimensionTarget(targets, selected.dimension.id, 1f))
        assertEquals(nearest, preferredDimensionTarget(targets, selected.dimension.id, 0.5f))
        assertEquals(label, preferredDimensionTarget(targets + label, selected.dimension.id, 1f))
        val directEnd = nearest.copy(distance = 0f, direct = true)
        assertEquals(directEnd, preferredDimensionTarget(listOf(label, directEnd), null, 1f))
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
