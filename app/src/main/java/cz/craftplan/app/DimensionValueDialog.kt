package cz.craftplan.app

import android.content.res.Configuration
import android.view.WindowInsets as AndroidWindowInsets
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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

private val keyboardSaver = listSaver<DimensionKeyboard, Any>(
    save = { listOf(it.field.name, it.valueMode.name, it.noteMode.name, it.visible) },
    restore = {
        DimensionKeyboard(
            DimensionField.valueOf(it[0] as String),
            DimensionKeyboardMode.valueOf(it[1] as String),
            DimensionKeyboardMode.valueOf(it[2] as String),
            it[3] as Boolean,
        )
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DimensionValueDialog(
    draft: DimensionValueDraft,
    onChange: ((DimensionValueDraft) -> DimensionValueDraft) -> Unit,
    onConfirm: () -> Boolean,
    onCancel: () -> Unit,
    showNotes: Boolean,
    onToggleNotes: () -> Unit,
) {
    val portrait = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
    var showError by rememberSaveable { mutableStateOf(false) }
    var keepNumber by rememberSaveable { mutableStateOf(false) }
    var keys by rememberSaveable(stateSaver = keyboardSaver) { mutableStateOf(DimensionKeyboard()) }
    var showSymbols by rememberSaveable { mutableStateOf(false) }
    var valueField by remember { mutableStateOf(editableText(draft.input)) }
    var noteField by remember { mutableStateOf(editableText(draft.note)) }
    var unitsOpen by remember { mutableStateOf(false) }
    val applyInput: (DimensionField, TextFieldValue) -> Unit = { target, edited ->
        val previous = if (target == DimensionField.VALUE) valueField else noteField
        if (target != DimensionField.NOTE || edited.text.length <= MAX_DIMENSION_NOTE_LENGTH ||
            edited.text.length < previous.text.length
        ) {
            if (target == DimensionField.VALUE) valueField = edited else noteField = edited
            if (previous.text != edited.text) {
                onChange { if (target == DimensionField.VALUE) it.type(edited.text) else it.withNote(edited.text) }
                showError = false
            }
        }
    }
    val confirm: () -> Unit = { if (!onConfirm()) showError = true }
    var handleBack by remember { mutableStateOf<() -> Unit>({}) }
    Dialog(
        onDismissRequest = { handleBack() },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val ime = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val valueFocus = remember { FocusRequester() }
        val noteFocus = remember { FocusRequester() }
        val valueBounds = remember { BringIntoViewRequester() }
        val noteBounds = remember { BringIntoViewRequester() }
        val imeVisible = WindowInsets.isImeVisible
        var observedIme by remember { mutableStateOf(false) }
        val hideKeyboard: () -> Unit = {
            keys = keys.hide()
            showSymbols = false
            ime?.hide()
            focusManager.clearFocus()
        }
        LaunchedEffect(imeVisible) {
            // Native Back can be handled entirely by the system keyboard.
            if (observedIme && !imeVisible && keys.systemOpen) hideKeyboard()
            observedIme = imeVisible
        }
        LaunchedEffect(keys.systemOpen, keys.field) {
            if (keys.systemOpen) {
                if (keys.field == DimensionField.VALUE) valueFocus.requestFocus() else noteFocus.requestFocus()
                ime?.show()
            } else {
                ime?.hide()
                focusManager.clearFocus()
            }
        }
        LaunchedEffect(keys.field, keys.systemOpen, imeVisible) {
            if (keys.systemOpen) {
                if (keys.field == DimensionField.VALUE) valueBounds.bringIntoView() else noteBounds.bringIntoView()
            }
        }
        val latestBack by rememberUpdatedState<() -> Unit> {
            val nativeIme = view.rootWindowInsets?.isVisible(AndroidWindowInsets.Type.ime()) ?: imeVisible
            handleDimensionDialogBack(keys.visible || nativeIme, hideKeyboard, confirm)
        }
        val dispatchBack = remember { { latestBack() } }
        SideEffect { handleBack = dispatchBack }
        val outsideTap by rememberUpdatedState(hideKeyboard)
        val openField: (DimensionField) -> Unit = { target ->
            if (keys.field != target) showSymbols = false
            keys = keys.open(target)
        }

        Box(
            Modifier.fillMaxSize().safeDrawingPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                detectTapGestures { outsideTap() }
            })
            Surface(
                modifier = Modifier.padding(horizontal = 4.dp).widthIn(max = 480.dp).fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DimensionInputField(
                                modifier = Modifier.weight(1f).bringIntoViewRequester(valueBounds),
                                value = valueField,
                                label = stringResource(R.string.dimension_value),
                                editable = keys.systemOpen && keys.field == DimensionField.VALUE,
                                active = keys.field == DimensionField.VALUE,
                                focus = valueFocus,
                                onActivate = { openField(DimensionField.VALUE) },
                                onValueChange = { applyInput(DimensionField.VALUE, it) },
                                onDone = hideKeyboard,
                                isError = showError,
                            )
                            val unitText = if (draft.showUnit) draft.unit.symbol else stringResource(R.string.dimension_no_unit)
                            val unitLabel = stringResource(R.string.dimension_unit, unitText)
                            Box(Modifier.padding(horizontal = 4.dp)) {
                                OutlinedButton(
                                    onClick = { unitsOpen = true },
                                    modifier = Modifier.semantics { contentDescription = unitLabel },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) { Text(unitText) }
                                DropdownMenu(
                                    expanded = unitsOpen,
                                    onDismissRequest = { unitsOpen = false },
                                    modifier = if (portrait) Modifier.widthIn(max = 300.dp).heightIn(max = 360.dp) else Modifier,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.dimension_no_unit)) },
                                        onClick = {
                                            onChange { it.withUnitVisibility(false) }
                                            unitsOpen = false
                                        },
                                    )
                                    LengthUnit.entries.forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.symbol) },
                                            onClick = {
                                                onChange {
                                                    val next = it.selectUnit(unit, keepNumber)
                                                    if (valueField.text != next.input) valueField = editableText(next.input)
                                                    next
                                                }
                                                showError = false
                                                unitsOpen = false
                                            },
                                        )
                                    }
                                    if (portrait) {
                                        KeepNumberOption(keepNumber, { keepNumber = it }, Modifier.padding(horizontal = 12.dp))
                                    }
                                }
                            }
                            val switchLabel = stringResource(
                                if (keys.mode == DimensionKeyboardMode.SYSTEM) R.string.use_dimension_keypad
                                else R.string.use_system_keyboard,
                            )
                            IconButton(
                                onClick = { keys = keys.switch(); showSymbols = false },
                                modifier = Modifier.semantics { contentDescription = switchLabel },
                            ) {
                                Icon(
                                    painterResource(
                                        if (keys.mode == DimensionKeyboardMode.SYSTEM) R.drawable.ic_dimension_keypad
                                        else R.drawable.ic_dimension_keyboard,
                                    ),
                                    contentDescription = null,
                                )
                            }
                        }
                        DimensionInputField(
                            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(noteBounds),
                            value = noteField,
                            label = stringResource(R.string.dimension_note),
                            editable = keys.systemOpen && keys.field == DimensionField.NOTE,
                            active = keys.field == DimensionField.NOTE,
                            focus = noteFocus,
                            onActivate = { openField(DimensionField.NOTE) },
                            onValueChange = { applyInput(DimensionField.NOTE, it) },
                            onDone = hideKeyboard,
                            isNote = true,
                        )
                        if (portrait) {
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                                    value = showNotes, role = Role.Switch, onValueChange = { onToggleNotes() },
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Switch(checked = showNotes, onCheckedChange = null)
                                Text(stringResource(if (showNotes) R.string.hide_dimension_notes else R.string.show_dimension_notes))
                            }
                        } else {
                            KeepNumberOption(keepNumber, { keepNumber = it })
                        }
                        if (showError) {
                            Text(
                                stringResource(R.string.dimension_value_invalid),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (keys.visible && keys.mode == DimensionKeyboardMode.NUMERIC && !imeVisible) {
                            DimensionKeypad(
                                showSymbols = showSymbols,
                                portrait = portrait,
                                onToggleSymbols = { showSymbols = !showSymbols },
                                onKey = { key ->
                                    val editingNote = keys.field == DimensionField.NOTE
                                    val field = if (editingNote) noteField else valueField
                                    val limit = if (editingNote) MAX_DIMENSION_NOTE_LENGTH
                                        else ManualLengthMeasurement.MAX_INPUT_LENGTH
                                    applyInput(keys.field, editDimensionInput(field, key, limit))
                                    showSymbols = false
                                },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (keys.visible || imeVisible) {
                            IconButton(onClick = hideKeyboard) {
                                Icon(
                                    painterResource(R.drawable.ic_keyboard_hide),
                                    contentDescription = stringResource(R.string.hide_system_keyboard),
                                )
                            }
                        }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel_value)) }
                        Button(onClick = confirm) { Text(stringResource(R.string.confirm_value)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepNumberOption(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
            value = checked, role = Role.Checkbox, onValueChange = onChange,
        ).then(modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(stringResource(R.string.keep_number_on_unit_change))
    }
}

@Composable
private fun DimensionInputField(
    modifier: Modifier,
    value: TextFieldValue,
    label: String,
    editable: Boolean,
    active: Boolean,
    focus: FocusRequester,
    onActivate: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
    isNote: Boolean = false,
    isError: Boolean = false,
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            readOnly = !editable,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = isNote,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
        if (!editable) {
            Box(Modifier.matchParentSize().clickable(onClickLabel = label, onClick = onActivate))
        }
    }
}

internal val dimensionKeyRows = listOf("789\b", "456C", "123◷", "⋯0,_")
internal val dimensionSymbolRows = listOf("+−±/", ".% ↩")

internal fun displayedDimensionKeyRows(showSymbols: Boolean, portrait: Boolean): List<String> = when {
    !showSymbols -> dimensionKeyRows
    portrait -> dimensionSymbolRows + listOf("____", "____")
    else -> dimensionSymbolRows
}

internal fun handleDimensionDialogBack(keyboardOpen: Boolean, hideKeyboard: () -> Unit, confirm: () -> Unit) {
    if (keyboardOpen) hideKeyboard() else confirm()
}

@Composable
private fun DimensionKeypad(showSymbols: Boolean, portrait: Boolean, onToggleSymbols: () -> Unit, onKey: (Char) -> Unit) {
    val colors = MaterialTheme.colorScheme
    for (row in displayedDimensionKeyRows(showSymbols, portrait)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (key in row) {
                val keyHeight = if (portrait) Modifier.height(62.dp) else Modifier.heightIn(min = 62.dp)
                val description = when (key) {
                    '\b' -> stringResource(R.string.dimension_key_delete)
                    'C' -> stringResource(R.string.dimension_key_clear)
                    '◷' -> stringResource(R.string.dimension_key_history)
                    '⋯' -> stringResource(R.string.dimension_key_symbols)
                    '↩' -> stringResource(R.string.use_dimension_keypad)
                    '_' -> stringResource(R.string.dimension_key_reserved)
                    ' ' -> stringResource(R.string.dimension_key_space)
                    else -> null
                }
                OutlinedButton(
                    onClick = {
                        when (key) {
                            '⋯', '↩' -> onToggleSymbols()
                            '◷', '_' -> Unit
                            else -> onKey(key)
                        }
                    },
                    enabled = key != '◷' && key != '_',
                    modifier = Modifier.weight(1f).then(keyHeight).semantics {
                        if (description != null) contentDescription = description
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.surfaceContainerHigh,
                        contentColor = colors.onSurface,
                        disabledContainerColor = colors.surfaceContainer,
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    if (key == '↩') {
                        Icon(painterResource(R.drawable.ic_dimension_keypad), contentDescription = null)
                    } else {
                        Text(
                            text = when (key) {
                                '\b' -> "⌫"
                                ' ' -> "␣"
                                '_' -> ""
                                else -> key.toString()
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
            }
        }
    }
}

private fun editableText(text: String) = TextFieldValue(text, TextRange(text.length))

internal fun editDimensionInput(
    field: TextFieldValue, key: Char, limit: Int = ManualLengthMeasurement.MAX_INPUT_LENGTH,
): TextFieldValue {
    if (key == 'C') return TextFieldValue()
    val end = field.selection.max
    val start = if (key == '\b' && field.selection.collapsed && end > 0) {
        field.text.offsetByCodePoints(end, -1)
    } else {
        field.selection.min
    }
    val replacement = if (key == '\b') "" else key.toString()
    val text = field.text.replaceRange(start, end, replacement)
    if (text.length > limit && text.length >= field.text.length) return field
    return TextFieldValue(text, TextRange(start + replacement.length))
}
