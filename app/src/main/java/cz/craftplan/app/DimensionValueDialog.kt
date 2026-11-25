package cz.craftplan.app

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import android.view.WindowInsets as AndroidWindowInsets
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val keyboardSaver = listSaver<DimensionKeyboard, Any>(
    save = { listOf(it.field.name, it.mode.name, it.visible) },
    restore = {
        val field = DimensionField.valueOf(it[0] as String)
        // Older dialog state stored both field preferences before the visibility flag.
        val modeIndex = if (it.size == 4 && field == DimensionField.NOTE) 2 else 1
        DimensionKeyboard(
            field,
            DimensionKeyboardMode.valueOf(it[modeIndex] as String),
            it.last() as Boolean,
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
    val context = LocalContext.current.applicationContext
    val quickPreferences = remember(context) {
        context.getSharedPreferences("dimension_keypad", Context.MODE_PRIVATE)
    }
    var quickSymbol by remember(quickPreferences) {
        mutableStateOf(validQuickSymbol(quickPreferences.getString("quick_symbol", "") ?: ""))
    }
    val setQuickSymbol: (String) -> Unit = { symbol ->
        val validated = validQuickSymbol(symbol)
        quickSymbol = validated
        // A local keyboard preference only, never a measurement or a document save.
        quickPreferences.edit().putString("quick_symbol", validated).apply()
        showSymbols = false
    }
    var valueField by remember { mutableStateOf(editableText(draft.input)) }
    var noteField by remember { mutableStateOf(editableText(draft.note)) }
    var unitsOpen by remember { mutableStateOf(false) }
    val inkSettings = rememberInkLanguageSettings()
    val scope = rememberCoroutineScope()
    val ink = remember(draft.targetId, scope) { InkDraftController(scope) }
    DisposableEffect(ink) { onDispose { ink.interrupt() } }
    val currentDraftId by rememberUpdatedState(draft.targetId)
    var inkNeedsSave by rememberSaveable(draft.targetId) { mutableStateOf(false) }
    fun currentInkTarget(field: DimensionField): InkDraftTarget? {
        val language = inkSettings.readyLanguage ?: return null
        val input = if (field == DimensionField.VALUE) valueField else noteField
        return InkDraftTarget(currentDraftId, field, language, input)
    }
    val applyInput: (DimensionField, TextFieldValue) -> Unit = { target, edited ->
        val previous = if (target == DimensionField.VALUE) valueField else noteField
        if (previous != edited) ink.interrupt()
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
    fun inkCallbacks(field: DimensionField, tap: () -> Unit) = InkFieldCallbacks(
        begin = { currentInkTarget(field)?.let(ink::start) },
        preview = ink::previewStroke,
        finish = { ticket, points ->
            ink.finish(
                ticket, points, { currentInkTarget(field) },
                apply = { edited ->
                    inkNeedsSave = true
                    if (keys.field != field) showSymbols = false
                    keys = keys.copy(field = field)
                    applyInput(field, edited)
                },
                failed = { Toast.makeText(context, R.string.ink_recognition_failed, Toast.LENGTH_SHORT).show() },
            )
        },
        cancel = ink::cancel,
        tap = tap,
        otherContact = ink::interrupt,
        missingModel = inkSettings::requestModelForWriting,
    )
    val waitForInk = { Toast.makeText(context, R.string.ink_pending, Toast.LENGTH_SHORT).show() }
    val confirm: () -> Unit = {
        if (ink.hasPendingInput) waitForInk()
        else {
            ink.interrupt()
            if (!onConfirm()) showError = true
        }
    }
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
        val latestImeVisible by rememberUpdatedState(imeVisible)
        var imeHidePending by remember { mutableStateOf(false) }
        val hideKeyboard: () -> Unit = {
            keys = keys.hide()
            showSymbols = false
        }
        LaunchedEffect(imeVisible, keys.systemOpen) {
            // Finish an app-requested hide if the IME becomes visible late.
            if (imeVisible && !keys.systemOpen) {
                imeHidePending = true
                ime?.hide()
            }
        }
        LaunchedEffect(keys.systemOpen, keys.field) {
            val requested = keys
            if (requested.systemOpen) {
                // A delayed close from the previous mode must not close this request.
                val previousHideSettled = !imeHidePending || !latestImeVisible ||
                    awaitSystemImeHidden(snapshotFlow { latestImeVisible })
                imeHidePending = false
                if (keys != requested) return@LaunchedEffect
                if (requested.field == DimensionField.VALUE) valueFocus.requestFocus() else noteFocus.requestFocus()
                withFrameNanos { } // Let the focused editor establish its input connection.
                if (keys != requested) return@LaunchedEffect
                ime?.show()
                if (!previousHideSettled) {
                    // A late hide may still arrive. Retry showing, but never wait without a deadline.
                    awaitSystemImeHidden(snapshotFlow { latestImeVisible })
                    if (keys != requested) return@LaunchedEffect
                    ime?.show()
                }
                // Native Back can close the IME itself; only observe dismissal after showing this session.
                awaitSystemImeDismissal(snapshotFlow { latestImeVisible })
                if (keys == requested) hideKeyboard()
            } else {
                focusManager.clearFocus()
                imeHidePending = imeHidePending || latestImeVisible ||
                    view.rootWindowInsets?.isVisible(AndroidWindowInsets.Type.ime()) == true
                ime?.hide()
            }
        }
        LaunchedEffect(keys.field, keys.systemOpen, imeVisible) {
            if (keys.systemOpen) {
                if (keys.field == DimensionField.VALUE) valueBounds.bringIntoView() else noteBounds.bringIntoView()
            }
        }
        val latestBack by rememberUpdatedState<() -> Unit> {
            val nativeIme = view.rootWindowInsets?.isVisible(AndroidWindowInsets.Type.ime()) ?: imeVisible
            val keyboardOpen = keys.visible || nativeIme
            when {
                ink.hasPendingInput -> waitForInk()
                !keyboardOpen && inkNeedsSave -> Toast.makeText(context, R.string.ink_explicit_save, Toast.LENGTH_SHORT).show()
                else -> handleDimensionDialogBack(keyboardOpen, hideKeyboard, confirm)
            }
        }
        val dispatchBack = remember { { latestBack() } }
        SideEffect { handleBack = dispatchBack }
        val outsideTap by rememberUpdatedState(hideKeyboard)
        val openField: (DimensionField) -> Unit = { target ->
            ink.interrupt()
            if (keys.field != target) showSymbols = false
            keys = keys.open(target)
        }

        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                detectTapGestures { outsideTap() }
            })
            Surface(
                modifier = Modifier.padding(horizontal = 4.dp)
                    .then(if (portrait) Modifier.widthIn(max = 480.dp) else Modifier)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                tonalElevation = 6.dp,
            ) {
                val valueInput: @Composable (Modifier) -> Unit = { modifier ->
                    DimensionInputField(
                        modifier = modifier.bringIntoViewRequester(valueBounds),
                        value = valueField,
                        label = stringResource(R.string.dimension_value),
                        editable = keys.systemOpen && keys.field == DimensionField.VALUE,
                        active = keys.field == DimensionField.VALUE,
                        focus = valueFocus,
                        handwritingEnabled = inkSettings.readyLanguage != null,
                        inkCallbacks = inkCallbacks(DimensionField.VALUE) { openField(DimensionField.VALUE) },
                        inkPreview = { ink.previewFor(DimensionField.VALUE) },
                        onValueChange = { applyInput(DimensionField.VALUE, it) },
                        onDone = hideKeyboard,
                        isError = showError,
                        compactLabel = !portrait,
                    )
                }
                val unitControl: @Composable () -> Unit = {
                    val unitText = if (draft.showUnit) draft.unit.symbol else stringResource(R.string.dimension_no_unit)
                    val unitLabel = stringResource(R.string.dimension_unit, unitText)
                    Box(Modifier.padding(horizontal = if (portrait) 4.dp else 0.dp)) {
                        OutlinedButton(
                            onClick = { ink.interrupt(); unitsOpen = true },
                            modifier = Modifier.semantics { contentDescription = unitLabel },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text(unitText) }
                        DropdownMenu(
                            expanded = unitsOpen,
                            onDismissRequest = { unitsOpen = false },
                            modifier = Modifier.width(116.dp).heightIn(max = 408.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dimension_no_unit)) },
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                onClick = {
                                    onChange { it.withUnitVisibility(false) }
                                    unitsOpen = false
                                },
                            )
                            LengthUnit.entries.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit.symbol) },
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    onClick = {
                                        onChange {
                                            val next = it.selectUnit(unit, keepNumber)
                                            if (next === it && unit != it.unit && !keepNumber) {
                                                showError = true
                                            } else {
                                                if (valueField.text != next.input) valueField = editableText(next.input)
                                                showError = false
                                            }
                                            next
                                        }
                                        unitsOpen = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ink_language)) },
                                onClick = {
                                    unitsOpen = false
                                    inkSettings.chooserOpen = true
                                },
                            )
                            val conversionMode = stringResource(
                                if (keepNumber) R.string.keep_number_on_unit_change else R.string.convert_number_on_unit_change,
                            )
                            DropdownMenuItem(
                                onClick = { keepNumber = !keepNumber },
                                modifier = Modifier.semantics { contentDescription = conversionMode },
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Icon(
                                            painterResource(
                                                if (keepNumber) R.drawable.ic_number_locked else R.drawable.ic_number_unlocked,
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.dimension_conversion_example),
                                            modifier = Modifier.weight(1f, fill = false),
                                            autoSize = TextAutoSize.StepBased(6.sp, 11.sp, 0.5.sp),
                                            maxLines = 1,
                                            softWrap = false,
                                            letterSpacing = 0.sp,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                val keyboardControl: @Composable () -> Unit = {
                    val switchLabel = stringResource(
                        if (keys.mode == DimensionKeyboardMode.SYSTEM) R.string.use_dimension_keypad
                        else R.string.use_system_keyboard,
                    )
                    IconButton(
                        onClick = { ink.interrupt(); keys = keys.switch(); showSymbols = false },
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
                val keypadVisible = keys.visible && keys.mode == DimensionKeyboardMode.NUMERIC && !imeVisible
                val noteInput: @Composable (Modifier) -> Unit = { modifier ->
                    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                        DimensionInputField(
                            modifier = Modifier.weight(1f).bringIntoViewRequester(noteBounds),
                            value = noteField,
                            label = stringResource(R.string.dimension_note),
                            editable = keys.systemOpen && keys.field == DimensionField.NOTE,
                            active = keys.field == DimensionField.NOTE,
                            focus = noteFocus,
                            handwritingEnabled = inkSettings.readyLanguage != null,
                            inkCallbacks = inkCallbacks(DimensionField.NOTE) { openField(DimensionField.NOTE) },
                            inkPreview = { ink.previewFor(DimensionField.NOTE) },
                            onValueChange = { applyInput(DimensionField.NOTE, it) },
                            onDone = hideKeyboard,
                            isNote = true,
                            compactLabel = !portrait,
                        )
                        val notesState = stringResource(
                            if (showNotes) R.string.dimension_notes_visible else R.string.dimension_notes_hidden,
                        )
                        IconToggleButton(
                            checked = showNotes,
                            onCheckedChange = { onToggleNotes() },
                            modifier = Modifier.semantics {
                                contentDescription = notesState
                            },
                        ) {
                            Icon(
                                painterResource(if (showNotes) R.drawable.ic_notes_visible else R.drawable.ic_notes_hidden),
                                contentDescription = null,
                            )
                        }
                    }
                }
                val errorMessage: @Composable () -> Unit = {
                    if (showError || draft.hasApproximateDisplay()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val color = if (showError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            Text(if (showError) "⚠ " else "≈ ", color = color)
                            Text(
                                stringResource(
                                    if (showError) R.string.dimension_value_invalid
                                    else R.string.dimension_approximate_display,
                                ),
                                color = color,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                val keypad: @Composable (Dp) -> Unit = { keyHeight ->
                    DimensionKeypad(
                        showSymbols = showSymbols,
                        keyHeight = keyHeight,
                        quickSymbol = quickSymbol,
                        onQuickSymbolChange = setQuickSymbol,
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
                val actions: @Composable () -> Unit = {
                    TextButton(
                        onClick = { ink.interrupt(); onCancel() },
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = if (portrait) 20.dp else 16.dp),
                    ) { Text(stringResource(R.string.cancel_value), maxLines = if (portrait) Int.MAX_VALUE else 1) }
                    Button(
                        onClick = confirm,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = if (portrait) 28.dp else 18.dp),
                    ) { Text(stringResource(R.string.confirm_value), maxLines = if (portrait) Int.MAX_VALUE else 1) }
                }
                if (portrait) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Column(
                            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    valueInput(Modifier.weight(1f))
                                    unitControl()
                                    keyboardControl()
                                }
                                noteInput(Modifier.fillMaxWidth())
                                errorMessage()
                            }
                            if (keypadVisible) keypad(62.dp)
                        }
                        if (keypadVisible) Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) { actions() }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // On narrow displays only the inputs scroll horizontally; actions stay at the right edge.
                            BoxWithConstraints(Modifier.weight(1f)) {
                                val inputWidth = landscapeInputRowWidth(maxWidth.value).dp
                                val inputScroll = if (inputWidth > maxWidth) Modifier.horizontalScroll(rememberScrollState())
                                    else Modifier
                                Row(inputScroll.width(inputWidth), verticalAlignment = Alignment.CenterVertically) {
                                    valueInput(Modifier.weight(1f))
                                    unitControl()
                                    noteInput(Modifier.weight(1.35f))
                                    keyboardControl()
                                }
                            }
                            actions()
                        }
                        errorMessage()
                        if (keypadVisible) {
                            BoxWithConstraints(
                                Modifier.weight(1f, fill = false).widthIn(max = 480.dp).fillMaxWidth()
                                    .align(Alignment.CenterHorizontally),
                            ) {
                                val keyHeight = landscapeKeyHeight(maxHeight.value).dp
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) { keypad(keyHeight) }
                            }
                        }
                    }
                }
            }
        }
        InkLanguageDialogs(inkSettings)
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
    handwritingEnabled: Boolean,
    inkCallbacks: InkFieldCallbacks,
    inkPreview: () -> List<List<PenPoint>>,
    onValueChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
    isNote: Boolean = false,
    isError: Boolean = false,
    compactLabel: Boolean = false,
) {
    val currentCallbacks by rememberUpdatedState(inkCallbacks)
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.inkFieldInput(handwritingEnabled, editable) { currentCallbacks }
            .semantics {
                if (!editable) onClick(label) {
                    currentCallbacks.tap()
                    true
                }
            }
            .drawWithContent {
                drawContent()
                for (stroke in inkPreview()) {
                    for (index in 1 until stroke.size) {
                        val start = stroke[index - 1]
                        val end = stroke[index]
                        drawLine(
                            scheme.primary, Offset(start.x, start.y), Offset(end.x, end.y),
                            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                        )
                    }
                }
            },
    ) {
        val interaction = remember { MutableInteractionSource() }
        val colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = if (active) scheme.primary else scheme.outline,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).focusRequester(focus)
                .semantics(mergeDescendants = true) { }.padding(top = 6.dp),
            singleLine = true,
            readOnly = !editable,
            // Keep the Material floating label and border with tighter internal padding.
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(if (isError) scheme.error else scheme.primary),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = isNote,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value.text,
                    innerTextField = inner,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interaction,
                    isError = isError,
                    label = {
                        if (compactLabel) Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        else Text(label)
                    },
                    colors = colors,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                )
            },
        )
    }
}

