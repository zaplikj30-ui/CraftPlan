package cz.craftplan.app

import java.util.UUID

internal enum class DiagnosticAction {
    DRAWING, PAN, PINCH_ATTEMPT, PINCH, TAP, SELECT, EDIT_START, EDIT_END, EDIT_BODY, EDIT_LABEL, EDIT_SPAN, OPEN_VALUE,
    COMMIT_VALUE, CANCEL_VALUE, NOTES, LABEL_LOCK, FIT, TOOL, DELETE, UNDO, REDO, GENERATE,
    CLEAR_BATCH, TOOLBAR, ORIENTATION, SCENE, PREPARATION, DRAW, HIT_TEST, FRAME,
}
internal enum class DiagnosticResult { START, APPLIED, UNCHANGED, MISSED, REJECTED, CANCELED }
internal enum class CalculationInputSource { FIELD, KEYPAD, INK }
internal enum class CalculationInputDecision { CALCULATE, EDIT, UNCHANGED, INK_BLOCKED, LIMIT_BLOCKED }
internal enum class DiagnosticTouch { DOWN, MOVE, UP, POINTER_DOWN, POINTER_UP, CANCEL }
internal enum class PenWindowEvent { DOWN, MOVE, UP, CANCEL, POINTER_DOWN, POINTER_UP, HOVER_ENTER, HOVER_MOVE, HOVER_EXIT }
internal enum class PenDiagnosticStage {
    WINDOW_ATTACHED, WINDOW_UNAVAILABLE, WINDOW_DETACHED,
    ROOT_DOWN, ROOT_ROUTE, ROOT_RELEASE, ROOT_ABORT,
    FIELD_DOWN, FIELD_ALREADY_OWNED, FIELD_RELEASE, FIELD_ABORT,
    TAP, DOUBLE_TAP, HOLD, OPEN_FIELD, OPEN_BLOCKED, LOCAL_COMMA,
}
internal const val COLD_START_DIAGNOSTICS_KEY = "diagnostics_next_cold_start"

// Clear before recording so an Activity recreation cannot re-arm a one-shot request.
internal fun consumeColdStartDiagnosticRequest(armed: Boolean, clear: () -> Boolean): Boolean =
    armed && clear()

internal enum class KeypadDiagnosticStage {
    WARMUP_START, WARMUP_END, WARMUP_FAILED, WARMUP_MODEL_CHECK_START, WARMUP_MODEL_CHECK_END,
    SETTINGS_START, SETTINGS_END, MODEL_CHECK_START, MODEL_CHECK_END,
    MODEL_CHECK_DEFERRED, MODEL_CHECK_RELEASED,
    MODEL_TASK_START, MODEL_TASK_CREATED, MODEL_TASK_JOINED, WINDOW_ATTACHED, WINDOW_UNAVAILABLE, WINDOW_DETACHED,
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
    private var sessionIdentity = Any()
    private var ended: Long? = null
    private var nextMove = Long.MIN_VALUE
    private var nextPenHover = Long.MIN_VALUE
    private var nextPenMove = Long.MIN_VALUE
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
    private var isolateInk = false
    val isolateInkCheck: Boolean get() = recording && isolateInk

    fun toggleInkIsolation() {
        val time = recordingTime() ?: return
        isolateInk = !isolateInk
        append(time, "keypad_isolation defer_model_check=$isolateInk")
    }
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
        sessionIdentity = Any()
        started = null
        ended = null
        events.clear()
        isolateInk = false
        targetNumbers.clear()
        metrics.reset()
        nextMove = Long.MIN_VALUE
        nextPenHover = Long.MIN_VALUE
        nextPenMove = Long.MIN_VALUE
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

