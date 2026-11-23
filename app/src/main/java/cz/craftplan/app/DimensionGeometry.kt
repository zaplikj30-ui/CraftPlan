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
    val startDistance = point.distanceTo(start)
    val endDistance = point.distanceTo(end)
    val handleRadius = 31f * density
    if (minOf(startDistance, endDistance) < handleRadius) {
        if (t <= 0.27f && startDistance <= endDistance) return DimensionHit.START
        if (t >= 0.73f && endDistance < startDistance) return DimensionHit.END
    }

    val alongLine = t.coerceIn(0f, 1f)
    val onLine = EditorPoint(start.x + alongLine * axis.x, start.y + alongLine * axis.y)
    return DimensionHit.BODY.takeIf { point.distanceTo(onLine) <= 20f * density }
}

internal fun PhotoDimension.move(hit: DimensionHit, du: Float, dv: Float): PhotoDimension {
    val movedStart = if (hit == DimensionHit.END) start else start.shift(du, dv) ?: return this
    val movedEnd = if (hit == DimensionHit.START) end else end.shift(du, dv) ?: return this
    return if (movedStart == movedEnd) this else copy(start = movedStart, end = movedEnd)
}

private fun PhotoAnchor.shift(du: Float, dv: Float): PhotoAnchor? {
    val x = u + du
    val y = v + dv
    return if (x.isFinite() && y.isFinite()) PhotoAnchor(x, y) else null
}

internal enum class HeldPointerRelease {
    FINISH, CANCEL, END_PAN, IGNORE,
}

internal fun heldPointerRelease(
    heldId: Int,
    panId: Int?,
    releasedId: Int,
    canceled: Boolean,
): HeldPointerRelease = when (releasedId) {
    heldId -> if (canceled) HeldPointerRelease.CANCEL else HeldPointerRelease.FINISH
    panId -> HeldPointerRelease.END_PAN
    else -> HeldPointerRelease.IGNORE
}

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

/** Signed camera speed. The photo moves toward the middle of the view. */
internal fun edgePanSpeed(position: Float, size: Int, edgeSize: Float, maxSpeed: Float): Float {
    val edge = minOf(edgeSize, size / 2f)
    if (edge <= 0f || !position.isFinite()) return 0f
    val near = ((edge - position) / edge).coerceIn(0f, 1f)
    val far = ((position - (size - edge)) / edge).coerceIn(0f, 1f)
    return (near - far) * maxSpeed
}