internal fun landscapeInputRowWidth(availableWidth: Float): Float = availableWidth.coerceAtLeast(360f)

// The measured keypad area excludes the header and panel padding.
internal fun landscapeKeyHeight(availableHeight: Float): Float =
    ((availableHeight - 12f) / 4f).coerceIn(48f, 62f)

internal fun handleDimensionDialogBack(keyboardOpen: Boolean, hideKeyboard: () -> Unit, confirm: () -> Unit) {
    if (keyboardOpen) hideKeyboard() else confirm()
}

private fun editableText(text: String) = TextFieldValue(text, TextRange(text.length))

internal fun editDimensionInput(
    field: TextFieldValue, key: Char, limit: Int = ManualLengthMeasurement.MAX_INPUT_LENGTH,
): TextFieldValue = editDimensionInput(field, key.toString(), limit)

internal fun editDimensionInput(
    field: TextFieldValue, key: String, limit: Int = ManualLengthMeasurement.MAX_INPUT_LENGTH,
): TextFieldValue {
    if (key == "C") return TextFieldValue()
    val end = field.selection.max
    val start = if (key == "\b" && field.selection.collapsed && end > 0) {
        field.text.offsetByCodePoints(end, -1)
    } else {
        field.selection.min
    }
    val replacement = if (key == "\b") "" else key
    val text = field.text.replaceRange(start, end, replacement)
    if (text.length > limit && text.length >= field.text.length) return field
    return TextFieldValue(text, TextRange(start + replacement.length))
}
