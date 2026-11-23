package cz.craftplan.app

import kotlin.math.atan2
import kotlin.math.hypot

internal data class EditorPoint(val x: Float, val y: Float) {
    operator fun minus(other: EditorPoint) = EditorPoint(x - other.x, y - other.y)
    fun distanceTo(other: EditorPoint): Float = hypot(x - other.x, y - other.y)
}

internal enum class DimensionHit {
    START, END, BODY,
}

internal fun hitDimension(
    point: EditorPoint,
    start: EditorPoint,
    end: EditorPoint,
    density: Float,
): DimensionHit? {
    val axis = end - start
    val lengthSquared = axis.x * axis.x + axis.y * axis.y
    if (lengthSquared == 0f) return null
    val relative = point - start
    val t = (relative.x * axis.x + relative.y * axis.y) / lengthSquared
    val endRadius = 31f * density
    if (t <= 0.27f && point.distanceTo(start) < endRadius) return DimensionHit.START
    if (t >= 0.73f && point.distanceTo(end) < endRadius) return DimensionHit.END
    if (t !in 0.27f..0.73f) return null
    val onLine = EditorPoint(start.x + t * axis.x, start.y + t * axis.y)
    return DimensionHit.BODY.takeIf { point.distanceTo(onLine) <= 20f * density }
}

internal fun PhotoDimension.move(hit: DimensionHit, du: Float, dv: Float): PhotoDimension {
    if (hit == DimensionHit.BODY) {
        val u = du.coerceIn(-minOf(start.u, end.u), 1f - maxOf(start.u, end.u))
        val v = dv.coerceIn(-minOf(start.v, end.v), 1f - maxOf(start.v, end.v))
        val movedStart = start.shift(u, v)
        val movedEnd = end.shift(u, v)
        return if (movedStart == movedEnd) this else copy(start = movedStart, end = movedEnd)
    }
    val movedStart = if (hit == DimensionHit.START) start.shift(du, dv) else start
    val movedEnd = if (hit == DimensionHit.END) end.shift(du, dv) else end
    return if (movedStart == movedEnd) this else copy(start = movedStart, end = movedEnd)
}

private fun PhotoAnchor.shift(du: Float, dv: Float) =
    PhotoAnchor((u + du).coerceIn(0f, 1f), (v + dv).coerceIn(0f, 1f))

internal fun readableAngle(axis: EditorPoint): Float {
    val degrees = Math.toDegrees(atan2(axis.y.toDouble(), axis.x.toDouble())).toFloat()
    return when {
        degrees > 90f -> degrees - 180f
        degrees <= -90f -> degrees + 180f
        else -> degrees
    }
}

internal fun magnifierOnRight(x: Float, width: Int, previous: Boolean?): Boolean = when {
    x < width * 0.45f -> true
    x > width * 0.55f -> false
    else -> previous ?: (x <= width / 2f)
}

internal fun passesDragThreshold(down: EditorPoint, current: EditorPoint, threshold: Float): Boolean =
    down.distanceTo(current) > threshold
