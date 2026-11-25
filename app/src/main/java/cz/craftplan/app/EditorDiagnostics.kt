package cz.craftplan.app

import java.util.UUID

internal enum class DiagnosticAction {
    DRAWING, PAN, PINCH_ATTEMPT, PINCH, TAP, SELECT, EDIT_START, EDIT_END, EDIT_BODY, EDIT_LABEL, EDIT_SPAN, OPEN_VALUE,
    COMMIT_VALUE, CANCEL_VALUE, NOTES, LABEL_LOCK, FIT, TOOL, DELETE, UNDO, REDO, GENERATE,
    CLEAR_BATCH, TOOLBAR, ORIENTATION, SCENE, PREPARATION, DRAW, HIT_TEST, FRAME,
}
internal enum class DiagnosticResult { START, APPLIED, UNCHANGED, MISSED, REJECTED, CANCELED }
internal enum class DiagnosticTouch { DOWN, MOVE, UP, POINTER_DOWN, POINTER_UP, CANCEL }
internal const val COLD_START_DIAGNOSTICS_KEY = "diagnostics_next_cold_start"

// Clear before recording so an Activity recreation cannot re-arm a one-shot request.
internal fun consumeColdStartDiagnosticRequest(armed: Boolean, clear: () -> Boolean): Boolean =
    armed && clear()

internal enum class KeypadDiagnosticStage {
    WARMUP_START, WARMUP_END, WARMUP_FAILED,
    SETTINGS_START, SETTINGS_END, MODEL_CHECK_START, MODEL_CHECK_END,
    MODEL_TASK_START, MODEL_TASK_CREATED, WINDOW_ATTACHED, WINDOW_UNAVAILABLE, WINDOW_DETACHED,
    REVEAL_REQUEST, FIRST_PREPARED, ANIMATION_START, ANIMATION_END, ANIMATION_CANCELED, NATIVE_HANDOFF,
}

internal data class DiagnosticGripBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

