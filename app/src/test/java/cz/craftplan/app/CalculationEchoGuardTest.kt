package cz.craftplan.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculationEchoGuardTest {
    private data class Edit(val text: String, val caret: Int = text.length, val composing: Boolean = false)
    private val submitted = Edit("4x50=")
    private val result = Edit("4 × 50 = 200")

    @Test
    fun repeatedExactCallbacksBeforePublicationDoNotReplaceResult() {
        val guard = CalculationEchoGuard<Edit>()
        guard.arm(submitted, result)
        guard.published(Edit("4x50"))
        assertTrue(guard.isEcho(result, submitted))
        assertTrue(guard.isEcho(result, submitted))
        guard.published(result)
        assertFalse(guard.isEcho(result, submitted))
    }

    @Test
    fun deliberateNewInputDeletionOrSelectionIsNotBlocked() {
        for (edit in listOf(Edit(result.text + "+"), Edit(result.text.dropLast(1)),
            Edit(""), Edit(result.text, 0), submitted.copy(composing = true),
            submitted.copy(caret = 1))) {
            val guard = CalculationEchoGuard<Edit>()
            guard.arm(submitted, result)
            assertFalse(guard.isEcho(result, edit))
            assertFalse(guard.isEcho(result, submitted))
        }
    }

    @Test
    fun otherFieldAndChangedCurrentValueAreNotProtected() {
        val value = CalculationEchoGuard<Edit>()
        val note = CalculationEchoGuard<Edit>()
        value.arm(submitted, result)
        assertFalse(note.isEcho(result, submitted))
        assertFalse(value.isEcho(Edit("different"), submitted))
        assertFalse(value.isEcho(result, submitted))
    }

    @Test
    fun explicitOtherSourceAndNewCalculationResetProtection() {
        val guard = CalculationEchoGuard<Edit>()
        guard.arm(submitted, result)
        guard.clear()
        assertFalse(guard.isEcho(result, submitted))
        val next = Edit(result.text + "+1=")
        val nextResult = Edit(result.text + " + 1 = 201")
        guard.arm(next, nextResult)
        assertTrue(guard.isEcho(nextResult, next))
        guard.published(nextResult)
        assertFalse(guard.isEcho(nextResult, next))
        guard.arm(next, next)
        assertFalse(guard.isEcho(next, next))
    }
}
