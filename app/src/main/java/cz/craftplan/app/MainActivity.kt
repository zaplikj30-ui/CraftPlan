package cz.craftplan.app

import android.app.Application
import android.content.res.Configuration
import android.content.ContentResolver
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Window
import android.view.WindowInsets as AndroidWindowInsets
import android.view.WindowInsetsController
import android.view.ViewTreeObserver
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.union
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val editorState by viewModels<PhotoEditorState>()
    private val systemCamera by lazy { SystemPhotoCamera(this, editorState) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Consume a user-armed recording before setContent, once per new ViewModel.
        editorState.restorePreferences(getSharedPreferences("editor_preferences", MODE_PRIVATE))
        editorState.diagnosticGripBounds = null
        editorState.diagnosticSharedGripBounds = null
        systemCamera.restore(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                CraftPlan(
                    editorState, window, onCapture = {
                        val mode = if (editorState.cameraPreviewEnabled) CameraResultMode.CRAFTPLAN_PREVIEW
                            else CameraResultMode.DIRECT_AFTER_SUCCESS
                        editorState.requestCapture { id -> systemCamera.launch(id, mode) }
                    },
                    onStopCameraWait = systemCamera::stopWaiting,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        systemCamera.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        systemCamera.unregister()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Observe only; the normal Android/Compose dispatch and return value stay untouched.
        if (BuildConfig.ENABLE_STRESS_TOOLS && editorState.diagnostics.recording) {
            val kind = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> DiagnosticTouch.DOWN
                MotionEvent.ACTION_UP -> DiagnosticTouch.UP
                MotionEvent.ACTION_POINTER_DOWN -> DiagnosticTouch.POINTER_DOWN
                MotionEvent.ACTION_POINTER_UP -> DiagnosticTouch.POINTER_UP
                MotionEvent.ACTION_CANCEL -> DiagnosticTouch.CANCEL
                else -> null
            }
            val index = event.actionIndex
            if (kind != null && index in 0 until event.pointerCount) {
                val input = when (event.getToolType(index)) {
                    MotionEvent.TOOL_TYPE_FINGER -> EditorPointerKind.FINGER
                    MotionEvent.TOOL_TYPE_STYLUS -> EditorPointerKind.STYLUS
                    MotionEvent.TOOL_TYPE_ERASER -> EditorPointerKind.ERASER
                    MotionEvent.TOOL_TYPE_MOUSE -> EditorPointerKind.MOUSE
                    else -> EditorPointerKind.OTHER
                }
                editorState.diagnostics.toolbarWindowTouch(
                    kind, input, event.getX(index), event.getY(index), event.pointerCount,
                    event.flags and MotionEvent.FLAG_CANCELED != 0, editorState.diagnosticGripBounds,
                    (if (editorState.toolbarExpanded) 1 else 0) + (if (editorState.toolbarExtraExpanded) 2 else 0),
                    editorState.diagnosticSharedGripBounds,
                )
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onStop() {
        if (!isChangingConfigurations) editorState.stopDiagnostics()
        super.onStop()
    }
}

internal class PhotoEditorState(application: Application) : AndroidViewModel(application) {
    var currentPhoto by mutableStateOf<StoredPhoto?>(null)
        private set
    var importedPhotos by mutableStateOf(PhotoList(emptyList(), emptyList()))
        private set
    var importPreview by mutableStateOf<ImportPreview?>(null)
        private set
    var importCandidate by mutableStateOf<ImportCandidate?>(null)
        private set
    var captureInFlight by mutableStateOf<PhotoId?>(null)
        private set
    var cameraPreviewId by mutableStateOf<PhotoId?>(null)
        private set
    var captureSources by mutableStateOf<List<CaptureSource>>(emptyList())
        private set
    var selectedCaptureSource by mutableStateOf<CaptureSource?>(null)
        private set
    var selectedCaptureDigest by mutableStateOf<String?>(null)
        private set
    var failedPhotoRecovery by mutableStateOf<PhotoId?>(null)
        private set
    var recoveringConfirmedPhoto by mutableStateOf(false)
        private set
    var importBusy by mutableStateOf(false)
        private set
    var importDiagnostic by mutableStateOf<String?>(null)
        private set

    private val storage get() = AppDocumentStorage.get(getApplication())
    private var importRefresh: Job? = null
    private var cameraRecoveryScanned = false
    var cameraReviewHidden by mutableStateOf(false)
        private set
    var interruptedCaptures by mutableStateOf<List<InterruptedCapture>>(emptyList())
        private set

    var photo by mutableStateOf<Bitmap?>(null)
        private set
    var openingPhoto by mutableStateOf(false)
        private set
    var storageDiagnostic by mutableStateOf<String?>(null)
        private set
    private var storageStatus by mutableStateOf(DocumentSaveStatus.SAVING)
    var recoveredFromMedia by mutableStateOf(false)
        private set
    private var gestureEditing by mutableStateOf(false)
    val saveStatus: DocumentSaveStatus
        get() = if (storageStatus == DocumentSaveStatus.SAVED && (gestureEditing || valueDraft != null)) {
            DocumentSaveStatus.EDITING
        } else storageStatus
    val hasUnsavedChanges get() = autosave?.hasPendingChanges == true
    private var autosave: DocumentAutosave? = null
    private var saveJob: Job? = null
    private var retainedHistoryVersion: DocumentVersion? = null

    fun openPhoto(id: PhotoId = PhotoId.SYNTHETIC_WORKBENCH) {
        if (photo != null || openingPhoto || importBusy) return
        openingPhoto = true
        storageDiagnostic = null
        failedPhotoRecovery = null
        viewModelScope.launch {
            try {
                val (document, bitmap) = withContext(Dispatchers.IO) {
                    val document = if (id == PhotoId.SYNTHETIC_WORKBENCH) storage.synthetic.open()
                    else storage.imports.open(id)
                    document to decodePhoto(document.photoBytes, document.snapshot.photo)
                }
                enterEditor(document, bitmap)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                storageDiagnostic = storageFailureCode(error, StoragePhase.OPEN)
                if (id != PhotoId.SYNTHETIC_WORKBENCH) {
                    try {
                        if (withContext(Dispatchers.IO) { storage.imports.captureSources(id).isNotEmpty() }) {
                            failedPhotoRecovery = id
                        }
                    } catch (inspection: CancellationException) {
                        throw inspection
                    } catch (_: Exception) {
                        // Keep the original opening diagnostic if the sources cannot be listed.
                    }
                }
            } finally {
                openingPhoto = false
            }
        }
    }

    private fun enterEditor(document: OpenDocument, bitmap: Bitmap) {
        val snapshot = document.snapshot
        if (snapshot.photo.id != PhotoId.SYNTHETIC_WORKBENCH) stopDiagnostics()
        clearTestAppearances()
        currentPhoto = snapshot.photo
        scene = DimensionScene(snapshot.dimensions, photoId = snapshot.photo.id)
        if (document.recovered || retainedHistoryVersion != document.version) history = DimensionHistory()
        retainedHistoryVersion = null
        showNotes = snapshot.showNotes
        recoveredFromMedia = document.recoveredFromMedia
        if (document.recovered) {
            storageStatus = DocumentSaveStatus.RECOVERED
        } else {
            autosave = DocumentAutosave(document.version)
            storageStatus = DocumentSaveStatus.SAVED
            if (document.version.revisionId == null) queueCompletedChange()
        }
        photo = bitmap
    }

    fun refreshImports() {
        if (photo != null || importBusy) return
        importRefresh?.cancel()
        importRefresh = viewModelScope.launch {
            try {
                if (!cameraRecoveryScanned) {
                    val active = captureInFlight
                    interruptedCaptures = withContext(Dispatchers.IO) {
                        storage.imports.interruptedCaptures(active, releaseOutput = {
                            revokeCameraOutput(getApplication(), it)
                        })
                    }
                    cameraRecoveryScanned = true
                }
                importedPhotos = withContext(Dispatchers.IO) { storage.imports.list() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                importDiagnostic = storageFailureCode(error, StoragePhase.OPEN)
            }
        }
    }

    fun deferInterruptedCapture(id: PhotoId) {
        interruptedCaptures = interruptedCaptures.filterNot { it.id == id }
    }

    fun discardInterruptedCapture(offer: InterruptedCapture) {
        val candidate = offer.discardCandidate ?: return
        if (offer !in interruptedCaptures || candidate.photoId == captureInFlight) return
        runImport(StoragePhase.CAMERA_CAPTURE) {
            withContext(Dispatchers.IO) {
                revokeCameraOutput(getApplication(), candidate.photoId)
                storage.imports.discardInterruptedCapture(candidate)
            }
            deferInterruptedCapture(offer.id)
        }
    }

    fun receivePhoto(uri: Uri) {
        if (importCandidate != null || cameraPreviewId != null) return
        runImport(StoragePhase.IMPORT_STAGE) {
            val candidate = withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                require(uri.scheme == ContentResolver.SCHEME_CONTENT)
                val input = resolver.openInputStream(uri) ?: throw IOException("Photo is unavailable")
                input.use { storage.imports.receive(it) }
            }
            loadPreview(candidate)
        }
    }

    fun updateCaptureInFlight(id: PhotoId?) {
        captureInFlight = id
    }

    fun reportCameraError(error: Exception) {
        importDiagnostic = storageFailureCode(error, StoragePhase.CAMERA_CAPTURE)
    }

    fun requestCapture(launch: (PhotoId) -> Unit) {
        if (captureInFlight != null) return
        val previous = cameraPreviewId
        val preview = importPreview
        val ordinary = !recoveringConfirmedPhoto && selectedCaptureSource == null && captureSources.isEmpty()
        var id: PhotoId? = null
        runImport(StoragePhase.CAMERA_CAPTURE, after = { id?.let(launch) }) {
            if (previous != null) {
                if (!ordinary || preview?.candidate?.photoId != previous) {
                    throw ImportValidationFailure(ImportRejection.CAMERA_SOURCE_PROTECTED)
                }
                withContext(Dispatchers.IO) {
                    revokeCameraOutput(getApplication(), previous)
                    storage.imports.rejectCaptureForRetake(preview.candidate)
                }
                clearImportPreview()
            }
            id = withContext(Dispatchers.IO) { storage.imports.beginCapture() }
        }
    }

    fun cameraLaunchFailed(id: PhotoId, code: String) {
        runImport(StoragePhase.CAMERA_CAPTURE) {
            withContext(Dispatchers.IO) {
                revokeCameraOutput(getApplication(), id)
                storage.imports.finishCanceledCapture(id)
            }
            importDiagnostic = code
        }
    }

    fun cameraResult(id: PhotoId, success: Boolean, mode: CameraResultMode) {
        try {
            revokeCameraOutput(getApplication(), id)
        } catch (error: Exception) {
            reportCameraError(error)
            refreshImports()
            return
        }
        if (!success) {
            runImport(StoragePhase.CAMERA_CAPTURE) {
                withContext(Dispatchers.IO) { storage.imports.finishCanceledCapture(id) }
            }
        } else if (cameraPreviewId == null && importCandidate == null) {
            loadCameraResult(id, mode)
        }
    }

    fun previewCapture(id: PhotoId) = loadCameraResult(id, CameraResultMode.CRAFTPLAN_PREVIEW)

    private fun loadCameraResult(id: PhotoId, mode: CameraResultMode) {
        if (captureInFlight == id) return
        runImport(StoragePhase.CAMERA_CAPTURE) {
            cameraReviewHidden = mode == CameraResultMode.DIRECT_AFTER_SUCCESS
            try {
                clearImportPreview()
                cameraPreviewId = id
                try {
                    val candidate = withContext(Dispatchers.IO) {
                        revokeCameraOutput(getApplication(), id)
                        storage.imports.stageCapture(id)
                    }
                    loadPreview(candidate)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    captureSources = withContext(Dispatchers.IO) { storage.imports.captureSources(id) }
                    throw error
                }
                val preview = checkNotNull(importPreview)
                if (mode.confirmsAutomatically(resultSucceeded = true, sourceDamaged = preview.sourceDamaged)) {
                    saveConfirmedPreview(preview, PhotoConfirmation.AUTOMATIC, source = null)
                }
            } finally {
                // In direct mode never flash a second photo preview while saving.
                // A failed or damaged result retains the normal explicit review.
                cameraReviewHidden = false
            }
        }
    }

    fun showConfirmedRecovery(id: PhotoId) {
        if (id == captureInFlight) return
        runImport(StoragePhase.OPEN) {
            clearImportPreview()
            cameraPreviewId = id
            recoveringConfirmedPhoto = true
            captureSources = withContext(Dispatchers.IO) { storage.imports.captureSources(id) }
        }
    }

    fun openConfirmedRecovery() {
        val id = cameraPreviewId ?: return
        if (!recoveringConfirmedPhoto || id == captureInFlight) return
        runImport(StoragePhase.OPEN) {
            val (document, bitmap) = withContext(Dispatchers.IO) {
                revokeCameraOutput(getApplication(), id)
                val verified = storage.imports.recoverConfirmed(id)
                verified to decodePhoto(verified.photoBytes, verified.snapshot.photo)
            }
            clearImportPreview()
            failedPhotoRecovery = null
            storageDiagnostic = null
            enterEditor(document, bitmap)
        }
    }

    fun previewCaptureSource(source: CaptureSource) {
        if (source.photoId != cameraPreviewId || source.photoId == captureInFlight || source !in captureSources) return
        runImport(StoragePhase.IMPORT_PREVIEW) {
            selectedCaptureSource = source
            selectedCaptureDigest = null
            importPreview = null
            importCandidate = null
            val copy = withContext(Dispatchers.IO) {
                if (source is CaptureSource.Camera) revokeCameraOutput(getApplication(), source.photoId)
                storage.imports.readCaptureCopy(source)
            }
            selectedCaptureDigest = copy.candidate.digest
            importPreview = withContext(Dispatchers.IO) { storage.imports.previewCaptureCopy(copy) }
        }
    }

    fun discardSelectedCaptureSource() {
        val source = selectedCaptureSource ?: return
        val digest = selectedCaptureDigest ?: return
        if (source.photoId == captureInFlight) return
        runImport(StoragePhase.CAMERA_CAPTURE) {
            captureSources = withContext(Dispatchers.IO) {
                if (source is CaptureSource.Camera) revokeCameraOutput(getApplication(), source.photoId)
                storage.imports.discardCaptureSource(source, digest)
                storage.imports.captureSources(source.photoId)
            }
            selectedCaptureSource = null
            selectedCaptureDigest = null
            importPreview = null
            if (captureSources.isEmpty()) {
                failedPhotoRecovery = null
                clearImportPreview()
            }
        }
    }

    fun previewImport(candidate: ImportCandidate) {
        runImport(StoragePhase.IMPORT_PREVIEW) { loadPreview(candidate) }
    }

    private suspend fun loadPreview(candidate: ImportCandidate) {
        importCandidate = candidate
        importPreview = null
        importPreview = withContext(Dispatchers.IO) { storage.imports.preview(candidate) }
    }

    fun deferImport() {
        if (importBusy) return
        clearImportPreview()
        importDiagnostic = null
    }

    private fun clearImportPreview() {
        importPreview = null
        importCandidate = null
        cameraPreviewId = null
        captureSources = emptyList()
        selectedCaptureSource = null
        selectedCaptureDigest = null
        recoveringConfirmedPhoto = false
    }

    fun confirmImport(displayed: ImportCandidate, confirmation: PhotoConfirmation) {
        val preview = importPreview?.takeIf { it.candidate == displayed } ?: return
        require(confirmation != PhotoConfirmation.AUTOMATIC)
        val source = selectedCaptureSource
        runImport(StoragePhase.REVISION_CONFIRM) { saveConfirmedPreview(preview, confirmation, source) }
    }

    private suspend fun saveConfirmedPreview(
        preview: ImportPreview, confirmation: PhotoConfirmation, source: CaptureSource?,
    ) {
        val document = withContext(Dispatchers.IO) { storage.imports.confirmPreview(preview, confirmation, source) }
        check(document.snapshot.photo == preview.photo)
        clearImportPreview()
        enterEditor(document, preview.bitmap)
    }

    fun discardImport() {
        if (recoveringConfirmedPhoto) return
        val capture = cameraPreviewId
        val candidate = importCandidate
        if (capture == null && candidate == null) return
        runImport(if (capture == null) StoragePhase.IMPORT_STAGE else StoragePhase.CAMERA_CAPTURE) {
            withContext(Dispatchers.IO) {
                if (capture != null) {
                    revokeCameraOutput(getApplication(), capture)
                    storage.imports.discardCapture(capture)
                } else storage.imports.discard(checkNotNull(candidate))
            }
            clearImportPreview()
        }
    }

    private fun runImport(phase: StoragePhase, after: () -> Unit = {}, action: suspend () -> Unit) {
        if (importBusy || openingPhoto || photo != null) return
        val refresh = importRefresh
        refresh?.cancel()
        importBusy = true
        importDiagnostic = null
        viewModelScope.launch {
            try {
                // A canceled startup scan may still be leaving a blocking filesystem call.
                // Wait before creating a new camera output it could otherwise discover.
                refresh?.join()
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                importDiagnostic = storageFailureCode(error, phase)
            } finally {
                try {
                    importedPhotos = withContext(Dispatchers.IO) { storage.imports.list() }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (importDiagnostic == null) importDiagnostic = storageFailureCode(error, StoragePhase.OPEN)
                } finally {
                    importBusy = false
                }
            }
            after()
        }
    }

    fun closePhoto() {
        clearToolbarHint()
        check(saveJob?.isActive != true)
        cancelStressGeneration()
        stopDiagnostics()
        diagnosticGripBounds = null
        diagnosticSharedGripBounds = null
        retainedHistoryVersion = autosave?.confirmedVersion?.takeUnless { hasUnsavedChanges }
        if (retainedHistoryVersion == null) history = DimensionHistory()
        autosave = null
        gestureEditing = false
        photo = null
        currentPhoto = null
        clearTestAppearances()
        storageDiagnostic = null
        scene = DimensionScene()
    }

    fun editGesture(active: Boolean) {
        if (active && !gestureEditing) autosave?.beginEdit()
        gestureEditing = active
    }

    private fun queueCompletedChange() {
        val session = autosave ?: return
        session.finishedEdit(DocumentSnapshot(scene.dimensions, showNotes, checkNotNull(currentPhoto)))
        if (storageStatus == DocumentSaveStatus.SAVED || storageStatus == DocumentSaveStatus.SAVING ||
            session.hasCorrectedSnapshot
        ) {
            retrySave()
        }
    }

    fun retrySave() {
        val session = autosave ?: return
        if (!session.hasPendingChanges || saveJob?.isActive == true) return
        storageStatus = DocumentSaveStatus.SAVING
        storageDiagnostic = null
        saveJob = viewModelScope.launch {
            try {
                while (true) {
                    val request = session.nextRequest() ?: break
                    try {
                        val ack = withContext(Dispatchers.IO) {
                            if (request.snapshot.photo.id == PhotoId.SYNTHETIC_WORKBENCH) storage.synthetic.save(request)
                            else storage.imports.save(request)
                        }
                        if (!session.acknowledge(ack)) {
                            throw UncertainDocumentSave(StorageFailure(StoragePhase.ACK,
                                IllegalStateException("Unexpected revision acknowledgement")))
                        }
                    } catch (error: Exception) {
                        if (session.recordFailure(request, error)) continue
                        throw error
                    }
                }
                storageStatus = DocumentSaveStatus.SAVED
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                storageDiagnostic = storageFailureCode(error, StoragePhase.ACK)
                storageStatus = session.failureStatus ?: DocumentSaveStatus.FAILED
            }
        }
    }

    private var currentScene by mutableStateOf(DimensionScene())
    var scene: DimensionScene
        get() = currentScene
        set(next) {
            val previous = currentScene
            currentScene = next
            if (previous.tool != next.tool) diagnostics.action(
                DiagnosticAction.TOOL, DiagnosticResult.APPLIED, detail = next.tool.ordinal,
            )
            if (previous.dimensions.size != next.dimensions.size) diagnostics.action(
                DiagnosticAction.SCENE, DiagnosticResult.APPLIED, detail = next.dimensions.size,
            )
        }
    var history by mutableStateOf(DimensionHistory())
        private set
    val diagnostics = EditorDiagnostics()
    var diagnosticGripBounds: DiagnosticGripBounds? = null
    var diagnosticSharedGripBounds: DiagnosticGripBounds? = null
    var diagnosticsEnabled by mutableStateOf(false)
        private set
    var diagnosticsRecording by mutableStateOf(false)
        private set
    var diagnosticsColdStartArmed by mutableStateOf(false)
        private set

    fun toggleColdStartDiagnostics(): Boolean {
        if (!BuildConfig.ENABLE_STRESS_TOOLS) return false
        val source = preferences ?: return false
        val next = !diagnosticsColdStartArmed
        // Persist before confirming the request: the user may force-stop immediately.
        if (!source.edit().putBoolean(COLD_START_DIAGNOSTICS_KEY, next).commit()) return false
        diagnosticsColdStartArmed = next
        return true
    }

    fun toggleDiagnostics() {
        if (diagnosticsEnabled) stopDiagnostics()
        diagnosticsEnabled = !diagnosticsEnabled
    }

    fun startDiagnostics() {
        if (!BuildConfig.ENABLE_STRESS_TOOLS || !diagnosticsEnabled || diagnosticsRecording) return
        lastDrawnObjects = null
        visibleDrawCandidates = null
        diagnostics.start()
        diagnosticsRecording = true
    }

    fun stopDiagnostics() {
        diagnostics.stop()
        diagnosticsRecording = false
    }

    fun resetDiagnostics() {
        stopDiagnostics()
        diagnostics.reset()
    }

    var pendingDiagnosticExport: String? = null
    var lastDrawnObjects: Int? = null
    var visibleDrawCandidates: Int? = null
    var stressBusy by mutableStateOf(false)
        private set
    var stressGenerationMs by mutableStateOf<Double?>(null)
        private set
    private var stressJob: Job? = null

    fun generateStressBatch(photoId: PhotoId, count: Int) {
        if (!BuildConfig.ENABLE_STRESS_TOOLS || photoId != PhotoId.SYNTHETIC_WORKBENCH || scene.photoId != photoId ||
            count !in SyntheticDimensions.counts || stressBusy
        ) return
        stressBusy = true
        diagnostics.action(DiagnosticAction.GENERATE, DiagnosticResult.START, detail = count)
        stressJob = viewModelScope.launch {
            try {
                val (batch, elapsed) = withContext(Dispatchers.Default) {
                    val start = System.nanoTime()
                    val dimensions = SyntheticDimensions.generate(photoId, count)
                    dimensions to (System.nanoTime() - start) / 1_000_000.0
                }
                stressGenerationMs = elapsed
                // Use the current scene: manual edits made while the worker ran must survive.
                applyDimensionChange(scene.replaceSynthetic(batch))
                diagnostics.action(DiagnosticAction.GENERATE, DiagnosticResult.APPLIED, detail = elapsed * 1_000_000)
            } catch (canceled: CancellationException) {
                diagnostics.action(DiagnosticAction.GENERATE, DiagnosticResult.CANCELED)
                throw canceled
            } finally {
                stressBusy = false
            }
        }
    }

    fun clearStressBatch(photoId: PhotoId) {
        if (!BuildConfig.ENABLE_STRESS_TOOLS || photoId != PhotoId.SYNTHETIC_WORKBENCH || scene.photoId != photoId || stressBusy) return
        stressGenerationMs = null
        applyDimensionChange(scene.replaceSynthetic(emptyList()))
        diagnostics.action(DiagnosticAction.CLEAR_BATCH, DiagnosticResult.APPLIED)
    }

    fun cancelStressGeneration() { stressJob?.cancel() }
    val inkController = InkDraftController(viewModelScope)
    var valueDraft by mutableStateOf<DimensionValueDraft?>(null)
    var showNotes by mutableStateOf(true)
    var toolbarExpanded by mutableStateOf(false)
        private set
    var toolbarExtraExpanded by mutableStateOf(false)
        private set
    var fitRequest by mutableStateOf(0)
        private set
    var dimensionsRequireSelection by mutableStateOf(false)
        private set
    var toolbarLayout by mutableStateOf(ToolbarLayout.Default)
        private set
    var cameraPreviewEnabled by mutableStateOf(true)
        private set
    var toolbarHintsEnabled by mutableStateOf(true)
        private set
    var toolbarHint by mutableStateOf<Int?>(null)
        private set
    private var toolbarHintJob: Job? = null
    var labelsLocked by mutableStateOf(false)
        private set
    var statusBarSettings by mutableStateOf(EditorStatusBarSettings())
        private set
    var dimensionAppearance by mutableStateOf(DimensionAppearance())
        private set
    var dimensionTestAppearances by mutableStateOf(DimensionTestAppearances())
        private set

    fun updateTestAppearance(id: UUID, value: DimensionAppearance?) {
        val dimension = scene.find(id) ?: return
        if (currentPhoto?.id != dimension.photoId || autosave == null) return
        dimensionTestAppearances = if (value == null) {
            dimensionTestAppearances.withoutOverride(dimension)
        } else {
            dimensionTestAppearances.withOverride(dimension, value)
        }
    }

    fun clearTestAppearances() {
        dimensionTestAppearances = DimensionTestAppearances()
    }
    private var preferences: SharedPreferences? = null

    fun restorePreferences(source: SharedPreferences) {
        if (preferences != null) return
        preferences = source
        if (BuildConfig.ENABLE_STRESS_TOOLS) {
            diagnosticsColdStartArmed = source.getBoolean(COLD_START_DIAGNOSTICS_KEY, false)
            if (consumeColdStartDiagnosticRequest(diagnosticsColdStartArmed) {
                    source.edit().remove(COLD_START_DIAGNOSTICS_KEY).commit()
                }) {
                diagnosticsColdStartArmed = false
                diagnosticsEnabled = true
                startDiagnostics()
                diagnostics.coldStart()
            }
        }
        toolbarExpanded = source.getBoolean("toolbar_expanded", false)
        toolbarExtraExpanded = source.getBoolean("toolbar_extra_expanded", false)
        dimensionsRequireSelection = source.getBoolean("dimensions_require_selection", false)
        toolbarLayout = ToolbarLayout.fromPreference(source.all["toolbar_icon_layout"] as? String)
        cameraPreviewEnabled = source.getBoolean("camera_preview_enabled", true)
        toolbarHintsEnabled = source.getBoolean("toolbar_hints_enabled", true)
        labelsLocked = source.getBoolean("labels_locked", false)
        val legacyAppearance = source.getInt("dimension_appearance_calibration", 0) < 1
        val geometryPercent = source.getInt("dimension_geometry_size", 100)
        val labelPercent = source.getInt("dimension_label_size", 100)
        val tolerance = source.getInt("dimension_inline_drag_tolerance_degrees", DEFAULT_INLINE_DRAG_TOLERANCE_DEGREES)
        dimensionAppearance = DimensionAppearance(
            inline = source.getBoolean("dimension_label_inline", true),
            background = if (legacyAppearance) DimensionLabelBackground.TRANSPARENT else
                DimensionLabelBackground.fromPreference(source.getString("dimension_label_background", null),
                    source.getBoolean("dimension_label_transparent", true)),
            geometryPercent = if (legacyAppearance) rebaseLegacyDimensionSize(geometryPercent, DIMENSION_GEOMETRY_BASE_PERCENT)
                else dimensionSizePercent(geometryPercent),
            labelPercent = if (legacyAppearance) rebaseLegacyDimensionSize(labelPercent, DIMENSION_LABEL_BASE_PERCENT)
                else dimensionSizePercent(labelPercent),
            inlineDragToleranceDegrees = if (legacyAppearance && tolerance == 20) DEFAULT_INLINE_DRAG_TOLERANCE_DEGREES
                else clampInlineDragTolerance(tolerance),
            geometryColor = DimensionColor.fromPreference(source.getString("dimension_geometry_color", null), DimensionColor.YELLOW),
            labelColor = DimensionColor.fromPreference(source.getString("dimension_label_color", null), DimensionColor.WHITE),
        )
        if (legacyAppearance) {
            // Old transparency was false by default and was written even when only a size changed.
            // Adopt the new reference default once; subsequent choices are retained unchanged.
            source.edit().putString("dimension_label_background", dimensionAppearance.background.name)
                .putInt("dimension_geometry_size", dimensionAppearance.geometryPercent)
                .putInt("dimension_label_size", dimensionAppearance.labelPercent)
                .putInt("dimension_inline_drag_tolerance_degrees", dimensionAppearance.inlineDragToleranceDegrees)
                .putInt("dimension_appearance_calibration", 1).apply()
        }
        statusBarSettings = EditorStatusBarSettings(
            portraitOverride = if (source.contains("status_bar_hide_portrait")) source.getBoolean("status_bar_hide_portrait", false) else null,
            landscapeOverride = if (source.contains("status_bar_hide_landscape")) source.getBoolean("status_bar_hide_landscape", false) else null,
        )
    }

    fun moveToolbarIcon(icon: ToolbarIcon, drop: ToolbarDrop) {
        updateToolbarLayout(toolbarLayout.move(icon, drop))
    }

    fun resetToolbarLayout() {
        updateToolbarLayout(ToolbarLayout.Default)
    }

    private fun updateToolbarLayout(layout: ToolbarLayout) {
        if (layout == toolbarLayout) return
        toolbarLayout = layout
        preferences?.edit()?.putString("toolbar_icon_layout", layout.toPreference())?.apply()
    }

    fun updateCameraPreviewEnabled(enabled: Boolean) {
        cameraPreviewEnabled = enabled
        preferences?.edit()?.putBoolean("camera_preview_enabled", enabled)?.apply()
    }

    fun updateToolbarHintsEnabled(enabled: Boolean) {
        toolbarHintsEnabled = enabled
        preferences?.edit()?.putBoolean("toolbar_hints_enabled", enabled)?.apply()
        if (!enabled) clearToolbarHint()
    }

    fun clearToolbarHint() {
        toolbarHintJob?.cancel()
        toolbarHintJob = null
        toolbarHint = null
    }

    fun showToolbarHint(label: Int) {
        if (!toolbarHintsEnabled) return
        toolbarHintJob?.cancel()
        toolbarHint = label
        toolbarHintJob = viewModelScope.launch {
            delay(3_000)
            toolbarHint = null
            toolbarHintJob = null
        }
    }

    fun updateDimensionsRequireSelection(enabled: Boolean) {
        dimensionsRequireSelection = enabled
        preferences?.edit()?.putBoolean("dimensions_require_selection", enabled)?.apply()
    }

    fun updateDimensionAppearance(value: DimensionAppearance) {
        if (dimensionAppearance == value) return
        dimensionAppearance = value
        preferences?.edit()
            ?.putBoolean("dimension_label_inline", value.inline)
            ?.putString("dimension_label_background", value.background.name)
            ?.putString("dimension_geometry_color", value.geometryColor.name)
            ?.putString("dimension_label_color", value.labelColor.name)
            ?.putInt("dimension_geometry_size", value.geometryPercent)
            ?.putInt("dimension_label_size", value.labelPercent)
            ?.putInt("dimension_inline_drag_tolerance_degrees", value.inlineDragToleranceDegrees)
            ?.apply()
    }

    fun setStatusBarHidden(portrait: Boolean, hidden: Boolean) {
        statusBarSettings = statusBarSettings.withHidden(portrait, hidden)
        preferences?.edit()?.putBoolean(
            if (portrait) "status_bar_hide_portrait" else "status_bar_hide_landscape", hidden,
        )?.apply()
    }

    private fun recordToolbarState() {
        diagnostics.action(
            DiagnosticAction.TOOLBAR, DiagnosticResult.APPLIED,
            detail = (if (toolbarExpanded) 1 else 0) + (if (toolbarExtraExpanded) 2 else 0),
        )
    }

    fun applyToolbarHorizontalDrag(delta: Float, threshold: Float) {
        val (expanded, extraExpanded) = toolbarHorizontalExpansionAfterDrag(
            toolbarExpanded, toolbarExtraExpanded, delta, threshold,
        )
        if (toolbarExpanded == expanded && toolbarExtraExpanded == extraExpanded) return
        toolbarExpanded = expanded
        toolbarExtraExpanded = extraExpanded
        recordToolbarState()
        preferences?.edit()
            ?.putBoolean("toolbar_expanded", expanded)
            ?.putBoolean("toolbar_extra_expanded", extraExpanded)
            ?.apply()
    }

    fun setToolbarExtraExpansion(expanded: Boolean) {
        if (toolbarExtraExpanded == expanded) return
        toolbarExtraExpanded = expanded
        recordToolbarState()
        preferences?.edit()?.putBoolean("toolbar_extra_expanded", expanded)?.apply()
    }

    fun requestFit() {
        fitRequest++
        diagnostics.action(DiagnosticAction.FIT, DiagnosticResult.APPLIED)
    }

    fun toggleNotes() {
        showNotes = !showNotes
        queueCompletedChange()
        diagnostics.action(DiagnosticAction.NOTES, DiagnosticResult.APPLIED, detail = if (showNotes) 1 else 0)
    }

    fun deleteSelected() {
        val deleted = scene.selectedId ?: return
        applyDimensionChange(scene.deleteSelected())
        diagnostics.action(DiagnosticAction.DELETE, DiagnosticResult.APPLIED, deleted)
    }

    fun toggleLabelLock() {
        labelsLocked = !labelsLocked
        diagnostics.action(DiagnosticAction.LABEL_LOCK, DiagnosticResult.APPLIED, detail = if (labelsLocked) 1 else 0)
        preferences?.edit()?.putBoolean("labels_locked", labelsLocked)?.apply()
    }

    fun applyDimensionChange(next: DimensionScene): Boolean {
        if (autosave == null) return false
        val previousHistory = history
        history = history.record(scene, next)
        scene = next
        val changed = history !== previousHistory
        if (changed) queueCompletedChange()
        return changed
    }

    fun writeDiagnosticExport(resolver: ContentResolver, uri: Uri, data: String, finished: (Boolean) -> Unit) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val output = resolver.openOutputStream(uri) ?: throw IOException("No output stream")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(data) }
                    true
                } catch (_: IOException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }
            finished(saved)
        }
    }

    fun undo() {
        val (previous, nextHistory) = history.undo(scene) ?: return
        scene = previous
        history = nextHistory
        queueCompletedChange()
        diagnostics.action(DiagnosticAction.UNDO, DiagnosticResult.APPLIED)
    }

    fun redo() {
        val (next, nextHistory) = history.redo(scene) ?: return
        scene = next
        history = nextHistory
        queueCompletedChange()
        diagnostics.action(DiagnosticAction.REDO, DiagnosticResult.APPLIED)
    }

    fun openValue(dimension: PhotoDimension) {
        val current = scene.find(dimension.id)
        val opened = autosave != null && current == dimension &&
            valueDraft == null && !inkController.hasPendingInput
        if (opened) {
            autosave?.beginEdit()
            valueDraft = DimensionValueDraft(checkNotNull(current))
        }
        diagnostics.action(DiagnosticAction.OPEN_VALUE, if (opened) DiagnosticResult.APPLIED else DiagnosticResult.REJECTED, dimension.id)
    }

    fun confirmValue(): Boolean {
        if (inkController.hasPendingInput) return false
        val draft = valueDraft ?: return false
        val current = scene.find(draft.targetId) ?: return false
        val confirmed = draft.confirm(current) ?: run {
            diagnostics.action(DiagnosticAction.COMMIT_VALUE, DiagnosticResult.REJECTED, current.id)
            return false
        }
        diagnostics.action(
            DiagnosticAction.COMMIT_VALUE,
            if (confirmed == current) DiagnosticResult.UNCHANGED else DiagnosticResult.APPLIED,
            current.id,
        )
        applyDimensionChange(scene.edit(current, confirmed))
        valueDraft = null
        return true
    }
}

