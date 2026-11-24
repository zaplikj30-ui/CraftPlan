package cz.craftplan.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val toolbarShape = RoundedCornerShape(8.dp)
private val toolbarIconSize = 28.dp
private val toolbarButtonSize = Modifier.size(width = 52.dp, height = 56.dp)
private val toolbarQuickSize = Modifier.size(width = 56.dp, height = 56.dp)

@Composable
internal fun EditorToolbar(state: PhotoEditorState, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val expanded = state.toolbarExpanded
    val verticalExpanded = state.toolbarExtraExpanded
    val currentExpanded by rememberUpdatedState(expanded)
    val currentVertical by rememberUpdatedState(verticalExpanded)
    val scrollState = rememberScrollState()
    val tool = state.scene.tool
    val repeat = tool == DimensionTool.REPEAT
    val activeColors = MaterialTheme.colorScheme
    val handleLabel = stringResource(if (verticalExpanded) R.string.toolbar_collapse else R.string.toolbar_expand)
    val fixedToolsLabel = stringResource(R.string.toolbar_horizontal_gesture)
    val repeatAction = stringResource(if (repeat) R.string.toolbar_repeat_off else R.string.toolbar_repeat_on)
    Surface(if (expanded || verticalExpanded) modifier.fillMaxWidth() else modifier, shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp), tonalElevation = 4.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(width = 64.dp, height = 24.dp)
                    .semantics { contentDescription = handleLabel }
                    .clickable(role = Role.Button) { state.setToolbarExtraExpansion(!state.toolbarExtraExpanded) }
                    .pointerInput(state) {
                        var motion = 0f
                        detectVerticalDragGestures(
                            onDragStart = { motion = 0f },
                            onDragEnd = {
                                state.setToolbarExtraExpansion(toolbarExpansionAfterDrag(currentVertical, -motion, 4.dp.toPx()))
                            },
                        ) { change, delta ->
                            motion += delta
                            change.consume()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(32.dp, 4.dp).background(activeColors.onSurfaceVariant, RoundedCornerShape(2.dp)))
            }
            if (verticalExpanded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = state::requestFit, modifier = toolbarButtonSize) {
                        Icon(painterResource(R.drawable.ic_toolbar_fit), stringResource(R.string.toolbar_fit), Modifier.size(toolbarIconSize))
                    }
                    IconButton(
                        onClick = { state.showNotes = !state.showNotes },
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
                        onClick = { state.scene = state.scene.closePhoto() },
                        modifier = toolbarButtonSize,
                    ) {
                        Icon(painterResource(R.drawable.ic_toolbar_deselect), stringResource(R.string.toolbar_deselect), Modifier.size(toolbarIconSize))
                    }
                    if (BuildConfig.ENABLE_STRESS_TOOLS) StressToolbarMenu(state)
                }
            }
            if (repeat || tool == DimensionTool.SINGLE) {
                Text(
                    stringResource(if (repeat) R.string.toolbar_repeat_active else R.string.drag_dimension_short),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(
                modifier = Modifier.align(Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Row(
                    Modifier.pointerInput(state) {
                        var motion = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { motion = 0f },
                            onDragEnd = {
                                state.setToolbarExpansion(toolbarExpansionAfterDrag(currentExpanded, motion, 4.dp.toPx()))
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
                            onClick = { menuOpen = true },
                            modifier = toolbarQuickSize.background(
                                if (menuOpen) activeColors.primaryContainer else Color.Transparent, toolbarShape,
                            ),
                        ) {
                            Icon(painterResource(R.drawable.ic_tools_list), stringResource(R.string.toolbar_tools), Modifier.size(toolbarIconSize))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.toolbar_dimension)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_dimension_tool), null) },
                                onClick = {
                                    state.scene = state.scene.prepareSingle()
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
                                onClick = { state.scene = state.scene.tapDrawingTool() },
                                onLongClickLabel = repeatAction,
                                onLongClick = { state.scene = state.scene.toggleRepeat() },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_dimension_tool), null,
                            Modifier.size(toolbarIconSize),
                            tint = if (repeat) activeColors.onPrimary else activeColors.onSurface,
                        )
                        if (repeat) {
                            Icon(painterResource(R.drawable.ic_toolbar_repeat), null, Modifier.size(44.dp), activeColors.onPrimary)
                        }
                    }
                }
                if (expanded) {
                    Box(Modifier.width(1.dp).height(28.dp).background(activeColors.outlineVariant))
                    Row(Modifier.weight(1f).horizontalScroll(scrollState), verticalAlignment = Alignment.CenterVertically) {
                        HistoryAction(false, R.string.toolbar_undo, state.history.canUndo, state::undo)
                        HistoryAction(true, R.string.toolbar_redo, state.history.canRedo, state::redo)
                        IconButton(
                            enabled = state.scene.selectedId != null,
                            onClick = { state.applyDimensionChange(state.scene.deleteSelected()) },
                            modifier = toolbarButtonSize,
                        ) {
                            Icon(painterResource(R.drawable.ic_dimension_delete), stringResource(R.string.toolbar_delete), Modifier.size(toolbarIconSize))
                        }
                        val locked = state.labelsLocked
                        val lockLabel = stringResource(if (locked) R.string.toolbar_label_locked else R.string.toolbar_label_unlocked)
                        IconToggleButton(
                            checked = locked,
                            onCheckedChange = { state.toggleLabelLock() },
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
                    }
                }
            }
        }
    }
}

@Composable
private fun StressToolbarMenu(state: PhotoEditorState) {
    var open by remember { mutableStateOf(false) }
    val photoId = PhotoId.SYNTHETIC_WORKBENCH
    val total = state.scene.dimensions.size
    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.stress_toolbar, total))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(max = 310.dp)) {
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
            val metrics = state.stressMetrics.snapshot()
            val heap = Runtime.getRuntime()
            val heapMiB = (heap.totalMemory() - heap.freeMemory()) / 1_048_576.0
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.stress_counts, total, generated))
                if (state.stressBusy) Text(stringResource(R.string.stress_working))
                state.stressGenerationMs?.let { Text(stringResource(R.string.stress_generation_ms, it)) }
                Text(stringResource(R.string.stress_preparation, metrics.maxPreparationMs))
                Text(stringResource(R.string.stress_draw, metrics.maxDrawMs, metrics.draws))
                Text(stringResource(R.string.stress_hit, metrics.maxHitMs, metrics.hits))
                Text(stringResource(R.string.stress_gesture, metrics.maxGestureGapMs, metrics.gapsOver50ms))
                Text(stringResource(R.string.stress_heap, heapMiB))
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
