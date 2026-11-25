package cz.craftplan.app

internal const val TABLET_SMALLEST_WIDTH_DP = 600

/** Null means use the device default, not a stored user decision. */
internal data class EditorStatusBarSettings(
    val portraitOverride: Boolean? = null,
    val landscapeOverride: Boolean? = null,
) {
    fun hidden(portrait: Boolean, isTablet: Boolean): Boolean =
        if (portrait) portraitOverride ?: false else landscapeOverride ?: !isTablet

    fun withHidden(portrait: Boolean, hidden: Boolean): EditorStatusBarSettings =
        if (portrait) copy(portraitOverride = hidden) else copy(landscapeOverride = hidden)
}

/** The status bar and cutout overlap; subtract already reserved content space. */
internal fun editorOverlayTop(statusTop: Int, cutoutTop: Int, contentTop: Int): Int =
    (maxOf(statusTop, cutoutTop) - contentTop).coerceAtLeast(0)
