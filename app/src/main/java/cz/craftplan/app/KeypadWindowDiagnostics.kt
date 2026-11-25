package cz.craftplan.app

import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
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
            if (registered) {
                window?.removeOnFrameMetricsAvailableListener(listener)
                diagnostics.keypadStage(KeypadDiagnosticStage.WINDOW_DETACHED)
            }
        }
    }
}
