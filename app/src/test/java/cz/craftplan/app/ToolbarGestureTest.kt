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

    @Test fun gripReleaseReasonExplainsTheExistingDecision() {
        assertEquals(ToolbarGripOutcome.TAP, toolbarGripReleaseReason(0f, 0f, 0f, 8f))
        assertEquals(ToolbarGripOutcome.UP, toolbarGripReleaseReason(2f, -8f, 8f, 8f))
        assertEquals(ToolbarGripOutcome.DOWN, toolbarGripReleaseReason(2f, 8f, 8f, 8f))
        assertEquals(ToolbarGripOutcome.BELOW_VERTICAL_THRESHOLD, toolbarGripReleaseReason(0f, 0f, 20f, 8f))
        assertEquals(ToolbarGripOutcome.NON_VERTICAL, toolbarGripReleaseReason(9f, -9f, 9f, 8f))
        assertEquals(ToolbarGripOutcome.INVALID, toolbarGripReleaseReason(0f, Float.NaN, 20f, 8f))
    }

    @Test fun horizontalDragCollapsesBothRowsAndPreservesExtraOnExpansion() {
        for (expanded in listOf(false, true)) {
            for (extra in listOf(false, true)) {
                assertEquals(false to false, toolbarHorizontalExpansionAfterDrag(expanded, extra, -4f, 4f))
                assertEquals(true to extra, toolbarHorizontalExpansionAfterDrag(expanded, extra, 4f, 4f))
                for (delta in listOf(-3f, 0f, 3f, Float.NaN, Float.POSITIVE_INFINITY)) {
                    assertEquals(expanded to extra, toolbarHorizontalExpansionAfterDrag(expanded, extra, delta, 4f))
                }
                for (threshold in listOf(0f, -4f, Float.NaN, Float.POSITIVE_INFINITY)) {
                    assertEquals(expanded to extra, toolbarHorizontalExpansionAfterDrag(expanded, extra, -5f, threshold))
                }
            }
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
