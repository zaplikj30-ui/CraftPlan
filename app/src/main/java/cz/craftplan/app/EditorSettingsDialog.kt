package cz.craftplan.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Keep the editor mounted while settings are open. Only app preferences change.
@Composable
internal fun EditorSettingsDialog(state: PhotoEditorState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.editor_settings), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.dimension_move_done)) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.editor_settings_help_section),
                        Modifier.padding(top = 16.dp, bottom = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = state.toolbarHintsEnabled,
                            role = Role.Switch,
                            onValueChange = state::updateToolbarHintsEnabled,
                        ).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.editor_settings_toolbar_hints),
                                style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.editor_settings_toolbar_hints_detail),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = state.toolbarHintsEnabled, onCheckedChange = null)
                    }
                }
            }
        }
    }
}
