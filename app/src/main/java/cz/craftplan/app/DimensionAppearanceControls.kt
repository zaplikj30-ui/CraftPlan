package cz.craftplan.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Stateless with respect to the selected scope; reusable in other settings surfaces. */
@Composable
internal fun DimensionAppearanceControls(
    appearance: DimensionAppearance,
    onChange: (DimensionAppearance) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(if (appearance.inline) R.string.dimension_label_inline
            else R.string.dimension_label_above), Modifier.weight(1f))
        Switch(checked = appearance.inline,
            onCheckedChange = { onChange(appearance.copy(inline = it)) })
    }
    Text(stringResource(R.string.dimension_label_placement_explanation))
    DimensionColorChoice(R.string.dimension_geometry_color, appearance.geometryColor) {
        onChange(appearance.copy(geometryColor = it))
    }
    DimensionColorChoice(R.string.dimension_label_color, appearance.labelColor) {
        onChange(appearance.copy(labelColor = it))
    }
    DimensionBackgroundChoice(appearance.background) {
        onChange(appearance.copy(background = it))
    }
    val automaticPercent = kotlin.math.round(
        automaticDimensionSizeFactor(LocalConfiguration.current.smallestScreenWidthDp) * 100f
    ).toInt()
    Text(stringResource(R.string.dimension_automatic_size, automaticPercent))
    DimensionSizeSlider(R.string.dimension_geometry_size, appearance.geometryPercent) {
        onChange(appearance.copy(geometryPercent = it))
    }
    DimensionSizeSlider(R.string.dimension_label_size, appearance.labelPercent) {
        onChange(appearance.copy(labelPercent = it))
    }
    TextButton(onClick = { onChange(appearance.resetSizes()) }) {
        Text(stringResource(R.string.dimension_reset_sizes))
    }
    InlineDragToleranceSlider(appearance.inlineDragToleranceDegrees) {
        onChange(appearance.copy(inlineDragToleranceDegrees = it))
    }
}

private fun DimensionColor.labelRes(): Int = when (this) {
    DimensionColor.YELLOW -> R.string.dimension_color_yellow
    DimensionColor.WHITE -> R.string.dimension_color_white
    DimensionColor.BLACK -> R.string.dimension_color_black
    DimensionColor.RED -> R.string.dimension_color_red
    DimensionColor.BLUE -> R.string.dimension_color_blue
    DimensionColor.GREEN -> R.string.dimension_color_green
    DimensionColor.ORANGE -> R.string.dimension_color_orange
    DimensionColor.PINK -> R.string.dimension_color_pink
}

private fun DimensionLabelBackground.labelRes(): Int = when (this) {
    DimensionLabelBackground.TRANSPARENT -> R.string.dimension_background_transparent
    DimensionLabelBackground.AUTO -> R.string.dimension_background_auto
    DimensionLabelBackground.BLACK -> R.string.dimension_color_black
    DimensionLabelBackground.WHITE -> R.string.dimension_color_white
}

@Composable
private fun DimensionColorSwatch(color: DimensionColor) {
    Surface(
        modifier = Modifier.size(20.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color(color.argb),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {}
}

@Composable
private fun DimensionColorChoice(labelRes: Int, value: DimensionColor, onChange: (DimensionColor) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(stringResource(labelRes))
    Box {
        OutlinedButton(onClick = { open = true }) {
            DimensionColorSwatch(value)
            Text(stringResource(value.labelRes()), Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (color in DimensionColor.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(color.labelRes())) },
                    leadingIcon = { DimensionColorSwatch(color) },
                    onClick = { open = false; onChange(color) },
                )
            }
        }
    }
}

@Composable
private fun DimensionBackgroundChoice(value: DimensionLabelBackground, onChange: (DimensionLabelBackground) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(stringResource(R.string.dimension_label_background))
    Box {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(value.labelRes())) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (background in DimensionLabelBackground.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(background.labelRes())) },
                    onClick = { open = false; onChange(background) },
                )
            }
        }
    }
    if (value == DimensionLabelBackground.AUTO) {
        Text(stringResource(R.string.dimension_background_auto_explanation))
    }
}

@Composable
private fun DimensionSizeSlider(
    labelRes: Int,
    percent: Int,
    onChange: (Int) -> Unit,
) {
    Text(stringResource(labelRes, percent))
    Slider(
        value = percent.toFloat(),
        onValueChange = { onChange(dimensionSizePercent(kotlin.math.round(it).toInt())) },
        valueRange = MIN_DIMENSION_SIZE_PERCENT.toFloat()..MAX_DIMENSION_SIZE_PERCENT.toFloat(),
        steps = (MAX_DIMENSION_SIZE_PERCENT - MIN_DIMENSION_SIZE_PERCENT) / DIMENSION_SIZE_STEP_PERCENT - 1,
    )
}

/** Test the angle independently of display sizes; changes apply to the next gesture. */
@Composable
private fun InlineDragToleranceSlider(
    degrees: Int,
    onChange: (Int) -> Unit,
) {
    Text(stringResource(R.string.dimension_drag_tolerance, degrees))
    Text(stringResource(R.string.dimension_drag_tolerance_explanation))
    val decrease = stringResource(R.string.dimension_drag_tolerance_decrease)
    val increase = stringResource(R.string.dimension_drag_tolerance_increase)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = { onChange(clampInlineDragTolerance(degrees - 1)) },
            enabled = degrees > MIN_INLINE_DRAG_TOLERANCE_DEGREES,
            modifier = Modifier.width(48.dp).semantics { contentDescription = decrease },
            contentPadding = PaddingValues(0.dp),
        ) { Text("−1°") }
        Slider(
            modifier = Modifier.weight(1f),
            value = degrees.toFloat(),
            onValueChange = { onChange(clampInlineDragTolerance(kotlin.math.round(it).toInt())) },
            valueRange = MIN_INLINE_DRAG_TOLERANCE_DEGREES.toFloat()..
                MAX_INLINE_DRAG_TOLERANCE_DEGREES.toFloat(),
            steps = MAX_INLINE_DRAG_TOLERANCE_DEGREES - MIN_INLINE_DRAG_TOLERANCE_DEGREES - 1,
        )
        TextButton(
            onClick = { onChange(clampInlineDragTolerance(degrees + 1)) },
            enabled = degrees < MAX_INLINE_DRAG_TOLERANCE_DEGREES,
            modifier = Modifier.width(48.dp).semantics { contentDescription = increase },
            contentPadding = PaddingValues(0.dp),
        ) { Text("+1°") }
    }
    TextButton(onClick = { onChange(DEFAULT_INLINE_DRAG_TOLERANCE_DEGREES) }) {
        Text(stringResource(R.string.dimension_reset_drag_tolerance, DEFAULT_INLINE_DRAG_TOLERANCE_DEGREES))
    }
}
