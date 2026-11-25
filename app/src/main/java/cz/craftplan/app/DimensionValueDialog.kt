package cz.craftplan.app

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
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
import kotlinx.coroutines.CompletableDeferred
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
    ink: InkDraftController,
    onChange: ((DimensionValueDraft) -> DimensionValueDraft) -> Unit,
    onConfirm: () -> Boolean,
    onCancel: () -> Unit,
    showNotes: Boolean,
    onToggleNotes: () -> Unit,
    diagnostics: EditorDiagnostics? = null,
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
    var automaticCalculations by remember(quickPreferences) {
        mutableStateOf(quickPreferences.getBoolean("automatic_calculations", true))
    }
    var valueCalculationError by rememberSaveable(draft.targetId) { mutableStateOf(false) }
    var noteCalculationError by rememberSaveable(draft.targetId) { mutableStateOf(false) }
    val calculationError = valueCalculationError || noteCalculationError
    var calculationPlaces by remember(quickPreferences) {
        mutableStateOf(quickPreferences.getInt("calculation_places", DEFAULT_CALCULATION_PLACES).coerceIn(0, 12))
    }
    var placesOpen by rememberSaveable { mutableStateOf(false) }
    var calculationNeedsSave by rememberSaveable(draft.targetId) { mutableStateOf(false) }
    fun toggleCalculations() {
        automaticCalculations = !automaticCalculations
        quickPreferences.edit().putBoolean("automatic_calculations", automaticCalculations).apply()
        if (!automaticCalculations) {
            valueCalculationError = false
            noteCalculationError = false
        }
    }
    var symbolLayout by remember(quickPreferences) {
        mutableStateOf(KeySymbolLayout.decode(quickPreferences.getString("symbol_layout", null)))
    }
    var holdMs by remember(quickPreferences) {
        mutableStateOf(keyHoldMs(quickPreferences.getInt("key_hold_ms", DEFAULT_KEY_HOLD_MS)))
    }
    var keypadSettingsOpen by rememberSaveable { mutableStateOf(false) }
    fun updateSymbolLayout(next: KeySymbolLayout) {
        symbolLayout = next
        quickPreferences.edit().putString("symbol_layout", next.encode()).apply()
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
    // The controller owns pending selection and strokes across configuration changes.
    var valueField by remember(draft.targetId) {
        mutableStateOf(ink.pendingInput(DimensionField.VALUE)?.takeIf { it.text == draft.input } ?: editableText(draft.input))
    }
    var noteField by remember(draft.targetId) {
        mutableStateOf(ink.pendingInput(DimensionField.NOTE)?.takeIf { it.text == draft.note } ?: editableText(draft.note))
    }
    var unitsOpen by remember { mutableStateOf(false) }
    // Explicit synthetic A/B recording only; no gate in ordinary editing.
    val inkCheckGate = remember(diagnostics) {
        if (diagnostics?.isolateInkCheck == true) CompletableDeferred<Unit>() else null
    }
    val inkSettings = rememberInkLanguageSettings(diagnostics, inkCheckGate)
    val inkOwner = remember { Any() }
    val waitForInk = { Toast.makeText(context, R.string.ink_pending, Toast.LENGTH_SHORT).show() }
    var discardInkOpen by rememberSaveable { mutableStateOf(false) }
    var valueInkOrigin by remember { mutableStateOf<Offset?>(null) }
    var noteInkOrigin by remember { mutableStateOf<Offset?>(null) }
    var valueInkBounds by remember { mutableStateOf<Rect?>(null) }
    var noteInkBounds by remember { mutableStateOf<Rect?>(null) }
    val currentDraftId by rememberUpdatedState(draft.targetId)
    var inkNeedsSave by rememberSaveable(draft.targetId) { mutableStateOf(false) }
    fun currentInkTarget(field: DimensionField): InkDraftTarget? {
        val language = ink.pendingLanguage ?: inkSettings.readyLanguage ?: return null
        val input = if (field == DimensionField.VALUE) valueField else noteField
        return InkDraftTarget(currentDraftId, field, language, input.copy(composition = null))
    }
    val applyInput: (DimensionField, TextFieldValue) -> Unit = apply@{ target, edited ->
        val previous = if (target == DimensionField.VALUE) valueField else noteField
        if (ink.hasPendingInput) {
            if (previous != edited) waitForInk()
            return@apply
        }
        if (target != DimensionField.NOTE || edited.text.length <= MAX_DIMENSION_NOTE_LENGTH ||
            edited.text.length < previous.text.length
        ) {
            val isNote = target == DimensionField.NOTE
            if (isNote) noteField = edited else valueField = edited
            // All input sources share the actual text transition. Transient IME
            // selection/composition must neither suppress nor repeat a calculation.
            val calculationText = edited.text.trimEnd(' ')
            val calculate = requestsCalculationFromEdit(
                previous.text, edited.text, automaticCalculations, note = isNote,
            )
            if (calculate) {
                calculationNeedsSave = true
                onChange { current ->
                    val result = if (isNote) current.calculateNote(calculationText, calculationPlaces)
                        else current.calculate(calculationText, calculationPlaces)
                    if (isNote) noteCalculationError = result == null else valueCalculationError = result == null
                    showError = false
                    if (result != null) {
                        if (isNote) noteField = editableText(result.note) else valueField = editableText(result.input)
                        result
                    } else if (isNote) current.withNote(edited.text) else current.type(edited.text)
                }
            } else if (previous.text != edited.text) {
                if (isNote) noteCalculationError = false else valueCalculationError = false
                onChange { current ->
                    val next = if (isNote) current.editNote(edited.text) else current.type(edited.text)
                    val actual = if (isNote) next.note else next.input
                    if (actual != edited.text) {
                        // Only an approximation marker is removed. Keep the user's cursor near
                        // the edited position and end any stale IME composition.
                        val removed = edited.text.length - actual.length
                        val selection = TextRange(
                            (edited.selection.start - removed).coerceIn(0, actual.length),
                            (edited.selection.end - removed).coerceIn(0, actual.length),
                        )
                        val corrected = TextFieldValue(actual, selection)
                        if (isNote) noteField = corrected else valueField = corrected
                    }
                    next
                }
                showError = false
            }
        }
    }
    val liveInkCurrent by rememberUpdatedState<(DimensionField) -> InkDraftTarget?>({ currentInkTarget(it) })
    val liveInkApply by rememberUpdatedState<(DimensionField, TextFieldValue) -> Unit>({ field, edited ->
        inkNeedsSave = true
        if (keys.field != field) {
            showSymbols = false
            keys = if (keys.visible) keys.open(field) else DimensionKeyboard().open(field).hide()
        }
        applyInput(field, edited)
    })
    DisposableEffect(ink, inkOwner, diagnostics) {
        ink.attach(inkOwner, { liveInkCurrent(it) }, { field, edited -> liveInkApply(field, edited) },
            { Toast.makeText(context, R.string.ink_recognition_failed, Toast.LENGTH_SHORT).show() }, diagnostics)
        onDispose { ink.detach(inkOwner) }
    }
    fun inkOrigin(field: DimensionField): Offset? =
        if (field == DimensionField.VALUE) valueInkOrigin else noteInkOrigin
    val inkGestureDensity = LocalDensity.current.density
    fun inkCallbacks(
        field: DimensionField,
        sourceOrigin: () -> Offset? = { inkOrigin(field) },
        tap: () -> Unit,
    ): InkFieldCallbacks {
        // Freeze the coordinate transform at DOWN. Layout/IME movement must
        // not bend the current stroke or move it into another field.
        var translation = Offset.Zero
        fun routedPoints(points: List<PenPoint>): List<PenPoint> =
            if (translation == Offset.Zero) points
            else points.map { it.copy(x = it.x + translation.x, y = it.y + translation.y) }
        return InkFieldCallbacks(
            begin = {
                val target = ink.pendingField ?: field
                val from = sourceOrigin()
                val to = inkOrigin(target)
                if (from == null || to == null) null else {
                    translation = from - to
                    val bounds = if (target == DimensionField.VALUE) valueInkBounds else noteInkBounds
                    val area = bounds?.takeIf { it.width > 0f && it.height > 0f }
                        ?.let { InkWritingArea(it.width, it.height) }
                    currentInkTarget(target)?.let { ink.start(it, area, inkGestureDensity) }
                }
            },
            preview = { ticket, points -> ink.previewStroke(ticket, routedPoints(points)) },
            finish = { ticket, points -> ink.finish(ticket, routedPoints(points)) },
            finishDot = { ticket, points -> ink.finishDot(ticket, routedPoints(points)) },
            cancel = ink::cancel,
            tap = tap,
            pending = { ink.hasPendingInput },
            blocked = waitForInk,
            missingModel = inkSettings::requestModelForWriting,
            diagnostics = diagnostics,
            field = field,
        )
    }
    val confirm: () -> Unit = {
        if (ink.hasPendingInput) waitForInk()
        else if (calculationError) {
            Toast.makeText(context, R.string.dimension_calculation_error, Toast.LENGTH_SHORT).show()
        } else {
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
        KeypadWindowDiagnostics(diagnostics)
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
        var keypadPrepared by remember { mutableStateOf(false) }
        val revealContent by remember(keypadReveal) { derivedStateOf { keypadReveal.value > 0f } }
        val keypadVisible = numericRequested && !imeVisible
        LaunchedEffect(numericRequested, keys.systemOpen, density, inkCheckGate) {
            if (!numericRequested) inkCheckGate?.complete(Unit)
            diagnostics?.keypadStage(KeypadDiagnosticStage.REVEAL_REQUEST, if (numericRequested) 1 else 0)
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
            if (numericRequested && !keypadPrepared && imeInsets.getBottom(density) <= baseline) {
                // Compose/measure the clipped keypad before starting the first
                // visible animation. No extra delay on later reveals or IME handoffs.
                withFrameNanos { }
                withFrameNanos { }
                keypadPrepared = true
                diagnostics?.keypadStage(KeypadDiagnosticStage.FIRST_PREPARED)
            }
            if (numericRequested && imeInsets.getBottom(density) > baseline) {
                // The native keyboard is still occupying space: reveal the replacement
                // only as that space becomes available, using the native animation.
                keypadPrepared = true
                diagnostics?.keypadStage(KeypadDiagnosticStage.NATIVE_HANDOFF)
                keypadReveal.snapTo(1f)
                inkCheckGate?.complete(Unit)
            } else {
                diagnostics?.keypadStage(KeypadDiagnosticStage.ANIMATION_START)
                var finished = false
                try {
                    keypadReveal.animateTo(
                        if (numericRequested) 1f else 0f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    )
                    finished = true
                } finally {
                    diagnostics?.keypadStage(if (finished) KeypadDiagnosticStage.ANIMATION_END
                        else KeypadDiagnosticStage.ANIMATION_CANCELED)
                    if (finished) inkCheckGate?.complete(Unit)
                }
            }
        }
        var keypadInkBounds by remember { mutableStateOf<Rect?>(null) }
        val keypadMotion = Modifier.onGloballyPositioned { coordinates ->
            // Observe the outside of the clipped/revealed layout, not its
            // full measured child height. Both orientations use this modifier.
            keypadInkBounds = coordinates.boundsInRoot()
        }.then(
            if (diagnostics != null) Modifier.onSizeChanged { diagnostics.keypadHeight(it.height) } else Modifier,
        ).clipToBounds().layout { measurable, constraints ->
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
        val hideKeyboard: () -> Unit = {
            if (ink.hasPendingInput) waitForInk() else requestKeyboard(keys.hide())
        }

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
                !keyboardOpen && calculationNeedsSave -> Toast.makeText(context, R.string.dimension_calculation_explicit_save, Toast.LENGTH_SHORT).show()
                !keyboardOpen && inkNeedsSave -> Toast.makeText(context, R.string.ink_explicit_save, Toast.LENGTH_SHORT).show()
                else -> handleDimensionDialogBack(keyboardOpen, hideKeyboard, confirm)
            }
        }
        val dispatchBack = remember { { latestBack() } }
        SideEffect { handleBack = dispatchBack }
        val outsideTap by rememberUpdatedState(hideKeyboard)
        val openField: (DimensionField) -> Unit = open@{ target ->
            if (ink.hasPendingInput) {
                diagnostics?.penGesture(PenDiagnosticStage.OPEN_BLOCKED, target, pending = true)
                waitForInk()
                return@open
            }
            // Normal taps inside an already focused editable field still only
            // move the cursor. A missing IME, however, gets a fresh show request.
            val next = keys.open(target)
            diagnostics?.penGesture(PenDiagnosticStage.OPEN_FIELD, target, pending = false)
            if (next != keys || (next.systemOpen && !latestImeVisible)) {
                val previousField = keys.field
                keyboardRequest++
                keys = next
                if (previousField != target) showSymbols = false
            }
        }

        var previewOrigin by remember { mutableStateOf<Offset?>(null) }
        var dialogInkOrigin by remember { mutableStateOf<Offset?>(null) }
        val routeInk by rememberUpdatedState<(Offset) -> DialogInkContact?>({ local ->
            val origin = dialogInkOrigin
            val start = origin?.let {
                dialogInkStart(local + it, ink.pendingField, keys.field, valueInkBounds, noteInkBounds,
                    topMarginPx = with(density) { INK_START_TOP_DP.dp.toPx() },
                    sideMarginPx = with(density) { INK_START_SIDE_DP.dp.toPx() },
                    bottomMarginPx = with(density) { INK_START_BOTTOM_DP.dp.toPx() },
                    visibleKeypadBounds = if (numericRequested || revealContent) keypadInkBounds else null)
            }
            start?.let {
                DialogInkContact(it.continuing,
                    ink.pendingLanguage != null || inkSettings.readyLanguage != null,
                    inkCallbacks(it.field, sourceOrigin = { origin }) { })
            }
        })
        val inkColor = MaterialTheme.colorScheme.primary

        // This root does not read its constraints; avoid an unnecessary subcomposition.
        Box(
            Modifier.fillMaxSize()
                .onGloballyPositioned { dialogInkOrigin = it.positionInRoot() }
                .inkDialogInput(diagnostics) { routeInk(it) }
                .safeDrawingPadding(),
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
                        active = (ink.pendingField ?: keys.field) == DimensionField.VALUE,
                        focus = valueFocus,
                        handwritingEnabled = ink.pendingLanguage != null || inkSettings.readyLanguage != null,
                        inkCallbacks = inkCallbacks(DimensionField.VALUE) { openField(DimensionField.VALUE) },
                        onInkPosition = { origin, bounds -> valueInkOrigin = origin; valueInkBounds = bounds },
                        onValueChange = { applyInput(DimensionField.VALUE, it) },
                        onDone = hideKeyboard,
                        isError = showError || valueCalculationError,
                        compactLabel = !portrait,
                    )
                }
                val unitControl: @Composable () -> Unit = {
                    val unitText = if (draft.showUnit && !draft.isTextOnly()) draft.effectiveDisplayUnit().symbol else stringResource(R.string.dimension_no_unit)
                    val unitLabel = stringResource(R.string.dimension_unit, unitText)
                    Box(Modifier.padding(horizontal = if (portrait) 4.dp else 0.dp)) {
                        OutlinedButton(
                            onClick = { if (ink.hasPendingInput) waitForInk() else unitsOpen = true },
                            modifier = Modifier.semantics { contentDescription = unitLabel },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text(unitText) }
                        DropdownMenu(
                            expanded = unitsOpen,
                            onDismissRequest = { unitsOpen = false },
                            modifier = Modifier.width(240.dp).heightIn(max = 408.dp),
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
                                            if (next === it && unit != it.unit && !keepNumber && !it.isTextOnly()) {
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dimension_automatic_calculations)) },
                                trailingIcon = { Switch(checked = automaticCalculations, onCheckedChange = null) },
                                onClick = { toggleCalculations() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dimension_calculation_places, calculationPlaces)) },
                                onClick = { unitsOpen = false; placesOpen = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dimension_keypad_settings)) },
                                onClick = { unitsOpen = false; keypadSettingsOpen = true },
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
                        onClick = { if (ink.hasPendingInput) waitForInk() else requestKeyboard(keys.switch()) },
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
                            active = (ink.pendingField ?: keys.field) == DimensionField.NOTE,
                            focus = noteFocus,
                            handwritingEnabled = ink.pendingLanguage != null || inkSettings.readyLanguage != null,
                            inkCallbacks = inkCallbacks(DimensionField.NOTE) { openField(DimensionField.NOTE) },
                            onInkPosition = { origin, bounds -> noteInkOrigin = origin; noteInkBounds = bounds },
                            onValueChange = { applyInput(DimensionField.NOTE, it) },
                            onDone = hideKeyboard,
                            isNote = true,
                            isError = noteCalculationError,
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
                    if (ink.failed) {
                        Text(stringResource(R.string.ink_retained), color = MaterialTheme.colorScheme.error)
                        Row {
                            TextButton(onClick = ink::retry) { Text(stringResource(R.string.ink_retry)) }
                            TextButton(onClick = { discardInkOpen = true }) { Text(stringResource(R.string.ink_discard)) }
                        }
                    }
                    if (calculationError || showError || draft.hasApproximateDisplay() || draft.isTextOnly()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val color = if (showError || calculationError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            if (showError || calculationError || draft.hasApproximateDisplay()) {
                                Text(if (showError || calculationError) "⚠ " else "≈ ", color = color)
                            }
                            Text(
                                stringResource(
                                    when {
                                        calculationError -> R.string.dimension_calculation_error
                                        showError -> R.string.dimension_value_invalid
                                        draft.hasApproximateDisplay() -> R.string.dimension_calculation_approximate
                                        else -> R.string.dimension_text_only
                                    },
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
                        symbolLayout = symbolLayout,
                        holdMs = holdMs,
                        onSwapSymbols = { first, second ->
                            if (keypadVisible) updateSymbolLayout(symbolLayout.swap(first, second))
                        },
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
                        onClick = {
                            if (ink.hasPendingInput) discardInkOpen = true
                            else { ink.interrupt(); onCancel() }
                        },
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
                        // Shared gap: the fields no longer add outer vertical padding.
                        // Preserve the reference numeric-keypad spacing when either keyboard closes.
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
                        if (stroke.size == 1) {
                            val point = stroke.first()
                            drawCircle(inkColor, radius = 1.5.dp.toPx(),
                                center = Offset(point.x, point.y) + translation)
                        }
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
        if (discardInkOpen) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { discardInkOpen = false },
                title = { Text(stringResource(R.string.ink_discard)) },
                text = { Text(stringResource(R.string.ink_discard_question)) },
                confirmButton = {
                    TextButton(onClick = { ink.interrupt(); discardInkOpen = false }) {
                        Text(stringResource(R.string.ink_discard))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { discardInkOpen = false }) { Text(stringResource(R.string.cancel_value)) }
                },
            )
        }
        if (placesOpen) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { placesOpen = false },
                title = { Text(stringResource(R.string.dimension_calculation_places_title)) },
                text = {
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        (0..12).forEach { places ->
                            DropdownMenuItem(
                                text = { Text(places.toString()) },
                                leadingIcon = {
                                    androidx.compose.material3.RadioButton(
                                        selected = places == calculationPlaces,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    if (ink.hasPendingInput) {
                                        waitForInk()
                                    } else {
                                        onChange { current ->
                                            val next = if (automaticCalculations) current.recalculate(places) else current
                                            if (next == null) {
                                                Toast.makeText(context, R.string.dimension_calculation_error, Toast.LENGTH_SHORT).show()
                                                current
                                            } else {
                                                if (next.input != current.input || next.note != current.note) {
                                                    calculationNeedsSave = true
                                                    if (next.input != current.input) valueField = editableText(next.input)
                                                    if (next.note != current.note) noteField = editableText(next.note)
                                                }
                                                calculationPlaces = places
                                                quickPreferences.edit().putInt("calculation_places", places).apply()
                                                placesOpen = false
                                                next
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { placesOpen = false }) { Text(stringResource(R.string.cancel_value)) }
                },
            )
        }
        if (keypadSettingsOpen) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { keypadSettingsOpen = false },
                title = { Text(stringResource(R.string.dimension_keypad_settings)) },
                text = {
                    Column {
                        Text(stringResource(R.string.dimension_key_hold_ms, holdMs))
                        Slider(
                            value = holdMs.toFloat(),
                            onValueChange = {
                                holdMs = keyHoldMs(it.toInt())
                                quickPreferences.edit().putInt("key_hold_ms", holdMs).apply()
                            },
                            valueRange = 150f..1000f,
                            steps = 16,
                        )
                        TextButton(onClick = {
                            holdMs = DEFAULT_KEY_HOLD_MS
                            quickPreferences.edit().putInt("key_hold_ms", holdMs).apply()
                        }) { Text(stringResource(R.string.dimension_reset_hold)) }
                        TextButton(onClick = { updateSymbolLayout(KeySymbolLayout.default()) }) {
                            Text(stringResource(R.string.dimension_reset_symbols))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { keypadSettingsOpen = false }) { Text("OK") }
                },
            )
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
    onInkPosition: (Offset, Rect) -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
    isNote: Boolean = false,
    isError: Boolean = false,
    compactLabel: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val density = LocalDensity.current
    var fieldOrigin by remember { mutableStateOf(Offset.Zero) }
    var textOrigin by remember { mutableStateOf(Offset.Zero) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var penMenu by remember { mutableStateOf(false) }
    var lastPenTapTime by remember { mutableStateOf(-1L) }
    var lastPenTapPosition by remember { mutableStateOf(Offset.Zero) }

    fun offsetAt(position: Offset): Int {
        val layout = textLayout?.takeIf { it.layoutInput.text.text == value.text } ?: return value.selection.end
        return layout.getOffsetForPosition(position + fieldOrigin - textOrigin).coerceIn(0, value.text.length)
    }
    val currentCallbacks by rememberUpdatedState(inkCallbacks.copy(
        preview = { ticket, points ->
            lastPenTapTime = -1L
            inkCallbacks.preview(ticket, points)
        },
        finishDot = { ticket, points ->
            lastPenTapTime = -1L
            inkCallbacks.finishDot(ticket, points)
        },
        penTap = { position, time ->
            val doubleTap = lastPenTapTime >= 0L && time - lastPenTapTime in 1L..300L &&
                (position - lastPenTapPosition).getDistance() <= with(density) { 24.dp.toPx() }
            val offset = offsetAt(position)
            val layout = textLayout
            val selection = if (doubleTap && value.text.isNotEmpty() && layout != null &&
                layout.layoutInput.text.text == value.text) {
                layout.getWordBoundary(offset.coerceIn(0, value.text.lastIndex))
            } else TextRange(offset)
            inkCallbacks.diagnostics?.penGesture(
                if (doubleTap) PenDiagnosticStage.DOUBLE_TAP else PenDiagnosticStage.TAP,
                inkCallbacks.field, eventMs = time, selection = !selection.collapsed)
            lastPenTapTime = if (doubleTap) -1L else time
            lastPenTapPosition = position
            inkCallbacks.tap()
            onValueChange(value.copy(selection = selection, composition = null))
        },
        penHold = { position ->
            lastPenTapTime = -1L
            val offset = offsetAt(position)
            val selection = if (!value.selection.collapsed && offset in value.selection.min..value.selection.max)
                value.selection else TextRange(offset)
            inkCallbacks.diagnostics?.penGesture(PenDiagnosticStage.HOLD, inkCallbacks.field,
                selection = !selection.collapsed)
            inkCallbacks.tap()
            onValueChange(value.copy(selection = selection, composition = null))
            penMenu = true
        },
        fingerHold = {
            if (!inkCallbacks.pending()) {
                inkCallbacks.tap()
                penMenu = true
            }
        },
    ))
    Box(
        modifier.onGloballyPositioned {
            fieldOrigin = it.positionInRoot()
            onInkPosition(fieldOrigin, it.boundsInRoot())
        }.inkFieldInput(handwritingEnabled, editable) { currentCallbacks }
            .semantics {
                if (!editable) onClick(label) {
                    currentCallbacks.tap()
                    true
                }
            },
    ) {
        val interaction = remember { MutableInteractionSource() }
        val border = if (active) scheme.primary else scheme.outline
        val labelColor = if (active) scheme.primary else scheme.onSurfaceVariant
        val colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = border,
            unfocusedBorderColor = border,
            focusedLabelColor = labelColor,
            unfocusedLabelColor = labelColor,
        )
        // Retain Compose's native selection handles but suppress its separate
        // toolbar, especially the unrelated Autofill action. The shared menu
        // is presented explicitly after the finger or pen release.
        val nativeToolbar = LocalTextToolbar.current
        val toolbar = remember(nativeToolbar) {
            object : TextToolbar by nativeToolbar {
                override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
                override fun hide() = Unit
                override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?,
                    onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?,
                    onSelectAllRequested: (() -> Unit)?) = Unit
                override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?,
                    onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?,
                    onSelectAllRequested: (() -> Unit)?, onAutofillRequested: (() -> Unit)?) = Unit
            }
        }
        // Keep the actual text field editable for cursor/selection in numeric
        // mode. Block only the native IME connection, not Compose editing.
        val platformInput = remember(allowSystemInput) {
            PlatformTextInputInterceptor { request, next ->
                if (allowSystemInput) next.startInputMethod(request)
                else awaitCancellation()
            }
        }
        CompositionLocalProvider(LocalTextToolbar provides toolbar) {
            InterceptPlatformTextInput(interceptor = platformInput) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth()
                    // Same compact visible field in both orientations. The parent owns
                    // the pen contact beyond the field; vertical gaps need not grow for ink.
                    .padding(horizontal = 5.dp)
                    .heightIn(min = 54.dp).focusRequester(focus)
                    .onFocusChanged { state ->
                        if (state.isFocused && !editable && !currentCallbacks.pending()) currentCallbacks.tap()
                    }
                    .semantics(mergeDescendants = true) { }.padding(top = 6.dp),
                singleLine = true,
                onTextLayout = { textLayout = it },
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
                        innerTextField = {
                            Box(Modifier.onGloballyPositioned { textOrigin = it.positionInRoot() }) { inner() }
                        },
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
        // One text menu for both input devices. Back dismisses the menu first.
        DropdownMenu(expanded = penMenu, onDismissRequest = { penMenu = false }) {
            val selected = value.text.substring(value.selection.min, value.selection.max)
            fun replaceSelection(insertion: String) {
                val limit = if (isNote) MAX_DIMENSION_NOTE_LENGTH else ManualLengthMeasurement.MAX_INPUT_LENGTH
                if (insertion.any { it == '\r' || it == '\n' || it == '\u0000' } ||
                    insertion.length > limit - (value.text.length - selected.length)) return
                val start = value.selection.min
                val text = value.text.replaceRange(start, value.selection.max, insertion)
                onValueChange(TextFieldValue(text, TextRange(start + insertion.length)))
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pen_copy)) },
                enabled = selected.isNotEmpty(),
                onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", selected))
                    penMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pen_cut)) },
                enabled = selected.isNotEmpty(),
                onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", selected))
                    replaceSelection("")
                    penMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pen_paste)) },
                enabled = clipboard.hasPrimaryClip(),
                onClick = {
                    clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                        ?.let { replaceSelection(it) }
                    penMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pen_select_all)) },
                enabled = value.text.isNotEmpty(),
                onClick = {
                    onValueChange(value.copy(selection = TextRange(0, value.text.length), composition = null))
                    penMenu = false
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