/** Main-thread recorder for the synthetic editor. Its schema cannot carry measurement text or photos. */
internal class EditorDiagnostics(
    private val now: () -> Long = System::nanoTime,
    private val capacity: Int = 2_048,
    private val durationLimitNs: Long = 300_000_000_000L,
) {
    val metrics = EditorStressMetrics()
    private val events = ArrayDeque<Event>()
    private val targetNumbers = HashMap<UUID, Int>()
    private var started: Long? = null
    private var ended: Long? = null
    private var nextMove = Long.MIN_VALUE
    private var dropped = 0
    private var touches = 0L
    private var skippedMoves = 0L
    private var hitMisses = 0L
    private var canceledActions = 0L
    private var rejectedActions = 0L
    private var frames = 0L
    private var frameTotal = 0L
    private var frameMax = 0L
    private var knownDeadlines = 0L
    private var missedDeadlines = 0L
    private var lostCallbacks = 0L
    private var frameListenerAvailable: Boolean? = null
    private var lastScene: String? = null
    private var lastSampleMs = 0L
    private var lastSampleFrames = 0L
    private var lastSampleMisses = 0L
    private var lastSampleDeadlines = 0L

    init {
        require(capacity > 0 && durationLimitNs > 0)
    }

    val recording: Boolean get() = started != null && ended == null
    val hasSession: Boolean get() = started != null
    fun elapsedMs(): Long = started?.let { ((ended ?: now()) - it).coerceAtLeast(0) / 1_000_000 } ?: 0L

    fun expired(): Boolean = recording && now() - checkNotNull(started) >= durationLimitNs

    fun start() {
        reset()
        started = now()
        append(0, "session START")
    }

    fun stop() {
        if (!recording) return
        ended = minOf(now(), checkNotNull(started) + durationLimitNs)
        val time = elapsedMs()
        append(time, "session END")
    }

    fun reset() {
        started = null
        ended = null
        events.clear()
        targetNumbers.clear()
        metrics.reset()
        nextMove = Long.MIN_VALUE
        dropped = 0
        touches = 0
        skippedMoves = 0
        hitMisses = 0
        canceledActions = 0
        rejectedActions = 0
        frames = 0
        frameTotal = 0
        frameMax = 0
        knownDeadlines = 0
        missedDeadlines = 0
        lostCallbacks = 0
        frameListenerAvailable = null
        lastScene = null
        lastSampleMs = 0
        lastSampleFrames = 0
        lastSampleMisses = 0
        lastSampleDeadlines = 0
    }

    fun action(
        action: DiagnosticAction, result: DiagnosticResult,
        target: UUID? = null, detail: Number? = null,
    ) {
        val time = recordingTime() ?: return
        if (action == DiagnosticAction.HIT_TEST && result == DiagnosticResult.MISSED) hitMisses++
        if (result == DiagnosticResult.CANCELED) canceledActions++
        if (result == DiagnosticResult.REJECTED) rejectedActions++
        val number = target?.let {
            targetNumbers[it] ?: if (targetNumbers.size < capacity) {
                (targetNumbers.size + 1).also { number -> targetNumbers[it] = number }
            } else null
        }
        append(time, "action ${action.name} ${result.name}" +
            (number?.let { " target=$it" } ?: if (target != null) " target=unavailable" else "") +
            (detail?.let { " detail=$it" } ?: ""))
    }

    fun touch(
        kind: DiagnosticTouch, input: EditorPointerKind, x: Float, y: Float, pointers: Int,
        width: Int, height: Int, landscape: Boolean, dispatchDelayMs: Long, canceled: Boolean = false,
    ) {
        val time = recordingTime() ?: return
        touches++
        if (kind == DiagnosticTouch.MOVE && time < nextMove) {
            skippedMoves++
            return
        }
        nextMove = time + 32
        append(time, "touch ${kind.name} ${input.name} x_px=$x y_px=$y pointers=$pointers" +
            " width_px=$width height_px=$height landscape=$landscape canceled_flag=$canceled dispatch_delay_ms=$dispatchDelayMs")
    }

    // Window coordinates are separate from the AndroidView/editor-local touch stream.
    fun toolbarWindowTouch(
        kind: DiagnosticTouch, input: EditorPointerKind, x: Float, y: Float,
        pointers: Int, canceled: Boolean, bounds: DiagnosticGripBounds?, toolbarState: Int,
        sharedBounds: DiagnosticGripBounds? = null,
    ) {
        val time = recordingTime() ?: return
        append(time, "toolbar_window ${kind.name} ${input.name} x_px=$x y_px=$y pointers=$pointers" +
            " canceled_flag=$canceled state=$toolbarState in_grip=${bounds?.contains(x, y) ?: "NA"}" +
            " grip_bounds_px=${bounds?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "NA"}" +
            " in_shared=${sharedBounds?.contains(x, y) ?: "NA"}" +
            " shared_bounds_px=${sharedBounds?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "NA"}")
    }

    fun toolbarGripStart(input: EditorPointerKind, side: Boolean, x: Float, y: Float,
        width: Int, height: Int, threshold: Float, expanded: Boolean, shared: Boolean = false,
    ) {
        val time = recordingTime() ?: return
        append(time, "toolbar_grip START ${input.name} side=$side x_local_px=$x y_local_px=$y" +
            " width_px=$width height_px=$height threshold_px=$threshold expanded=$expanded zone=${if (shared) "shared" else "dedicated"}")
    }

    fun toolbarGripEnd(reason: ToolbarGripOutcome, dx: Float, dy: Float, maxTravel: Float,
        expandedBefore: Boolean, expandedAfter: Boolean, shared: Boolean = false,
    ) {
        val time = recordingTime() ?: return
        append(time, "toolbar_grip END ${reason.name} dx_px=$dx dy_px=$dy max_travel_px=$maxTravel" +
            " before=$expandedBefore after=$expandedAfter changed=${expandedBefore != expandedAfter} zone=${if (shared) "shared" else "dedicated"}")
    }

    fun frameListener(available: Boolean) {
        if (recording) frameListenerAvailable = available
    }

    fun frame(totalNs: Long, deadlineNs: Long, droppedCallbacks: Int, intendedVsyncNs: Long) {
        val time = recordingTime() ?: return
        if (intendedVsyncNs > 0 && intendedVsyncNs < checkNotNull(started)) return
        lostCallbacks += droppedCallbacks.coerceAtLeast(0)
        if (totalNs <= 0) return
        frames++
        frameTotal += totalNs
        frameMax = maxOf(frameMax, totalNs)
        if (deadlineNs > 0) {
            knownDeadlines++
            if (totalNs >= deadlineNs) {
                missedDeadlines++
                slow(time, DiagnosticAction.FRAME, totalNs)
            }
        }
    }

    fun coldStart() {
        val time = recordingTime() ?: return
        append(time, "cold_start activity_on_create_before_content")
    }

    // Dialog frames must never pollute the main activity's aggregate FPS.
    fun keypadStage(stage: KeypadDiagnosticStage, detail: Number? = null) {
        val time = recordingTime() ?: return
        append(time, "keypad_stage ${stage.name}" + (detail?.let { " detail=$it" } ?: ""))
    }

    fun keypadHeight(heightPx: Int) {
        val time = recordingTime() ?: return
        append(time, "keypad_height px=$heightPx")
    }

    fun keypadFrame(
        totalNs: Long, deadlineNs: Long, layoutNs: Long, drawNs: Long,
        syncNs: Long, issueNs: Long, unknownNs: Long, intendedVsyncNs: Long,
        droppedCallbacks: Int, firstDraw: Boolean,
    ) {
        val time = recordingTime() ?: return
        val start = checkNotNull(started)
        if (intendedVsyncNs > 0 && intendedVsyncNs < start) return
        val vsyncOffsetMs = if (intendedVsyncNs > 0) (intendedVsyncNs - start) / 1_000_000 else null
        append(time, "keypad_frame vsync_ms=${vsyncOffsetMs ?: "NA"} total_ns=$totalNs deadline_ns=$deadlineNs" +
            " layout_ns=$layoutNs draw_ns=$drawNs sync_ns=$syncNs issue_ns=$issueNs unknown_ns=$unknownNs" +
            " dropped_callbacks=${droppedCallbacks.coerceAtLeast(0)} first_draw=$firstDraw")
    }

    fun operation(action: DiagnosticAction, elapsedNs: Long) {
        if (elapsedNs < 32_000_000) return
        val time = recordingTime() ?: return
        slow(time, action, elapsedNs)
    }

    fun sample(
        objects: Int, notes: Boolean, drawn: Int?, visibleCandidates: Int?,
        heapBytes: Long, pssKb: Long?, refreshHz: Float?,
    ) {
        val time = recordingTime() ?: return
        lastScene = "objects=$objects notes_visible=$notes drawn_last_canvas=${drawn ?: "NA"}" +
            " visible_candidates_last_canvas=${visibleCandidates ?: "NA"} java_heap_bytes=$heapBytes" +
            " process_pss_kb=${pssKb ?: "NA"} refresh_hz=${refreshHz ?: "NA"}"
        val intervalMs = time - lastSampleMs
        val intervalFrames = frames - lastSampleFrames
        val intervalFps = if (intervalMs > 0 && intervalFrames > 0) intervalFrames * 1_000.0 / intervalMs else null
        val intervalMisses = (missedDeadlines - lastSampleMisses).takeIf { knownDeadlines > lastSampleDeadlines }
        append(time, "sample $lastScene interval_ms=$intervalMs interval_frames=$intervalFrames" +
            " interval_observed_fps=${intervalFps ?: "NA"} interval_deadline_misses=${intervalMisses ?: "NA"}" +
            " ${performance()}")
        lastSampleMs = time
        lastSampleFrames = frames
        lastSampleMisses = missedDeadlines
        lastSampleDeadlines = knownDeadlines
    }

    fun summary(version: String, build: Int?, device: String, sdk: Int): String = buildString {
        appendLine("CraftPlan diagnostics v1 | version=$version build=${build ?: "local"}")
        appendLine("scenario=synthetic_workbench device=$device android_api=$sdk elapsed_ms=${elapsedMs()}")
        appendLine(lastScene ?: "scene_sample=NA")
        appendLine(performance())
        appendLine("touches_received=$touches move_samples_skipped=$skippedMoves truncated_events=$dropped")
        appendLine("hit_misses=$hitMisses canceled_actions=$canceledActions rejected_actions=$rejectedActions")
    }

    fun export(version: String, build: Int?, device: String, sdk: Int): String = buildString {
        append(summary(version, build, device, sdk))
        appendLine("time=monotonic ms since Start; touch coordinates=editor px; edit start detail=drag threshold px; edit span and release detail=px")
        appendLine("hit-test detail=elapsed ns; generate detail=start count or applied elapsed ns; scene/pinch detail=count; tool detail=0 select/1 single/2 repeat; notes, lock, landscape detail=0/1; toolbar detail=0-3 (bit 0 horizontal, bit 1 vertical)")
        appendLine("toolbar_window: activity window px, grip_bounds=left,top,right,bottom in same coordinates; in_grip/in_shared=measured bounds only; toolbar_grip: local px, dedicated/shared zone, release, yield or cancellation reason; OS-intercepted touches may be absent")
        appendLine("frames=Android FrameMetrics for the activity window (including controls/timer, excluding dialog/IME); observed FPS includes idle and excludes lost callbacks; deadline misses are not a count of lost presentations")
        appendLine("keypad_frame=separate dimension dialog window FrameMetrics, time=callback receipt, vsync_ms=intended frame start relative to recording; negative metrics unavailable; first_draw is SDK flag; excludes native IME. keypad_height=measured clipped height px; keypad_stage=stage markers, REVEAL_REQUEST detail 1=open/0=close, other timing details ns. No field contents.")
        appendLine("canvas timings=elapsed onDraw on UI thread; drawn=dimension draw commands issued, including full drag previews; visible candidates=scene dimensions or their edits inside conservative viewport bounds; process memory=PSS estimate")
        for (event in events) appendLine("${event.ms}\t${event.body}")
    }

    private fun performance(): String {
        val snap = metrics.snapshot()
        val fps = if (frames > 0 && elapsedMs() > 0) frames * 1000.0 / elapsedMs() else null
        return "canvas_max_ms=${snap.maxDrawMs.takeIf { snap.draws > 0 } ?: "NA"} canvas_samples=${snap.draws}" +
            " hit_max_ms=${snap.maxHitMs.takeIf { snap.hits > 0 } ?: "NA"} hit_samples=${snap.hits}" +
            " prepare_max_ms=${snap.maxPreparationMs.takeIf { it > 0 } ?: "NA"}" +
            " frame_listener=${frameListenerAvailable ?: "NA"} frames=$frames frame_callbacks_lost=$lostCallbacks" +
            " frame_mean_ms=${if (frames > 0) frameTotal / frames / 1_000_000.0 else "NA"}" +
            " frame_max_ms=${frameMax.takeIf { frames > 0 }?.div(1_000_000.0) ?: "NA"}" +
            " observed_window_fps=${fps ?: "NA"} deadline_samples=$knownDeadlines deadline_misses=${missedDeadlines.takeIf { knownDeadlines > 0 } ?: "NA"}"
    }

    private fun recordingTime(): Long? {
        if (!recording) return null
        val elapsed = now() - checkNotNull(started)
        if (elapsed >= durationLimitNs) return null
        return elapsed.coerceAtLeast(0) / 1_000_000
    }

    private fun slow(time: Long, action: DiagnosticAction, elapsedNs: Long) {
        append(time, "slow ${action.name} elapsed_ns=$elapsedNs")
    }

    private fun append(ms: Long, body: String) {
        if (events.size == capacity) {
            events.removeFirst()
            dropped++
        }
        events.addLast(Event(ms, body))
    }

    private data class Event(val ms: Long, val body: String)
}
