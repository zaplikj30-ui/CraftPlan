package cz.craftplan.app

import android.graphics.Bitmap
import android.view.ViewTreeObserver
import android.view.WindowInsets as AndroidWindowInsets
import android.view.WindowInsetsController
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
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
    val listStatus = state.photoListStatus
    var listLayoutRecorded by remember(listStatus, state.diagnosticsRecording) { mutableStateOf(false) }
    val listEnabled = enabled && listStatus != PhotoListStatus.LOADING

    Button(
        onClick = { listPage = "saved" },
        enabled = listEnabled,
        modifier = Modifier.fillMaxWidth().onGloballyPositioned {
            if (state.diagnosticsRecording && listStatus == PhotoListStatus.READY && !listLayoutRecorded) {
                listLayoutRecorded = true
                state.diagnostics.photoListStage(PhotoListDiagnosticStage.LIST_LAYOUT)
            }
        },
    ) {
        Text(if (listStatus == PhotoListStatus.READY)
            stringResource(R.string.photo_list_saved, state.importedPhotos.confirmed.size)
            else stringResource(R.string.photo_list_saved_title))
    }
    Button(
        onClick = { listPage = "pending" },
        enabled = listEnabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (listStatus == PhotoListStatus.READY) stringResource(R.string.photo_list_pending, pendingCount)
            else stringResource(R.string.photo_list_pending_title))
    }
    when (listStatus) {
        PhotoListStatus.LOADING -> Text(stringResource(R.string.photo_list_loading))
        PhotoListStatus.FAILED -> TextButton(onClick = state::refreshImports, enabled = enabled) {
            Text(stringResource(R.string.photo_list_retry))
        }
        PhotoListStatus.READY -> Unit
    }
    if (recoveryIds.isNotEmpty()) {
        TextButton(onClick = { listPage = "saved" }, enabled = listEnabled) {
            Text(stringResource(R.string.photo_list_recovery_available))
        }
    }

    // Only one dialog at a time; opening an item closes its list, not its stored data.
    if (state.cameraPreviewId == null && state.importCandidate == null && !state.cameraReviewHidden) {
        when (listPage) {
            "saved" -> PhotoListDialog(R.string.photo_list_saved_title, { listPage = null }) {
                if (listStatus != PhotoListStatus.READY) {
                    item { Text(stringResource(if (listStatus == PhotoListStatus.LOADING)
                        R.string.photo_list_loading else R.string.photo_list_retry)) }
                } else if (state.importedPhotos.confirmed.isEmpty() && recoveryIds.isEmpty()) {
                    item { Text(stringResource(R.string.photo_list_empty)) }
                }
                itemsIndexed(state.importedPhotos.confirmed, key = { _, id -> "saved-${id.name}" }) { index, photo ->
                    TextButton(onClick = {
                        listPage = null
                        state.openPhoto(photo)
                    }, enabled = listEnabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.import_open_confirmed, index + 1))
                    }
                }
                itemsIndexed(recoveryIds, key = { _, id -> "recovery-${id.name}" }) { _, id ->
                    val index = state.importedPhotos.confirmed.indexOf(id)
                    TextButton(onClick = {
                        listPage = null
                        state.showConfirmedRecovery(id)
                    }, enabled = listEnabled && id != state.captureInFlight, modifier = Modifier.fillMaxWidth()) {
                        Text(if (index < 0) stringResource(R.string.camera_recovery_open_sources)
                            else stringResource(R.string.camera_recovery_open_indexed, index + 1))
                    }
                }
            }
            "pending" -> PhotoListDialog(R.string.photo_list_pending_title, { listPage = null }) {
                if (listStatus != PhotoListStatus.READY) {
                    item { Text(stringResource(if (listStatus == PhotoListStatus.LOADING)
                        R.string.photo_list_loading else R.string.photo_list_retry)) }
                } else if (pendingCount == 0) item { Text(stringResource(R.string.photo_list_empty)) }
                state.importDiagnostic?.let { code ->
                    item { Text(stringResource(R.string.document_storage_diagnostic, code),
                        color = MaterialTheme.colorScheme.error) }
                }
                if (state.importBusy) item { Text(stringResource(R.string.import_working)) }
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
                                }, enabled = listEnabled) {
                                    Text(stringResource(R.string.camera_show_interrupted))
                                }
                                if (offer?.discardCandidate != null) {
                                    TextButton(onClick = { confirmDiscard = true }, enabled = listEnabled) {
                                        Text(stringResource(R.string.camera_discard_interrupted))
                                    }
                                }
                            }
                            if (offer != null) {
                                TextButton(onClick = {
                                    state.deferInterruptedCapture(offer.id)
                                    listPage = null
                                }, enabled = listEnabled) { Text(stringResource(R.string.import_later)) }
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
                                    state.discardInterruptedCapture(checkNotNull(offer))
                                }, enabled = listEnabled) { Text(stringResource(R.string.camera_discard_interrupted)) }
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
                    }, enabled = listEnabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.import_resume, captures.size + index + 1))
                    }
                }
            }
            else -> Unit
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
        var confirmDiscard by rememberSaveable(id.name, state.selectedCaptureSource) { mutableStateOf(false) }
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
            val shown = state.importPreview
            val ordinary = state.ordinaryCameraReview && !state.retryingPhotoConfirmation
            val canDiscard = !state.retryingPhotoConfirmation && (discardSource || ordinary ||
                (capture == null && !state.recoveringConfirmedPhoto && shown != null))
            val confirmLabel = if (state.selectedCaptureSource != null) {
                if (shown?.sourceDamaged == true) R.string.camera_recovery_save_damaged
                else R.string.camera_recovery_save
            } else if (shown?.sourceDamaged == true) {
                R.string.import_confirm_damaged
            } else if (state.retryingPhotoConfirmation) R.string.document_retry
            else if (capture != null) R.string.camera_confirm else R.string.import_confirm
            PhotoReviewDialog(
                fullScreen = capture != null,
                bitmap = shown?.bitmap,
                showQuality = capture != null && shown != null && !shown.sourceDamaged &&
                    !state.recoveringConfirmedPhoto && state.captureSources.isEmpty() &&
                    state.selectedCaptureSource == null,
                onDismissRequest = {
                    if (enabled) {
                        if (ordinary) confirmDiscard = true else state.deferImport()
                    }
                },
                title = {
                    Text(stringResource(when {
                        state.recoveringConfirmedPhoto -> R.string.camera_recovery_open_sources
                        capture != null -> R.string.camera_preview
                        else -> R.string.import_preview
                    }))
                },
                details = {
                    Column {
                        if (shown?.sourceDamaged == true) {
                            Text(stringResource(R.string.import_damage_warning),
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (state.importBusy) Text(stringResource(R.string.import_working))
                        state.importDiagnostic?.let {
                            if (shown == null) {
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
                    }
                },
                retake = if (ordinary) ReviewAction(R.string.camera_retake,
                    enabled && state.captureInFlight == null, onCapture) else null,
                discard = if (canDiscard) ReviewAction(discardLabel, enabled) { confirmDiscard = true } else null,
                confirm = ReviewAction(confirmLabel, enabled && shown != null) {
                    if (shown != null) {
                        val consent = if (shown.sourceDamaged) PhotoConfirmation.EXPLICIT_DAMAGED
                            else PhotoConfirmation.EXPLICIT
                        state.confirmImport(shown.candidate, consent)
                    }
                },
                later = if (ordinary) null else ReviewAction(R.string.photo_list_close, enabled, state::deferImport),
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

private data class ReviewAction(val label: Int, val enabled: Boolean, val onClick: () -> Unit)

/** Immersive policy is scoped to the review dialog, not the editor/activity window. */
@Composable
private fun CameraReviewFullscreenEffect() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(window) {
        if (window == null) {
            onDispose { }
        } else {
            val decor = window.decorView
            fun hideBars() {
                window.insetsController?.let { controller ->
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(AndroidWindowInsets.Type.statusBars() or AndroidWindowInsets.Type.navigationBars())
                }
            }
            val observer = decor.viewTreeObserver
            val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
                if (focused) hideBars()
            }
            observer.addOnWindowFocusChangeListener(listener)
            hideBars()
            onDispose {
                if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)
                else decor.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
                // The dialog window is dismissed; never request bars over the next editor window.
            }
        }
    }
}

/** Keep the camera review separate from validation and durable confirmation. */
@Composable
private fun PhotoReviewDialog(
    fullScreen: Boolean,
    bitmap: Bitmap?,
    showQuality: Boolean,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    details: @Composable () -> Unit,
    retake: ReviewAction?,
    discard: ReviewAction?,
    confirm: ReviewAction,
    later: ReviewAction?,
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
                    details()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        retake?.let { ReviewTextAction(it) }
                        discard?.let { ReviewTextAction(it) }
                    }
                }
            },
            confirmButton = { ReviewTextAction(confirm) },
            dismissButton = { later?.let { ReviewTextAction(it) } },
        )
        return
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        CameraReviewFullscreenEffect()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (maxWidth > maxHeight && bitmap != null) {
                    // Evaluate BoxWithConstraints values before entering RowScope.
                    val controlsWidth = minOf(180.dp, maxWidth * 0.30f)
                    Row(Modifier.fillMaxSize()) {
                        ZoomableReviewImage(bitmap, Modifier.weight(1f).fillMaxHeight())
                        Column(
                            Modifier.width(controlsWidth).fillMaxHeight()
                                .verticalScroll(rememberScrollState()).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ReviewHeading(showQuality, title)
                            details()
                            ReviewPill(confirm, Modifier.fillMaxWidth(), primary = true)
                            retake?.let { ReviewPill(it, Modifier.fillMaxWidth()) }
                            later?.let { ReviewPill(it, Modifier.fillMaxWidth()) }
                            discard?.let { ReviewPill(it, Modifier.fillMaxWidth()) }
                        }
                    }
                } else {
                    val detailsLimit = maxHeight * 0.48f
                    Column(Modifier.fillMaxSize()) {
                        if (bitmap != null) {
                            ZoomableReviewImage(bitmap, Modifier.weight(1f).fillMaxWidth())
                        }
                        Column(
                            (if (bitmap != null) Modifier.fillMaxWidth().heightIn(max = detailsLimit)
                            else Modifier.weight(1f).fillMaxWidth())
                                .verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ReviewHeading(showQuality, title)
                            details()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                retake?.let { ReviewPill(it, Modifier.weight(1f)) }
                                ReviewPill(confirm, Modifier.weight(1f), primary = true)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                discard?.let { ReviewPill(it, Modifier.weight(1f)) }
                                later?.let { ReviewPill(it, Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewHeading(showQuality: Boolean, title: @Composable () -> Unit) {
    if (showQuality) {
        Text(stringResource(R.string.camera_quality_full),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        title()
    }
}

@Composable
private fun ReviewTextAction(action: ReviewAction) {
    TextButton(onClick = action.onClick, enabled = action.enabled) {
        Text(stringResource(action.label))
    }
}

@Composable
private fun ReviewPill(action: ReviewAction, modifier: Modifier, primary: Boolean = false) {
    val shape = RoundedCornerShape(20.dp)
    val padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    val label: @Composable () -> Unit = {
        Text(stringResource(action.label), textAlign = TextAlign.Center)
    }
    if (primary) {
        Button(onClick = action.onClick, modifier = modifier, enabled = action.enabled,
            shape = shape, contentPadding = padding) { label() }
    } else {
        FilledTonalButton(onClick = action.onClick, modifier = modifier, enabled = action.enabled,
            shape = shape, contentPadding = padding) { label() }
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
