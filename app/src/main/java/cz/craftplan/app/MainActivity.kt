package cz.craftplan.app

import android.content.pm.PackageManager
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
import androidx.compose.ui.Modifier
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
    var toolbarExpanded by mutableStateOf(false)

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
                onCreateDimension = { editorState.scene = editorState.scene.create(it) },
                onChangeDimension = { old, new -> editorState.scene = editorState.scene.edit(old, new) },
                onSelectDimension = { editorState.scene = editorState.scene.select(it) },
                onClose = closePhoto,
                onEditValue = editorState::openValue,
                showNotes = editorState.showNotes,
                toolbar = { modifier -> EditorToolbar(editorState, modifier) },
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
                factory = { context -> PhotoEditorView(context).apply { this.scene = scene } },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.onCreateDimension = onCreateDimension
                    view.onChangeDimension = onChangeDimension
                    view.onSelectDimension = onSelectDimension
                    view.onEditValue = onEditValue
                    view.showNotes = showNotes
                    view.scene = scene
                },
            )
            toolbar(Modifier.align(Alignment.BottomCenter))
        }
    }
}
