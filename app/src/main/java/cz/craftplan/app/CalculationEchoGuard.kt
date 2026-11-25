package cz.craftplan.app

/** A narrowly scoped duplicate callback before a calculated value is published.
 * Main-thread, per field and dialog lifetime. No time window and no persisted text. */
internal class CalculationEchoGuard<T : Any> {
    private data class Pending<T>(val submitted: T, val result: T)
    private var pending: Pending<T>? = null

    fun arm(submitted: T, result: T) {
        pending = if (submitted != result) Pending(submitted, result) else null
    }

    /** Reject only a duplicate of the exact submitted state while the result is current.
     * Any distinct edit (including selection changes) ends the protection. */
    fun isEcho(current: T, incoming: T): Boolean {
        val expected = pending ?: return false
        if (current == expected.result && incoming == expected.submitted) return true
        clear()
        return false
    }

    /** End protection once the result is supplied to the actual text field. */
    fun published(value: T) {
        if (pending?.result == value) clear()
    }

    fun clear() {
        pending = null
    }
}
