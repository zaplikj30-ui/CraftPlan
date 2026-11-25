package cz.craftplan.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun DiagnosticsRecordingEffects(state: PhotoEditorState, window: Window) {
    if (!state.diagnosticsRecording) return
    val diagnostics = state.diagnostics
    val context = LocalContext.current
    DisposableEffect(window, diagnostics) {
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, dropped ->
            diagnostics.frame(
                frame.getMetric(FrameMetrics.TOTAL_DURATION),
                frame.getMetric(FrameMetrics.DEADLINE),
                dropped,
                frame.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
            )
        }
        val registered = try {
            window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
        diagnostics.frameListener(registered)
        onDispose { if (registered) window.removeOnFrameMetricsAvailableListener(listener) }
    }
    LaunchedEffect(diagnostics) {
        while (true) {
            if (diagnostics.expired()) {
                state.stopDiagnostics()
                break
            }
            delay(250)
        }
    }
    LaunchedEffect(diagnostics, context) {
        while (true) {
            val pssKb = withContext(Dispatchers.Default) { Debug.getPss().takeIf { it > 0 } }
            val runtime = Runtime.getRuntime()
            diagnostics.sample(
                state.scene.dimensions.size, state.showNotes, state.lastDrawnObjects, state.visibleDrawCandidates,
                runtime.totalMemory() - runtime.freeMemory(), pssKb, context.display.refreshRate,
            )
            delay(2_000)
        }
    }
}

@Composable
internal fun DiagnosticsOverlay(state: PhotoEditorState) {
    if (!state.diagnosticsRecording) return
    val diagnostics = state.diagnostics
    var seconds by remember { mutableLongStateOf(diagnostics.elapsedMs() / 1_000) }
    LaunchedEffect(diagnostics) {
        while (true) {
            seconds = diagnostics.elapsedMs() / 1_000
            delay(250)
        }
    }
    Surface(Modifier.padding(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Text(stringResource(R.string.diagnostic_timer, seconds), Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
internal fun rememberDiagnosticExport(state: PhotoEditorState): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val data = state.pendingDiagnosticExport
        state.pendingDiagnosticExport = null
        if (uri != null && data != null) {
            val appContext = context.applicationContext
            state.writeDiagnosticExport(appContext.contentResolver, uri, data) { saved ->
                val message = if (saved) R.string.diagnostic_export_done else R.string.diagnostic_export_error
                android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    return {
        state.pendingDiagnosticExport = state.diagnostics.export(
            BuildConfig.VERSION_NAME, BuildConfig.M1_BUILD_NUMBER.takeIf { it > 0 },
            "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.SDK_INT,
        )
        try {
            launcher.launch("craftplan-diagnostics.txt")
        } catch (_: ActivityNotFoundException) {
            state.pendingDiagnosticExport = null
            android.widget.Toast.makeText(context, R.string.diagnostic_export_error, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
internal fun DiagnosticActions(state: PhotoEditorState, export: () -> Unit, closeMenu: () -> Unit) {
    val context = LocalContext.current
    val diagnostics = state.diagnostics
    val build = BuildConfig.M1_BUILD_NUMBER.takeIf { it > 0 }
    val device = "${Build.MANUFACTURER} ${Build.MODEL}"
    DropdownMenuItem(
        text = { Text(stringResource(if (state.diagnosticsEnabled) R.string.diagnostic_disable else R.string.diagnostic_enable)) },
        onClick = { state.toggleDiagnostics(); closeMenu() },
    )
    DropdownMenuItem(
        text = {
            Text(stringResource(if (state.diagnosticsColdStartArmed) R.string.diagnostic_disarm_cold_start
                else R.string.diagnostic_arm_cold_start))
        },
        onClick = {
            val changed = state.toggleColdStartDiagnostics()
            val message = if (!changed) R.string.diagnostic_cold_start_error
                else if (state.diagnosticsColdStartArmed) R.string.diagnostic_cold_start_armed
                else R.string.diagnostic_cold_start_disarmed
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            closeMenu()
        },
    )
    if (state.diagnosticsRecording) {
        DropdownMenuItem(
            text = { Text(stringResource(if (diagnostics.isolateInkCheck) R.string.diagnostic_ink_isolation_off
                else R.string.diagnostic_ink_isolation_on)) },
            onClick = {
                diagnostics.toggleInkIsolation()
                if (diagnostics.isolateInkCheck) android.widget.Toast.makeText(context,
                    R.string.diagnostic_ink_isolation_warning, android.widget.Toast.LENGTH_LONG).show()
                closeMenu()
            },
        )
    }
    if (state.diagnosticsEnabled) {
        DropdownMenuItem(
            text = { Text(stringResource(if (state.diagnosticsRecording) R.string.diagnostic_stop else R.string.diagnostic_start)) },
            onClick = {
                if (state.diagnosticsRecording) state.stopDiagnostics() else state.startDiagnostics()
                closeMenu()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.diagnostic_reset)) },
            onClick = { state.resetDiagnostics(); closeMenu() },
        )
    }
    if (diagnostics.hasSession) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.diagnostic_copy)) },
            enabled = !state.diagnosticsRecording,
            onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                    ClipData.newPlainText("CraftPlan diagnostics", diagnostics.summary(BuildConfig.VERSION_NAME, build, device, Build.VERSION.SDK_INT)),
                )
                closeMenu()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.diagnostic_export)) },
            enabled = !state.diagnosticsRecording,
            onClick = {
                export()
                closeMenu()
            },
        )
    }
}
