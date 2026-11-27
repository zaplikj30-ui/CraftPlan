package cz.craftplan.app

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private val toolbarVerticalPadding = 9.dp
private val toolbarShape = RoundedCornerShape(8.dp)
private val toolbarIconSize = 28.dp
private val toolbarButtonSize = Modifier.size(width = 52.dp, height = 56.dp)
private val toolbarQuickSize = Modifier.size(width = 72.dp, height = 56.dp)
private val gripTopOuterHitPadding = 18.dp
private val gripTopWidth = 160.dp
private val gripTopOverlap = 9.dp
private val gripSharedDepth = 20.dp
private val gripSideOuterHitPadding = 23.dp

@Composable
internal fun EditorToolbar(
    state: PhotoEditorState, repeatHintOffset: Dp, modifier: Modifier = Modifier,
    onFixedToolbarTop: (Float) -> Unit = {},
    onGripRegion: (ToolbarGripRegion) -> Unit = {},
) {
    val tool = state.scene.tool
    val sideGrip = !state.toolbarExpanded && !state.toolbarExtraExpanded
    Box(modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth()) {
        if (tool != DimensionTool.SELECT) {
            Surface(
                Modifier.align(Alignment.CenterHorizontally)
                    .absoluteOffset(x = repeatHintOffset, y = if (sideGrip) 0.dp else gripTopOuterHitPadding)
                    .padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
                shape = toolbarShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    stringResource(if (tool == DimensionTool.REPEAT) R.string.toolbar_repeat_active else R.string.drag_dimension_short),
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        EditorToolbarSurface(state, onFixedToolbarTop, onGripRegion)
    }
        // Overlay only: transient feedback must not move the toolbar or the photo.
        // Leave the existing active drawing guidance intact.
        val hint = state.toolbarHint
        if (state.toolbarHintsEnabled && hint != null && state.valueDraft == null) {
            Box(Modifier.matchParentSize()) {
                Surface(
                    Modifier.align(Alignment.TopCenter)
                        .absoluteOffset(x = repeatHintOffset, y = (-32).dp)
                        .padding(horizontal = 4.dp),
                    shape = toolbarShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(stringResource(hint), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ToolbarExpansionGrip(
    state: PhotoEditorState, modifier: Modifier = Modifier, side: Boolean = false,
    onGripRegion: (ToolbarGripRegion) -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val handleLabel = stringResource(if (state.toolbarExtraExpanded) R.string.toolbar_collapse else R.string.toolbar_expand)
    val activeColors = MaterialTheme.colorScheme
    // Extend only outwards: the visible 9dp strip and the neighbouring buttons keep their layout.
    Box(
        modifier.size(
            width = if (side) 9.dp + gripSideOuterHitPadding else gripTopWidth,
            height = if (side) 54.dp else 9.dp + gripTopOuterHitPadding,
        ).onGloballyPositioned { coordinates ->
            val origin = coordinates.positionInRoot()
            val right = origin.x + coordinates.size.width
            val workspaceEdge = when {
                !side -> origin.y + with(density) { gripTopOuterHitPadding.toPx() }
                rtl -> right - with(density) { 9.dp.toPx() }
                else -> origin.x + with(density) { 9.dp.toPx() }
            }
            onGripRegion(ToolbarGripRegion(
                origin.x, origin.y, right, origin.y + coordinates.size.height,
                workspaceEdge, side = side, rtl = rtl,
            ))
            if (BuildConfig.ENABLE_STRESS_TOOLS) {
                val bounds = coordinates.boundsInWindow()
                state.diagnosticGripBounds = DiagnosticGripBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
                if (side) state.diagnosticSharedGripBounds = null
            }
        }.semantics {
            contentDescription = handleLabel
            role = Role.Button
            onClick {
                state.setToolbarExtraExpansion(!state.toolbarExtraExpanded)
                true
            }
        },
        contentAlignment = if (side) Alignment.CenterStart else Alignment.BottomCenter,
    ) {
        Box(
            Modifier.size(width = if (side) 9.dp else 76.dp, height = if (side) 50.dp else 9.dp)
                .background(activeColors.surfaceContainerHigh, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = if (side) 4.dp else 64.dp, height = if (side) 44.dp else 4.dp)
                .background(activeColors.onSurfaceVariant, RoundedCornerShape(2.dp)))
        }
    }
}

// Observe either grip from the common parent of the toolbar and AndroidView.
// Invisible workspace overlaps must not be pointer-input siblings of the editor.
// A protected editor contact receives the original DOWN and the entire stream.
internal fun Modifier.toolbarGrip(
    state: PhotoEditorState,
    region: () -> ToolbarGripRegion?,
    editorOrigin: () -> Offset?,
    editorHasPriority: (Float, Float, EditorPointerKind) -> Boolean,
): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        if (state.valueDraft != null || state.saveStatus == DocumentSaveStatus.RECOVERED)
            return@awaitEachGesture
        val bounds = region() ?: return@awaitEachGesture
        // Ignore the old region during the layout transition between grip types.
        if (bounds.side != (!state.toolbarExpanded && !state.toolbarExtraExpanded))
            return@awaitEachGesture
        val origin = editorOrigin() ?: return@awaitEachGesture
        val root = origin + down.position
        if (!bounds.contains(root.x, root.y)) return@awaitEachGesture
        val input = when (down.type) {
            PointerType.Touch -> EditorPointerKind.FINGER
            PointerType.Stylus -> EditorPointerKind.STYLUS
            PointerType.Eraser -> EditorPointerKind.ERASER
            PointerType.Mouse -> EditorPointerKind.MOUSE
            else -> EditorPointerKind.OTHER
        }
        val workspace = bounds.inWorkspace(root.x, root.y)
        if (!bounds.canTrack(root.x, root.y,
                workspace && editorHasPriority(down.position.x, down.position.y, input))
        ) return@awaitEachGesture

        // Only reserve a DOWN on the visible grip itself; in the workspace
        // a blank-area pan remains eligible until the first decisive motion along this grip's axis.
        if (!workspace) down.consume()
        val expanded = if (bounds.side) state.toolbarExpanded else state.toolbarExtraExpanded
        val threshold = viewConfiguration.touchSlop
        state.diagnostics.toolbarGripStart(input, bounds.side, root.x - bounds.left, root.y - bounds.top,
            (bounds.right - bounds.left).roundToInt(), (bounds.bottom - bounds.top).roundToInt(), threshold, expanded)
        var dx = 0f
        var dy = 0f
        var maxTravel = 0f
        var claimed = false
        var reason = ToolbarGripOutcome.STREAM_CANCELED
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    reason = ToolbarGripOutcome.POINTER_MISSING
                    break
                }
                if (change.isConsumed) {
                    reason = ToolbarGripOutcome.CONSUMED
                    break
                }
                if (event.changes.any { it.id != down.id && it.pressed }) {
                    reason = ToolbarGripOutcome.MULTI_POINTER
                    break
                }
                dx = change.position.x - down.position.x
                dy = change.position.y - down.position.y
                maxTravel = maxOf(maxTravel, abs(dx), abs(dy))
                if (!claimed) {
                    val decision = toolbarSharedDecision(dx, dy, threshold,
                        change.uptimeMillis - down.uptimeMillis, viewConfiguration.longPressTimeoutMillis)
                    when (decision) {
                        ToolbarSharedDecision.VERTICAL, ToolbarSharedDecision.HORIZONTAL -> {
                            if (toolbarGripClaimsMotion(bounds.side, decision)) {
                                claimed = true
                            } else {
                                reason = if (bounds.side) ToolbarGripOutcome.NON_HORIZONTAL
                                    else ToolbarGripOutcome.SHARED_HORIZONTAL
                                break
                            }
                        }
                        ToolbarSharedDecision.LONG_PRESS -> {
                            reason = ToolbarGripOutcome.SHARED_LONG_PRESS
                            break
                        }
                        ToolbarSharedDecision.INVALID -> {
                            reason = ToolbarGripOutcome.INVALID
                            break
                        }
                        ToolbarSharedDecision.WAIT -> {
                            if (!change.pressed) {
                                reason = ToolbarGripOutcome.TAP
                                break
                            }
                        }
                    }
                }
                if (claimed) {
                    // AndroidView interop cancels a previous blank-area gesture
                    // when Compose consumes motion; never synthesize or replay DOWN.
                    change.consume()
                    if (!change.pressed) {
                        reason = toolbarGripReleaseReason(dx, dy, maxTravel, threshold, side = bounds.side)
                        when (reason) {
                            ToolbarGripOutcome.RIGHT, ToolbarGripOutcome.LEFT ->
                                state.applyToolbarHorizontalDrag(dx, threshold)
                            ToolbarGripOutcome.UP -> state.setToolbarExtraExpansion(true)
                            ToolbarGripOutcome.DOWN -> state.setToolbarExtraExpansion(false)
                            else -> Unit
                        }
                        break
                    }
                }
            }
        } finally {
            state.diagnostics.toolbarGripEnd(reason, dx, dy, maxTravel, expanded,
                if (bounds.side) state.toolbarExpanded else state.toolbarExtraExpanded)
        }
    }
}

// The shared strip is an ancestor of the real buttons, not a covering sibling.
// Observe Initial without consuming until a vertical drag wins. Descendants keep taps,
// long presses and horizontal drags. Once won, consume the rest of this gesture.
private fun Modifier.sharedToolbarGrip(state: PhotoEditorState): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            if (!toolbarSharedContains(down.position.x, down.position.y, size.width.toFloat(),
                    gripTopWidth.toPx(), gripTopOverlap.toPx(), gripSharedDepth.toPx())) return@awaitEachGesture

            val expanded = state.toolbarExtraExpanded
            val threshold = viewConfiguration.touchSlop
            val input = when (down.type) {
                PointerType.Touch -> EditorPointerKind.FINGER
                PointerType.Stylus -> EditorPointerKind.STYLUS
                PointerType.Eraser -> EditorPointerKind.ERASER
                PointerType.Mouse -> EditorPointerKind.MOUSE
                else -> EditorPointerKind.OTHER
            }
            state.diagnostics.toolbarGripStart(input, false, down.position.x, down.position.y,
                size.width, size.height, threshold, expanded, shared = true)
            var dx = 0f
            var dy = 0f
            var maxTravel = 0f
            var claimed = false
            var reason = ToolbarGripOutcome.STREAM_CANCELED
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        reason = ToolbarGripOutcome.POINTER_MISSING
                        break
                    }
                    if (change.isConsumed) {
                        reason = ToolbarGripOutcome.CONSUMED
                        break
                    }
                    if (event.changes.any { it.id != down.id && it.pressed }) {
                        reason = ToolbarGripOutcome.MULTI_POINTER
                        break
                    }
                    dx = change.position.x - down.position.x
                    dy = change.position.y - down.position.y
                    maxTravel = maxOf(maxTravel, abs(dx), abs(dy))
                    if (!claimed) {
                        when (toolbarSharedDecision(dx, dy, threshold,
                            change.uptimeMillis - down.uptimeMillis, viewConfiguration.longPressTimeoutMillis)) {
                            ToolbarSharedDecision.VERTICAL -> claimed = true
                            ToolbarSharedDecision.HORIZONTAL -> {
                                reason = ToolbarGripOutcome.SHARED_HORIZONTAL
                                break
                            }
                            ToolbarSharedDecision.LONG_PRESS -> {
                                reason = ToolbarGripOutcome.SHARED_LONG_PRESS
                                break
                            }
                            ToolbarSharedDecision.INVALID -> {
                                reason = ToolbarGripOutcome.INVALID
                                break
                            }
                            ToolbarSharedDecision.WAIT -> {
                                if (!change.pressed) {
                                    reason = ToolbarGripOutcome.SHARED_TAP
                                    break
                                }
                            }
                        }
                    }
                    if (claimed) {
                        change.consume()
                        if (!change.pressed) {
                            reason = toolbarGripReleaseReason(dx, dy, maxTravel, threshold)
                            // A shared-area drag can never become a tap/toggle.
                            if (reason == ToolbarGripOutcome.UP || reason == ToolbarGripOutcome.DOWN) {
                                state.setToolbarExtraExpansion(reason == ToolbarGripOutcome.UP)
                            }
                            break
                        }
                    } else {
                        // Respect a descendant that consumed this undecided move in Main.
                        val finalChange = awaitPointerEvent(PointerEventPass.Final).changes
                            .firstOrNull { it.id == down.id }
                        if (finalChange == null || finalChange.isConsumed) {
                            reason = if (finalChange == null) ToolbarGripOutcome.POINTER_MISSING else ToolbarGripOutcome.CONSUMED
                            break
                        }
                    }
                }
            } finally {
                state.diagnostics.toolbarGripEnd(reason, dx, dy, maxTravel, expanded,
                    state.toolbarExtraExpanded, shared = true)
            }
        }
    }

