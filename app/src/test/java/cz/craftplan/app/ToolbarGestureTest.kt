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

    @Test fun gripTapAndVerticalDragAreExclusiveAndDirectional() {
        for (current in listOf(false, true)) {
            assertEquals(!current, toolbarGripAfterRelease(current, 0f, 0f, 0f, 8f))
            assertEquals(!current, toolbarGripAfterRelease(current, 3f, 2f, 3f, 8f))
            assertEquals(true, toolbarGripAfterRelease(current, 2f, -8f, 8f, 8f))
            assertEquals(false, toolbarGripAfterRelease(current, 2f, 8f, 8f, 8f))
            assertEquals(current, toolbarGripAfterRelease(current, 20f, 2f, 20f, 8f))
            assertEquals(current, toolbarGripAfterRelease(current, 9f, -9f, 9f, 8f))
            assertEquals(current, toolbarGripAfterRelease(current, 0f, 0f, 20f, 8f))
            assertEquals(current, toolbarGripAfterRelease(current, 0f, -20f, 20f, 0f))
            assertEquals(current, toolbarGripAfterRelease(current, 0f, Float.NaN, 20f, 8f))
        }
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
