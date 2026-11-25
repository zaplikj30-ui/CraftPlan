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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay

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
    symbolLayout: KeySymbolLayout,
    holdMs: Int,
    onSwapSymbols: (String, String) -> Unit,
    onQuickSymbolChange: (String) -> Unit,
    onToggleSymbols: () -> Unit,
    onKey: (String) -> Unit,
) {
    var chooserOpen by remember { mutableStateOf(false) }
    val dropBounds = remember(showSymbols) { mutableMapOf<Int, Rect>() }
    var symbolPage by remember(showSymbols) { mutableStateOf(0) }
    var dragging by remember(showSymbols) { mutableStateOf<String?>(null) }
    var dragOverTarget by remember(showSymbols) { mutableStateOf<String?>(null) }
    var hoveringPage by remember(showSymbols) { mutableStateOf(false) }
    var selectedForSwap by remember(symbolLayout, showSymbols) { mutableStateOf<String?>(null) }
    val rows = if (showSymbols) symbolLayout.rows(symbolPage) else dimensionKeyRows
    val pageLabel = "${symbolPage + 1}/$SYMBOL_PAGE_COUNT"

    fun targetAt(point: Offset?): String? = point?.let { p ->
        dropBounds.entries.firstOrNull { it.value.contains(p) }?.key
            ?.let { rows[it / 4][it % 4] }
            ?.takeIf { it in dimensionQuickSymbolOptions }
    }

    // One flip per entry into the page button. A stationary held pointer can
    // change pages without lifting, and leaving the button rearms it.
    LaunchedEffect(dragging, hoveringPage) {
        if (dragging != null && hoveringPage) {
            delay(450)
            symbolPage = (symbolPage + 1) % SYMBOL_PAGE_COUNT
            dragOverTarget = null
        }
    }
    val colors = MaterialTheme.colorScheme

    for ((rowIndex, row) in rows.withIndex()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((columnIndex, key) in row.withIndex()) {
                val slot = rowIndex * 4 + columnIndex
                val quick = key == QUICK_KEY
                val target = showSymbols && key in dimensionQuickSymbolOptions
                val pageButton = key == SYMBOL_PAGE
                val alternate = if (showSymbols) null else symbolLayout.alternate(key)
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
                    SYMBOL_PAGE -> stringResource(R.string.dimension_symbol_page, symbolPage + 1, SYMBOL_PAGE_COUNT)
                    QUICK_KEY -> if (quickSymbol.isEmpty()) stringResource(R.string.dimension_quick_key_empty)
                        else stringResource(R.string.dimension_quick_key_value, keyLabel(quickSymbol))
                    " " -> stringResource(R.string.dimension_key_space)
                    else -> null
                }
                val tap: () -> Unit = {
                    val selected = selectedForSwap
                    if (selected != null && target) {
                        selectedForSwap = null
                        onSwapSymbols(selected, key)
                    } else when (key) {
                        "⋯", SYMBOL_RETURN -> onToggleSymbols()
                        SYMBOL_PAGE -> symbolPage = (symbolPage + 1) % SYMBOL_PAGE_COUNT
                        "◷" -> Unit
                        QUICK_KEY -> if (quickSymbol.isEmpty()) chooserOpen = true else onKey(quickSymbol)
                        else -> onKey(key)
                    }
                }
                val longAction: () -> Unit = {
                    when (gesture) {
                        KeyGesture.ALTERNATE -> alternate?.let(onKey)
                        KeyGesture.QUICK -> chooserOpen = true
                        // Accessibility equivalent: select source, then tap destination.
                        KeyGesture.DRAG -> selectedForSwap = key
                        KeyGesture.TAP -> Unit
                    }
                }
                Box(Modifier.weight(1f).height(keyHeight)) {
                    val activeTarget = (target && (dragging == key || selectedForSwap == key || dragOverTarget == key)) ||
                        (pageButton && hoveringPage)
                    val label = when {
                        quick -> keyLabel(quickSymbol)
                        pageButton -> pageLabel
                        else -> keyLabel(key)
                    }
                    DimensionKeypadKey(
                        slot = slot,
                        key = key,
                        label = label,
                        alternate = alternate,
                        gesture = gesture,
                        holdMs = holdMs,
                        enabled = isDimensionKeyEnabled(key) || alternate != null,
                        description = description,
                        longDescription = when (gesture) {
                            KeyGesture.ALTERNATE -> stringResource(R.string.dimension_key_insert_alternate, alternate.orEmpty())
                            KeyGesture.QUICK -> stringResource(R.string.dimension_choose_quick_symbol)
                            KeyGesture.DRAG -> stringResource(R.string.dimension_swap_symbol)
                            KeyGesture.TAP -> null
                        },
                        modifier = Modifier.fillMaxSize().then(
                            if (target || pageButton) Modifier.onGloballyPositioned { dropBounds[slot] = it.boundsInRoot() }
                            else Modifier,
                        ),
                        activeTarget = activeTarget,
                        overTarget = dragOverTarget == key,
                        onTap = tap,
                        onLongAction = longAction,
                        onDragStart = {
                            dragging = key
                            selectedForSwap = null
                            dragOverTarget = null
                            hoveringPage = false
                        },
                        onDragMove = { point ->
                            hoveringPage = point != null && dropBounds[SYMBOL_PAGE_SLOT]?.contains(point) == true
                            dragOverTarget = targetAt(point)
                        },
                        onDragFinish = { point ->
                            // Source identity is captured at long press, NOT the
                            // new symbol displayed at the original slot after paging.
                            val source = dragging
                            val destination = targetAt(point)
                            if (source != null && destination != null) onSwapSymbols(source, destination)
                        },
                        onDragEnd = { dragging = null; dragOverTarget = null; hoveringPage = false },
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
    slot: Int,
    key: String,
    label: String,
    alternate: String?,
    gesture: KeyGesture,
    holdMs: Int,
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
    var held by remember(slot, gesture) { mutableStateOf(false) }
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
        // Do not restart the pointer coroutine when a symbol page changes.
        // The physical slot remains composed until this contact is released.
        }.pointerInput(slot, gesture, holdMs) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                // Distinguish a real timeout from a cancelled tap (e.g. parent scrolling).
                var completedBeforeHold = false
                val up = withTimeoutOrNull(holdMs.toLong()) {
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
                                if (moved) dragFinish(rootPoint) else longAction()
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
            Text(label, style = MaterialTheme.typography.headlineSmall,
                color = if (key == "◷") contentColor.copy(alpha = 0.38f) else contentColor)
        }
        if (alternate != null) {
            Text(
                keyLabel(alternate),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
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
                        Text(keyLabel(alternate), style = MaterialTheme.typography.headlineSmall, color = colors.onPrimary)
                    }
                }
            }
        }
    }
}