@Composable
private fun EditorToolbarSurface(
    state: PhotoEditorState, onFixedToolbarTop: (Float) -> Unit,
    onGripRegion: (ToolbarGripRegion) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val expanded = state.toolbarExpanded
    val verticalExpanded = state.toolbarExtraExpanded
    val sideGrip = !expanded && !verticalExpanded
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val extraScrollState = rememberScrollState()
    val tool = state.scene.tool
    val repeat = tool == DimensionTool.REPEAT
    val activeColors = MaterialTheme.colorScheme
    val fixedToolsLabel = stringResource(R.string.toolbar_horizontal_gesture)
    val repeatAction = stringResource(if (repeat) R.string.toolbar_repeat_off else R.string.toolbar_repeat_on)
    if (settingsOpen) {
        EditorSettingsDialog(state, onDismiss = { settingsOpen = false })
    }
    Box {
        Surface(
            modifier = (if (expanded || verticalExpanded) Modifier.fillMaxWidth() else Modifier)
                .padding(top = if (sideGrip) 0.dp else gripTopOuterHitPadding, end = if (sideGrip) gripSideOuterHitPadding else 0.dp),
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = (if (sideGrip) Modifier else Modifier.onGloballyPositioned { coordinates ->
                    if (BuildConfig.ENABLE_STRESS_TOOLS) {
                        val bounds = coordinates.boundsInWindow()
                        val half = minOf(bounds.width, with(density) { gripTopWidth.toPx() }) / 2f
                        val top = bounds.top + with(density) { gripTopOverlap.toPx() }
                        state.diagnosticSharedGripBounds = DiagnosticGripBounds(
                            bounds.left + bounds.width / 2f - half, top,
                            bounds.left + bounds.width / 2f + half,
                            minOf(bounds.bottom, top + with(density) { gripSharedDepth.toPx() }),
                        )
                    }
                }.sharedToolbarGrip(state))
                    .padding(vertical = toolbarVerticalPadding).padding(end = if (sideGrip) 9.dp else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (verticalExpanded) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 3.dp).horizontalScroll(extraScrollState), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { state.requestFit(); state.showToolbarHint(R.string.toolbar_fit) }, modifier = toolbarButtonSize) {
                            Icon(painterResource(R.drawable.ic_toolbar_fit), stringResource(R.string.toolbar_fit), Modifier.size(toolbarIconSize))
                        }
                        IconButton(
                            onClick = {
                                state.toggleNotes()
                                state.showToolbarHint(if (state.showNotes) R.string.dimension_notes_visible else R.string.dimension_notes_hidden)
                            },
                            modifier = toolbarButtonSize.background(
                                if (state.showNotes) activeColors.primaryContainer else Color.Transparent, toolbarShape,
                            ),
                        ) {
                            Icon(
                                painterResource(if (state.showNotes) R.drawable.ic_notes_visible else R.drawable.ic_notes_hidden),
                                stringResource(if (state.showNotes) R.string.dimension_notes_visible else R.string.dimension_notes_hidden),
                                Modifier.size(toolbarIconSize),
                            )
                        }
                        IconButton(
                            enabled = state.scene.selectedId != null || tool != DimensionTool.SELECT,
                            onClick = {
                                state.scene = state.scene.closePhoto()
                                state.showToolbarHint(R.string.toolbar_deselect)
                            },
                            modifier = toolbarButtonSize,
                        ) {
                            Icon(painterResource(R.drawable.ic_toolbar_deselect), stringResource(R.string.toolbar_deselect), Modifier.size(toolbarIconSize))
                        }
                        DimensionMoveAction(state)
                        if (BuildConfig.ENABLE_STRESS_TOOLS && state.scene.photoId == PhotoId.SYNTHETIC_WORKBENCH) StressToolbarMenu(state)
                    }
                    if (state.dimensionTestAppearances.values.isNotEmpty()) {
                        TextButton(onClick = { state.clearTestAppearances(); state.showToolbarHint(R.string.toolbar_test_appearances_cleared) }) {
                            Text(stringResource(R.string.toolbar_clear_test_appearances))
                        }
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.Start).padding(top = if (verticalExpanded) 3.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Row(
                        Modifier.onGloballyPositioned { coordinates ->
                            // The fixed buttons stay at the bottom, even with the extra row open.
                            onFixedToolbarTop(coordinates.positionInRoot().y - with(density) { toolbarVerticalPadding.toPx() })
                        }.pointerInput(state) {
                            var motion = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { motion = 0f },
                                onDragEnd = {
                                    state.applyToolbarHorizontalDrag(motion, 4.dp.toPx())
                                },
                            ) { change, delta ->
                                motion += delta
                                change.consume()
                            }
                        }.semantics {
                            contentDescription = fixedToolsLabel
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            IconButton(
                                onClick = { menuOpen = true; state.showToolbarHint(R.string.toolbar_tools) },
                                modifier = toolbarQuickSize.background(
                                    if (menuOpen) activeColors.primaryContainer else Color.Transparent, toolbarShape,
                                ),
                            ) {
                                Icon(painterResource(R.drawable.ic_tools_list), stringResource(R.string.toolbar_tools), Modifier.size(toolbarIconSize))
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                modifier = Modifier.widthIn(min = 144.dp),
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(painterResource(R.drawable.ic_dimension_tool), null, Modifier.size(32.dp))
                                            Text(
                                                stringResource(R.string.toolbar_dimension),
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                        }
                                    },
                                    onClick = {
                                        state.scene = state.scene.prepareSingle()
                                        state.showToolbarHint(R.string.drag_dimension_short)
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                        val dimensionLabel = stringResource(R.string.add_dimension)
                        val toolBackground = when (tool) {
                            DimensionTool.REPEAT -> activeColors.primary
                            DimensionTool.SINGLE -> activeColors.primaryContainer
                            DimensionTool.SELECT -> Color.Transparent
                        }
                        Box(
                            toolbarQuickSize
                                .background(toolBackground, toolbarShape)
                                .semantics { contentDescription = dimensionLabel }
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = {
                                        state.scene = state.scene.tapDrawingTool()
                                        state.showToolbarHint(drawingToolHint(state.scene.tool))
                                    },
                                    onLongClickLabel = repeatAction,
                                    onLongClick = {
                                        state.scene = state.scene.toggleRepeat()
                                        state.showToolbarHint(drawingToolHint(state.scene.tool))
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_dimension_tool), null,
                                Modifier.size(toolbarIconSize),
                                tint = if (repeat) activeColors.onPrimary else activeColors.onSurface,
                            )
                            if (repeat) {
                                Icon(painterResource(R.drawable.ic_toolbar_repeat), null, Modifier.size(52.dp), activeColors.onPrimary)
                            }
                        }
                    }
                    if (expanded) {
                        Box(Modifier.width(1.dp).height(28.dp).background(activeColors.outline))
                        Row(Modifier.weight(1f).horizontalScroll(scrollState), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    state.clearToolbarHint()
                                    settingsOpen = true
                                },
                                modifier = toolbarButtonSize,
                            ) {
                                Icon(painterResource(R.drawable.ic_toolbar_settings), stringResource(R.string.editor_settings), Modifier.size(toolbarIconSize))
                            }
                            HistoryAction(false, R.string.toolbar_undo, state.history.canUndo) {
                                val before = state.history
                                state.undo()
                                if (state.history !== before) state.showToolbarHint(R.string.toolbar_undo)
                            }
                            HistoryAction(true, R.string.toolbar_redo, state.history.canRedo) {
                                val before = state.history
                                state.redo()
                                if (state.history !== before) state.showToolbarHint(R.string.toolbar_redo)
                            }
                            IconButton(
                                enabled = state.scene.selectedId != null,
                                onClick = {
                                    val target = state.scene.selectedId
                                    state.deleteSelected()
                                    if (target != null && state.scene.find(target) == null) {
                                        state.showToolbarHint(R.string.toolbar_deleted)
                                    }
                                },
                                modifier = toolbarButtonSize,
                            ) {
                                Icon(painterResource(R.drawable.ic_dimension_delete), stringResource(R.string.toolbar_delete), Modifier.size(toolbarIconSize))
                            }
                            val locked = state.labelsLocked
                            val lockLabel = stringResource(if (locked) R.string.toolbar_label_locked else R.string.toolbar_label_unlocked)
                            IconToggleButton(
                                checked = locked,
                                onCheckedChange = {
                                    state.toggleLabelLock()
                                    state.showToolbarHint(if (state.labelsLocked) R.string.toolbar_label_locked else R.string.toolbar_label_unlocked)
                                },
                                colors = IconButtonDefaults.iconToggleButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = activeColors.onSurface,
                                    checkedContainerColor = Color.Transparent,
                                    checkedContentColor = activeColors.onPrimaryContainer,
                                ),
                                modifier = toolbarButtonSize
                                    .background(if (locked) activeColors.primaryContainer else Color.Transparent, toolbarShape)
                                    .semantics { contentDescription = lockLabel },
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("123", style = MaterialTheme.typography.labelSmall)
                                        Icon(
                                            painterResource(if (locked) R.drawable.ic_number_locked else R.drawable.ic_number_unlocked),
                                            null, Modifier.size(15.dp),
                                        )
                                    }
                                    Icon(
                                        painterResource(R.drawable.ic_toolbar_label_motion), null, Modifier.size(32.dp, 12.dp),
                                    )
                                }
                            }
                            if (BuildConfig.ENABLE_STRESS_TOOLS) ReservedToolbarActions()
                        }
                    }
                }
            }
        }
        ToolbarExpansionGrip(
            state,
            Modifier.align(if (sideGrip) Alignment.CenterEnd else Alignment.TopCenter),
            side = sideGrip,
            onGripRegion = onGripRegion,
        )
    }
}

