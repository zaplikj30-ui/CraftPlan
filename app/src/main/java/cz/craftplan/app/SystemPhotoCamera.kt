package cz.craftplan.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle

internal class CameraOutputProvider : FileProvider()

private const val CameraGrant = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

private fun cameraUri(context: Context, id: PhotoId): Uri = FileProvider.getUriForFile(
    context, "${context.packageName}.camera-output", CameraCaptureStore.outputFile(context.filesDir, id),
)

internal fun revokeCameraOutput(context: Context, id: PhotoId) =
    context.revokeUriPermission(cameraUri(context, id), CameraGrant)

private class TakeFullPhoto : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input).apply {
        clipData = ClipData.newRawUri("CraftPlan", input)
        addFlags(CameraGrant)
    }
}

/** Result keys belong to an attempt, so a late result can never be attached to a different photo. */
internal class SystemPhotoCamera(
    private val activity: ComponentActivity,
    private val state: PhotoEditorState,
) {
    private var active: PhotoId? = null
    private var launcher: ActivityResultLauncher<Uri>? = null
    private var mode = CameraResultMode.CRAFTPLAN_PREVIEW

    fun restore(saved: Bundle?) {
        val name = saved?.getString(SavedAttempt) ?: run {
            state.updateCaptureInFlight(null)
            return
        }
        val id = try {
            PhotoId(name).takeUnless { it == PhotoId.SYNTHETIC_WORKBENCH }
        } catch (_: IllegalArgumentException) {
            null
        }
        val restoredMode = if (saved.getBoolean(SavedDirectMode)) CameraResultMode.DIRECT_AFTER_SUCCESS
            else CameraResultMode.CRAFTPLAN_PREVIEW
        if (id == null) state.updateCaptureInFlight(null) else register(id, restoredMode)
    }

    fun save(out: Bundle) {
        out.putString(SavedAttempt, active?.name)
        out.putBoolean(SavedDirectMode, mode == CameraResultMode.DIRECT_AFTER_SUCCESS)
    }

    fun launch(id: PhotoId, resultMode: CameraResultMode = CameraResultMode.CRAFTPLAN_PREVIEW) {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            state.cameraLaunchFailed(id, "CAMERA_CAPTURE / LAUNCH_INTERRUPTED")
            return
        }
        try {
            abandon()
            register(id, resultMode)
            checkNotNull(launcher).launch(cameraUri(activity, id))
        } catch (error: Exception) {
            if (active == id) {
                unregister()
                state.updateCaptureInFlight(null)
            }
            state.cameraLaunchFailed(id, cameraLaunchFailureCode(error))
        }
    }

    fun stopWaiting() {
        try {
            abandon()
        } catch (error: Exception) {
            state.reportCameraError(error)
        }
    }

    private fun abandon() {
        active?.let { revokeCameraOutput(activity, it) }
        unregister()
        state.updateCaptureInFlight(null)
    }

    private fun register(id: PhotoId, resultMode: CameraResultMode) {
        active = id
        mode = resultMode
        state.updateCaptureInFlight(id)
        // Use the attempt key also when the Activity is recreated over the system camera.
        val registered = activity.activityResultRegistry.register("take-photo-${id.name}", TakeFullPhoto()) { ok ->
            if (active == id) {
                unregister()
                state.updateCaptureInFlight(null)
                state.cameraResult(id, ok, resultMode)
            }
        }
        // A pending result can be delivered synchronously by register().
        if (active == id) launcher = registered else registered.unregister()
    }

    fun unregister() {
        active = null
        launcher?.unregister()
        launcher = null
    }

    private companion object {
        const val SavedAttempt = "craftplan.system_camera_attempt"
        const val SavedDirectMode = "craftplan.system_camera_direct"
    }
}

internal fun cameraLaunchFailureCode(error: Exception): String = if (error is ActivityNotFoundException) {
    "CAMERA_CAPTURE / CAMERA_UNAVAILABLE"
} else storageFailureCode(error, StoragePhase.CAMERA_CAPTURE)
