package cz.craftplan.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DimensionInputTest {
    @Test
    fun usesSpecifiedNumericAndSymbolRows() {
        assertEquals(listOf("789\b", "456C", "123◷", "⋯0,_"), dimensionKeyRows)
        assertEquals(listOf("+−±/", ".% ↩"), dimensionSymbolRows)
    }

    @Test
    fun clearAndBackspaceCanConfirmAnUnmeasuredDimension() {
        val dimension = PhotoDimension(
            UUID.randomUUID(), PhotoId.SYNTHETIC_WORKBENCH, PhotoAnchor(0f, 0f), PhotoAnchor(1f, 1f),
        ).withManualLength("1/3 in")
        val draft = DimensionValueDraft(dimension)
        val cleared = editDimensionInput(TextFieldValue(draft.input, TextRange(3)), 'C')
        assertEquals(TextFieldValue(), cleared)
        assertNull(draft.type(cleared.text).confirm(dimension)!!.measurement)
        val deleted = editDimensionInput(TextFieldValue("0", TextRange(1)), '\b')
        assertNull(draft.type(deleted.text).confirm(dimension)!!.measurement)
        assertEquals("127/15", dimension.measurement?.length?.displayNumber(LengthUnit.MM))
    }

    @Test
    fun unsupportedSymbolsAreEditableButNotValidMeasurements() {
        for (symbol in "+−±%") {
            val edited = editDimensionInput(TextFieldValue("12", TextRange(2)), symbol)
            assertEquals("12$symbol", edited.text)
            assertNull(ManualLengthMeasurement.parse(edited.text, LengthUnit.MM))
        }
    }

    @Test
    fun backHidesOpenImeBeforeAllowingConfirmation() {
        var hidden = 0
        var confirmed = 0
        val hide = { hidden += 1 }
        val confirm = { confirmed += 1 }
        handleDimensionDialogBack(keyboardOpen = true, hideKeyboard = hide, confirm = confirm)
        assertEquals(1, hidden)
        assertEquals(0, confirmed)
        handleDimensionDialogBack(keyboardOpen = false, hideKeyboard = hide, confirm = confirm)
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
