package cz.craftplan.app

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val editorState by viewModels<PhotoEditorState>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var valueDraft by mutableStateOf<DimensionValueDraft?>(null)
    var showNotes by mutableStateOf(true)

    fun openValue(dimension: PhotoDimension) {
        val current = scene.find(dimension.id) ?: return
        if (current == dimension) valueDraft = DimensionValueDraft(current)
    }

    fun confirmValue(): Boolean {
        val draft = valueDraft ?: return false
        val current = scene.find(draft.targetId) ?: return false
        val confirmed = draft.confirm(current) ?: return false
        scene = scene.edit(current, confirmed)
        valueDraft = null
        return true
    }
}

@Composable
private fun CraftPlan(editorState: PhotoEditorState) {
    var photoOpen by rememberSaveable { mutableStateOf(false) }
    val closePhoto = {
        editorState.scene = editorState.scene.copy(tool = DimensionTool.SELECT, selectedId = null)
        photoOpen = false
    }
    BackHandler(enabled = photoOpen && editorState.valueDraft == null, onBack = closePhoto)

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { insets ->
        val screen = Modifier.fillMaxSize().padding(insets)
        if (photoOpen) {
            SamplePhoto(
                modifier = screen,
                scene = editorState.scene,
                onStartDimension = {
                    editorState.scene = editorState.scene.copy(tool = DimensionTool.SINGLE)
                },
                onCreateDimension = { editorState.scene = editorState.scene.create(it) },
                onChangeDimension = { old, new -> editorState.scene = editorState.scene.edit(old, new) },
                onSelectDimension = { editorState.scene = editorState.scene.select(it) },
                onClose = closePhoto,
                onEditValue = editorState::openValue,
                showNotes = editorState.showNotes,
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
    onStartDimension: () -> Unit,
    onCreateDimension: (PhotoDimension) -> Unit,
    onChangeDimension: (PhotoDimension, PhotoDimension) -> Unit,
    onSelectDimension: (UUID?) -> Unit,
    onClose: () -> Unit,
    onEditValue: (PhotoDimension) -> Unit,
    showNotes: Boolean,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hint = when {
        scene.tool != DimensionTool.SELECT -> if (isLandscape) R.string.drag_dimension_short else R.string.drag_dimension
        scene.find(scene.selectedId)?.measurementState == MeasurementState.UNMEASURED -> R.string.dimension_unmeasured
        else -> if (isLandscape) R.string.photo_gestures_short else R.string.photo_gestures
    }
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            if (isLandscape) {
                // In landscape the test controls occupy just one compact row.
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onClose) {
                        Text(stringResource(R.string.back_to_start))
                    }
                    Text(
                        text = stringResource(R.string.sample_title),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Button(
                        onClick = onStartDimension,
                        enabled = scene.tool == DimensionTool.SELECT,
                    ) {
                        Text(stringResource(R.string.add_dimension), maxLines = 1)
                    }
                    Text(
                        text = stringResource(hint),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            } else {
                Column {
                    TextButton(onClick = onClose, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.back_to_start))
                    }
                    Text(
                        text = stringResource(R.string.sample_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Button(
                        onClick = onStartDimension,
                        enabled = scene.tool == DimensionTool.SELECT,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.add_dimension))
                    }
                    Text(
                        text = stringResource(hint),
                        style = MaterialTheme.typography.bodyMedium,
                        minLines = 2,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
        AndroidView(
            factory = { context -> PhotoEditorView(context).apply { this.scene = scene } },
            modifier = Modifier.fillMaxWidth().weight(1f),
            update = { view ->
                view.onCreateDimension = onCreateDimension
                view.onChangeDimension = onChangeDimension
                view.onSelectDimension = onSelectDimension
                view.onEditValue = onEditValue
                view.showNotes = showNotes
                view.scene = scene
            },
        )
    }
}