/**
 * The activity and each editor dialog own their own window policy. Closing a
 * dialog must not request visible bars over the still-active photo editor.
 */
@Composable
private fun EditorStatusBarEffect(
    window: Window?,
    active: Boolean,
    hidden: Boolean,
    restoreOnDispose: Boolean = true,
) {
    DisposableEffect(window, active, hidden, restoreOnDispose) {
        if (!active || window == null) {
            onDispose { }
        } else {
            val decor = window.decorView
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            var previousAppearance: Int? = null
            var previousBehavior: Int? = null
            fun applyPolicy() {
                // A dialog can compose before its decor is attached.
                val controller = window.insetsController ?: return
                if (previousAppearance == null) {
                    previousAppearance = controller.systemBarsAppearance and mask
                    previousBehavior = controller.systemBarsBehavior
                }
                controller.setSystemBarsAppearance(0, mask)
                controller.systemBarsBehavior = if (hidden) {
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    WindowInsetsController.BEHAVIOR_DEFAULT
                }
                if (hidden) controller.hide(AndroidWindowInsets.Type.statusBars())
                else controller.show(AndroidWindowInsets.Type.statusBars())
            }
            val observer = decor.viewTreeObserver
            val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
                if (focused) applyPolicy()
            }
            observer.addOnWindowFocusChangeListener(listener)
            applyPolicy()
            onDispose {
                if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)
                else decor.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
                if (restoreOnDispose) {
                    window.insetsController?.let { controller ->
                        controller.show(AndroidWindowInsets.Type.statusBars())
                        previousBehavior?.let { controller.systemBarsBehavior = it }
                        previousAppearance?.let { controller.setSystemBarsAppearance(it, mask) }
                    }
                }
            }
        }
    }
}

