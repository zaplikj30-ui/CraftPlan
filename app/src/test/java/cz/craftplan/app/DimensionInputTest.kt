package cz.craftplan.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class DimensionInputTest {
    @Test
    fun usesStandardNumericRowsWithDecimalAndBackspace() {
        assertEquals(listOf("123", "456", "789", ",0\b"), dimensionKeyRows)
    }

    @Test
    fun backHidesOpenImeBeforeAllowingConfirmation() {
        var hidden = 0
        var confirmed = 0
        val hide = { hidden += 1 }
        val confirm = { confirmed += 1 }
        handleDimensionDialogBack(imeOpen = true, hideIme = hide, confirm = confirm)
        assertEquals(1, hidden)
        assertEquals(0, confirmed)
        handleDimensionDialogBack(imeOpen = false, hideIme = hide, confirm = confirm)
        assertEquals(1, hidden)
        assertEquals(1, confirmed)
    }

    @Test
    fun insertsAtCaretAndReplacesSelection() {
        val decimal = editDimensionInput(TextFieldValue("125", TextRange(1)), ',')
        assertEquals("1,25", decimal.text)
        assertEquals(TextRange(2), decimal.selection)

        val fraction = editDimensionInput(TextFieldValue("1,25", TextRange(4, 1)), '/')
        assertEquals("1/", fraction.text)
        assertEquals(TextRange(2), fraction.selection)
    }

    @Test
    fun backspaceDeletesSelectionOrPreviousCharacter() {
        val shortened = editDimensionInput(TextFieldValue("12/3", TextRange(3)), '\b')
        assertEquals("123", shortened.text)
        assertEquals(TextRange(2), shortened.selection)
        assertEquals("3", editDimensionInput(TextFieldValue("12/3", TextRange(0, 3)), '\b').text)
        assertEquals("3", editDimensionInput(TextFieldValue("3", TextRange.Zero), '\b').text)
    }

    @Test
    fun respectsInputLimitAndSupportsExactMixedFractions() {
        val limit = ManualLengthMeasurement.MAX_INPUT_LENGTH
        val full = TextFieldValue("1".repeat(limit), TextRange(limit))
        assertEquals(full, editDimensionInput(full, '2'))
        assertEquals(limit - 1, editDimensionInput(full, '\b').text.length)
        val entered = "1 1/16".fold(TextFieldValue(), ::editDimensionInput)
        val measurement = ManualLengthMeasurement.parse(entered.text, LengthUnit.IN)!!
        assertEquals(LengthMmEncoding.Decimal("26.9875"), measurement.length.encoding)
    }
}
