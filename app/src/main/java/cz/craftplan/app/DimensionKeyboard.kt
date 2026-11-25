package cz.craftplan.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

internal enum class DimensionField { VALUE, NOTE }
internal enum class DimensionKeyboardMode { NUMERIC, SYSTEM }

internal data class DimensionKeyboard(
    val field: DimensionField = DimensionField.VALUE,
    val mode: DimensionKeyboardMode = DimensionKeyboardMode.NUMERIC,
    val visible: Boolean = true,
) {
    val systemOpen: Boolean get() = visible && mode == DimensionKeyboardMode.SYSTEM

    fun edits(target: DimensionField): Boolean = visible && field == target

    fun allowsSystemInput(target: DimensionField): Boolean = systemOpen && field == target

    fun open(field: DimensionField): DimensionKeyboard {
        val nextMode = when {
            visible && field == this.field -> mode
            field == DimensionField.VALUE -> DimensionKeyboardMode.NUMERIC
            else -> DimensionKeyboardMode.SYSTEM
        }
        return copy(field = field, mode = nextMode, visible = true)
    }
    fun hide() = copy(
        mode = if (field == DimensionField.VALUE) DimensionKeyboardMode.NUMERIC else DimensionKeyboardMode.SYSTEM,
        visible = false,
    )

    fun switch(): DimensionKeyboard {
        val next = if (mode == DimensionKeyboardMode.NUMERIC) DimensionKeyboardMode.SYSTEM else DimensionKeyboardMode.NUMERIC
        return copy(mode = next, visible = true)
    }
}

// A delayed Android IME hide must never suspend the next keyboard opening forever.
internal suspend fun awaitSystemImeHidden(visibility: Flow<Boolean>, timeoutMs: Long = 500L): Boolean =
    withTimeoutOrNull(timeoutMs) {
        visibility.first { !it }
        true
    } ?: false

// Ignore short-lived insets during a focus or keyboard handoff.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal suspend fun awaitStableImeVisibility(
    visibility: Flow<Boolean>,
    expected: Boolean,
    stableMs: Long,
) {
    visibility.transformLatest { visible ->
        if (visible == expected) {
            delay(stableMs)
            emit(Unit)
        }
    }.first()
}

// The system IME already occupies its own animated bottom inset.
internal fun keypadRevealHeight(fullHeight: Int, fraction: Float, imeOverlap: Int): Int =
    ((fullHeight.coerceAtLeast(0) * fraction.coerceIn(0f, 1f)).roundToInt() - imeOverlap.coerceAtLeast(0))
        .coerceAtLeast(0)
