package cz.craftplan.app

import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
    var systemKeyboard by rememberSaveable { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(draft.input, TextRange(draft.input.length))) }
    var unitsOpen by remember { mutableStateOf(false) }
    val updateField: (TextFieldValue) -> Unit = { edited ->
        val textChanged = field.text != edited.text
        field = edited
        if (textChanged) {
            onChange(draft.type(edited.text))
            showError = false
        }
    }
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
            val dispatcher = window.onBackInvokedDispatcher
            val callback = OnBackInvokedCallback { latestConfirm() }
            // Back confirms the field even while the IME is visible (UX-39).
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            onDispose { dispatcher.unregisterOnBackInvokedCallback(callback) }
        }
        // Keep the window at a stable size and lay out the panel in the unobscured area.
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val wide = maxWidth >= 540.dp
            val focus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(systemKeyboard) {
                if (systemKeyboard) {
                    focus.requestFocus()
                    keyboard?.show()
                }
            }
            val actions: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel_value)) }
                    Button(onClick = confirm) { Text(stringResource(R.string.confirm_value)) }
                }
            }
            val inputRow: @Composable (Modifier) -> Unit = { modifier ->
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = field,
                        onValueChange = updateField,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        label = { Text(stringResource(R.string.dimension_value)) },
                        singleLine = true,
                        isError = showError,
                        readOnly = !systemKeyboard,
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
                                        val next = draft.type(field.text).selectUnit(unit, keepNumber)
                                        if (next.input != field.text) {
                                            field = TextFieldValue(next.input, TextRange(next.input.length))
                                        }
                                        onChange(next)
                                        showError = false
                                        unitsOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.widthIn(max = if (wide) 620.dp else 440.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (wide) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            inputRow(Modifier.weight(1f))
                            actions()
                        }
                    } else {
                        inputRow(Modifier.fillMaxWidth())
                    }
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
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
                        TextButton(onClick = {
                            if (systemKeyboard) {
                                keyboard?.hide()
                                field = field.copy(composition = null)
                            }
                            systemKeyboard = !systemKeyboard
                        }) {
                            Text(stringResource(
                                if (systemKeyboard) R.string.use_dimension_keypad else R.string.use_system_keyboard,
                            ))
                        }
                        if (!systemKeyboard) {
                            DimensionKeypad { key -> updateField(editDimensionInput(field, key)) }
                        }
                    }
                    if (!wide) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { actions() }
                    }
                }
            }
        }
    }
}

private val dimensionKeyRows = listOf("12345", "67890", ",./ \b")

@Composable
private fun DimensionKeypad(onKey: (Char) -> Unit) {
    for (row in dimensionKeyRows) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (key in row) {
                val description = when (key) {
                    '\b' -> stringResource(R.string.dimension_key_delete)
                    ' ' -> stringResource(R.string.dimension_key_space)
                    else -> null
                }
                OutlinedButton(
                    onClick = { onKey(key) },
                    modifier = Modifier.weight(1f).semantics {
                        if (description != null) contentDescription = description
                    },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(when (key) {
                        '\b' -> "⌫"
                        ' ' -> "␣"
                        else -> key.toString()
                    })
                }
            }
        }
    }
}

internal fun editDimensionInput(field: TextFieldValue, key: Char): TextFieldValue {
    val end = field.selection.max
    val start = if (key == '\b' && field.selection.collapsed && end > 0) {
        field.text.offsetByCodePoints(end, -1)
    } else {
        field.selection.min
    }
    val replacement = if (key == '\b') "" else key.toString()
    val text = field.text.replaceRange(start, end, replacement)
    if (text.length > ManualLengthMeasurement.MAX_INPUT_LENGTH && key != '\b') return field
    return TextFieldValue(text, TextRange(start + replacement.length))
}
