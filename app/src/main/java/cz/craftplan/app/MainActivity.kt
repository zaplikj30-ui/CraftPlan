package cz.craftplan.app

import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val editorState by viewModels<PhotoEditorState>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editorState.restorePreferences(getSharedPreferences("editor_preferences", MODE_PRIVATE))
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                CraftPlan(editorState)
            }
        }
    }
}

internal class PhotoEditorState : ViewModel() {
    var scene by mutableStateOf(DimensionScene())
    var history by mutableStateOf(DimensionHistory())
        private set
    val stressMetrics = EditorStressMetrics()
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
        stressJob = viewModelScope.launch {
            try {
                val (batch, elapsed) = withContext(Dispatchers.Default) {
                    val start = System.nanoTime()
                    val dimensions = SyntheticDimensions.generate(photoId, count)
                    dimensions to (System.nanoTime() - start) / 1_000_000.0
                }
                stressMetrics.reset()
                stressGenerationMs = elapsed
                // Use the current scene: manual edits made while the worker ran must survive.
                applyDimensionChange(scene.replaceSynthetic(batch))
            } finally {
                stressBusy = false
            }
        }
    }

    fun clearStressBatch(photoId: PhotoId) {
        if (!BuildConfig.ENABLE_STRESS_TOOLS || photoId != PhotoId.SYNTHETIC_WORKBENCH || stressBusy) return
        stressMetrics.reset()
        stressGenerationMs = null
        applyDimensionChange(scene.replaceSynthetic(emptyList()))
    }

    fun cancelStressGeneration() { stressJob?.cancel() }
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

    fun setToolbarExpansion(expanded: Boolean) {
        if (toolbarExpanded == expanded) return
        toolbarExpanded = expanded
        preferences?.edit()?.putBoolean("toolbar_expanded", expanded)?.apply()
    }

    fun setToolbarExtraExpansion(expanded: Boolean) {
        if (toolbarExtraExpanded == expanded) return
        toolbarExtraExpanded = expanded
        preferences?.edit()?.putBoolean("toolbar_extra_expanded", expanded)?.apply()
    }

    fun requestFit() { fitRequest++ }

    fun toggleLabelLock() {
        labelsLocked = !labelsLocked
        preferences?.edit()?.putBoolean("labels_locked", labelsLocked)?.apply()
    }

    fun applyDimensionChange(next: DimensionScene) {
        history = history.record(scene, next)
        scene = next
    }

    fun undo() {
        val (previous, nextHistory) = history.undo(scene) ?: return
        scene = previous
        history = nextHistory
    }

    fun redo() {
        val (next, nextHistory) = history.redo(scene) ?: return
        scene = next
        history = nextHistory
    }

    fun openValue(dimension: PhotoDimension) {
        val current = scene.find(dimension.id) ?: return
        if (current == dimension) valueDraft = DimensionValueDraft(current)
    }

    fun confirmValue(): Boolean {
        val draft = valueDraft ?: return false
        val current = scene.find(draft.targetId) ?: return false
        val confirmed = draft.confirm(current) ?: return false
        applyDimensionChange(scene.edit(current, confirmed))
        valueDraft = null
        return true
    }
}

@Composable
private fun CraftPlan(editorState: PhotoEditorState) {
    var photoOpen by rememberSaveable { mutableStateOf(false) }
    val closePhoto = {
        editorState.cancelStressGeneration()
        editorState.scene = editorState.scene.closePhoto()
        photoOpen = false
    }
    BackHandler(enabled = photoOpen && editorState.valueDraft == null, onBack = closePhoto)

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { insets ->
        val screen = Modifier.fillMaxSize().padding(insets)
        if (photoOpen) {
            SamplePhoto(
                modifier = screen,
                scene = editorState.scene,
                onCreateDimension = { editorState.applyDimensionChange(editorState.scene.create(it)) },
                onChangeDimension = { old, new -> editorState.applyDimensionChange(editorState.scene.edit(old, new)) },
                onSelectDimension = { editorState.scene = editorState.scene.select(it) },
                onClose = closePhoto,
                onEditValue = editorState::openValue,
                showNotes = editorState.showNotes,
                labelsLocked = editorState.labelsLocked,
                fitRequest = editorState.fitRequest,
                stressMetrics = editorState.stressMetrics.takeIf { BuildConfig.ENABLE_STRESS_TOOLS },
                toolbar = { modifier ->
                    EditorToolbar(editorState, modifier.alpha(if (editorState.valueDraft == null) 1f else 0f))
                },
            )
            editorState.valueDraft?.let { draft ->
                DimensionValueDialog(
                    draft = draft,
                    onChange = { update -> editorState.valueDraft = editorState.valueDraft?.let(update) },
                    onConfirm = editorState::confirmValue,
                    onCancel = { editorState.valueDraft = null },
                    showNotes = editorState.showNotes,
                    onToggleNotes = { editorState.showNotes = !editorState.showNotes },
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
                val context = LocalContext.current
                val installed = remember(context) {
                    context.packageManager.getPackageInfo(
                        context.packageName, PackageManager.PackageInfoFlags.of(0),
                    )
                }
                Text(
                    stringResource(
                        R.string.installed_version, installed.versionName.orEmpty(), installed.longVersionCode,
                    ),
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
    onCreateDimension: (PhotoDimension) -> Unit,
    onChangeDimension: (PhotoDimension, PhotoDimension) -> Unit,
    onSelectDimension: (UUID?) -> Unit,
    onClose: () -> Unit,
    onEditValue: (PhotoDimension) -> Unit,
    showNotes: Boolean,
    labelsLocked: Boolean,
    fitRequest: Int,
    stressMetrics: EditorStressMetrics?,
    toolbar: @Composable (Modifier) -> Unit,
) {
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.back_to_start)) }
                Text(
                    text = stringResource(R.string.sample_title),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { context ->
                    PhotoEditorView(context).apply {
                        this.stressMetrics = stressMetrics
                        this.scene = scene
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.onCreateDimension = onCreateDimension
                    view.onChangeDimension = onChangeDimension
                    view.onSelectDimension = onSelectDimension
                    view.onEditValue = onEditValue
                    view.showNotes = showNotes
                    view.labelsLocked = labelsLocked
                    view.scene = scene
                    view.fitRequest = fitRequest
                },
            )
            toolbar(Modifier.align(Alignment.BottomStart))
        }
    }
}
