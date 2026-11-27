package cz.craftplan.app

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun CameraPreviewSetting(state: PhotoEditorState, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().toggleable(
            value = state.cameraPreviewEnabled,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = state::updateCameraPreviewEnabled,
        ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.camera_preview_setting), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (state.cameraPreviewEnabled) R.string.camera_preview_both else R.string.camera_preview_system_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.cameraPreviewEnabled, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun PhotoImportControls(state: PhotoEditorState, onCapture: () -> Unit, onStopCameraWait: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.receivePhoto(uri)
    }
    val enabled = !state.importBusy && !state.openingPhoto
    Button(onClick = onCapture, enabled = enabled && state.captureInFlight == null) {
        Text(stringResource(R.string.camera_take_picture))
    }
    CameraPreviewSetting(state, enabled && state.captureInFlight == null)
    if (state.captureInFlight != null) {
        TextButton(onClick = onStopCameraWait, enabled = enabled) {
            Text(stringResource(R.string.camera_stop_waiting))
        }
    }
    Button(onClick = { picker.launch(arrayOf("image/jpeg")) }, enabled = enabled) {
        Text(stringResource(R.string.import_photo))
    }
    val captures = state.importedPhotos.captures.filterNot { it == state.captureInFlight }
    val recovery = state.interruptedCaptures.filter { it.id in captures }.associateBy { it.id }
    val recoveryIds = (listOfNotNull(state.failedPhotoRecovery) + state.importedPhotos.confirmedWithRecoverySources).distinct()
    val pendingCount = captures.size + state.importedPhotos.pending.size
    var listPage by rememberSaveable { mutableStateOf<String?>(null) }

    Button(
        onClick = { listPage = "saved" },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.photo_list_saved, state.importedPhotos.confirmed.size))
    }
    Button(
        onClick = { listPage = "pending" },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.photo_list_pending, pendingCount))
    }
    if (recoveryIds.isNotEmpty()) {
        TextButton(onClick = { listPage = "saved" }, enabled = enabled) {
            Text(stringResource(R.string.photo_list_recovery_available))
        }
    }

    // Only one dialog at a time; opening an item closes its list, not its stored data.
    if (state.cameraPreviewId == null && state.importCandidate == null && !state.cameraReviewHidden) {
        when (listPage) {
            "saved" -> PhotoListDialog(R.string.photo_list_saved_title, { listPage = null }) {
                if (state.importedPhotos.confirmed.isEmpty() && recoveryIds.isEmpty()) {
                    item { Text(stringResource(R.string.photo_list_empty)) }
                }
                itemsIndexed(state.importedPhotos.confirmed, key = { _, id -> "saved-${id.name}" }) { index, photo ->
                    TextButton(onClick = {
                        listPage = null
                        state.openPhoto(photo)
                    }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.import_open_confirmed, index + 1))
                    }
                }
                itemsIndexed(recoveryIds, key = { _, id -> "recovery-${id.name}" }) { _, id ->
                    val index = state.importedPhotos.confirmed.indexOf(id)
                    TextButton(onClick = {
                        listPage = null
                        state.showConfirmedRecovery(id)
                    }, enabled = enabled && id != state.captureInFlight, modifier = Modifier.fillMaxWidth()) {
                        Text(if (index < 0) stringResource(R.string.camera_recovery_open_sources)
                            else stringResource(R.string.camera_recovery_open_indexed, index + 1))
                    }
                }
            }
            "pending" -> PhotoListDialog(R.string.photo_list_pending_title, { listPage = null }) {
                if (pendingCount == 0) item { Text(stringResource(R.string.photo_list_empty)) }
                if (captures.isNotEmpty()) item {
                    Text(stringResource(R.string.photo_list_pending_info),
                        style = MaterialTheme.typography.bodyMedium)
                }
                itemsIndexed(captures, key = { _, id -> "capture-${id.name}" }) { index, id ->
                    val offer = recovery[id]
                    var confirmDiscard by remember(id, offer) { mutableStateOf(false) }
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            Text(stringResource(R.string.camera_resume, index + 1))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton(onClick = {
                                    listPage = null
                                    state.previewCapture(id)
                                }, enabled = enabled) {
                                    Text(stringResource(R.string.camera_show_interrupted))
                                }
                                if (offer?.discardCandidate != null) {
                                    TextButton(onClick = { confirmDiscard = true }, enabled = enabled) {
                                        Text(stringResource(R.string.camera_discard_interrupted))
                                    }
                                }
                            }
                            if (offer != null) {
                                TextButton(onClick = {
                                    state.deferInterruptedCapture(offer.id)
                                    listPage = null
                                }, enabled = enabled) { Text(stringResource(R.string.import_later)) }
                            }
                        }
                    }
                    if (confirmDiscard && offer?.discardCandidate != null) {
                        AlertDialog(
                            onDismissRequest = { confirmDiscard = false },
                            title = { Text(stringResource(R.string.camera_discard_interrupted)) },
                            text = { Text(stringResource(R.string.camera_discard_warning)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmDiscard = false
                                    state.discardInterruptedCapture(offer)
                                }, enabled = enabled) { Text(stringResource(R.string.camera_discard_interrupted)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDiscard = false }) {
                                    Text(stringResource(R.string.cancel_value))
                                }
                            },
                        )
                    }
                }
                itemsIndexed(state.importedPhotos.pending, key = { _, candidate -> "import-${candidate.photoId.name}-${candidate.digest}" }) { index, candidate ->
                    TextButton(onClick = {
                        listPage = null
                        state.previewImport(candidate)
                    }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.import_resume, captures.size + index + 1))
                    }
                }
            }
        }
    }
    if (state.importBusy) Text(stringResource(R.string.import_working))
    if (state.cameraReviewHidden) return
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
            PhotoReviewDialog(
                fullScreen = capture != null,
                bitmap = state.importPreview?.bitmap,
                onDismissRequest = state::deferImport,
                title = {
                    Text(stringResource(when {
                        state.recoveringConfirmedPhoto -> R.string.camera_recovery_open_sources
                        capture != null -> R.string.camera_preview
                        else -> R.string.import_preview
                    }))
                },
                text = { narrow ->
                    Column {
                        if (state.importPreview?.sourceDamaged == true) {
                            Text(stringResource(R.string.import_damage_warning),
                                color = MaterialTheme.colorScheme.error)
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
                        val secondaryActions: @Composable () -> Unit = {
                            if (capture != null && !state.recoveringConfirmedPhoto &&
                                state.captureSources.isEmpty() && state.selectedCaptureSource == null &&
                                state.importPreview?.candidate?.photoId == capture
                            ) {
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
                        if (narrow) {
                            Column(Modifier.fillMaxWidth()) { secondaryActions() }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                secondaryActions()
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


@Composable
private fun PhotoListDialog(title: Int, onDismiss: () -> Unit, content: LazyListScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.photo_list_close)) }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
        }
    }
}

/** Keep the camera review separate from validation and durable confirmation. */
@Composable
private fun PhotoReviewDialog(
    fullScreen: Boolean,
    bitmap: Bitmap?,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable (Boolean) -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    if (!fullScreen) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    bitmap?.let {
                        Image(it.asImageBitmap(), stringResource(R.string.import_preview),
                            Modifier.fillMaxWidth().heightIn(max = 220.dp), contentScale = ContentScale.Fit)
                    }
                    text(false)
                }
            },
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
        return
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                if (maxWidth > maxHeight && bitmap != null) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ZoomableReviewImage(bitmap, Modifier.weight(1f).fillMaxHeight())
                        Column(
                            Modifier.width(minOf(180.dp, maxWidth * 0.30f)).fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            title()
                            text(true)
                            confirmButton()
                            dismissButton()
                        }
                    }
                } else {
                    val detailsLimit = maxHeight * 0.4f
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        title()
                        if (bitmap != null) {
                            ZoomableReviewImage(bitmap, Modifier.weight(1f).fillMaxWidth())
                        }
                        Column(
                            if (bitmap != null) Modifier.fillMaxWidth().heightIn(max = detailsLimit)
                                .verticalScroll(rememberScrollState())
                            else Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        ) { text(false) }
                        ReviewActions(dismissButton, confirmButton)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewActions(dismissButton: @Composable () -> Unit, confirmButton: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { dismissButton() }
        Box(Modifier.weight(1.5f), contentAlignment = Alignment.CenterEnd) { confirmButton() }
    }
}

@Composable
private fun ZoomableReviewImage(bitmap: Bitmap, modifier: Modifier) {
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val fit = minOf(width / bitmap.width, height / bitmap.height)
        var scale by remember(bitmap, width, height) { mutableStateOf(1f) }
        var offset by remember(bitmap, width, height) { mutableStateOf(Offset.Zero) }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.camera_preview),
            modifier = Modifier.fillMaxSize()
                .pointerInput(bitmap, width, height) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 5f)
                        val center = Offset(width / 2f, height / 2f)
                        val moved = (offset + center - centroid) * (next / scale) + centroid - center + pan
                        val maxX = ((bitmap.width * fit * next - width) / 2f).coerceAtLeast(0f)
                        val maxY = ((bitmap.height * fit * next - height) / 2f).coerceAtLeast(0f)
                        offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                        scale = next
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
        )
    }
}
