package cz.craftplan.app

internal const val DEFAULT_KEY_HOLD_MS = 300
internal fun keyHoldMs(value: Int): Int = ((value.coerceIn(150, 1000) + 25) / 50) * 50

internal data class KeySymbolLayout private constructor(val symbols: List<String>) {
    fun rows(): List<List<String>> {
        var index = 0
        return dimensionSymbolRows.map { row ->
            row.map { if (it == SYMBOL_RETURN) it else symbols[index++] }
        }
    }

    fun alternate(key: String): String? = dimensionAlternateSymbols[key]?.let {
        symbols[dimensionQuickSymbolOptions.indexOf(it)]
    }

    fun swap(first: String, second: String): KeySymbolLayout {
        val a = symbols.indexOf(first)
        val b = symbols.indexOf(second)
        if (a < 0 || b < 0 || a == b) return this
        val result = symbols.toMutableList()
        result[a] = second
        result[b] = first
        return KeySymbolLayout(result.toList())
    }

    // Persist indices, not a whitespace-delimited list of symbols.
    fun encode(): String = symbols.joinToString(",") { dimensionQuickSymbolOptions.indexOf(it).toString() }

    companion object {
        fun default() = KeySymbolLayout(dimensionQuickSymbolOptions.toList())
        fun decode(encoded: String?): KeySymbolLayout {
            val indices = encoded?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return default()
            if (indices.size != dimensionQuickSymbolOptions.size ||
                indices.toSet() != dimensionQuickSymbolOptions.indices.toSet() ||
                encoded.split(',').size != indices.size) return default()
            return KeySymbolLayout(indices.map { dimensionQuickSymbolOptions[it] })
        }
    }
}