/** Configure the dialog's own window, never the activity or IME window. */
@Suppress("DEPRECATION")
@Composable
internal fun EditorDialogStatusBarEffect(hidden: Boolean) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(window) {
        // Android 13/14 still use this window color; newer edge-to-edge windows
        // are transparent by platform policy.
        window?.statusBarColor = android.graphics.Color.TRANSPARENT
        onDispose { }
    }
    EditorStatusBarEffect(window, active = true, hidden = hidden, restoreOnDispose = false)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CraftPlan(
    editorState: PhotoEditorState, window: Window, onCapture: () -> Unit, onStopCameraWait: () -> Unit,
) {
    val photo = editorState.photo
    val photoOpen = photo != null
    LaunchedEffect(photoOpen) { if (!photoOpen) editorState.refreshImports() }
    var confirmLeave by remember { mutableStateOf(false) }
    if (BuildConfig.ENABLE_STRESS_TOOLS) DiagnosticsRecordingEffects(editorState, window)
    PrepareDimensionDialog(editorState.diagnostics.takeIf { BuildConfig.ENABLE_STRESS_TOOLS && editorState.diagnosticsRecording })
    BackHandler(enabled = photoOpen && editorState.valueDraft == null) {
        if (editorState.hasUnsavedChanges) confirmLeave = true else editorState.closePhoto()
    }
    LaunchedEffect(editorState.saveStatus) {
        if (!editorState.hasUnsavedChanges) confirmLeave = false
    }
    if (confirmLeave && editorState.hasUnsavedChanges) {
        val writing = editorState.saveStatus == DocumentSaveStatus.SAVING
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.document_leave_title)) },
            text = { Text(stringResource(if (writing) R.string.document_leave_wait else R.string.document_leave_warning)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.document_stay)) }
            },
            dismissButton = if (!writing) {
                {
                    TextButton(onClick = {
                        confirmLeave = false
                        editorState.closePhoto()
                    }) { Text(stringResource(R.string.document_leave)) }
                }
            } else null,
        )
    }
    val configuration = LocalConfiguration.current
    val portrait = configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP
    val hideStatusBar = editorState.statusBarSettings.hidden(portrait, isTablet)
    EditorStatusBarEffect(window, photoOpen, hideStatusBar)

    // The entire editor toolbar can sit behind the transparent gesture navigation bar.
    // Draw the photo beneath the status bar. Reserve stable horizontal insets,
    // desktop caption controls and the existing bottom navigation/IME space.
    val contentInsets = if (photoOpen) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            .union(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal))
            .union(WindowInsets.captionBar.only(WindowInsetsSides.Top))
            .union(WindowInsets.navigationBars.only(WindowInsetsSides.Top))
            .union(WindowInsets.tappableElement.only(WindowInsetsSides.Bottom))
            .union(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
            .union(WindowInsets.ime.only(WindowInsetsSides.Bottom))
    } else {
        WindowInsets.safeDrawing
    }
    Scaffold(contentWindowInsets = contentInsets) { insets ->
        val screen = Modifier.fillMaxSize().padding(insets)
        if (photo != null) {
            val direction = LocalLayoutDirection.current
            val density = LocalDensity.current
            val statusTop = WindowInsets.statusBarsIgnoringVisibility.getTop(density)
            val cutoutTop = WindowInsets.displayCutout.getTop(density)
            val contentTop = with(density) { insets.calculateTopPadding().roundToPx() }
            val safeOverlayTop = editorOverlayTop(statusTop, cutoutTop, contentTop)
            val statusScrimHeight = if (!hideStatusBar) {
                (statusTop - contentTop).coerceAtLeast(0)
            } else 0
            val repeatHintOffset = toolbarHintCenterOffset(
                insets.calculateLeftPadding(direction), insets.calculateRightPadding(direction),
            )
            SamplePhoto(
                modifier = screen,
                safeOverlayTopPx = safeOverlayTop,
                statusScrimHeightPx = statusScrimHeight,
                photo = photo,
                media = checkNotNull(editorState.currentPhoto),
                editable = editorState.saveStatus != DocumentSaveStatus.RECOVERED,
                scene = editorState.scene,
                onCreateDimension = { editorState.applyDimensionChange(editorState.scene.create(it)) },
                onChangeDimension = { old, new -> editorState.applyDimensionChange(editorState.scene.edit(old, new)) },
                onSelectDimension = { editorState.scene = editorState.scene.select(it) },
                onEditValue = editorState::openValue,
                onGestureEditing = editorState::editGesture,
                showNotes = editorState.showNotes,
                labelsLocked = editorState.labelsLocked,
                dimensionsRequireSelection = editorState.dimensionsRequireSelection,
                appearance = editorState.dimensionAppearance,
                testAppearances = editorState.dimensionTestAppearances,
                automaticSizeFactor = automaticDimensionSizeFactor(configuration.smallestScreenWidthDp),
                fitRequest = editorState.fitRequest,
                diagnostics = editorState.diagnostics.takeIf {
                    BuildConfig.ENABLE_STRESS_TOOLS && editorState.scene.photoId == PhotoId.SYNTHETIC_WORKBENCH
                },
                onDrawCounts = { drawn, visible ->
                    editorState.lastDrawnObjects = drawn
                    editorState.visibleDrawCandidates = visible
                },
                overlays = {
                    if (BuildConfig.ENABLE_STRESS_TOOLS && editorState.scene.photoId == PhotoId.SYNTHETIC_WORKBENCH) {
                        DiagnosticsOverlay(editorState)
                    }
                },
                storageStatus = {
                    DocumentStatus(editorState.saveStatus, editorState.storageDiagnostic, editorState::retrySave,
                        recoveredFromMedia = editorState.recoveredFromMedia)
                },
                toolbarState = editorState,
                toolbar = { modifier, onFixedToolbarTop, onGripRegion ->
                    if (editorState.saveStatus != DocumentSaveStatus.RECOVERED) EditorToolbar(
                        editorState, repeatHintOffset,
                        modifier.alpha(if (editorState.valueDraft == null) 1f else 0f),
                        onFixedToolbarTop = onFixedToolbarTop,
                        onGripRegion = onGripRegion,
                    )
                },
            )
            editorState.valueDraft?.let { draft ->
                DimensionValueDialog(
                    draft = draft,
                    ink = editorState.inkController,
                    onChange = { update -> editorState.valueDraft = editorState.valueDraft?.let(update) },
                    onConfirm = editorState::confirmValue,
                    onCancel = {
                        editorState.diagnostics.action(DiagnosticAction.CANCEL_VALUE, DiagnosticResult.CANCELED)
                        editorState.valueDraft = null
                    },
                    showNotes = editorState.showNotes,
                    onToggleNotes = editorState::toggleNotes,
                    hideStatusBarPortrait = editorState.statusBarSettings.hidden(true, isTablet),
                    hideStatusBarLandscape = editorState.statusBarSettings.hidden(false, isTablet),
                    onStatusBarHidden = editorState::setStatusBarHidden,
                    appearance = editorState.dimensionAppearance,
                    onAppearanceChange = editorState::updateDimensionAppearance,
                    testAppearance = editorState.dimensionTestAppearances.values[draft.targetId]
                        .takeIf { editorState.dimensionTestAppearances.photoId == editorState.scene.photoId },
                    onTestAppearanceChange = { editorState.updateTestAppearance(draft.targetId, it) },
                    diagnostics = editorState.diagnostics.takeIf { BuildConfig.ENABLE_STRESS_TOOLS && editorState.diagnosticsRecording },
                )
            }
        } else {
            Column(
                modifier = screen.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                if (BuildConfig.ENABLE_STRESS_TOOLS) DiagnosticsOverlay(editorState)
                Button(onClick = { editorState.openPhoto() }, enabled = !editorState.openingPhoto && !editorState.importBusy) {
                    Text(stringResource(if (editorState.openingPhoto) R.string.document_opening else R.string.open_sample))
                }
                editorState.storageDiagnostic?.let { code ->
                    Text(stringResource(R.string.document_open_failed))
                    Text(stringResource(R.string.document_storage_diagnostic, code))
                }
                PhotoImportControls(editorState, onCapture, onStopCameraWait)
                val number = BuildConfig.M1_BUILD_NUMBER
                Text(
                    if (number > 0) stringResource(R.string.installed_version, BuildConfig.VERSION_NAME, number)
                    else stringResource(R.string.installed_local_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DocumentStatus(
    status: DocumentSaveStatus, diagnostic: String?, retry: () -> Unit, recoveredFromMedia: Boolean,
) {
    val label = when (status) {
        DocumentSaveStatus.SAVING -> R.string.document_saving
        DocumentSaveStatus.SAVED -> R.string.document_saved
        DocumentSaveStatus.EDITING -> R.string.document_editing
        DocumentSaveStatus.FAILED -> R.string.document_save_failed
        DocumentSaveStatus.UNCERTAIN -> R.string.document_save_uncertain
        DocumentSaveStatus.CONFLICT -> R.string.document_save_conflict
        DocumentSaveStatus.RECOVERED -> if (recoveredFromMedia) {
            R.string.document_recovered_media
        } else R.string.document_recovered
    }
    Column(Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)) {
        Text(stringResource(label), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium)
        if (status == DocumentSaveStatus.FAILED || status == DocumentSaveStatus.UNCERTAIN ||
            status == DocumentSaveStatus.CONFLICT
        ) {
            diagnostic?.let { code ->
                Text(stringResource(R.string.document_storage_diagnostic, code),
                    Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = retry) { Text(stringResource(R.string.document_retry)) }
        }
    }
}

@Composable
private fun SamplePhoto(
    modifier: Modifier,
    safeOverlayTopPx: Int,
    statusScrimHeightPx: Int,
    photo: Bitmap,
    media: StoredPhoto,
    editable: Boolean,
    scene: DimensionScene,
    onCreateDimension: (PhotoDimension) -> Boolean,
    onChangeDimension: (PhotoDimension, PhotoDimension) -> Boolean,
    onSelectDimension: (UUID?) -> Unit,
    onEditValue: (PhotoDimension) -> Unit,
    onGestureEditing: (Boolean) -> Unit,
    showNotes: Boolean,
    labelsLocked: Boolean,
    dimensionsRequireSelection: Boolean,
    appearance: DimensionAppearance,
    testAppearances: DimensionTestAppearances,
    automaticSizeFactor: Float,
    fitRequest: Int,
    diagnostics: EditorDiagnostics?,
    onDrawCounts: (Int, Int) -> Unit,
    overlays: @Composable () -> Unit,
    storageStatus: @Composable () -> Unit,
    toolbarState: PhotoEditorState,
    toolbar: @Composable (Modifier, (Float) -> Unit, (ToolbarGripRegion) -> Unit) -> Unit,
) {
    var editorOrigin by remember { mutableStateOf<Offset?>(null) }
    var editorView by remember { mutableStateOf<PhotoEditorView?>(null) }
    var gripRegion by remember { mutableStateOf<ToolbarGripRegion?>(null) }
    var fixedToolbarTop by remember { mutableStateOf<Float?>(null) }
    val fixedToolbarTopPx = editorOrigin?.let { origin -> fixedToolbarTop?.let { (it - origin.y).roundToInt() } }
    // The full-size editor and toolbar share this content Box's local coordinates.
    Box(modifier = modifier.toolbarGrip(
        toolbarState, { gripRegion }, { editorOrigin },
        { x, y, input -> editorView?.hasToolbarGesturePriority(x, y, input) ?: true },
    )) {
        AndroidView(
            factory = { context ->
                PhotoEditorView(context, photo, media).apply {
                    editorView = this
                    this.diagnostics = diagnostics
                    this.safeOverlayTopPx = safeOverlayTopPx
                    this.scene = scene
                }
            },
            modifier = Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                editorOrigin = coordinates.positionInRoot()
            },
            update = { view ->
                view.safeOverlayTopPx = safeOverlayTopPx
                view.fixedToolbarTopPx = fixedToolbarTopPx
                view.onCreateDimension = onCreateDimension
                view.onChangeDimension = onChangeDimension
                view.onSelectDimension = onSelectDimension
                view.onEditValue = onEditValue
                view.onGestureEditing = onGestureEditing
                view.editable = editable
                view.onDrawCounts = onDrawCounts
                view.showNotes = showNotes
                view.labelsLocked = labelsLocked
                view.dimensionsRequireSelection = dimensionsRequireSelection
                view.appearance = appearance
                view.testAppearances = testAppearances
                view.automaticSizeFactor = automaticSizeFactor
                view.scene = scene
                view.fitRequest = fitRequest
            },
        )
        // A translucent contrast layer only behind visible system icons. No hit target.
        val density = LocalDensity.current
        if (statusScrimHeightPx > 0) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .height(with(density) { statusScrimHeightPx.toDp() })
                .background(Color.Black.copy(alpha = 0.55f)))
        }
        toolbar(Modifier.align(Alignment.BottomStart), { fixedToolbarTop = it }, { gripRegion = it })
        Box(Modifier.align(Alignment.TopStart)
            .padding(start = 4.dp, top = with(density) { safeOverlayTopPx.toDp() } + 4.dp)) { storageStatus() }
        Box(Modifier.align(Alignment.TopEnd)
            .padding(top = with(density) { safeOverlayTopPx.toDp() })) { overlays() }
    }
}
