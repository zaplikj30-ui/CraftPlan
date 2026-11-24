package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDiagnosticsTest {
    @Test
    fun reportSeparatesTouchGestureAndAnonymousResultAndStopsRecording() {
        var now = 0L
        val recorder = EditorDiagnostics(now = { now })
        val id = UUID.randomUUID()
        recorder.action(DiagnosticAction.UNDO, DiagnosticResult.APPLIED)
        recorder.start()
        now += 8_000_000
        recorder.touch(DiagnosticTouch.DOWN, EditorPointerKind.STYLUS, 12f, 13f, 1, 300, 400, false, 3)
        recorder.action(DiagnosticAction.EDIT_END, DiagnosticResult.START, id, 3.5)
        now += 50_000_000
        recorder.action(DiagnosticAction.EDIT_END, DiagnosticResult.CANCELED, id)
        recorder.stop()
        recorder.action(DiagnosticAction.REDO, DiagnosticResult.APPLIED)
        val report = recorder.export("0.1.0-test", 45, "test", 36)
        assertTrue(report.contains("build=45"))
        assertTrue(report.contains("8\ttouch DOWN STYLUS x_px=12.0 y_px=13.0"))
        assertTrue(report.contains("8\taction EDIT_END START target=1 detail=3.5"))
        assertTrue(report.contains("58\taction EDIT_END CANCELED target=1"))
        assertTrue(report.contains("canceled_actions=1"))
        assertFalse(report.contains(id.toString()))
        assertFalse(report.contains("UNDO"))
        assertFalse(report.contains("REDO"))
        assertFalse(recorder.recording)
    }

    @Test
    fun toolbarDiagnosticsSeparateWindowMissFromGripReleaseAndRespectRecording() {
        val recorder = EditorDiagnostics(now = { 0L })
        val bounds = DiagnosticGripBounds(10f, 20f, 50f, 40f)
        recorder.toolbarWindowTouch(DiagnosticTouch.DOWN, EditorPointerKind.FINGER, 25f, 25f, 1, false, bounds, 0)
        recorder.start()
        recorder.toolbarWindowTouch(DiagnosticTouch.DOWN, EditorPointerKind.FINGER, 60f, 25f, 1, false, bounds, 1)
        recorder.toolbarWindowTouch(DiagnosticTouch.DOWN, EditorPointerKind.STYLUS, 25f, 25f, 1, false, bounds, 1)
        recorder.toolbarGripStart(EditorPointerKind.STYLUS, false, 15f, 5f, 40, 20, 8f, false)
        recorder.toolbarGripEnd(ToolbarGripOutcome.UP, 1f, -20f, 20f, false, true)
        recorder.toolbarGripEnd(ToolbarGripOutcome.CONSUMED, 0f, 0f, 0f, true, true)
        recorder.stop()
        recorder.toolbarGripEnd(ToolbarGripOutcome.DOWN, 0f, 10f, 10f, true, false)
        val report = recorder.export("local", null, "test", 36)
        assertTrue(report.contains("toolbar_window DOWN FINGER x_px=60.0 y_px=25.0 pointers=1 canceled_flag=false state=1 in_grip=false"))
        assertTrue(report.contains("toolbar_window DOWN STYLUS x_px=25.0 y_px=25.0 pointers=1 canceled_flag=false state=1 in_grip=true"))
        assertTrue(report.contains("toolbar_grip START STYLUS"))
        assertTrue(report.contains("toolbar_grip END UP dx_px=1.0 dy_px=-20.0 max_travel_px=20.0 before=false after=true changed=true"))
        assertTrue(report.contains("toolbar_grip END CONSUMED"))
        assertFalse(report.contains("toolbar_grip END DOWN"))
        assertFalse(report.contains("state=0 in_grip=true"))
    }

    @Test
    fun sharedGripRecordsYieldSeparatelyFromDedicatedGrip() {
        val recorder = EditorDiagnostics(now = { 0L })
        recorder.start()
        val grip = DiagnosticGripBounds(10f, 20f, 50f, 40f)
        val shared = DiagnosticGripBounds(10f, 40f, 50f, 60f)
        recorder.toolbarWindowTouch(DiagnosticTouch.DOWN, EditorPointerKind.FINGER,
            30f, 45f, 1, false, grip, 1, shared)
        recorder.toolbarGripStart(EditorPointerKind.FINGER, false, 20f, 25f, 100, 80, 8f, false, shared = true)
        recorder.toolbarGripEnd(ToolbarGripOutcome.SHARED_TAP, 0f, 0f, 0f, false, false, shared = true)
        recorder.stop()
        val report = recorder.export("local", null, "test", 36)
        assertTrue(report.contains("in_grip=false grip_bounds_px=10.0,20.0,50.0,40.0 in_shared=true"))
        assertTrue(report.contains("toolbar_grip END SHARED_TAP dx_px=0.0 dy_px=0.0 max_travel_px=0.0 before=false after=false changed=false zone=shared"))
    }

    @Test
    fun limitsDurationCapacityAndMovementSamplingAndResets() {
        var now = 0L
        val recorder = EditorDiagnostics(now = { now }, capacity = 3, durationLimitNs = 100_000_000)
        recorder.start()
        recorder.touch(DiagnosticTouch.DOWN, EditorPointerKind.FINGER, 0f, 0f, 1, 100, 200, false, 0)
        recorder.touch(DiagnosticTouch.MOVE, EditorPointerKind.FINGER, 1f, 1f, 1, 100, 200, false, 0)
        now = 50_000_000
        recorder.touch(DiagnosticTouch.MOVE, EditorPointerKind.FINGER, 2f, 2f, 1, 100, 200, false, 0)
        now = 101_000_000
        recorder.action(DiagnosticAction.REDO, DiagnosticResult.APPLIED)
        assertTrue(recorder.expired())
        recorder.stop()
        val report = recorder.export("local", null, "test", 33)
        assertTrue(report.contains("move_samples_skipped=1 truncated_events=1"))
        assertFalse(report.contains("session START"))
        assertFalse(report.contains("REDO"))
        assertEquals(100L, recorder.elapsedMs())
        recorder.reset()
        assertFalse(recorder.hasSession)
        assertTrue(recorder.summary("local", null, "test", 33).contains("frame_mean_ms=NA"))
    }

    @Test
    fun completedFrameTimingDeadlineAndMissingCallbacksStaySeparate() {
        var now = 100_000_000L
        val recorder = EditorDiagnostics(now = { now })
        recorder.start()
        recorder.frameListener(true)
        now = 1_100_000_000L
        recorder.frame(30_000_000, 15_000_000, 0, 90_000_000)
        recorder.frame(10_000_000, 15_000_000, 0, 200_000_000)
        recorder.frame(20_000_000, 15_000_000, 2, 300_000_000)
        recorder.frame(15_000_000, -1, 0, 400_000_000)
        recorder.stop()
        val report = recorder.export("local", null, "test", 36)
        assertTrue(report.contains("frames=3 frame_callbacks_lost=2"))
        assertTrue(report.contains("frame_mean_ms=15.0 frame_max_ms=20.0"))
        assertTrue(report.contains("observed_window_fps=3.0 deadline_samples=2 deadline_misses=1"))
        assertTrue(report.contains("slow FRAME elapsed_ns=20000000"))
    }

    @Test
    fun conservativeBoundsPreserveProtrudingLabelsHandlesAndCrossingStroke() {
        assertTrue(boundsMayBeVisible(-100f, 10f, 500f, 10f, 14f, 400, 800))
        assertFalse(boundsMayBeVisible(10f, 820f, 200f, 820f, 14f, 400, 800))
        assertTrue(boundsMayBeVisible(105f, 820f, 105f, 820f, 26f, 400, 800))
        assertTrue(boundsMayBeVisible(-15f, 240f, -15f, 240f, 16f, 400, 800))
        assertFalse(boundsMayBeVisible(-17f, 240f, -17f, 240f, 16f, 400, 800))
    }
}