private fun drawingToolHint(tool: DimensionTool): Int = when (tool) {
    DimensionTool.SELECT -> R.string.toolbar_drawing_off
    DimensionTool.SINGLE -> R.string.drag_dimension_short
    DimensionTool.REPEAT -> R.string.toolbar_repeat_active
}

@Composable
private fun DimensionMoveAction(state: PhotoEditorState) {
    val selected = state.dimensionsRequireSelection
    val colors = MaterialTheme.colorScheme
    val label = stringResource(if (selected) R.string.dimension_move_selected else R.string.dimension_move_direct)
    val description = stringResource(R.string.dimension_move_description, label)
    IconToggleButton(
        checked = selected,
        onCheckedChange = {
            state.updateDimensionsRequireSelection(it)
            state.showToolbarHint(if (state.dimensionsRequireSelection) R.string.toolbar_move_selected_hint else R.string.toolbar_move_direct_hint)
        },
        colors = IconButtonDefaults.iconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onSurface,
            checkedContainerColor = Color.Transparent,
            checkedContentColor = colors.onPrimaryContainer,
        ),
        modifier = toolbarButtonSize
            .background(if (selected) colors.primaryContainer else Color.Transparent, toolbarShape)
            .semantics { contentDescription = description },
    ) {
        Icon(
            painterResource(if (selected) R.drawable.ic_toolbar_move_selected else R.drawable.ic_toolbar_move_direct),
            null, Modifier.size(toolbarIconSize),
        )
    }
}

