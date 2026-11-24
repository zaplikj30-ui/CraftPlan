package cz.craftplan.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

internal const val QUICK_KEY = "_"
internal const val SYMBOL_RETURN = "↩"

internal val dimensionKeyRows = listOf(
    listOf("7", "8", "9", "\b"),
    listOf("4", "5", "6", "C"),
    listOf("1", "2", "3", "◷"),
    listOf("⋯", "0", ",", QUICK_KEY),
)
internal val dimensionSymbolRows = listOf(
    listOf("+", "−", "×", "÷"),
    listOf("±", "/", "=", "%"),
    listOf("°", "Ø", "m²", "m³"),
    listOf(".", " ", SYMBOL_RETURN, "R"),
)
internal val dimensionAlternateSymbols = mapOf(
    "7" to "+", "8" to "−", "9" to "×",
    "4" to "÷", "5" to "=", "6" to "%",
    "1" to "/", "2" to "±", "3" to "°",
    "0" to "Ø", "," to ".",
)
internal val dimensionQuickSymbolOptions = dimensionSymbolRows.flatten().filter { it != SYMBOL_RETURN }

internal fun validQuickSymbol(symbol: String): String =
    symbol.takeIf { it in dimensionQuickSymbolOptions } ?: ""

internal fun displayedDimensionKeyRows(showSymbols: Boolean): List<List<String>> =
    if (showSymbols) dimensionSymbolRows else dimensionKeyRows

internal fun isDimensionKeyEnabled(key: String): Boolean = key != "◷"

private fun keyLabel(key: String): String = when (key) {
    "\b" -> "⌫"
    " " -> "␣"
    "" -> "…"
    else -> key
}

private enum class KeyGesture { TAP, ALTERNATE, QUICK, DRAG }

