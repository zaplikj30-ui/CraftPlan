package cz.craftplan.app

import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal fun DimensionValueDialog(
    draft: DimensionValueDraft,
    onChange: (DimensionValueDraft) -> Unit,
    onConfirm: () -> Boolean,
    onCancel: () -> Unit,
) {
    var showError by rememberSaveable { mutableStateOf(false) }
    var keepNumber by rememberSaveable { mutableStateOf(false) }
    val confirm: () -> Unit = { if (!onConfirm()) showError = true }
    Dialog(
        onDismissRequest = confirm,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        val latestConfirm by rememberUpdatedState(confirm)
        DisposableEffect(window) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            val dispatcher = window.onBackInvokedDispatcher
            val callback = OnBackInvokedCallback { latestConfirm() }
            // Back confirms the field even while the IME is visible (UX-39).
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            onDispose { dispatcher.unregisterOnBackInvokedCallback(callback) }
        }
        Box(Modifier.imePadding().safeDrawingPadding().padding(12.dp)) {
            Surface(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        var unitsOpen by remember { mutableStateOf(false) }
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = draft.input,
                                onValueChange = { onChange(draft.type(it)); showError = false },
                                modifier = Modifier.weight(1f).focusRequester(focus),
                                label = { Text(stringResource(R.string.dimension_value)) },
                                singleLine = true,
                                isError = showError,
                                // A text keyboard also allows slash and explicit or mixed units.
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { confirm() }),
                            )
                            Box {
                                OutlinedButton(onClick = { unitsOpen = true }) {
                                    Text(stringResource(R.string.dimension_unit, draft.unit.symbol))
                                }
                                DropdownMenu(expanded = unitsOpen, onDismissRequest = { unitsOpen = false }) {
                                    LengthUnit.entries.forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.symbol) },
                                            onClick = {
                                                onChange(draft.selectUnit(unit, keepNumber))
                                                showError = false
                                                unitsOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                                value = keepNumber,
                                role = Role.Checkbox,
                                onValueChange = { keepNumber = it },
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = keepNumber, onCheckedChange = null)
                            Text(stringResource(R.string.keep_number_on_unit_change))
                        }
                        if (showError) {
                            Text(
                                stringResource(
                                    if (draft.input.isBlank()) R.string.dimension_value_required
                                    else R.string.dimension_value_invalid,
                                ),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel_value)) }
                        Button(onClick = confirm) { Text(stringResource(R.string.confirm_value)) }
                    }
                }
            }
        }
    }
}
