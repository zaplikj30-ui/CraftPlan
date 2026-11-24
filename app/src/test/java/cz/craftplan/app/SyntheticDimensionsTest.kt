package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticDimensionsTest {
    private val photo = PhotoId.SYNTHETIC_WORKBENCH

    @Test
    fun allSizesHaveDeterministicDistinctIdsAndMixedContents() {
        val full = SyntheticDimensions.generate(photo, 10_000)
        for (size in SyntheticDimensions.counts) {
            val batch = SyntheticDimensions.generate(photo, size)
            assertEquals(size, batch.size)
            assertEquals(size, batch.map { it.id }.toSet().size)
            assertEquals(full.take(size), batch)
            assertTrue(batch.all { SyntheticDimensions.owns(it) && it.start != it.end })
        }
        assertEquals(listOf(null, "0 mm", "850,5 mm", "1/3 in", "1250 µm"), full.take(5).map { it.measurement?.originalInput })
        assertTrue(full.any { it.note.isNotBlank() })
    }

    @Test
    fun swappingAndClearingBatchesKeepsManualEditsAndSelection() {
        val manual = PhotoDimension(
            UUID.randomUUID(), photo, PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f),
        ).withManualLength("1/3 in").copy(note = "Ruční testovací poznámka")
        val before = DimensionScene(listOf(manual), manual.id)
        val withFirstBatch = before.replaceSynthetic(SyntheticDimensions.generate(photo, 100))
        val next = withFirstBatch.replaceSynthetic(SyntheticDimensions.generate(photo, 500))
        assertEquals(501, next.dimensions.size)
        assertSame(manual, next.find(manual.id))
        assertEquals(manual.id, next.selectedId)
        val cleared = next.replaceSynthetic(emptyList())
        assertEquals(before, cleared)
        val restored = DimensionHistory().record(next, cleared).undo(cleared)
        assertEquals(next, restored?.first)
    }

    @Test
    fun metricsSeparateCpuTimeFromGestureGapsAndResetBetweenBatches() {
        val metrics = EditorStressMetrics()
        val ms = 1_000_000L
        metrics.draw(10 * ms, 15 * ms, duringGesture = true)
        metrics.draw(70 * ms, 77 * ms, duringGesture = true)
        metrics.beginGesture()
        metrics.draw(210 * ms, 211 * ms, duringGesture = true)
        metrics.hit(3 * ms)
        metrics.hit(4 * ms)
        metrics.prepareScene(12 * ms)
        assertEquals(EditorStressMetrics.Snapshot(7.0, 3, 4.0, 2, 60.0, 1, 12.0), metrics.snapshot())
        metrics.reset()
        assertEquals(EditorStressMetrics.Snapshot(0.0, 0, 0.0, 0, 0.0, 0, 0.0), metrics.snapshot())
    }
}