    // Only structural metadata, never the entered text, operands or results.
    fun calculationInput(
        field: DimensionField, source: CalculationInputSource, systemInput: Boolean,
        enabled: Boolean, decision: CalculationInputDecision,
        previous: String, next: String, selectionCollapsed: Boolean,
        caretAtEnd: Boolean, composingBefore: Boolean, composingAfter: Boolean,
    ) {
        val time = recordingTime() ?: return
        val before = previous.trimEnd(' ')
        val after = next.trimEnd(' ')
        append(time, "calculation_input field=${field.name} source=${source.name} system_input=$systemInput" +
            " enabled=$enabled decision=${decision.name} changed=${previous != next}" +
            " before_length=${previous.length} after_length=${next.length}" +
            " before_equals=${previous.count { it == '=' }} after_equals=${next.count { it == '=' }}" +
            " before_tail=${diagnosticCalculationTail(before)} after_tail=${diagnosticCalculationTail(after)}" +
            " trailing_ascii_spaces=${next.length - after.length}" +
            " trailing_other_space=${after.lastOrNull()?.isWhitespace() == true}" +
            " next_is_shorter_prefix=${next.length < previous.length && previous.startsWith(next)}" +
            " selection_collapsed=$selectionCollapsed caret_at_end=$caretAtEnd" +
            " composing_before=$composingBefore composing_after=$composingAfter")
    }

    fun calculationResult(field: DimensionField, source: CalculationInputSource,
        success: Boolean, draftMatchedPrevious: Boolean, resultLength: Int?, fieldMatchesResult: Boolean?,
    ) {
        val time = recordingTime() ?: return
        append(time, "calculation_result field=${field.name} source=${source.name}" +
            " success=$success draft_matched_previous=$draftMatchedPrevious" +
            " result_length=${resultLength ?: "NA"} field_matches_result=${fieldMatchesResult ?: "NA"}")
    }

    // Dialog frames must never pollute the main activity's aggregate FPS.
    fun keypadStage(stage: KeypadDiagnosticStage, detail: Number? = null) {
        val time = recordingTime() ?: return
        append(time, "keypad_stage ${stage.name}" + (detail?.let { " detail=$it" } ?: ""))
    }

    // No positions, recognized text, strokes, pressure, or measurement values.
    fun penWindow(kind: PenWindowEvent, tool: Int, pointers: Int, eventMs: Long,
        downMs: Long, canceled: Boolean, history: Int,
    ) {
        val time = recordingTime() ?: return
        when (kind) {
            PenWindowEvent.HOVER_MOVE -> {
                if (time < nextPenHover) return
                nextPenHover = time + 100
            }
            PenWindowEvent.MOVE -> {
                if (time < nextPenMove) return
                nextPenMove = time + 100
            }
            PenWindowEvent.DOWN -> nextPenMove = Long.MIN_VALUE
            PenWindowEvent.HOVER_ENTER -> nextPenHover = Long.MIN_VALUE
            else -> Unit
        }
        append(time, "pen_window ${kind.name} tool=$tool pointers=$pointers event_uptime_ms=$eventMs" +
            " down_uptime_ms=$downMs canceled=$canceled history=$history")
    }

    fun penGesture(stage: PenDiagnosticStage, field: DimensionField? = null,
        eventMs: Long? = null, pending: Boolean? = null, enabled: Boolean? = null,
        ticket: Boolean? = null, kind: PenContactKind? = null, durationMs: Long? = null,
        displacementDp: Float? = null, pathDp: Float? = null, rawAction: Int? = null,
        consumed: Boolean? = null, selection: Boolean? = null, continuing: Boolean? = null,
    ) {
        val time = recordingTime() ?: return
        append(time, "pen_gesture ${stage.name} field=${field?.name ?: "NA"}" +
            " event_uptime_ms=${eventMs ?: "NA"} pending=${pending ?: "NA"} enabled=${enabled ?: "NA"}" +
            " ticket=${ticket ?: "NA"} kind=${kind?.name ?: "NA"} duration_ms=${durationMs ?: "NA"}" +
            " displacement_dp=${displacementDp ?: "NA"} path_dp=${pathDp ?: "NA"} raw_action=${rawAction ?: "NA"}" +
            " consumed=${consumed ?: "NA"} selection=${selection ?: "NA"} continuing=${continuing ?: "NA"}")
    }

