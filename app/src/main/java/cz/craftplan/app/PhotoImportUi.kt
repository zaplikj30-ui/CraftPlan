package cz.craftplan.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun PhotoImportControls(state: PhotoEditorState) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.receivePhoto(uri)
    }
    val enabled = !state.importBusy && !state.openingPhoto
    Button(onClick = { picker.launch(arrayOf("image/jpeg")) }, enabled = enabled) {
        Text(stringResource(R.string.import_photo))
    }
    state.importedPhotos.pending.forEachIndexed { index, candidate ->
        TextButton(onClick = { state.previewImport(candidate) }, enabled = enabled) {
            Text(stringResource(R.string.import_resume, index + 1))
        }
    }
    state.importedPhotos.confirmed.forEachIndexed { index, photo ->
        TextButton(onClick = { state.openPhoto(photo) }, enabled = enabled) {
            Text(stringResource(R.string.import_open_confirmed, index + 1))
        }
    }
    if (state.importBusy) Text(stringResource(R.string.import_working))
    state.importCandidate?.let { candidate ->
        var confirmDiscard by remember(candidate) { mutableStateOf(false) }
        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text(stringResource(R.string.import_discard)) },
                text = { Text(stringResource(R.string.import_discard_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscard = false
                        state.discardImport()
                    }, enabled = enabled) { Text(stringResource(R.string.import_discard)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel_value)) }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = state::deferImport,
                title = { Text(stringResource(R.string.import_preview)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        state.importPreview?.let {
                            if (it.sourceDamaged) Text(stringResource(R.string.import_damage_warning))
                            Image(it.bitmap.asImageBitmap(), stringResource(R.string.import_preview),
                                Modifier.fillMaxWidth().heightIn(max = 220.dp), contentScale = ContentScale.Fit)
                        }
                        if (state.importBusy) Text(stringResource(R.string.import_working))
                        state.importDiagnostic?.let {
                            if (state.importPreview == null) Text(stringResource(R.string.import_failed))
                            Text(stringResource(R.string.document_storage_diagnostic, it))
                        }
                        TextButton(onClick = { confirmDiscard = true }, enabled = enabled) {
                            Text(stringResource(R.string.import_discard))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = state::confirmImport, enabled = enabled && state.importPreview != null) {
                        val label = if (state.importPreview?.sourceDamaged == true) {
                            R.string.import_confirm_damaged
                        } else R.string.import_confirm
                        Text(stringResource(label))
                    }
                },
                dismissButton = {
                    TextButton(onClick = state::deferImport, enabled = enabled) {
                        Text(stringResource(R.string.import_later))
                    }
                },
            )
        }
    } ?: state.importDiagnostic?.let {
        Text(stringResource(R.string.import_failed))
        Text(stringResource(R.string.document_storage_diagnostic, it))
    }
}
