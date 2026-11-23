package cz.craftplan.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensionKeyboardTest {
    @Test
    fun remembersKeyboardForEachFieldAndReopensExplicitly() {
        val initial = DimensionKeyboard()
        assertEquals(DimensionKeyboardMode.NUMERIC, initial.mode)
        val note = initial.open(DimensionField.NOTE)
        assertTrue(note.systemOpen)
        val numericNote = note.switch()
        assertEquals(DimensionField.NOTE, numericNote.field)
        assertFalse(numericNote.systemOpen)
        val systemValue = numericNote.open(DimensionField.VALUE).switch()
        assertTrue(systemValue.systemOpen)
        assertFalse(systemValue.hide().visible)
        assertTrue(systemValue.hide().open(DimensionField.VALUE).systemOpen)
        val reopenedNote = systemValue.hide().open(DimensionField.NOTE)
        assertEquals(DimensionKeyboardMode.NUMERIC, reopenedNote.mode)
        assertEquals(DimensionField.NOTE, reopenedNote.switch().field)
    }

    @Test
    fun numericNoteEntryUsesNoteLimit() {
        val text = "a".repeat(100)
        val edited = editDimensionInput(TextFieldValue(text, TextRange(100)), '7', MAX_DIMENSION_NOTE_LENGTH)
        assertEquals(text + "7", edited.text)
    }
}