// Temporary, intentionally disabled slots for the already planned editor controls.
@Composable
private fun ReservedToolbarActions() {
    val labels = listOf(
        R.string.toolbar_reserved_color,
        R.string.toolbar_reserved_width,
        R.string.toolbar_reserved_multi_select,
        R.string.toolbar_reserved_anchor_lock,
        R.string.toolbar_reserved_move,
        R.string.toolbar_reserved_reorder,
        R.string.toolbar_reserved_reset_order,
        R.string.toolbar_reserved_export,
    )
    for (label in labels) {
        val name = stringResource(label)
        val description = stringResource(R.string.toolbar_reserved_description, name)
        TextButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.size(width = 96.dp, height = 56.dp)
                .semantics { contentDescription = description },
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun StressToolbarMenu(state: PhotoEditorState) {
    var open by remember { mutableStateOf(false) }
    val photoId = PhotoId.SYNTHETIC_WORKBENCH
    val total = state.scene.dimensions.size
    val export = rememberDiagnosticExport(state)
    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.stress_toolbar, total))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(max = 310.dp)) {
            DiagnosticActions(state, export) { open = false }
            for (count in SyntheticDimensions.counts) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stress_generate, count)) },
                    enabled = !state.stressBusy,
                    onClick = { state.generateStressBatch(photoId, count) },
                )
            }
            val generated = state.scene.dimensions.count(SyntheticDimensions::owns)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stress_clear)) },
                enabled = !state.stressBusy && generated > 0,
                onClick = { state.clearStressBatch(photoId) },
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.stress_counts, total, generated))
                if (state.stressBusy) Text(stringResource(R.string.stress_working))
                state.stressGenerationMs?.let { Text(stringResource(R.string.stress_generation_ms, it)) }
                if (state.diagnostics.hasSession) {
                    val snapshot = state.diagnostics.metrics.snapshot()
                    if (snapshot.draws > 0) Text(stringResource(R.string.stress_draw, snapshot.maxDrawMs, snapshot.draws))
                    if (snapshot.hits > 0) Text(stringResource(R.string.stress_hit, snapshot.maxHitMs, snapshot.hits))
                }
            }
        }
    }
}

@Composable
private fun HistoryAction(redo: Boolean, label: Int, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(label)
    IconButton(
        onClick = onClick, enabled = enabled,
        modifier = toolbarButtonSize.semantics { contentDescription = description },
    ) {
        Icon(
            painterResource(R.drawable.ic_toolbar_undo), null,
            Modifier.size(toolbarIconSize).graphicsLayer { scaleX = if (redo) -1f else 1f },
        )
    }
}