    // Only aggregate, translation-independent shape measurements, never raw points.
    fun penShape(field: DimensionField?, kind: PenContactKind, points: List<PenPoint>, density: Float) {
        val time = recordingTime() ?: return
        if (points.isEmpty() || !density.isFinite() || density <= 0f ||
            points.any { !it.x.isFinite() || !it.y.isFinite() }) return
        val first = points.first()
        val last = points.last()
        val dx = (last.x - first.x) / density
        val dy = (last.y - first.y) / density
        val chord = kotlin.math.hypot(dx.toDouble(), dy.toDouble())
        var path = 0.0
        var bend = 0.0
        var previous = first
        for (point in points) {
            path += kotlin.math.hypot((point.x - previous.x).toDouble() / density,
                (point.y - previous.y).toDouble() / density)
            if (chord > 0.0) {
                val x = (point.x - first.x).toDouble() / density
                val y = (point.y - first.y).toDouble() / density
                bend = maxOf(bend, kotlin.math.abs(dx * y - dy * x) / chord)
            }
            previous = point
        }
        val width = (points.maxOf { it.x } - points.minOf { it.x }) / density
        val height = (points.maxOf { it.y } - points.minOf { it.y }) / density
        val angle = if (chord > 0.0) Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) else null
        append(time, "pen_shape field=${field?.name ?: "NA"} kind=${kind.name}" +
            " sample_end_uptime_ms=${last.timeMs} samples=${points.size} width_dp=$width height_dp=$height" +
            " dx_dp=$dx dy_dp=$dy angle_deg=${angle ?: "NA"} chord_dp=$chord path_dp=$path" +
            " bend_dp=${if (chord > 0.0) bend else "NA"}")
    }

    // The callback is session-bound, cannot log arbitrary candidate strings,
    // and is called on the controller coroutine's existing main-thread context.
    fun inkCandidateObserver(field: DimensionField, strokeCount: Int, endMs: Long?,
        preContext: String): ((List<String>) -> Unit)? {
        if (recordingTime() == null) return null
        val session = sessionIdentity
        val contextClass = preContext.lastOrNull()?.let { diagnosticInkToken(it.toString()) } ?: "EMPTY"
        val contextLength = preContext.length
        val previousNonSpace = preContext.lastOrNull { !it.isWhitespace() }
            ?.let { diagnosticInkToken(it.toString()) } ?: "EMPTY"
        return observe@{ candidates ->
            if (sessionIdentity !== session) return@observe
            val time = recordingTime() ?: return@observe
            val tokens = candidates.map(::diagnosticInkToken)
            val ranks = listOf("COMMA", "SLASH", "LOWER_L", "UPPER_L", "ONE", "LOWER_I", "UPPER_I", "DOT")
                .joinToString(",") { token -> "$token:${tokens.indexOf(token).let { if (it < 0) "NA" else (it + 1).toString() }}" }
            append(time, "ink_candidates field=${field.name} sample_end_uptime_ms=${endMs ?: "NA"}" +
                " strokes=$strokeCount context_length=$contextLength context_last=$contextClass context_last_nonspace=$previousNonSpace" +
                " count=${tokens.size} top=${tokens.firstOrNull() ?: "NONE"} ranks=$ranks")
        }
    }

    fun keypadHeight(heightPx: Int) {
        val time = recordingTime() ?: return
        append(time, "keypad_height px=$heightPx")
    }

    fun keypadFrame(
        totalNs: Long, deadlineNs: Long, layoutNs: Long, drawNs: Long,
        syncNs: Long, issueNs: Long, unknownNs: Long, intendedVsyncNs: Long,
        droppedCallbacks: Int, firstDraw: Boolean,
        inputNs: Long = -1, animationNs: Long = -1, swapNs: Long = -1,
        gpuNs: Long = -1, actualVsyncNs: Long = -1,
    ) {
        val time = recordingTime() ?: return
        val start = checkNotNull(started)
        if (intendedVsyncNs > 0 && intendedVsyncNs < start) return
        val vsyncOffsetMs = if (intendedVsyncNs > 0) (intendedVsyncNs - start) / 1_000_000 else null
        append(time, "keypad_frame vsync_ms=${vsyncOffsetMs ?: "NA"} total_ns=$totalNs deadline_ns=$deadlineNs" +
            " layout_ns=$layoutNs draw_ns=$drawNs sync_ns=$syncNs issue_ns=$issueNs unknown_ns=$unknownNs" +
            " dropped_callbacks=${droppedCallbacks.coerceAtLeast(0)} first_draw=$firstDraw" +
            " input_ns=$inputNs animation_ns=$animationNs swap_ns=$swapNs gpu_ns=$gpuNs" +
            " vsync_delay_ns=${if (actualVsyncNs > 0 && intendedVsyncNs > 0) (actualVsyncNs - intendedVsyncNs).coerceAtLeast(0) else -1}")
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
        appendLine("keypad_isolation: opt-in only for current recording; model check waits for numeric reveal completion or native handoff; do not write with pen during comparison. input/animation/swap/gpu are Android frame metrics; vsync_delay is actual minus intended vsync, not presentation time. Metrics may overlap.")
        appendLine("pen_window: dialog Window.Callback touch/generic motion before delegation; tool=Android tool type, event/down times=Android uptime, MOVE/HOVER_MOVE sampled at 100 ms; hover or touch intercepted by OS/IME may be absent. pen_gesture: Compose route, classification, dispatch; displacement/path are aggregate dp, no coordinates or text; NA=unavailable.")
        appendLine("pen_shape: bounded sampled stroke geometry in dp; dx/dy signed, angle from right toward down, bend=max perpendicular distance to endpoint line; NA for zero chord. ink_candidates: 1-based first ranks of allowlisted single-symbol classes after trimming spaces, context length and last character/non-space class, no raw text; join by sample_end_uptime_ms (last retained point, not necessarily UP) and field, strokes is batch size.")
        appendLine("calculation_input: FIELD=editor callback (including IME, selection and clipboard), KEYPAD=custom keys, INK=recognized handwriting; system_input=system input permitted for that field. Tail is an allowlisted category after trimming ASCII spaces; lengths/counts only, no text. calculation_result records the actual draft calculation and field update; subsequent inputs may show a shorter prefix or other replacement, not proof of IME intent.")
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

/** Only a fixed category can leave the calculator text. */
private fun diagnosticCalculationTail(text: String): String = when (val last = text.lastOrNull()) {
    null -> "EMPTY"
    '=' -> "ASCII_EQUALS"
    '\uFF1D' -> "FULLWIDTH_EQUALS"
    '\uFE66' -> "SMALL_EQUALS"
    '≈', '≠', '≡', '⁼', '₌' -> "OTHER_EQUALS_LIKE"
    else -> when {
        last.isWhitespace() -> "SPACE"
        last.isDigit() -> "DIGIT"
        last.isLetter() -> "LETTER"
        else -> "OTHER"
    }
}

/** No arbitrary words, numbers or strings can enter the diagnostic report. */
internal fun diagnosticInkToken(text: String): String = when (val token = text.trim(' ')) {
    "," -> "COMMA"
    "/" -> "SLASH"
    "l" -> "LOWER_L"
    "L" -> "UPPER_L"
    "1" -> "ONE"
    "i" -> "LOWER_I"
    "I" -> "UPPER_I"
    "." -> "DOT"
    "" -> "SPACE_OR_EMPTY"
    else -> when {
        token.length != 1 -> "MULTI"
        token[0].isDigit() -> "OTHER_DIGIT"
        token[0].isLetter() -> "OTHER_LETTER"
        else -> "OTHER_SYMBOL"
    }
}
