package cz.craftplan.app

import android.graphics.Bitmap
import android.widget.Toast
import android.view.ViewTreeObserver
import android.view.WindowInsets as AndroidWindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
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
    BackHandler(enabled = state.cameraReviewHidden && !enabled) { }
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
    val cases = state.importedPhotos.recoveryCases(state.captureInFlight, state.failedPhotoRecovery)
    val technicalCaptures = if (BuildConfig.ENABLE_STRESS_TOOLS && state.diagnosticsEnabled)
        state.importedPhotos.emptyTechnicalCaptures(state.captureInFlight, state.failedPhotoRecovery) else emptyList()
    var recoveryOpen by rememberSaveable { mutableStateOf(false) }
    var recoveryIndex by rememberSaveable { mutableStateOf(0) }
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
    if (cases.isNotEmpty() && listStatus == PhotoListStatus.READY) {
        TextButton(onClick = {
            state.suppressAutomaticRecovery()
            recoveryIndex = 0
            recoveryOpen = true
        }, enabled = listEnabled) {
            Text(stringResource(R.string.photo_recovery_continue, cases.size))
        }
    }
    if (technicalCaptures.isNotEmpty() && listStatus == PhotoListStatus.READY) {
        TextButton(onClick = { listPage = "technical" }, enabled = listEnabled) {
            Text(stringResource(R.string.photo_technical_attempts, technicalCaptures.size))
        }
    }
    when (listStatus) {
        PhotoListStatus.LOADING -> Text(stringResource(R.string.photo_list_loading))
        PhotoListStatus.FAILED -> TextButton(onClick = state::refreshImports, enabled = enabled) {
            Text(stringResource(R.string.photo_list_retry))
        }
        PhotoListStatus.READY -> Unit
    }
    val noReview = state.cameraPreviewId == null && state.importCandidate == null && !state.cameraReviewHidden
    val canOffer = noReview && !recoveryOpen && !state.newPhotoFailed &&
        listPage == null && listEnabled && listStatus == PhotoListStatus.READY
    fun openCase(case: PhotoRecoveryCase) {
        when (case) {
            is PhotoRecoveryCase.Camera -> state.previewCapture(case.id)
            is PhotoRecoveryCase.Import -> state.previewImport(case.candidate)
            is PhotoRecoveryCase.Saved -> state.showConfirmedRecovery(case.id)
        }
    }
    LaunchedEffect(state.newPhotoFailed) {
        if (state.newPhotoFailed) {
            state.suppressAutomaticRecovery()
            recoveryOpen = false
        }
    }
    LaunchedEffect(canOffer, cases) {
        if (canOffer) {
            val index = state.takeAutomaticRecoveryIndex(cases)
            val case = cases.getOrNull(index)
            // Preserve the startup rule: do not automatically interrupt for an
            // unreadable source. Explicit recovery remains available for all cases.
            if (case != null) {
                val available = case.id == state.failedPhotoRecovery ||
                    state.recoveryPreview(case)?.let {
                        // Only a startup eligibility probe, not a second displayed preview.
                        it.image.recycle()
                        true
                    } == true
                if (available) {
                    recoveryIndex = index
                    recoveryOpen = true
                }
            }
        }
    }
    LaunchedEffect(recoveryOpen, noReview, enabled, cases, listStatus, state.newPhotoFailed) {
        if (recoveryOpen && noReview && enabled && listStatus == PhotoListStatus.READY && !state.newPhotoFailed) {
            val index = recoveryPageIndex(cases, null, recoveryIndex)
            if (index < 0) recoveryOpen = false
            else {
                recoveryIndex = index
                openCase(cases[index])
            }
        }
    }

    if (noReview && (!recoveryOpen || cases.isEmpty())) {
        when (listPage) {
            "technical" -> PhotoListDialog(R.string.photo_technical_title, { listPage = null }) {
                if (listStatus != PhotoListStatus.READY) {
                    item { Text(stringResource(if (listStatus == PhotoListStatus.LOADING)
                        R.string.photo_list_loading else R.string.photo_list_retry)) }
                } else {
                    if (technicalCaptures.isEmpty()) item { Text(stringResource(R.string.photo_list_empty)) }
                    itemsIndexed(technicalCaptures, key = { _, id -> "technical-${id.name}" }) { index, id ->
                        TextButton(onClick = {
                            listPage = null
                            state.previewCapture(id)
                        }, enabled = listEnabled, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.photo_technical_attempt, index + 1))
                        }
                    }
                }
            }
            "saved" -> PhotoListDialog(R.string.photo_list_saved_title, { listPage = null }) {
                if (listStatus != PhotoListStatus.READY) {
                    item { Text(stringResource(if (listStatus == PhotoListStatus.LOADING)
                        R.string.photo_list_loading else R.string.photo_list_retry)) }
                } else if (state.importedPhotos.confirmed.isEmpty()) {
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
            }
            else -> Unit
        }
    }
    if (state.importBusy) Text(stringResource(R.string.import_working))
    if (state.cameraReviewHidden) return
    val capture = state.cameraPreviewId
    val activeReviewId = capture ?: state.importCandidate?.photoId
    // A single, display-only thumbnail. It is not a validated ImportPreview
    // and cannot be used for confirmation or discard. Closing recovery drops it.
    var prefetched by remember(recoveryOpen) {
        mutableStateOf<Pair<PhotoRecoveryCase.Import, Bitmap>?>(null)
    }
    LaunchedEffect(recoveryOpen, activeReviewId, enabled, cases, state.importPreview?.candidate) {
        if (!recoveryOpen || !enabled || activeReviewId == null) return@LaunchedEffect
        val current = state.importPreview
        val next = if (current != null) recoveryPrefetchCase(cases, activeReviewId) else null
        if (prefetched?.first == next) return@LaunchedEffect
        prefetched = null
        if (next != null) {
            val result = state.recoveryPreview(next)
            if (result != null && !result.sourceDamaged) prefetched = next to result.image
        }
    }
    var switchingImport by remember(recoveryOpen) { mutableStateOf<ImportCandidate?>(null) }
    LaunchedEffect(enabled, activeReviewId, state.newPhotoFailed) {
        if (enabled || state.newPhotoFailed) switchingImport = null
    }
    val context = LocalContext.current
    // Keep the same full-screen dialog mounted while the selected case loads or
    // a completed discard refreshes the list. Placeholders never authorize actions.
    val pendingId = activeReviewId ?: if (recoveryOpen && !state.newPhotoFailed)
        cases.getOrNull(recoveryPageIndex(cases, null, recoveryIndex))?.id else null
    pendingId?.let { id ->
        val recoveryPage = if (recoveryOpen && cases.any { it.id == id })
            recoveryPageIndex(cases, id, recoveryIndex) else -1
        val pager = if (recoveryPage >= 0) key(id, cases) {
            rememberPagerState(initialPage = recoveryPage) { cases.size }
        } else null
        fun reviewReady(): Boolean =
            !state.importBusy && !state.openingPhoto &&
                (state.cameraPreviewId ?: state.importCandidate?.photoId) == id &&
                (pager == null || (!pager.isScrollInProgress && cases.getOrNull(pager.settledPage)?.id == id))
        val reviewEnabled = reviewReady()
        LaunchedEffect(pager, pager?.settledPage, pager?.isScrollInProgress, enabled, activeReviewId) {
            if (pager != null && enabled && activeReviewId == id && !pager.isScrollInProgress) {
                val target = cases.getOrNull(pager.settledPage)
                if (target != null && target.id != id) {
                    // Switching pages only defers the current review; it never
                    // confirms or discards it. Load just the newly selected case.
                    recoveryIndex = pager.settledPage
                    // Preserve the ordinary import panel, not permissions from the old page.
                    switchingImport = (target as? PhotoRecoveryCase.Import)?.candidate?.takeIf {
                        cases.getOrNull(recoveryPage) is PhotoRecoveryCase.Import &&
                            state.importPreview != null && !state.retryingPhotoConfirmation
                    }
                    state.deferImport()
                    openCase(target)
                } else if (target?.id == id) {
                    recoveryIndex = pager.settledPage
                }
            }
        }
        val displayedSource = state.selectedCaptureSource
        val displayedDigest = state.selectedCaptureDigest
        val displayedCandidate = state.importCandidate
        val discardSource = displayedSource != null && displayedDigest != null
        val discardLabel = when {
            discardSource -> R.string.camera_discard_selected
            capture == null -> R.string.import_discard
            else -> R.string.camera_discard
        }
        var confirmDiscard by rememberSaveable(id.name, state.selectedCaptureSource) { mutableStateOf(false) }
        if (confirmDiscard) {
            val ordinaryDiscard = capture != null && !discardSource
            AlertDialog(
                onDismissRequest = { if (enabled) confirmDiscard = false },
                title = { Text(stringResource(if (ordinaryDiscard) R.string.camera_discard_title else discardLabel)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(when {
                            discardSource -> R.string.camera_discard_selected_warning
                            capture == null -> R.string.import_discard_warning
                            else -> R.string.camera_discard_warning
                        }))
                        DiscardOperationStatus(state)
                    }
                },
                confirmButton = {
                    val action = ReviewAction(discardLabel, reviewEnabled) {
                        // Bind the destructive action to the candidate/source displayed
                        // when the user made the decision, even if a callback arrives late.
                        if (discardSource && displayedSource != null && displayedDigest != null) {
                            state.discardSelectedCaptureSource(displayedSource, displayedDigest)
                        } else if (!discardSource && displayedCandidate != null) {
                            state.discardImport(displayedCandidate)
                        }
                    }
                    if (!discardSource) ReviewPill(action, Modifier, destructive = true)
                    else ReviewTextAction(action)
                },
                dismissButton = {
                    val action = ReviewAction(R.string.cancel_value, enabled) { confirmDiscard = false }
                    if (!discardSource) ReviewPill(action, Modifier) else ReviewTextAction(action)
                },
            )
        } else {
            val shown = state.importPreview?.takeIf { activeReviewId == id }
            val ordinaryCamera = state.ordinaryCameraReview && !state.retryingPhotoConfirmation
            val ordinary = ordinaryCamera && !recoveryOpen
            val canDiscard = !state.retryingPhotoConfirmation && (discardSource || ordinaryCamera ||
                (capture == null && !state.recoveringConfirmedPhoto && (shown != null || state.canDiscardFailedImport)))
            val ordinaryImport = recoveryPage >= 0 && cases.getOrNull(recoveryPage) is PhotoRecoveryCase.Import
            val pagingImport = ordinaryImport && !enabled &&
                switchingImport == (cases.getOrNull(recoveryPage) as? PhotoRecoveryCase.Import)?.candidate
            val stableImportPanel = ordinaryImport && !state.retryingPhotoConfirmation &&
                ((enabled && shown != null && !shown.sourceDamaged) || pagingImport)
            // While paging, a tap gives immediate feedback instead of silently failing.
            // Never queue an action or borrow the previous image's permissions.
            fun reviewAction(label: Int, permitted: Boolean, action: () -> Unit): ReviewAction =
                ReviewAction(label, permitted || stableImportPanel) {
                    if (permitted && reviewReady()) action()
                    else if (stableImportPanel || !reviewReady()) Toast.makeText(context.applicationContext,
                        R.string.import_working, Toast.LENGTH_SHORT).show()
                }
            val confirmLabel = if (state.selectedCaptureSource != null) {
                R.string.camera_recovery_save
            } else if (state.retryingPhotoConfirmation) R.string.document_retry
            else if (capture != null) R.string.camera_confirm else R.string.import_confirm
            PhotoReviewDialog(
                bitmap = shown?.bitmap,
                showQuality = !recoveryOpen && capture != null && shown != null && !shown.sourceDamaged &&
                    !state.recoveringConfirmedPhoto && state.captureSources.isEmpty() &&
                    state.selectedCaptureSource == null,
                onDismissRequest = {
                    if (reviewEnabled) {
                        if (ordinary) confirmDiscard = true
                        else {
                            recoveryOpen = false
                            state.deferImport()
                        }
                    } else if (stableImportPanel) {
                        Toast.makeText(context.applicationContext, R.string.import_working, Toast.LENGTH_SHORT).show()
                    }
                },
                imageContent = if (pager != null) { modifier ->
                    HorizontalPager(
                        state = pager,
                        modifier = modifier,
                        userScrollEnabled = enabled && activeReviewId == id && cases.size > 1,
                        key = { cases[it].id.name },
                    ) { page ->
                        val pageCase = cases.getOrNull(page)
                        val thumbnail = prefetched?.takeIf {
                            it.first == pageCase && (it.first.id != activeReviewId || state.importBusy)
                        }?.second
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (pageCase?.id == id && shown?.bitmap != null) {
                                ZoomableReviewImage(shown.bitmap, Modifier.fillMaxSize(), inPager = true)
                            } else if (thumbnail != null) {
                                Image(thumbnail.asImageBitmap(), stringResource(R.string.camera_preview),
                                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            } else if (pageCase?.id == id && activeReviewId == id && !state.importBusy) {
                                Text(stringResource(R.string.photo_recovery_preview_unavailable))
                            }
                            if (pageCase?.id == id && state.importBusy && (shown == null || pagingImport)) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                } else null,
                title = {
                    Text(stringResource(when {
                        recoveryOpen -> R.string.photo_recovery_heading
                        state.recoveringConfirmedPhoto -> R.string.camera_recovery_open_sources
                        capture != null -> R.string.camera_preview
                        else -> R.string.import_preview
                    }))
                    if (recoveryPage >= 0) {
                        Text(stringResource(R.string.photo_recovery_position, recoveryPage + 1, cases.size))
                    }
                },
                details = {
                    Column {
                        if (shown?.sourceDamaged == true) {
                            Text(stringResource(R.string.import_damage_warning),
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (state.importBusy && !pagingImport) Text(stringResource(R.string.import_working))
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
                                TextButton(onClick = { state.previewCaptureSource(source) }, enabled = reviewEnabled) { Text(label) }
                            }
                        }
                        if (state.recoveringConfirmedPhoto && state.captureSources.isNotEmpty()) {
                            TextButton(onClick = state::openConfirmedRecovery, enabled = reviewEnabled) {
                                Text(stringResource(R.string.camera_recovery_open_original))
                            }
                        }
                    }
                },
                retake = if (ordinary) ReviewAction(R.string.camera_retake,
                    enabled && state.captureInFlight == null, onCapture) else null,
                discard = if (canDiscard || pagingImport)
                    reviewAction(discardLabel, canDiscard && reviewEnabled) { confirmDiscard = true } else null,
                confirm = reviewAction(confirmLabel, reviewEnabled && shown != null && !shown.sourceDamaged) {
                    if (shown != null && !shown.sourceDamaged) {
                        state.confirmImport(shown.candidate, PhotoConfirmation.EXPLICIT)
                    }
                },
                later = if (ordinary) null else reviewAction(R.string.photo_list_close, reviewEnabled) {
                    recoveryOpen = false
                    state.deferImport()
                },
            )
        }
    } ?: state.importDiagnostic?.let { code ->
        if (state.newPhotoFailed) {
            if (!state.importBusy) {
                val message = when (code.substringAfter(" / ")) {
                    "NO_SPACE" -> R.string.photo_add_no_space
                    "PHOTO_SIZE_LIMIT", "PHOTO_PIXEL_LIMIT" -> R.string.photo_add_too_large
                    else -> R.string.photo_add_failed
                }
                AlertDialog(
                    onDismissRequest = state::dismissNewPhotoFailure,
                    text = { Text(stringResource(message)) },
                    confirmButton = {
                        TextButton(onClick = state::dismissNewPhotoFailure) {
                            Text(stringResource(R.string.photo_add_ok))
                        }
                    },
                )
            }
        } else {
            Text(stringResource(if (code.startsWith("CAMERA_CAPTURE /")) R.string.camera_failed else R.string.import_failed))
            Text(stringResource(R.string.document_storage_diagnostic, code))
        }
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

@Composable
private fun DiscardOperationStatus(state: PhotoEditorState) {
    if (state.importBusy) Text(stringResource(R.string.import_working))
    state.importDiagnostic?.let {
        Text(stringResource(R.string.document_storage_diagnostic, it), color = MaterialTheme.colorScheme.error)
    }
}

private data class ReviewAction(val label: Int, val enabled: Boolean, val onClick: () -> Unit)

/** Immersive policy is scoped to the review dialog, not the editor/activity window. */
@Composable
private fun PhotoReviewFullscreenEffect() {
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

/** Shared full-screen photo review, separate from validation and durable confirmation. */
@Composable
private fun PhotoReviewDialog(
    bitmap: Bitmap?,
    showQuality: Boolean,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    details: @Composable () -> Unit,
    retake: ReviewAction?,
    discard: ReviewAction?,
    confirm: ReviewAction,
    later: ReviewAction?,
    imageContent: (@Composable (Modifier) -> Unit)? = null,
) {
    val hasImage = bitmap != null || imageContent != null
    val image: @Composable (Modifier) -> Unit = { modifier ->
        if (imageContent != null) imageContent(modifier)
        else if (bitmap != null) ZoomableReviewImage(bitmap, modifier)
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        PhotoReviewFullscreenEffect()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (maxWidth > maxHeight && hasImage) {
                    // Evaluate BoxWithConstraints values before entering RowScope.
                    val controlsWidth = minOf(180.dp, maxWidth * 0.30f)
                    Row(Modifier.fillMaxSize()) {
                        image(Modifier.weight(1f).fillMaxHeight())
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
                        if (hasImage) {
                            image(Modifier.weight(1f).fillMaxWidth())
                        }
                        Column(
                            (if (hasImage) Modifier.fillMaxWidth().heightIn(max = detailsLimit)
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
private fun ReviewPill(
    action: ReviewAction, modifier: Modifier, primary: Boolean = false, destructive: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    val padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    val label: @Composable () -> Unit = {
        Text(stringResource(action.label), textAlign = TextAlign.Center)
    }
    if (destructive) {
        Button(onClick = action.onClick, modifier = modifier, enabled = action.enabled,
            shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )) { label() }
    } else if (primary) {
        Button(onClick = action.onClick, modifier = modifier, enabled = action.enabled,
            shape = shape, contentPadding = padding) { label() }
    } else {
        FilledTonalButton(onClick = action.onClick, modifier = modifier, enabled = action.enabled,
            shape = shape, contentPadding = padding) { label() }
    }
}

@Composable
private fun ZoomableReviewImage(bitmap: Bitmap, modifier: Modifier, inPager: Boolean = false) {
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val fit = minOf(width / bitmap.width, height / bitmap.height)
        var scale by remember(bitmap, width, height) { mutableStateOf(1f) }
        var offset by remember(bitmap, width, height) { mutableStateOf(Offset.Zero) }
        fun transform(centroid: Offset, pan: Offset, zoom: Float) {
            val next = (scale * zoom).coerceIn(1f, 5f)
            val center = Offset(width / 2f, height / 2f)
            val anchor = if (centroid.isSpecified) centroid else center
            val moved = (offset + center - anchor) * (next / scale) + anchor - center + pan
            val maxX = ((bitmap.width * fit * next - width) / 2f).coerceAtLeast(0f)
            val maxY = ((bitmap.height * fit * next - height) / 2f).coerceAtLeast(0f)
            offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
            scale = next
        }
        val gestures = if (inPager) {
            val transformState = rememberTransformableState { centroid, zoom, pan, _ ->
                transform(centroid, pan, zoom)
            }
            Modifier.transformable(transformState, canPan = { scale > 1f })
        } else Modifier.pointerInput(bitmap, width, height) {
            detectTransformGestures { centroid, pan, zoom, _ -> transform(centroid, pan, zoom) }
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.camera_preview),
            modifier = Modifier.fillMaxSize()
                .then(gestures)
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
