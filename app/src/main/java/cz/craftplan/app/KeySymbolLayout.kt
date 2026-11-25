package cz.craftplan.app

internal const val DEFAULT_KEY_HOLD_MS = 300
internal fun keyHoldMs(value: Int): Int = ((value.coerceIn(150, 1000) + 25) / 50) * 50

internal const val QUICK_KEY = "_"
internal const val SYMBOL_RETURN = "↩"
internal const val SYMBOL_PAGE = "⇄"
internal const val SYMBOL_PAGE_COUNT = 2
internal const val SYMBOLS_PER_PAGE = 14
internal const val SYMBOL_RETURN_SLOT = 12
internal const val SYMBOL_PAGE_SLOT = 15

internal val dimensionKeyRows = listOf(
    listOf("7", "8", "9", "\b"),
    listOf("4", "5", "6", "C"),
    listOf("1", "2", "3", "◷"),
    listOf("⋯", "0", ",", QUICK_KEY),
)
internal val dimensionQuickSymbolOptions = listOf(
    "+", "−", "×", "R", "÷", "=", "%", " ", "/", "±", "°", "m²", "Ø", ".",
    "m³", "≈", "≤", "≥", "(", ")", "[", "]", "√", "∠", "µ", "²", "³", ":",
)
internal val dimensionSymbolRows = KeySymbolLayout.default().rows()
internal val dimensionAlternateSymbols = dimensionKeyRows.flatten().mapNotNull { key ->
    KeySymbolLayout.default().alternate(key)?.let { key to it }
}.toMap()

internal fun validQuickSymbol(symbol: String): String =
    symbol.takeIf { it in dimensionQuickSymbolOptions } ?: ""

internal fun displayedDimensionKeyRows(showSymbols: Boolean): List<List<String>> =
    if (showSymbols) dimensionSymbolRows else dimensionKeyRows

internal fun isDimensionKeyEnabled(key: String): Boolean = key != "◷"

internal data class KeySymbolLayout private constructor(val symbols: List<String>) {
    fun rows(page: Int = 0): List<List<String>> {
        require(page in 0 until SYMBOL_PAGE_COUNT)
        var index = page * SYMBOLS_PER_PAGE
        return List(16) { slot ->
            when (slot) {
                SYMBOL_RETURN_SLOT -> SYMBOL_RETURN
                SYMBOL_PAGE_SLOT -> SYMBOL_PAGE
                else -> symbols[index++]
            }
        }.chunked(4)
    }

    fun alternate(key: String): String? {
        val slot = dimensionKeyRows.flatten().indexOf(key)
        if (slot < 0 || slot == SYMBOL_RETURN_SLOT || slot == SYMBOL_PAGE_SLOT) return null
        return rows()[slot / 4][slot % 4]
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

    // The old one-page indices have different meanings; reset that layout rather
    // than silently map them to different symbols. Keep the independent quick key.
    fun encode(): String = "v2:" + symbols.joinToString(",") { dimensionQuickSymbolOptions.indexOf(it).toString() }

    companion object {
        fun default() = KeySymbolLayout(dimensionQuickSymbolOptions.toList())
        fun decode(encoded: String?): KeySymbolLayout {
            if (encoded == null || !encoded.startsWith("v2:")) return default()
            val parts = encoded.removePrefix("v2:").split(',')
            val indices = parts.mapNotNull { it.toIntOrNull() }
            if (indices.size != dimensionQuickSymbolOptions.size ||
                indices.toSet() != dimensionQuickSymbolOptions.indices.toSet() ||
                parts.size != indices.size) return default()
            return KeySymbolLayout(indices.map { dimensionQuickSymbolOptions[it] })
        }
    }
}
