package cz.craftplan.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensionKeyboardTest {
    @Test
    fun keyboardSwitchTargetsTheFieldFocusedByAndroidHandwriting() {
        val initial = DimensionKeyboard()
        val notesWithSystem = initial.switch(DimensionField.NOTE)
        assertEquals(DimensionField.NOTE, notesWithSystem.field)
        assertTrue(notesWithSystem.systemOpen)
        val valueWithKeypad = notesWithSystem.switch(DimensionField.VALUE)
        assertEquals(initial, valueWithKeypad)
    }

    @Test
    fun valueReturnsToNumericAfterEditingNote() {
        val valueSystem = DimensionKeyboard().switch()
        assertTrue(valueSystem.systemOpen)
        val note = valueSystem.open(DimensionField.NOTE)
        assertTrue(note.systemOpen)
        assertEquals(DimensionKeyboard(), note.open(DimensionField.VALUE))
    }

    @Test
    fun noteReturnsToSystemAfterEditingValue() {
        val numericNote = DimensionKeyboard().open(DimensionField.NOTE).switch()
        assertEquals(DimensionField.NOTE, numericNote.field)
        assertFalse(numericNote.systemOpen)
        val value = numericNote.open(DimensionField.VALUE)
        assertFalse(value.systemOpen)
        assertTrue(value.open(DimensionField.NOTE).systemOpen)
    }

    @Test
    fun sameFieldKeepsManualChoiceAndCanSwitchAfterReopening() {
        for (field in DimensionField.entries) {
            val initial = DimensionKeyboard().open(field)
            val changed = initial.switch()
            assertEquals(initial, changed.switch())
            assertEquals(changed, changed.open(field))
            assertFalse(changed.hide().visible)
            assertEquals(changed, changed.hide().open(field))
            assertEquals(initial, changed.hide().open(field).switch())
        }
    }

    @Test
    fun modeChangesKeepDraftTextAndAllowEditingAtPreviousSelections() {
        val value = TextFieldValue("12,30", TextRange(0, 2))
        val note = TextFieldValue("Kóta A", TextRange(5))
        val states = DimensionKeyboard().switch().open(DimensionField.NOTE).switch()
        val selectedValue = states.open(DimensionField.VALUE)
        assertEquals("4,30", editDimensionInput(value, '4').text)
        assertEquals(TextRange(1), editDimensionInput(value, '4').selection)
        assertEquals("Kóta 7A", editDimensionInput(note, '7', MAX_DIMENSION_NOTE_LENGTH).text)
        assertEquals(DimensionKeyboardMode.NUMERIC, selectedValue.mode)
        assertEquals(TextRange(0, 2), value.selection)
        assertEquals(TextRange(5), note.selection)
    }

    @Test
    fun systemDismissalIgnoresHiddenInsetsBeforeImeAppears() = runBlocking {
        val seen = mutableListOf<Boolean>()
        awaitSystemImeDismissal(flowOf(false, false, true, false, true).onEach { seen.add(it) })
        assertEquals(listOf(false, false, true, false), seen)
    }

    @Test
    fun delayedImeHideHasDeadlineAndDoesNotPreventFollowingTransition() = runBlocking {
        // A visible IME that never reports hidden must not suspend the transition indefinitely.
        assertFalse(awaitSystemImeHidden(MutableStateFlow(true), timeoutMs = 20L))
        assertTrue(awaitSystemImeHidden(flowOf(true, false), timeoutMs = 20L))
        val keyboard = DimensionKeyboard().switch().switch().switch()
        assertTrue(keyboard.systemOpen)
    }

    @Test
    fun numericNoteEntryUsesNoteLimit() {
        val text = "a".repeat(100)
        val edited = editDimensionInput(TextFieldValue(text, TextRange(100)), '7', MAX_DIMENSION_NOTE_LENGTH)
        assertEquals(text + "7", edited.text)
    }
}
