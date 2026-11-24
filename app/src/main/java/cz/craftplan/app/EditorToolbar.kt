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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
                    IconButton(onClick = state::requestFit) {
                        Icon(painterResource(R.drawable.ic_toolbar_fit), stringResource(R.string.toolbar_fit))
                    }
                    IconButton(onClick = { state.showNotes = !state.showNotes }) {
                        Icon(
                            painterResource(if (state.showNotes) R.drawable.ic_notes_visible else R.drawable.ic_notes_hidden),
                            stringResource(if (state.showNotes) R.string.dimension_notes_visible else R.string.dimension_notes_hidden),
                        )
                    }
                    IconButton(
                        enabled = state.scene.selectedId != null || tool != DimensionTool.SELECT,
                        onClick = { state.scene = state.scene.closePhoto() },
                    ) {
                        Icon(painterResource(R.drawable.ic_toolbar_deselect), stringResource(R.string.toolbar_deselect))
                    }
                }
            }
            if (repeat || tool == DimensionTool.SINGLE) {
                Text(
                    stringResource(if (repeat) R.string.toolbar_repeat_active else R.string.drag_dimension_short),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(
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
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_tools_list), stringResource(R.string.toolbar_tools))
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
                    Modifier.size(48.dp)
                        .background(toolBackground, RoundedCornerShape(8.dp))
                        .semantics { contentDescription = dimensionLabel }
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { state.scene = state.scene.prepareSingle() },
                            onLongClickLabel = repeatAction,
                            onLongClick = { state.scene = state.scene.toggleRepeat() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_dimension_tool), null,
                        tint = if (repeat) activeColors.onPrimary else activeColors.onSurface,
                    )
                }
                }
                if (expanded) {
                    Box(Modifier.width(1.dp).height(28.dp).background(activeColors.outlineVariant))
                    Row(Modifier.weight(1f).horizontalScroll(scrollState), verticalAlignment = Alignment.CenterVertically) {
                        ToolbarAction("↶", R.string.toolbar_undo, state.history.canUndo, state::undo)
                        ToolbarAction("↷", R.string.toolbar_redo, state.history.canRedo, state::redo)
                        IconButton(
                            enabled = state.scene.selectedId != null,
                            onClick = { state.applyDimensionChange(state.scene.deleteSelected()) },
                        ) {
                            Icon(painterResource(R.drawable.ic_dimension_delete), stringResource(R.string.toolbar_delete))
                        }
                        val locked = state.labelsLocked
                        val lockLabel = stringResource(if (locked) R.string.toolbar_label_locked else R.string.toolbar_label_unlocked)
                        IconToggleButton(
                            checked = locked,
                            onCheckedChange = { state.toggleLabelLock() },
                            colors = IconButtonDefaults.iconToggleButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = activeColors.onSurface,
                                checkedContainerColor = activeColors.primaryContainer,
                                checkedContentColor = activeColors.onPrimaryContainer,
                            ),
                            modifier = Modifier.semantics { contentDescription = lockLabel },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("123", style = MaterialTheme.typography.labelSmall)
                                    Text("↔", style = MaterialTheme.typography.labelSmall)
                                }
                                Icon(
                                    painterResource(if (locked) R.drawable.ic_number_locked else R.drawable.ic_number_unlocked),
                                    null, Modifier.size(20.dp),
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
private fun ToolbarAction(glyph: String, label: Int, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(label)
    IconButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
    ) { Text(glyph, style = MaterialTheme.typography.titleLarge) }
}
