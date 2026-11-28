package cz.craftplan.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.TorchState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer

/**
 * M1 preview-only step. No shutter, output path, import attempt or document state.
 * Keep the camera tied to this non-exported Activity, not the editor ViewModel.
 */
class CameraPreviewActivity : ComponentActivity() {
    private var permitted by mutableStateOf(false)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = hasCameraPermission()
    }

    private fun hasCameraPermission() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.ENABLE_STRESS_TOOLS) {
            finish()
            return
        }
        permitted = hasCameraPermission()
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(
                    Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (permitted) {
                        LivePreview(Modifier.weight(1f).fillMaxWidth())
                    } else {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            Text(stringResource(R.string.camera_permission_denied), color = Color.White)
                            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                                Text(stringResource(R.string.camera_x_permission))
                            }
                        }
                    }
                    Text(stringResource(R.string.camera_x_preview_only), color = Color.White)
                    Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.camera_x_close))
                    }
                }
            }
        }
        if (!permitted && savedInstanceState == null) permission.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        permitted = hasCameraPermission()
    }

    @Composable
    private fun LivePreview(modifier: Modifier) {
        val preview = remember {
            PreviewView(this).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FIT_CENTER
            }
        }
        val controller = remember {
            try {
                LifecycleCameraController(applicationContext)
            } catch (_: Exception) {
                null
            }
        }
        val mainExecutor = remember { ContextCompat.getMainExecutor(this) }
        var active by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(controller == null) }
        var streaming by remember { mutableStateOf(false) }
        var canSwitch by remember { mutableStateOf(false) }
        var facingBack by remember { mutableStateOf(true) }
        var hasFlash by remember { mutableStateOf(false) }
        var torchOn by remember { mutableStateOf(false) }
        var torchPending by remember { mutableStateOf(false) }

        DisposableEffect(controller, preview) {
            active = true
            val streamObserver = Observer<PreviewView.StreamState> { value ->
                streaming = value == PreviewView.StreamState.STREAMING
                hasFlash = streaming && controller?.cameraInfo?.hasFlashUnit() == true
            }
            val torchObserver = Observer<Int> { value -> torchOn = value == TorchState.ON }
            if (controller != null) {
                try {
                    preview.previewStreamState.observe(this@CameraPreviewActivity, streamObserver)
                    controller.torchState.observe(this@CameraPreviewActivity, torchObserver)
                    val initialized = controller.initializationFuture
                    initialized.addListener({
                        if (active && !isFinishing && !isDestroyed && hasCameraPermission()) {
                            try {
                                // get() here cannot block: this listener runs only after completion.
                                initialized.get()
                                val back = controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                                val front = controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                                if (!back && !front) {
                                    failed = true
                                } else {
                                    facingBack = back
                                    canSwitch = back && front
                                    controller.cameraSelector = if (back) CameraSelector.DEFAULT_BACK_CAMERA
                                        else CameraSelector.DEFAULT_FRONT_CAMERA
                                    // Configure the future photo use case, but do not call takePicture.
                                    controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
                                    controller.setPinchToZoomEnabled(true)
                                    controller.setTapToFocusEnabled(true)
                                    controller.bindToLifecycle(this@CameraPreviewActivity)
                                    preview.controller = controller
                                }
                            } catch (_: Exception) {
                                failed = true
                                controller.unbind()
                            }
                        }
                    }, mainExecutor)
                } catch (_: Exception) {
                    failed = true
                }
            }
            onDispose {
                active = false
                preview.previewStreamState.removeObserver(streamObserver)
                controller?.torchState?.removeObserver(torchObserver)
                preview.controller = null
                controller?.unbind()
            }
        }

        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
                if (failed || !streaming) {
                    Text(
                        stringResource(if (failed) R.string.camera_x_unavailable else R.string.camera_x_waiting),
                        color = Color.White,
                        modifier = Modifier.background(Color.Black).padding(12.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = controller != null && streaming && !failed && canSwitch && !torchPending,
                    onClick = {
                        if (controller != null && active && streaming && !failed && canSwitch && !torchPending) {
                            try {
                                val next = if (facingBack) CameraSelector.DEFAULT_FRONT_CAMERA
                                    else CameraSelector.DEFAULT_BACK_CAMERA
                                controller.cameraSelector = next
                                facingBack = !facingBack
                                hasFlash = controller.cameraInfo?.hasFlashUnit() == true
                            } catch (_: Exception) {
                                failed = true
                                controller.unbind()
                            }
                        }
                    },
                ) { Text(stringResource(R.string.camera_x_switch)) }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = controller != null && streaming && !failed && hasFlash && !torchPending,
                    onClick = {
                        if (controller != null && active && streaming && !failed && hasFlash && !torchPending) {
                            torchPending = true
                            try {
                                val result = controller.enableTorch(!torchOn)
                                result.addListener({
                                    if (active && !isDestroyed && !isFinishing) {
                                        torchPending = false
                                        try {
                                            result.get()
                                        } catch (_: Exception) {
                                            Toast.makeText(this@CameraPreviewActivity,
                                                R.string.camera_x_torch_failed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }, mainExecutor)
                            } catch (_: Exception) {
                                torchPending = false
                                Toast.makeText(this@CameraPreviewActivity,
                                    R.string.camera_x_torch_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) {
                    Text(stringResource(if (torchOn) R.string.camera_x_torch_off else R.string.camera_x_torch_on))
                }
            }
        }
    }
}
