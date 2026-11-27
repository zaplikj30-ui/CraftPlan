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
    val recovery = state.interruptedCaptures.filter { it.id in state.importedPhotos.captures && it.id != state.captureInFlight }
    for (offer in recovery) {
        Column {
            Text(stringResource(R.string.camera_interrupted))
            TextButton(onClick = { state.previewCapture(offer.id) }, enabled = enabled) {
                Text(stringResource(R.string.camera_show_interrupted))
            }
            if (offer.discardCandidate != null) {
                TextButton(onClick = { state.discardInterruptedCapture(offer) }, enabled = enabled) {
                    Text(stringResource(R.string.camera_discard_interrupted))
                }
            }
            TextButton(onClick = { state.deferInterruptedCapture(offer.id) }, enabled = enabled) {
                Text(stringResource(R.string.import_later))
            }
        }
    }
    state.importedPhotos.captures.filterNot { id -> id == state.captureInFlight || recovery.any { it.id == id } }
        .forEachIndexed { index, id ->
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
    val recoveryIds = (listOfNotNull(state.failedPhotoRecovery) + state.importedPhotos.confirmedWithRecoverySources).distinct()
    for (id in recoveryIds) {
        val index = state.importedPhotos.confirmed.indexOf(id)
        TextButton(onClick = { state.showConfirmedRecovery(id) }, enabled = enabled && id != state.captureInFlight) {
            val label = if (index < 0) stringResource(R.string.camera_recovery_open_sources)
                else stringResource(R.string.camera_recovery_open_indexed, index + 1)
            Text(label)
        }
    }
    if (state.importBusy) Text(stringResource(R.string.import_working))
    val capture = state.cameraPreviewId
    val pendingId = capture ?: state.importCandidate?.photoId
    pendingId?.let { id ->
        val discardSource = state.selectedCaptureDigest != null
        val discardLabel = when {
            discardSource -> R.string.camera_discard_selected
            capture == null -> R.string.import_discard
            else -> R.string.camera_discard
        }
        var confirmDiscard by remember(id, state.selectedCaptureSource) { mutableStateOf(false) }
        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text(stringResource(discardLabel)) },
                text = {
                    Text(stringResource(when {
                        discardSource -> R.string.camera_discard_selected_warning
                        capture == null -> R.string.import_discard_warning
                        else -> R.string.camera_discard_warning
                    }))
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscard = false
                        if (discardSource) state.discardSelectedCaptureSource() else state.discardImport()
                    }, enabled = enabled) { Text(stringResource(discardLabel)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel_value)) }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = state::deferImport,
                title = {
                    Text(stringResource(when {
                        state.recoveringConfirmedPhoto -> R.string.camera_recovery_open_sources
                        capture != null -> R.string.camera_preview
                        else -> R.string.import_preview
                    }))
                },
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
                        if (state.recoveringConfirmedPhoto && state.captureSources.isNotEmpty()) {
                            TextButton(onClick = state::openConfirmedRecovery, enabled = enabled) {
                                Text(stringResource(R.string.camera_recovery_open_original))
                            }
                        }
                        if (capture != null && !state.recoveringConfirmedPhoto) {
                            TextButton(onClick = onCapture, enabled = enabled && state.captureInFlight == null) {
                                Text(stringResource(R.string.camera_retake))
                            }
                        }
                        if (discardSource || (!state.recoveringConfirmedPhoto && state.captureSources.size < 2)) {
                            TextButton(onClick = { confirmDiscard = true }, enabled = enabled) {
                                Text(stringResource(discardLabel))
                            }
                        }
                    }
                },
                confirmButton = {
                    val shown = state.importPreview
                    TextButton(onClick = {
                        if (shown != null) {
                            val consent = if (shown.sourceDamaged) PhotoConfirmation.EXPLICIT_DAMAGED
                                else PhotoConfirmation.EXPLICIT
                            state.confirmImport(shown.candidate, consent)
                        }
                    }, enabled = enabled && shown != null) {
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
