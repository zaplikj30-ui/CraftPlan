package cz.craftplan.app

import kotlin.math.abs
import kotlin.math.hypot

internal const val INK_COMMA_MAX_DP = 20.0

/** Only a settled, single ink stroke. Coordinates are gesture geometry,
 * never measurement values. Accept either drawing direction of a slash-like
 * diagonal; leave taps, dots, vertical/horizontal marks and loops alone. */
internal fun isStandaloneInkComma(strokes: List<List<PenPoint>>, density: Float?): Boolean {
    if (density == null || !density.isFinite() || density <= 0f || strokes.size != 1) return false
    val points = strokes.single()
    if (points.size < 2 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return false
    val first = points.first()
    val last = points.last()
    val dx = (last.x.toDouble() - first.x) / density
    val dy = (last.y.toDouble() - first.y) / density
    val chord = hypot(dx, dy)
    val width = (points.maxOf { it.x }.toDouble() - points.minOf { it.x }) / density
    val height = (points.maxOf { it.y }.toDouble() - points.minOf { it.y }) / density
    if (chord <= PEN_DOT_RADIUS_DP || chord > INK_COMMA_MAX_DP ||
        hypot(width, height) > INK_COMMA_MAX_DP) return false
    // A comma slopes down-left (or is drawn in reverse). Reject near-vertical
    // and near-horizontal marks; modest curvature and pen jitter are allowed.
    if (dx * dy >= 0.0 || abs(dx) < 0.35 * abs(dy) || abs(dx) > 2.0 * abs(dy)) return false
    var path = 0.0
    var previous = first
    for (point in points) {
        path += hypot((point.x.toDouble() - previous.x) / density,
            (point.y.toDouble() - previous.y) / density)
        previous = point
    }
    return path <= 2.2 * chord
}