@Composable
internal fun DimensionKeypad(
    showSymbols: Boolean,
    keyHeight: Dp,
    quickSymbol: String,
    onQuickSymbolChange: (String) -> Unit,
    onToggleSymbols: () -> Unit,
    onKey: (String) -> Unit,
) {
    var chooserOpen by remember { mutableStateOf(false) }
    var dropBounds by remember { mutableStateOf<Rect?>(null) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOverTarget by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    for ((rowIndex, row) in displayedDimensionKeyRows(showSymbols).withIndex()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((columnIndex, key) in row.withIndex()) {
                val quick = key == QUICK_KEY
                val target = showSymbols && rowIndex == 3 && columnIndex == 3
                val alternate = if (showSymbols) null else dimensionAlternateSymbols[key]
                val gesture = when {
                    quick -> KeyGesture.QUICK
                    showSymbols && key in dimensionQuickSymbolOptions -> KeyGesture.DRAG
                    alternate != null -> KeyGesture.ALTERNATE
                    else -> KeyGesture.TAP
                }
                val description = when (key) {
                    "\b" -> stringResource(R.string.dimension_key_delete)
                    "C" -> stringResource(R.string.dimension_key_clear)
                    "◷" -> stringResource(R.string.dimension_key_history)
                    "⋯" -> stringResource(R.string.dimension_key_symbols)
                    SYMBOL_RETURN -> stringResource(R.string.use_dimension_keypad)
                    QUICK_KEY -> if (quickSymbol.isEmpty()) stringResource(R.string.dimension_quick_key_empty)
                        else stringResource(R.string.dimension_quick_key_value, keyLabel(quickSymbol))
                    " " -> stringResource(R.string.dimension_key_space)
                    else -> null
                }
                val tap: () -> Unit = {
                    when (key) {
                        "⋯", SYMBOL_RETURN -> onToggleSymbols()
                        "◷" -> Unit
                        QUICK_KEY -> if (quickSymbol.isEmpty()) chooserOpen = true else onKey(quickSymbol)
                        else -> onKey(key)
                    }
                }
                val longAction: () -> Unit = {
                    when (gesture) {
                        KeyGesture.ALTERNATE -> alternate?.let(onKey)
                        KeyGesture.QUICK -> chooserOpen = true
                        // Accessibility equivalent of dragging the symbol into the target.
                        KeyGesture.DRAG -> onQuickSymbolChange(key)
                        KeyGesture.TAP -> Unit
                    }
                }
                Box(Modifier.weight(1f).height(keyHeight)) {
                    val activeTarget = target && dragging != null
                    val label = when {
                        activeTarget -> keyLabel(dragging.orEmpty())
                        quick -> keyLabel(quickSymbol)
                        else -> keyLabel(key)
                    }
                    DimensionKeypadKey(
                        key = key,
                        label = label,
                        alternate = alternate,
                        gesture = gesture,
                        enabled = isDimensionKeyEnabled(key),
                        description = description,
                        longDescription = when (gesture) {
                            KeyGesture.ALTERNATE -> stringResource(R.string.dimension_key_insert_alternate, alternate.orEmpty())
                            KeyGesture.QUICK -> stringResource(R.string.dimension_choose_quick_symbol)
                            KeyGesture.DRAG -> stringResource(R.string.dimension_assign_quick_symbol)
                            KeyGesture.TAP -> null
                        },
                        modifier = Modifier.fillMaxSize().then(
                            if (target) Modifier.onGloballyPositioned { dropBounds = it.boundsInRoot() }
                            else Modifier,
                        ),
                        activeTarget = activeTarget,
                        overTarget = activeTarget && dragOverTarget,
                        onTap = tap,
                        onLongAction = longAction,
                        onDragStart = { dragging = key; dragOverTarget = false },
                        onDragMove = { point -> dragOverTarget = point != null && dropBounds?.contains(point) == true },
                        onDragFinish = { point ->
                            if (point != null && dropBounds?.contains(point) == true) onQuickSymbolChange(key)
                        },
                        onDragEnd = { dragging = null; dragOverTarget = false },
                    )
                    if (quick) {
                        DropdownMenu(
                            expanded = chooserOpen && !showSymbols,
                            onDismissRequest = { chooserOpen = false },
                            modifier = Modifier.width(232.dp),
                        ) {
                            Text(
                                stringResource(R.string.dimension_choose_quick_symbol),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            for (options in (dimensionQuickSymbolOptions + "").chunked(4)) {
                                Row(Modifier.fillMaxWidth()) {
                                    for (option in options) {
                                        val clearLabel = stringResource(R.string.dimension_clear_quick_symbol)
                                        TextButton(
                                            onClick = {
                                                chooserOpen = false
                                                onQuickSymbolChange(option)
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                                if (option.isEmpty()) contentDescription = clearLabel
                                            },
                                        ) {
                                            Text(
                                                if (option.isEmpty()) "⌫" else keyLabel(option),
                                                color = if (option == quickSymbol) colors.primary else colors.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionKeypadKey(
    key: String,
    label: String,
    alternate: String?,
    gesture: KeyGesture,
    enabled: Boolean,
    description: String?,
    longDescription: String?,
    modifier: Modifier,
    activeTarget: Boolean,
    overTarget: Boolean,
    onTap: () -> Unit,
    onLongAction: () -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (Offset?) -> Unit,
    onDragFinish: (Offset?) -> Unit,
    onDragEnd: () -> Unit,
) {
    var held by remember(key, gesture) { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val tap by rememberUpdatedState(onTap)
    val longAction by rememberUpdatedState(onLongAction)
    val dragStart by rememberUpdatedState(onDragStart)
    val dragMove by rememberUpdatedState(onDragMove)
    val dragFinish by rememberUpdatedState(onDragFinish)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)
    val touch = if (gesture == KeyGesture.TAP) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onTap)
    } else {
        Modifier.semantics {
            role = Role.Button
            onClick { tap(); true }
            onLongClick(label = longDescription) { longAction(); true }
        }.pointerInput(key, gesture) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                // Distinguish a real timeout from a cancelled tap (e.g. parent scrolling).
                var completedBeforeHold = false
                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    val result = waitForUpOrCancellation()
                    completedBeforeHold = true
                    result
                }
                if (completedBeforeHold) {
                    if (up != null && up.id == down.id) {
                        up.consume()
                        tap()
                    }
                    return@awaitEachGesture
                }
                val drag = gesture == KeyGesture.DRAG
                held = true
                if (drag) dragStart()
                var moved = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                        val point = change.position
                        if ((point - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        val rootPoint = coordinates?.takeIf { it.isAttached }?.localToRoot(point)
                        if (drag) dragMove(rootPoint)
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            if (drag) {
                                if (moved) dragFinish(rootPoint)
                            } else if (point.x >= 0f && point.y >= 0f &&
                                point.x < size.width && point.y < size.height
                            ) {
                                longAction()
                            }
                            break
                        }
                        change.consume()
                    }
                } finally {
                    held = false
                    if (drag) dragEnd()
                }
            }
        }
    }
    Box(
        modifier.onGloballyPositioned { coordinates = it }
            .clip(shape)
            .background(
                when {
                    held || overTarget -> colors.primaryContainer
                    !enabled -> colors.surfaceContainer
                    else -> colors.surfaceContainerHigh
                },
            )
            .border(1.dp, if (activeTarget) colors.primary else colors.outlineVariant, shape)
            .semantics { if (description != null) contentDescription = description }
            .then(touch),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = when {
            !enabled -> colors.onSurface.copy(alpha = 0.38f)
            held || overTarget -> colors.onPrimaryContainer
            else -> colors.onSurface
        }
        if (key == SYMBOL_RETURN) {
            Icon(painterResource(R.drawable.ic_dimension_keypad), contentDescription = null, tint = contentColor)
        } else {
            // The alternative has its own independent alignment; it cannot offset the main glyph.
            Text(label, style = MaterialTheme.typography.headlineSmall, color = contentColor)
        }
        if (alternate != null) {
            Text(
                alternate,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
        if (activeTarget) {
            Text(
                stringResource(R.string.dimension_quick_drop_target),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
            )
        }
        if (held && gesture == KeyGesture.ALTERNATE && alternate != null) {
            val offset = with(LocalDensity.current) { 46.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -offset),
                properties = PopupProperties(
                    focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false,
                ),
            ) {
                Surface(shape = RoundedCornerShape(6.dp), color = colors.primary, shadowElevation = 3.dp) {
                    Box(Modifier.width(46.dp).height(42.dp), contentAlignment = Alignment.Center) {
                        Text(alternate, style = MaterialTheme.typography.headlineSmall, color = colors.onPrimary)
                    }
                }
            }
        }
    }
}
