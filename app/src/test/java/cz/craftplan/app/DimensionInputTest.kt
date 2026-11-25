package cz.craftplan.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DimensionInputTest {
    @Test
    fun usesSpecifiedNumericAndSymbolRows() {
        assertEquals(listOf(
            listOf("7", "8", "9", "\b"), listOf("4", "5", "6", "C"),
            listOf("1", "2", "3", "◷"), listOf("⋯", "0", ",", QUICK_KEY),
        ), dimensionKeyRows)
        assertEquals(listOf(
            listOf("+", "−", "×", "R"), listOf("÷", "=", "%", " "),
            listOf("/", "±", "°", "m²"), listOf(SYMBOL_RETURN, "Ø", ".", SYMBOL_PAGE),
        ), dimensionSymbolRows)
        assertEquals(mapOf(
            "7" to "+", "8" to "−", "9" to "×", "4" to "÷", "5" to "=", "6" to "%",
            "1" to "/", "2" to "±", "3" to "°", "0" to "Ø", "," to ".",
            "\b" to "R", "C" to " ", "◷" to "m²",
        ), dimensionAlternateSymbols)
    }

    @Test
    fun symbolGridMatchesNumericGridAndOnlyInsertingSymbolsCanBeAssigned() {
        val numbers = displayedDimensionKeyRows(showSymbols = false)
        val symbols = displayedDimensionKeyRows(showSymbols = true)
        assertEquals(List(4) { 4 }, numbers.map { it.size })
        assertEquals(numbers.map { it.size }, symbols.map { it.size })
        assertEquals(QUICK_KEY, numbers.last().last())
        assertEquals(SYMBOL_PAGE, symbols.last().last())
        assertFalse(isDimensionKeyEnabled("◷"))
        assertEquals(28, dimensionQuickSymbolOptions.distinct().size)
        for (symbol in dimensionQuickSymbolOptions) assertEquals(symbol, validQuickSymbol(symbol))
        for (invalid in listOf("", QUICK_KEY, SYMBOL_RETURN, SYMBOL_PAGE, "C", "7")) assertEquals("", validQuickSymbol(invalid))
    }

    @Test
    fun reorderedSymbolsUpdateLongPressAndPersistWithFixedReturn() {
        val original = KeySymbolLayout.default()
        val swapped = original.swap("+", "×")
        assertEquals("×", swapped.rows()[0][0])
        assertEquals("+", swapped.rows()[0][2])
        assertEquals("×", swapped.alternate("7"))
        assertEquals("+", swapped.alternate("9"))
        assertEquals(SYMBOL_RETURN, swapped.rows()[3][0])
        assertEquals(SYMBOL_PAGE, swapped.rows()[3][3])
        assertEquals(swapped, KeySymbolLayout.decode(swapped.encode()))
        assertEquals(original, KeySymbolLayout.decode("0,0"))
        assertEquals(original, original.swap("+", SYMBOL_RETURN))
        assertEquals(300, DEFAULT_KEY_HOLD_MS)
        assertEquals(150, keyHoldMs(0))
        assertEquals(350, keyHoldMs(337))
        assertEquals(1000, keyHoldMs(2000))
    }

    @Test
    fun all28SymbolsSwapAcrossPagesWithoutLossOrMovingControls() {
        val base = KeySymbolLayout.default()
        val numeric = dimensionKeyRows.flatten()
        for (first in base.symbols) for (second in base.symbols) {
            val swapped = base.swap(first, second)
            assertEquals(base.symbols.toSet(), swapped.symbols.toSet())
            assertEquals(28, swapped.symbols.size)
            assertEquals(base, swapped.swap(second, first))
            assertEquals(swapped, KeySymbolLayout.decode(swapped.encode()))
            for (page in 0..1) {
                assertEquals(SYMBOL_RETURN, swapped.rows(page)[3][0])
                assertEquals(SYMBOL_PAGE, swapped.rows(page)[3][3])
            }
            for ((slot, key) in numeric.withIndex()) {
                assertEquals(
                    if (slot == 12 || slot == 15) null else swapped.rows()[slot / 4][slot % 4],
                    swapped.alternate(key),
                )
            }
        }
        val crossPage = base.swap("%", "m³")
        assertEquals("m³", crossPage.alternate("6"))
        assertEquals("%", crossPage.rows(1)[0][0])
        assertEquals("R", base.alternate("\b"))
        assertEquals(" ", base.alternate("C"))
        assertEquals("m²", base.alternate("◷"))
        assertEquals(base, KeySymbolLayout.decode((0..14).joinToString(",")))
        assertEquals(base, KeySymbolLayout.decode("v2:0,0"))
        assertEquals(base, base.swap("%", SYMBOL_PAGE))
        assertNull(base.alternate(QUICK_KEY))
        assertEquals("m³", validQuickSymbol("m³"))
    }

    @Test
    fun multiCharacterSymbolsInsertAtCaretAndRespectLimitsAtomically() {
        val field = TextFieldValue("ABCD", TextRange(1, 3))
        val edited = editDimensionInput(field, "m²")
        assertEquals("Am²D", edited.text)
        assertEquals(TextRange(3), edited.selection)
        assertEquals("m³", editDimensionInput(TextFieldValue(), "m³").text)
        val full = TextFieldValue("123", TextRange(3))
        assertEquals(full, editDimensionInput(full, "m³", 4))
        assertEquals("123m³", editDimensionInput(full, "m³", 5).text)
    }

    @Test
    fun landscapeKeysFitAvailableHeightOrKeepTouchTargetAndScroll() {
        assertEquals(62f, landscapeKeyHeight(300f), 0f)
        assertEquals(50f, landscapeKeyHeight(212f), 0f)
        assertEquals(48f, landscapeKeyHeight(204f), 0f)
        assertEquals(48f, landscapeKeyHeight(140f), 0f)
    }

    @Test
    fun landscapeInputsUseAvailableWidthOrKeepRoomForHorizontalScrolling() {
        assertEquals(440f, landscapeInputRowWidth(440f), 0f)
        assertEquals(360f, landscapeInputRowWidth(360f), 0f)
        assertEquals(360f, landscapeInputRowWidth(240f), 0f)
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
        for (symbol in listOf("+", "−", "±", "%", "×", "÷", "=", "°", "Ø", "m²", "m³", "R")) {
            val edited = editDimensionInput(TextFieldValue("12", TextRange(2)), symbol)
            assertEquals("12$symbol", edited.text)
            assertNull(ManualLengthMeasurement.parse(edited.text, LengthUnit.MM))
        }
    }

    @Test
    fun backHidesEitherKeyboardBeforeAllowingConfirmation() {
        for (field in DimensionField.entries) {
            val default = DimensionKeyboard().open(field)
            for (mode in DimensionKeyboardMode.entries) {
                var keyboard = DimensionKeyboard(field, mode)
                var confirmed = 0
                val hide = { keyboard = keyboard.hide() }
                val confirm = { confirmed += 1 }
                handleDimensionDialogBack(keyboardOpen = keyboard.visible, hideKeyboard = hide, confirm = confirm)
                assertFalse(keyboard.visible)
                assertEquals(default.mode, keyboard.mode)
                assertEquals(default, keyboard.open(field))
                assertEquals(0, confirmed)
                handleDimensionDialogBack(keyboardOpen = keyboard.visible, hideKeyboard = hide, confirm = confirm)
                assertEquals(1, confirmed)
            }
        }
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
