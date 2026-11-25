package cz.craftplan.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensionKeyboardTest {
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
    fun onlyActiveVisibleFieldIsEditableAndNumericModeBlocksNativeIme() {
        for (field in DimensionField.entries) {
            val other = DimensionField.entries.first { it != field }
            val numeric = DimensionKeyboard(field, DimensionKeyboardMode.NUMERIC)
            assertTrue(numeric.edits(field))
            assertFalse(numeric.edits(other))
            assertFalse(numeric.allowsSystemInput(field))
            assertFalse(numeric.allowsSystemInput(other))
            val system = numeric.switch()
            assertTrue(system.edits(field))
            assertTrue(system.allowsSystemInput(field))
            assertFalse(system.edits(other))
            assertFalse(system.allowsSystemInput(other))
            for (keyboard in listOf(numeric.hide(), system.hide())) {
                assertFalse(keyboard.edits(field))
                assertFalse(keyboard.allowsSystemInput(field))
            }
        }
    }

    @Test
    fun numericKeypadRespectsCursorAndSelectionInBothFields() {
        val value = TextFieldValue("1234", TextRange(2))
        assertEquals(TextFieldValue("12934", TextRange(3)), editDimensionInput(value, '9'))
        assertEquals(TextFieldValue("134", TextRange(1)), editDimensionInput(value, '\b'))
        val note = TextFieldValue("ABCD", TextRange(1, 3))
        assertEquals(TextFieldValue("A7D", TextRange(2)), editDimensionInput(note, '7', MAX_DIMENSION_NOTE_LENGTH))
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
    fun systemShowIgnoresTransientVisibilityFromPreviousRequest() = runBlocking {
        val seen = mutableListOf<Boolean>()
        awaitStableImeVisibility(flow {
            seen += true
            emit(true)
            seen += false
            emit(false)
            delay(40)
            seen += true
            emit(true)
        }, true, stableMs = 20)
        assertEquals(listOf(true, false, true), seen)
    }

    @Test
    fun systemDismissalIgnoresTransientHideDuringHandoff() = runBlocking {
        val seen = mutableListOf<Boolean>()
        awaitStableImeVisibility(flow {
            seen += false
            emit(false)
            seen += true
            emit(true)
            delay(40)
            seen += false
            emit(false)
        }, false, stableMs = 20)
        assertEquals(listOf(false, true, false), seen)
    }

    @Test
    fun missingNativeKeyboardDoesNotAcknowledgeAnOpenSession() = runBlocking {
        assertEquals(null, withTimeoutOrNull(40L) {
            awaitStableImeVisibility(MutableStateFlow(false), true, stableMs = 10)
            true
        })
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
    fun keypadRevealDoesNotStackOnNativeIme() {
        assertEquals(0, keypadRevealHeight(260, 0f, 0))
        assertEquals(130, keypadRevealHeight(260, 0.5f, 0))
        assertEquals(260, keypadRevealHeight(260, 1f, 0))
        assertEquals(160, keypadRevealHeight(260, 1f, 100))
        assertEquals(30, keypadRevealHeight(260, 0.5f, 100))
        assertEquals(0, keypadRevealHeight(260, 1f, 300))
    }

    @Test
    fun numericNoteEntryUsesNoteLimit() {
        val text = "a".repeat(100)
        val edited = editDimensionInput(TextFieldValue(text, TextRange(100)), '7', MAX_DIMENSION_NOTE_LENGTH)
        assertEquals(text + "7", edited.text)
    }
}
