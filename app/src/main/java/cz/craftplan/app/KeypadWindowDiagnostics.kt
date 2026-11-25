package cz.craftplan.app

import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Only installed for an explicitly started synthetic diagnostics recording. */
@Composable
internal fun KeypadWindowDiagnostics(diagnostics: EditorDiagnostics?) {
    if (diagnostics == null) return
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(window, diagnostics) {
        // Observe only: use the existing callback and return exactly its result.
        val original = window?.callback
        val observer = original?.let { PenWindowObserver(it, diagnostics) }
        if (window != null && observer != null) window.callback = observer
        diagnostics.penGesture(if (observer != null) PenDiagnosticStage.WINDOW_ATTACHED
            else PenDiagnosticStage.WINDOW_UNAVAILABLE)
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, dropped ->
            diagnostics.keypadFrame(
                totalNs = frame.getMetric(FrameMetrics.TOTAL_DURATION),
                deadlineNs = frame.getMetric(FrameMetrics.DEADLINE),
                layoutNs = frame.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                drawNs = frame.getMetric(FrameMetrics.DRAW_DURATION),
                syncNs = frame.getMetric(FrameMetrics.SYNC_DURATION),
                issueNs = frame.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
                unknownNs = frame.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION),
                intendedVsyncNs = frame.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                droppedCallbacks = dropped,
                firstDraw = frame.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L,
                inputNs = frame.getMetric(FrameMetrics.INPUT_HANDLING_DURATION),
                animationNs = frame.getMetric(FrameMetrics.ANIMATION_DURATION),
                swapNs = frame.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION),
                gpuNs = frame.getMetric(FrameMetrics.GPU_DURATION),
                actualVsyncNs = frame.getMetric(FrameMetrics.VSYNC_TIMESTAMP),
            )
        }
        val registered = if (window == null) false else try {
            window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
        diagnostics.keypadStage(if (registered) KeypadDiagnosticStage.WINDOW_ATTACHED else KeypadDiagnosticStage.WINDOW_UNAVAILABLE)
        onDispose {
            if (window != null && observer != null && window.callback === observer) {
                window.callback = original
                diagnostics.penGesture(PenDiagnosticStage.WINDOW_DETACHED)
            }
            if (registered) {
                window?.removeOnFrameMetricsAvailableListener(listener)
                diagnostics.keypadStage(KeypadDiagnosticStage.WINDOW_DETACHED)
            }
        }
    }
}

/** Window-level input evidence, before Compose consumption. No behavior change. */
private class PenWindowObserver(
    private val original: Window.Callback,
    private val diagnostics: EditorDiagnostics,
) : Window.Callback by original {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        observe(event)
        return original.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        observe(event)
        return original.dispatchGenericMotionEvent(event)
    }

    private fun observe(event: MotionEvent) {
        if (!diagnostics.recording) return
        val kind = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> PenWindowEvent.DOWN
            MotionEvent.ACTION_MOVE -> PenWindowEvent.MOVE
            MotionEvent.ACTION_UP -> PenWindowEvent.UP
            MotionEvent.ACTION_CANCEL -> PenWindowEvent.CANCEL
            MotionEvent.ACTION_POINTER_DOWN -> PenWindowEvent.POINTER_DOWN
            MotionEvent.ACTION_POINTER_UP -> PenWindowEvent.POINTER_UP
            MotionEvent.ACTION_HOVER_ENTER -> PenWindowEvent.HOVER_ENTER
            MotionEvent.ACTION_HOVER_MOVE -> PenWindowEvent.HOVER_MOVE
            MotionEvent.ACTION_HOVER_EXIT -> PenWindowEvent.HOVER_EXIT
            else -> return
        }
        val index = event.actionIndex
        val tool = if (index in 0 until event.pointerCount) event.getToolType(index) else -1
        diagnostics.penWindow(kind, tool, event.pointerCount, event.eventTime, event.downTime,
            event.flags and MotionEvent.FLAG_CANCELED != 0, event.historySize)
    }
}
