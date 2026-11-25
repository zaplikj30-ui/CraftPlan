package cz.craftplan.app

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import android.view.WindowInsets as AndroidWindowInsets
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeoutOrNull

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
        val density = LocalDensity.current
        val imeInsets = WindowInsets.ime
        val imeTarget = WindowInsets.imeAnimationTarget
        val bars = WindowInsets.systemBars
        val cutout = WindowInsets.displayCutout
        val numericRequested = keys.visible && keys.mode == DimensionKeyboardMode.NUMERIC
        val keypadReveal = remember { Animatable(0f) }
        val revealContent by remember(keypadReveal) { derivedStateOf { keypadReveal.value > 0f } }
        val keypadVisible = numericRequested && !imeVisible
        LaunchedEffect(numericRequested, keys.systemOpen, density) {
            val baseline = maxOf(bars.getBottom(density), cutout.getBottom(density))
            if (keys.systemOpen && keypadReveal.value > 0f) {
                // Keep the old reservation while the native inset replaces it.
                // Then animate any residual difference (e.g. a short/floating IME).
                withTimeoutOrNull(700L) {
                    snapshotFlow {
                        val bottom = imeInsets.getBottom(density)
                        latestImeVisible && bottom > baseline && bottom == imeTarget.getBottom(density)
                    }.first { it }
                }
            }
            if (numericRequested && imeInsets.getBottom(density) > baseline) {
                // The native keyboard is still occupying space: reveal the replacement
                // only as that space becomes available, using the native animation.
                keypadReveal.snapTo(1f)
            } else {
                keypadReveal.animateTo(
                    if (numericRequested) 1f else 0f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                )
            }
        }
        val keypadMotion = Modifier.clipToBounds().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0))
            val overlap = (imeInsets.getBottom(this) -
                maxOf(bars.getBottom(this), cutout.getBottom(this))).coerceAtLeast(0)
            // safeDrawingPadding already accounts for the native IME. Never add
            // the full outgoing/incoming numeric height on top of that inset.
            val height = keypadRevealHeight(placeable.height, keypadReveal.value, overlap)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(placeable.width, height) { placeable.placeRelative(0, 0) }
        }.then(if (keypadVisible) Modifier else Modifier.clearAndSetSemantics { }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        })
        var keyboardRequest by remember { mutableStateOf(0L) }
        var imeHidePending by remember { mutableStateOf(false) }
        fun requestKeyboard(next: DimensionKeyboard) {
            keyboardRequest++
            keys = next
            showSymbols = false
        }
        val hideKeyboard: () -> Unit = { requestKeyboard(keys.hide()) }

        // Exactly one cancellable owner of native IME show/hide commands. Even
        // a repeated request for the same field gets a fresh request identity.
        LaunchedEffect(keys, keyboardRequest) {
            val requested = keys
            val requestId = keyboardRequest
            fun isCurrent() = keys == requested && keyboardRequest == requestId
            val visibility = snapshotFlow { latestImeVisible }
            if (requested.systemOpen) {
                if (imeHidePending && latestImeVisible) {
                    awaitSystemImeHidden(visibility)
                }
                if (!isCurrent()) return@LaunchedEffect
                val focus = if (requested.field == DimensionField.VALUE) valueFocus else noteFocus
                var shown = false
                // A late hide or focus handoff can invalidate the first show.
                // Retry once; never loop indefinitely with a hardware keyboard.
                repeat(2) {
                    if (!shown) {
                        if (!isCurrent()) return@LaunchedEffect
                        focus.requestFocus()
                        withFrameNanos { }
                        if (!isCurrent()) return@LaunchedEffect
                        ime?.show()
                        if (it > 0) view.windowInsetsController?.show(AndroidWindowInsets.Type.ime())
                        shown = withTimeoutOrNull(800L) {
                            awaitStableImeVisibility(visibility, true, stableMs = 180L)
                            true
                        } ?: false
                    }
                }
                if (!isCurrent()) return@LaunchedEffect
                imeHidePending = false
                // An absent keyboard is not an acknowledged open/close session.
                // Keep the requested mode so an explicit tap can retry.
                if (shown) {
                    awaitStableImeVisibility(visibility, false, stableMs = 120L)
                    if (isCurrent()) hideKeyboard()
                }
            } else {
                imeHidePending = true
                if (ime != null) ime.hide()
                else view.windowInsetsController?.hide(AndroidWindowInsets.Type.ime())
                if (requested.visible) {
                    // Our keypad edits the focused field, but its platform input
                    // session is blocked below, so focus does not show the IME.
                    val focus = if (requested.field == DimensionField.VALUE) valueFocus else noteFocus
                    if (isCurrent()) focus.requestFocus()
                } else {
                    focusManager.clearFocus(force = true)
                }
                delay(120)
                // If the previous show reaches Android late, retry via native
                // insets while this request is current, never after a newer switch.
                visibility.collect {
                    if (it && isCurrent()) {
                        view.windowInsetsController?.hide(AndroidWindowInsets.Type.ime())
                    }
                }
            }
        }
        LaunchedEffect(keys.field, keys.systemOpen, density) {
            if (keys.systemOpen) {
                // Avoid a second scroll jump during the native keyboard animation.
                withTimeoutOrNull(700L) {
                    snapshotFlow {
                        latestImeVisible && imeInsets.getBottom(density) == imeTarget.getBottom(density)
                    }.first { it }
                }
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
            // Normal taps inside an already focused editable field still only
            // move the cursor. A missing IME, however, gets a fresh show request.
            val next = keys.open(target)
            if (next != keys || (next.systemOpen && !latestImeVisible)) {
                ink.interrupt()
                val previousField = keys.field
                keyboardRequest++
                keys = next
                if (previousField != target) showSymbols = false
            }
        }

        var valueInkOrigin by remember { mutableStateOf<Offset?>(null) }
        var noteInkOrigin by remember { mutableStateOf<Offset?>(null) }
        var previewOrigin by remember { mutableStateOf<Offset?>(null) }
        val inkColor = MaterialTheme.colorScheme.primary

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
                        editable = keys.edits(DimensionField.VALUE),
                        allowSystemInput = keys.allowsSystemInput(DimensionField.VALUE),
                        active = keys.field == DimensionField.VALUE,
                        focus = valueFocus,
                        handwritingEnabled = inkSettings.readyLanguage != null,
                        inkCallbacks = inkCallbacks(DimensionField.VALUE) { openField(DimensionField.VALUE) },
                        onInkPosition = { valueInkOrigin = it },
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
                        onClick = { ink.interrupt(); requestKeyboard(keys.switch()) },
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
                val noteInput: @Composable (Modifier) -> Unit = { modifier ->
                    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                        DimensionInputField(
                            modifier = Modifier.weight(1f).bringIntoViewRequester(noteBounds),
                            value = noteField,
                            label = stringResource(R.string.dimension_note),
                            editable = keys.edits(DimensionField.NOTE),
                            allowSystemInput = keys.allowsSystemInput(DimensionField.NOTE),
                            active = keys.field == DimensionField.NOTE,
                            focus = noteFocus,
                            handwritingEnabled = inkSettings.readyLanguage != null,
                            inkCallbacks = inkCallbacks(DimensionField.NOTE) { openField(DimensionField.NOTE) },
                            onInkPosition = { noteInkOrigin = it },
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
                        onQuickSymbolChange = { if (keypadVisible) setQuickSymbol(it) },
                        onToggleSymbols = { if (keypadVisible) showSymbols = !showSymbols },
                        onKey = keyAction@{ key ->
                            if (!keypadVisible) return@keyAction
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
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    valueInput(Modifier.weight(1f))
                                    unitControl()
                                    keyboardControl()
                                }
                                noteInput(Modifier.fillMaxWidth())
                                errorMessage()
                            }
                            if (numericRequested || revealContent) {
                                Column(keypadMotion.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    keypad(62.dp)
                                }
                            }
                        }
                        // Reserve the gap throughout keyboard show/hide, including the closed state.
                        Spacer(Modifier.height(4.dp))
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
                            Spacer(Modifier.width(4.dp))
                            actions()
                        }
                        errorMessage()
                        if (numericRequested || revealContent) {
                            BoxWithConstraints(
                                Modifier.weight(1f, fill = false).widthIn(max = 480.dp).fillMaxWidth()
                                    .align(Alignment.CenterHorizontally).then(keypadMotion),
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
            // Draw above the scrolling field containers so a captured pen stroke stays
            // visible when it crosses the frame. This Canvas has no input handler.
            Canvas(Modifier.matchParentSize().onGloballyPositioned { previewOrigin = it.positionInRoot() }) {
                val origin = previewOrigin ?: return@Canvas
                for ((field, fieldOrigin) in listOf(
                    DimensionField.VALUE to valueInkOrigin, DimensionField.NOTE to noteInkOrigin,
                )) {
                    val translation = (fieldOrigin ?: continue) - origin
                    for (stroke in ink.previewFor(field)) {
                        for (index in 1 until stroke.size) {
                            val start = stroke[index - 1]
                            val end = stroke[index]
                            drawLine(
                                inkColor, Offset(start.x, start.y) + translation,
                                Offset(end.x, end.y) + translation,
                                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
        }
        InkLanguageDialogs(inkSettings)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DimensionInputField(
    modifier: Modifier,
    value: TextFieldValue,
    label: String,
    editable: Boolean,
    allowSystemInput: Boolean,
    active: Boolean,
    focus: FocusRequester,
    handwritingEnabled: Boolean,
    inkCallbacks: InkFieldCallbacks,
    onInkPosition: (Offset) -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
    isNote: Boolean = false,
    isError: Boolean = false,
    compactLabel: Boolean = false,
) {
    val currentCallbacks by rememberUpdatedState(inkCallbacks)
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.onGloballyPositioned { onInkPosition(it.positionInRoot()) }
            .inkFieldInput(handwritingEnabled, editable) { currentCallbacks }
            .semantics {
                if (!editable) onClick(label) {
                    currentCallbacks.tap()
                    true
                }
            },
    ) {
        val interaction = remember { MutableInteractionSource() }
        val colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = if (active) scheme.primary else scheme.outline,
        )
        // Keep the actual text field editable for cursor/selection in numeric
        // mode. Block only the native IME connection, not Compose editing.
        val platformInput = remember(allowSystemInput) {
            PlatformTextInputInterceptor { request, next ->
                if (allowSystemInput) next.startInputMethod(request)
                else awaitCancellation()
            }
        }
        InterceptPlatformTextInput(interceptor = platformInput) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth()
                    // Allocate non-overlapping capture margins inside each field's parent.
                    .padding(horizontal = 5.dp, vertical = if (compactLabel) 4.dp else 6.dp)
                    .heightIn(min = if (compactLabel) 54.dp else 58.dp).focusRequester(focus)
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
