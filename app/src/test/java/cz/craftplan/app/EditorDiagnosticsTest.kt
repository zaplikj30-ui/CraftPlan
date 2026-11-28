package cz.craftplan.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDiagnosticsTest {
    @Test
    fun photoListDiagnosticsRecordOnlyTimingAndCountsWhileRecording() {
        var now = 0L
        val recorder = EditorDiagnostics(now = { now })
        recorder.photoListStage(PhotoListDiagnosticStage.REFRESH_FAILED)
        recorder.start()
        recorder.photoListStage(PhotoListDiagnosticStage.HOME_LAYOUT)
        now += 100_000_000
        recorder.photoListStage(PhotoListDiagnosticStage.RECOVERY_START)
        now += 700_000_000
        recorder.photoListStage(PhotoListDiagnosticStage.RECOVERY_END, 700_000_000, 3)
        now += 10_000_000
        recorder.photoListStage(PhotoListDiagnosticStage.LIST_END, 10_000_000, 30, 4)
        recorder.stop()
        recorder.photoListStage(PhotoListDiagnosticStage.REFRESH_CANCELED)
        val report = recorder.export("local", null, "test", 36)
        assertEquals(4, report.lineSequence().count { it.contains("\tphoto_list ") })
        assertTrue(report.contains("800\tphoto_list RECOVERY_END elapsed_ns=700000000 count=3 pending=NA"))
        assertTrue(report.contains("810\tphoto_list LIST_END elapsed_ns=10000000 count=30 pending=4"))
        assertFalse(report.contains("\tphoto_list REFRESH_FAILED"))
        assertFalse(report.contains("\tphoto_list REFRESH_CANCELED"))
    }

    @Test
    fun externalPickerPreservesOnlyAnActiveBoundedRecording() {
        var now = 0L
        val recorder = EditorDiagnostics(now = { now }, durationLimitNs = 100L)
        recorder.beginExternalImportPicker()
        assertFalse(recorder.keepRecordingForImportPicker)
        recorder.start()
        assertFalse(recorder.keepRecordingForImportPicker)
        recorder.beginExternalImportPicker()
        assertTrue(recorder.keepRecordingForImportPicker)
        recorder.finishExternalImportPicker()
        assertFalse(recorder.keepRecordingForImportPicker)
        assertTrue(recorder.recording)
        recorder.beginExternalImportPicker()
        recorder.stop()
        assertFalse(recorder.keepRecordingForImportPicker)
        recorder.start()
        recorder.beginExternalImportPicker()
        recorder.start()
        assertFalse(recorder.keepRecordingForImportPicker)
        recorder.beginExternalImportPicker()
        now = 100L
        assertFalse(recorder.keepRecordingForImportPicker)
        assertTrue(recorder.expired())
        recorder.stop()
        recorder.start()
        assertFalse(recorder.keepRecordingForImportPicker)
    }

    @Test
    fun importTimingIsLimitedToCurrentSessionAndRecordsCompletedAndFailedPhases() {
        var now = 0L
        val recorder = EditorDiagnostics(now = { now })
        assertEquals(null, recorder.beginPhotoImportTiming())
        recorder.start()
        val old = recorder.beginPhotoImportTiming()
        recorder.start()
        recorder.finishPhotoImportTiming(old, true)
        val timing = recorder.beginPhotoImportTiming()
        assertEquals(7, timing.measure(PhotoImportDiagnosticStage.STAGE_STREAM) { now += 20_000_000; 7 })
        timing.measure(PhotoImportDiagnosticStage.CAPACITY) {
            timing.measure(PhotoImportDiagnosticStage.CAPACITY_SOURCE_SIZE) { now += 3_000_000 }
            timing.measure(PhotoImportDiagnosticStage.CAPACITY_VOLUME) { now += 2_000_000 }
            timing.measure(PhotoImportDiagnosticStage.CAPACITY_ALLOCATABLE) { now += 15_000_000 }
        }
        try {
            timing.measure(PhotoImportDiagnosticStage.DECODE) {
                now += 30_000_000
                throw IllegalStateException("private marker")
            }
        } catch (_: IllegalStateException) { }
        recorder.finishPhotoImportTiming(timing, false)
        recorder.stop()
        recorder.finishPhotoImportTiming(timing, true)
        val report = recorder.export("local", null, "test", 36)
        assertEquals(8, report.lineSequence().count { it.contains("\tphoto_import ") })
        assertTrue(report.contains("CAPACITY_SOURCE_SIZE elapsed_ns=3000000 completed=true"))
        assertTrue(report.contains("CAPACITY_VOLUME elapsed_ns=2000000 completed=true"))
        assertTrue(report.contains("CAPACITY_ALLOCATABLE elapsed_ns=15000000 completed=true"))
        assertTrue(report.contains("CAPACITY elapsed_ns=20000000 completed=true"))
        assertTrue(report.contains("STAGE_STREAM elapsed_ns=20000000 completed=true"))
        assertTrue(report.contains("DECODE elapsed_ns=30000000 completed=false"))
        assertTrue(report.contains("END elapsed_ns=70000000 completed=false"))
        assertFalse(report.contains("private marker"))
    }

    @Test
    fun nativeImeOpenRecordsActualInsetsOnlyDuringRecording() {
        val recorder = EditorDiagnostics(now = { 0L })
        recorder.nativeImeOpen(DimensionField.VALUE, 1, true, false, false, 0, null, null)
        recorder.start()
        recorder.nativeImeOpen(DimensionField.VALUE, 1, true, true, true, 0, true, 0)
        recorder.nativeImeOpen(DimensionField.NOTE, 2, false, false, false, 0, null, null)
        recorder.stop()
        recorder.nativeImeOpen(DimensionField.VALUE, 2, false, true, true, 300, true, 300)
        val report = recorder.export("local", null, "test", 36)
        assertEquals(2, report.lineSequence().count { it.contains("\tnative_ime_open ") })
        assertTrue(report.contains("field=VALUE attempt=1 request=INSETS acknowledged=true compose_visible=true compose_bottom_px=0 native_visible=true native_bottom_px=0"))
        assertTrue(report.contains("field=NOTE attempt=2 request=COMPOSE acknowledged=false compose_visible=false compose_bottom_px=0 native_visible=NA native_bottom_px=NA"))
        assertFalse(report.contains("native_bottom_px=300"))
    }

    @Test
    fun calculatorDiagnosticsShowDecisionsAndPossibleReplacementWithoutText() {
        val recorder = EditorDiagnostics(now = { 0L })
        fun input(previous: String, next: String, decision: CalculationInputDecision,
            source: CalculationInputSource = CalculationInputSource.FIELD) {
            recorder.calculationInput(DimensionField.NOTE, source, true, true, decision,
                previous, next, false, false, true, true)
        }
        input("private 987654", "private 987654=", CalculationInputDecision.CALCULATE)
        recorder.start()
        input("private 987654", "private 987654=", CalculationInputDecision.CALCULATE)
        recorder.calculationResult(DimensionField.NOTE, CalculationInputSource.FIELD, true, true, 24, true)
        input("private 987654= 246810", "private 987654=", CalculationInputDecision.EDIT)
        input("secret 123", "secret 123＝ ", CalculationInputDecision.EDIT)
        input("secret 123", "secret 123= ", CalculationInputDecision.CALCULATE, CalculationInputSource.KEYPAD)
        recorder.calculationResult(DimensionField.VALUE, CalculationInputSource.KEYPAD, false, false, null, null)
        recorder.stop()
        input("secret 123", "secret 123=", CalculationInputDecision.CALCULATE)
        val report = recorder.export("local", null, "test", 36)
        assertEquals(4, report.lineSequence().count { it.contains("\tcalculation_input ") })
        assertEquals(2, report.lineSequence().count { it.contains("\tcalculation_result ") })
        assertTrue(report.contains("field=NOTE source=FIELD system_input=true enabled=true decision=CALCULATE"))
        assertTrue(report.contains("before_equals=0 after_equals=1 before_tail=DIGIT after_tail=ASCII_EQUALS"))
        assertTrue(report.contains("next_is_shorter_prefix=true"))
        assertTrue(report.contains("after_tail=FULLWIDTH_EQUALS trailing_ascii_spaces=1"))
        assertTrue(report.contains("success=true draft_matched_previous=true result_length=24 field_matches_result=true"))
        assertTrue(report.contains("success=false draft_matched_previous=false result_length=NA field_matches_result=NA"))
        assertFalse(report.contains("private"))
        assertFalse(report.contains("987654"))
        assertFalse(report.contains("246810"))
        assertFalse(report.contains("secret 123"))
    }

    @Test
    fun inkShapeAndCandidateDiagnosticsAreBoundedAndDoNotExportText() {
        val recorder = EditorDiagnostics(now = { 0L })
        assertEquals(null, recorder.inkCandidateObserver(DimensionField.VALUE, 1, 20, "secret"))
        recorder.start()
        recorder.penShape(DimensionField.VALUE, PenContactKind.INK,
            listOf(PenPoint(10f, 20f, 10), PenPoint(12f, 26f, 15), PenPoint(6f, 30f, 20)), 2f)
        val observer = requireNotNull(recorder.inkCandidateObserver(DimensionField.VALUE, 1, 20, "private123l"))
        observer(listOf("l", " /", " ,", "secret customer 98765", "1", "L"))
        val report = recorder.export("local", null, "test", 36)
        assertTrue(report.contains("width_dp=3.0 height_dp=5.0 dx_dp=-2.0 dy_dp=5.0"))
        assertTrue(report.contains("context_length=11 context_last=LOWER_L context_last_nonspace=LOWER_L"))
        assertTrue(report.contains("count=6 top=LOWER_L ranks=COMMA:3,SLASH:2,LOWER_L:1,UPPER_L:6,ONE:5"))
        assertFalse(report.contains("secret customer"))
        assertFalse(report.contains("private123"))
        val spaced = requireNotNull(recorder.inkCandidateObserver(DimensionField.NOTE, 1, 21, "l "))
        spaced(listOf(","))
        assertTrue(recorder.export("local", null, "test", 36).contains("context_last=SPACE_OR_EMPTY context_last_nonspace=LOWER_L"))
        recorder.stop()
        recorder.start()
        observer(listOf(","))
        assertFalse(recorder.export("local", null, "test", 36).contains("ink_candidates field="))
    }


    @Test
    fun penDiagnosticsRecordWindowAndClassificationOnlyWithinSessionAndSampleMoves() {
        var now = 0L
        val recorder = EditorDiagnostics(now = { now })
        recorder.penWindow(PenWindowEvent.DOWN, 2, 1, 900, 900, false, 0)
        recorder.start()
        recorder.penWindow(PenWindowEvent.HOVER_ENTER, 2, 1, 1000, 1000, false, 0)
        recorder.penWindow(PenWindowEvent.HOVER_MOVE, 2, 1, 1001, 1000, false, 0)
        recorder.penWindow(PenWindowEvent.HOVER_MOVE, 2, 1, 1002, 1000, false, 0)
        now += 120_000_000
        recorder.penWindow(PenWindowEvent.HOVER_MOVE, 2, 1, 1120, 1000, false, 0)
        recorder.penWindow(PenWindowEvent.DOWN, 2, 1, 1121, 1121, false, 0)
        recorder.penGesture(PenDiagnosticStage.FIELD_RELEASE, DimensionField.VALUE,
            eventMs = 1161, kind = PenContactKind.TAP, durationMs = 40,
            displacementDp = 0.5f, pathDp = 0.6f, rawAction = 1)
        recorder.penGesture(PenDiagnosticStage.DOUBLE_TAP, DimensionField.VALUE, selection = true)
        recorder.stop()
        recorder.penGesture(PenDiagnosticStage.HOLD)
        val report = recorder.export("local", null, "test", 36)
        assertEquals(2, report.lineSequence().count { it.contains("pen_window HOVER_MOVE") })
        assertTrue(report.contains("pen_window DOWN tool=2 pointers=1 event_uptime_ms=1121"))
        assertFalse(report.contains("event_uptime_ms=900"))
        assertTrue(report.contains("pen_gesture FIELD_RELEASE field=VALUE"))
        assertTrue(report.contains("kind=TAP duration_ms=40 displacement_dp=0.5 path_dp=0.6 raw_action=1"))
        assertTrue(report.contains("pen_gesture DOUBLE_TAP field=VALUE"))
        assertFalse(report.contains("pen_gesture HOLD"))
    }

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
    fun coldStartRequestIsConsumedOnlyOnceAndRequiresSuccessfulClear() {
        var armed = true
        var clears = 0
        fun consume() = consumeColdStartDiagnosticRequest(armed) {
            clears++
            armed = false
            true
        }
        assertTrue(consume())
        assertFalse(consume())
        assertEquals(1, clears)
        assertFalse(consumeColdStartDiagnosticRequest(true) { false })
    }

    @Test
    fun coldStartMarkerIsRecordedOnlyDuringSession() {
        val recorder = EditorDiagnostics(now = { 0L })
        recorder.coldStart()
        assertFalse(recorder.hasSession)
        recorder.start()
        recorder.coldStart()
        recorder.stop()
        recorder.coldStart()
        val report = recorder.export("local", null, "test", 36)
        assertEquals(1, report.lineSequence().count { it.contains("cold_start activity_on_create_before_content") })
    }

    @Test
    fun keypadFramesRemainSeparateAndIgnorePreRecordingFrames() {
        var now = 100_000_000L
        val recorder = EditorDiagnostics(now = { now })
        recorder.keypadStage(KeypadDiagnosticStage.SETTINGS_START)
        recorder.start()
        recorder.keypadStage(KeypadDiagnosticStage.REVEAL_REQUEST, 1)
        now = 140_000_000L
        recorder.keypadFrame(35_000_000, 16_000_000, 8_000_000, 4_000_000,
            2_000_000, 3_000_000, 18_000_000, 90_000_000, 2, true)
        recorder.keypadFrame(35_000_000, 16_000_000, 8_000_000, 4_000_000,
            2_000_000, 3_000_000, 18_000_000, 105_000_000, 1, true,
            inputNs = 11, animationNs = 22, swapNs = 33, gpuNs = 44, actualVsyncNs = 113_000_000)
        recorder.keypadHeight(120)
        recorder.stop()
        recorder.keypadStage(KeypadDiagnosticStage.ANIMATION_END)
        val report = recorder.export("local", null, "test", 36)
        assertTrue(report.contains("keypad_stage REVEAL_REQUEST detail=1"))
        assertTrue(report.contains("keypad_frame vsync_ms=5 total_ns=35000000 deadline_ns=16000000"))
        assertTrue(report.contains("dropped_callbacks=1 first_draw=true"))
        assertTrue(report.contains("input_ns=11 animation_ns=22 swap_ns=33 gpu_ns=44 vsync_delay_ns=8000000"))
        assertTrue(report.contains("keypad_height px=120"))
        assertTrue(report.contains("frames=0 frame_callbacks_lost=0"))
        assertFalse(report.contains("dropped_callbacks=2"))
        assertFalse(report.contains("keypad_stage SETTINGS_START"))
        assertFalse(report.contains("keypad_stage ANIMATION_END"))
    }

    @Test
    fun inkIsolationIsExplicitAndLimitedToRecording() {
        val recorder = EditorDiagnostics(now = { 0L })
        recorder.toggleInkIsolation()
        assertFalse(recorder.isolateInkCheck)
        recorder.start()
        assertFalse(recorder.isolateInkCheck)
        recorder.toggleInkIsolation()
        assertTrue(recorder.isolateInkCheck)
        recorder.toggleInkIsolation()
        assertFalse(recorder.isolateInkCheck)
        recorder.toggleInkIsolation()
        recorder.stop()
        assertFalse(recorder.isolateInkCheck)
        assertTrue(recorder.export("local", null, "test", 36).contains("keypad_isolation defer_model_check=true"))
        recorder.start()
        assertFalse(recorder.isolateInkCheck)
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
