package cz.craftplan.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarGestureTest {
    @Test fun expansionAndCollapseAreDirectionalAndIdempotent() {
        for (current in listOf(false, true)) {
            assertEquals(true, toolbarExpansionAfterDrag(current, 5f, 4f))
            assertEquals(false, toolbarExpansionAfterDrag(current, -5f, 4f))
            assertEquals(current, toolbarExpansionAfterDrag(current, 3f, 4f))
            assertEquals(current, toolbarExpansionAfterDrag(current, -3f, 4f))
            assertEquals(current, toolbarExpansionAfterDrag(current, Float.NaN, 4f))
        }
    }
}
