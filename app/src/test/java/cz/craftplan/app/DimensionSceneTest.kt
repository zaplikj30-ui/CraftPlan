package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DimensionSceneTest {
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
        val targets = listOf(label, nearest, selected)
        assertEquals(listOf(selected, nearest, label), orderedDimensionTargets(targets, selected.dimension.id, 7f))
        assertEquals(listOf(nearest, selected, label), orderedDimensionTargets(targets, selected.dimension.id, 5f))
    }

    @Test
    fun repeatedTapCyclesDespiteSelectionReorderingAndRestartsElsewhere() {
        val ids = (0..2).map { dimension(it).id }
        val point = EditorPoint(50f, 60f)
        val first = DimensionTapCycle(point, ids, 0)
        val second = first.next(point, ids.reversed(), 7f)
        assertEquals(ids[1], second.candidates[second.index])
        assertEquals(2, second.next(point, ids, 7f).index)
        assertEquals(0, second.next(EditorPoint(70f, 60f), ids, 7f).index)
    }

    private fun dimension(number: Int) = PhotoDimension(
        UUID(0, number.toLong()), PhotoId.SYNTHETIC_WORKBENCH, PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f),
    )
}
