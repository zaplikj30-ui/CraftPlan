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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel

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
    var placement by mutableStateOf<DimensionPlacement>(DimensionPlacement.Idle)
}

@Composable
private fun CraftPlan(editorState: PhotoEditorState) {
    var photoOpen by rememberSaveable { mutableStateOf(false) }
    val closePhoto = {
        if (editorState.placement !is DimensionPlacement.Complete) {
            editorState.placement = DimensionPlacement.Idle
        }
        photoOpen = false
    }
    BackHandler(enabled = photoOpen, onBack = closePhoto)

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { insets ->
        val screen = Modifier.fillMaxSize().padding(insets)
        if (photoOpen) {
            SamplePhoto(
                modifier = screen,
                placement = editorState.placement,
                onStartDimension = {
                    editorState.placement = DimensionPlacement.Ready(PhotoId.SYNTHETIC_WORKBENCH)
                },
                onDimension = { result ->
                    val current = editorState.placement
                    if (current is DimensionPlacement.Ready ||
                        current is DimensionPlacement.Complete && current.dimension.id == result.dimension.id
                    ) {
                        editorState.placement = result
                    }
                },
                onClose = closePhoto,
            )
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
    placement: DimensionPlacement,
    onStartDimension: () -> Unit,
    onDimension: (DimensionPlacement.Complete) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
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
                    enabled = placement == DimensionPlacement.Idle,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.add_dimension))
                }
                val hint = when (placement) {
                    DimensionPlacement.Idle -> R.string.photo_gestures
                    is DimensionPlacement.Ready -> R.string.drag_dimension
                    is DimensionPlacement.Complete -> when (placement.dimension.measurementState) {
                        MeasurementState.UNMEASURED -> R.string.dimension_unmeasured
                    }
                }
                Text(
                    text = stringResource(hint),
                    style = MaterialTheme.typography.bodyMedium,
                    minLines = 2,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        AndroidView(
            factory = { context -> PhotoEditorView(context) },
            modifier = Modifier.fillMaxWidth().weight(1f),
            update = { view ->
                view.onDimension = onDimension
                view.placement = placement
            },
        )
    }
}
