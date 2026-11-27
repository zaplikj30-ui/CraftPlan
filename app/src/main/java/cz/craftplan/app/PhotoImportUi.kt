package cz.craftplan.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun PhotoImportControls(state: PhotoEditorState, onCapture: () -> Unit, onStopCameraWait: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.receivePhoto(uri)
    }
    val enabled = !state.importBusy && !state.openingPhoto
    Button(onClick = onCapture, enabled = enabled && state.captureInFlight == null) {
        Text(stringResource(R.string.camera_take_picture))
    }
    if (state.captureInFlight != null) {
        TextButton(onClick = onStopCameraWait, enabled = enabled) {
            Text(stringResource(R.string.camera_stop_waiting))
        }
    }
    Button(onClick = { picker.launch(arrayOf("image/jpeg")) }, enabled = enabled) {
        Text(stringResource(R.string.import_photo))
    }
    state.importedPhotos.captures.filterNot { it == state.captureInFlight }.forEachIndexed { index, id ->
        TextButton(onClick = { state.previewCapture(id) }, enabled = enabled) {
            Text(stringResource(R.string.camera_resume, index + 1))
        }
    }
    state.importedPhotos.pending.forEachIndexed { index, candidate ->
        TextButton(onClick = { state.previewImport(candidate) }, enabled = enabled) {
            Text(stringResource(R.string.import_resume, index + 1))
        }
    }
    state.importedPhotos.confirmed.forEachIndexed { index, photo ->
        TextButton(onClick = { state.openPhoto(photo) }, enabled = enabled) {
            Text(stringResource(R.string.import_open_confirmed, index + 1))
        }
    }
    if (state.importBusy) Text(stringResource(R.string.import_working))
    val capture = state.cameraPreviewId
    val pendingId = capture ?: state.importCandidate?.photoId
    pendingId?.let { id ->
        val discardLabel = if (capture == null) R.string.import_discard else R.string.camera_discard
        var confirmDiscard by remember(id) { mutableStateOf(false) }
        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text(stringResource(discardLabel)) },
                text = { Text(stringResource(if (capture == null) R.string.import_discard_warning else R.string.camera_discard_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscard = false
                        state.discardImport()
                    }, enabled = enabled) { Text(stringResource(discardLabel)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel_value)) }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = state::deferImport,
                title = { Text(stringResource(if (capture == null) R.string.import_preview else R.string.camera_preview)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        state.importPreview?.let {
                            if (it.sourceDamaged) Text(stringResource(R.string.import_damage_warning))
                            Image(it.bitmap.asImageBitmap(), stringResource(R.string.import_preview),
                                Modifier.fillMaxWidth().heightIn(max = 220.dp), contentScale = ContentScale.Fit)
                        }
                        if (state.importBusy) Text(stringResource(R.string.import_working))
                        state.importDiagnostic?.let {
                            if (state.importPreview == null) {
                                Text(stringResource(if (capture == null) R.string.import_failed else R.string.camera_failed))
                            }
                            Text(stringResource(R.string.document_storage_diagnostic, it))
                        }
                        if (state.captureSources.isNotEmpty()) {
                            Text(stringResource(R.string.camera_recovery_sources))
                            var stageIndex = 0
                            for (source in state.captureSources) {
                                val label = when (source) {
                                    is CaptureSource.Camera -> stringResource(R.string.camera_recovery_output)
                                    is CaptureSource.Stage -> stringResource(R.string.camera_recovery_stage, ++stageIndex)
                                }
                                TextButton(onClick = { state.previewCaptureSource(source) }, enabled = enabled) { Text(label) }
                            }
                        }
                        if (capture != null) {
                            TextButton(onClick = onCapture, enabled = enabled && state.captureInFlight == null) {
                                Text(stringResource(R.string.camera_retake))
                            }
                        }
                        if (state.captureSources.size < 2) {
                            TextButton(onClick = { confirmDiscard = true }, enabled = enabled) {
                                Text(stringResource(discardLabel))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = state::confirmImport, enabled = enabled && state.importPreview != null) {
                        val label = if (state.selectedCaptureSource != null) {
                            if (state.importPreview?.sourceDamaged == true) R.string.camera_recovery_save_damaged
                            else R.string.camera_recovery_save
                        } else if (state.importPreview?.sourceDamaged == true) {
                            R.string.import_confirm_damaged
                        } else if (capture != null) R.string.camera_confirm else R.string.import_confirm
                        Text(stringResource(label))
                    }
                },
                dismissButton = {
                    TextButton(onClick = state::deferImport, enabled = enabled) {
                        Text(stringResource(R.string.import_later))
                    }
                },
            )
        }
    } ?: state.importDiagnostic?.let {
        Text(stringResource(if (it.startsWith("CAMERA_CAPTURE /")) R.string.camera_failed else R.string.import_failed))
        Text(stringResource(R.string.document_storage_diagnostic, it))
    }
}
