package cz.craftplan.app

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarGestureTest {
    @Test fun repeatHintCompensatesForAsymmetricPhysicalInsets() {
        assertEquals(0.dp, toolbarHintCenterOffset(16.dp, 16.dp))
        assertEquals((-12).dp, toolbarHintCenterOffset(24.dp, 0.dp))
        assertEquals(12.dp, toolbarHintCenterOffset(0.dp, 24.dp))
    }

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
