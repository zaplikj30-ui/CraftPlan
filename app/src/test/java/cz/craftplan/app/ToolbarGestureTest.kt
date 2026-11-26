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

    @Test fun topGripIgnoresTapsButRetainsDirectionalDrags() {
        for (current in listOf(false, true)) {
            assertEquals(current, toolbarGripAfterRelease(current, 0f, 0f, 0f, 8f, tapToggles = false))
            assertEquals(current, toolbarGripAfterRelease(current, 3f, 2f, 3f, 8f, tapToggles = false))
            assertEquals(true, toolbarGripAfterRelease(current, 2f, -8f, 8f, 8f, tapToggles = false))
            assertEquals(false, toolbarGripAfterRelease(current, 2f, 8f, 8f, 8f, tapToggles = false))
            assertEquals(current, toolbarGripAfterRelease(current, 20f, 2f, 20f, 8f, tapToggles = false))
            assertEquals(current, toolbarGripAfterRelease(current, 0f, 0f, 20f, 8f, tapToggles = false))
        }
    }

    @Test fun topGripWorkspaceOverlapYieldsToEditorButVisibleGripRemainsReachable() {
        for (width in listOf(400f, 800f)) {
            val center = width / 2f
            val region = ToolbarTopGripRegion(center - 80f, 300f, center + 80f, 327f, 318f)
            assertEquals(false, region.canTrack(center, 310f, editorPriority = true))
            assertEquals(true, region.canTrack(center, 310f, editorPriority = false))
            assertEquals(true, region.canTrack(center, 318f, editorPriority = true))
            assertEquals(true, region.canTrack(center, 326f, editorPriority = true))
            assertEquals(false, region.canTrack(center, 327f, editorPriority = false))
            assertEquals(false, region.canTrack(center + 80f, 310f, editorPriority = false))
            assertEquals(false, region.canTrack(center, 299f, editorPriority = false))
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

    @Test fun sharedGripClaimsOnlyDecisiveVerticalMotionBeforeLongPress() {
        val threshold = 8f
        assertEquals(ToolbarSharedDecision.WAIT, toolbarSharedDecision(0f, 0f, threshold, 40L, 500L))
        assertEquals(ToolbarSharedDecision.WAIT, toolbarSharedDecision(3f, -7f, threshold, 40L, 500L))
        assertEquals(ToolbarSharedDecision.VERTICAL, toolbarSharedDecision(2f, -8f, threshold, 40L, 500L))
        assertEquals(ToolbarSharedDecision.VERTICAL, toolbarSharedDecision(2f, 8f, threshold, 40L, 500L))
        assertEquals(ToolbarSharedDecision.HORIZONTAL, toolbarSharedDecision(8f, 2f, threshold, 40L, 500L))
        assertEquals(ToolbarSharedDecision.HORIZONTAL, toolbarSharedDecision(8f, -8f, threshold, 40L, 500L))
        assertEquals(ToolbarSharedDecision.LONG_PRESS, toolbarSharedDecision(0f, -20f, threshold, 500L, 500L))
        assertEquals(ToolbarSharedDecision.INVALID, toolbarSharedDecision(Float.NaN, 0f, threshold, 40L, 500L))
    }

    @Test fun sharedGripIsCenteredAndExtendsOnlyBelowDedicatedGrip() {
        for (width in listOf(1440f, 3248f)) {
            val center = width / 2f
            assertEquals(true, toolbarSharedContains(center, 31.5f, width, 560f, 31.5f, 70f))
            assertEquals(true, toolbarSharedContains(center, 89.4f, width, 560f, 31.5f, 70f))
            assertEquals(false, toolbarSharedContains(center, 31.4f, width, 560f, 31.5f, 70f))
            assertEquals(false, toolbarSharedContains(center, 101.5f, width, 560f, 31.5f, 70f))
            assertEquals(false, toolbarSharedContains(center + 280f, 60f, width, 560f, 31.5f, 70f))
        }
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
