package cz.craftplan.app

/** Delta is positive in the expansion direction. The platform already applies touch slop. */
internal fun toolbarExpansionAfterDrag(expanded: Boolean, delta: Float, threshold: Float): Boolean =
    when {
        !delta.isFinite() || !threshold.isFinite() || threshold <= 0f -> expanded
        delta >= threshold -> true
        delta <= -threshold -> false
        else -> expanded
    }
