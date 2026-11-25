package cz.craftplan.app

import android.content.ContentResolver
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Window
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val editorState by viewModels<PhotoEditorState>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editorState.restorePreferences(getSharedPreferences("editor_preferences", MODE_PRIVATE))
        editorState.diagnosticGripBounds = null
        editorState.diagnosticSharedGripBounds = null
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                CraftPlan(editorState, window)
            }
        }
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

internal class PhotoEditorState : ViewModel() {
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
        if (!BuildConfig.ENABLE_STRESS_TOOLS || photoId != PhotoId.SYNTHETIC_WORKBENCH ||
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
        if (!BuildConfig.ENABLE_STRESS_TOOLS || photoId != PhotoId.SYNTHETIC_WORKBENCH || stressBusy) return
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
    var labelsLocked by mutableStateOf(false)
        private set
    private var preferences: SharedPreferences? = null

    fun restorePreferences(source: SharedPreferences) {
        if (preferences != null) return
        preferences = source
        toolbarExpanded = source.getBoolean("toolbar_expanded", false)
        toolbarExtraExpanded = source.getBoolean("toolbar_extra_expanded", false)
        labelsLocked = source.getBoolean("labels_locked", false)
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
        val previousHistory = history
        history = history.record(scene, next)
        scene = next
        return history !== previousHistory
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
        diagnostics.action(DiagnosticAction.UNDO, DiagnosticResult.APPLIED)
    }

    fun redo() {
        val (next, nextHistory) = history.redo(scene) ?: return
        scene = next
        history = nextHistory
        diagnostics.action(DiagnosticAction.REDO, DiagnosticResult.APPLIED)
    }

    fun openValue(dimension: PhotoDimension) {
        val current = scene.find(dimension.id)
        val opened = current == dimension && valueDraft == null && !inkController.hasPendingInput
        if (opened) valueDraft = DimensionValueDraft(checkNotNull(current))
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

@Composable
private fun CraftPlan(editorState: PhotoEditorState, window: Window) {
    var photoOpen by rememberSaveable { mutableStateOf(false) }
    val closePhoto = {
        editorState.cancelStressGeneration()
        editorState.stopDiagnostics()
        editorState.diagnosticGripBounds = null
        editorState.diagnosticSharedGripBounds = null
        editorState.scene = editorState.scene.closePhoto()
        photoOpen = false
    }
    BackHandler(enabled = photoOpen && editorState.valueDraft == null, onBack = closePhoto)

    // The entire editor toolbar can sit behind the transparent gesture navigation bar.
    // Keep actual system buttons, display cutouts and the keyboard clear.
    val contentInsets = if (photoOpen) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            .union(WindowInsets.tappableElement.only(WindowInsetsSides.Bottom))
            .union(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
            .union(WindowInsets.ime.only(WindowInsetsSides.Bottom))
    } else {
        WindowInsets.safeDrawing
    }
    Scaffold(contentWindowInsets = contentInsets) { insets ->
        val screen = Modifier.fillMaxSize().padding(insets)
        if (photoOpen) {
            val direction = LocalLayoutDirection.current
            val repeatHintOffset = toolbarHintCenterOffset(
                insets.calculateLeftPadding(direction), insets.calculateRightPadding(direction),
            )
            SamplePhoto(
                modifier = screen,
                scene = editorState.scene,
                onCreateDimension = { editorState.applyDimensionChange(editorState.scene.create(it)) },
                onChangeDimension = { old, new -> editorState.applyDimensionChange(editorState.scene.edit(old, new)) },
                onSelectDimension = { editorState.scene = editorState.scene.select(it) },
                onEditValue = editorState::openValue,
                showNotes = editorState.showNotes,
                labelsLocked = editorState.labelsLocked,
                fitRequest = editorState.fitRequest,
                diagnostics = editorState.diagnostics.takeIf { BuildConfig.ENABLE_STRESS_TOOLS },
                onDrawCounts = { drawn, visible ->
                    editorState.lastDrawnObjects = drawn
                    editorState.visibleDrawCandidates = visible
                },
                overlays = { if (BuildConfig.ENABLE_STRESS_TOOLS) DiagnosticsOverlay(editorState, window) },
                toolbar = { modifier ->
                    EditorToolbar(
                        editorState, repeatHintOffset,
                        modifier.alpha(if (editorState.valueDraft == null) 1f else 0f),
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
                    diagnostics = editorState.diagnostics.takeIf { BuildConfig.ENABLE_STRESS_TOOLS && editorState.diagnosticsRecording },
                )
            }
        } else {
            Column(
                modifier = screen.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                Button(onClick = { photoOpen = true }) {
                    Text(stringResource(R.string.open_sample))
                }
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
private fun SamplePhoto(
    modifier: Modifier,
    scene: DimensionScene,
    onCreateDimension: (PhotoDimension) -> Boolean,
    onChangeDimension: (PhotoDimension, PhotoDimension) -> Boolean,
    onSelectDimension: (UUID?) -> Unit,
    onEditValue: (PhotoDimension) -> Unit,
    showNotes: Boolean,
    labelsLocked: Boolean,
    fitRequest: Int,
    diagnostics: EditorDiagnostics?,
    onDrawCounts: (Int, Int) -> Unit,
    overlays: @Composable () -> Unit,
    toolbar: @Composable (Modifier) -> Unit,
) {
    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                PhotoEditorView(context).apply {
                    this.diagnostics = diagnostics
                    this.scene = scene
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.onCreateDimension = onCreateDimension
                view.onChangeDimension = onChangeDimension
                view.onSelectDimension = onSelectDimension
                view.onEditValue = onEditValue
                view.onDrawCounts = onDrawCounts
                view.showNotes = showNotes
                view.labelsLocked = labelsLocked
                view.scene = scene
                view.fitRequest = fitRequest
            },
        )
        toolbar(Modifier.align(Alignment.BottomStart))
        Box(Modifier.align(Alignment.TopEnd)) { overlays() }
    }
}
