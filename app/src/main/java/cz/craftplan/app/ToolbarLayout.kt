package cz.craftplan.app

internal enum class ToolbarIcon(val id: String, val defaultRow: ToolbarRow) {
    SETTINGS("settings", ToolbarRow.MAIN),
    UNDO("undo", ToolbarRow.MAIN),
    REDO("redo", ToolbarRow.MAIN),
    DELETE("delete", ToolbarRow.MAIN),
    LABEL_LOCK("label-lock", ToolbarRow.MAIN),
    FIT("fit", ToolbarRow.EXTRA),
    NOTES("notes", ToolbarRow.EXTRA),
    DESELECT("deselect", ToolbarRow.EXTRA),
    DIMENSION_MOVE("dimension-move", ToolbarRow.EXTRA),
}

internal enum class ToolbarRow { MAIN, EXTRA }

internal data class ToolbarDrop(val row: ToolbarRow, val before: ToolbarIcon? = null)

internal data class ToolbarLayout private constructor(
    private val main: List<ToolbarIcon>,
    private val extra: List<ToolbarIcon>,
) {
    fun icons(row: ToolbarRow): List<ToolbarIcon> = if (row == ToolbarRow.MAIN) main else extra

    fun move(icon: ToolbarIcon, drop: ToolbarDrop): ToolbarLayout {
        if (drop.before == icon || (drop.before != null && drop.before !in icons(drop.row))) return this
        val newMain = (main - icon).toMutableList()
        val newExtra = (extra - icon).toMutableList()
        val destination = if (drop.row == ToolbarRow.MAIN) newMain else newExtra
        val index = drop.before?.let(destination::indexOf) ?: destination.size
        destination.add(index, icon)
        return ToolbarLayout(newMain, newExtra)
    }

    fun toPreference(): String = ToolbarRow.entries.joinToString("|") { row ->
        icons(row).joinToString(",") { it.id }
    }

    companion object {
        val Default = fromPreference(null)

        fun fromPreference(value: String?): ToolbarLayout {
            val rows = value?.split('|', limit = 3)?.takeIf { it.size == 2 }
            val known = ToolbarIcon.entries.associateBy(ToolbarIcon::id)
            val seen = mutableSetOf<ToolbarIcon>()
            val main = rows?.get(0).orEmpty().split(',').mapNotNull(known::get).filter(seen::add).toMutableList()
            val extra = rows?.get(1).orEmpty().split(',').mapNotNull(known::get).filter(seen::add).toMutableList()
            for (icon in ToolbarIcon.entries) {
                if (seen.add(icon)) (if (icon.defaultRow == ToolbarRow.MAIN) main else extra).add(icon)
            }
            return ToolbarLayout(main, extra)
        }
    }
}
