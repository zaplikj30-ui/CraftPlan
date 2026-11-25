package cz.craftplan.app

/**
 * A continuous contact never stops collecting merely because a point budget is
 * reached. Keep the origin and newest samples; thin the older half only when
 * necessary. Memory and preview/recognition work remain bounded even for a
 * very long stroke. Positions are in field-local pixels, not measured lengths.
 */
internal class PenStrokeBuffer(first: PenPoint) {
    private val points = ArrayList<PenPoint>().apply { add(first) }
    val size: Int get() = points.size

    fun append(point: PenPoint) {
        val previous = points.last()
        val dx = point.x - previous.x
        val dy = point.y - previous.y
        if (dx * dx + dy * dy < 1f) return
        if (points.size >= MAX_POINTS) {
            val olderEnd = points.size / 2
            // Compact in place and preserve the recent half at full resolution.
            var write = 1
            for (read in 2 until olderEnd step 2) points[write++] = points[read]
            for (read in olderEnd until points.size) points[write++] = points[read]
            points.subList(write, points.size).clear()
        }
        points.add(point)
    }

    fun snapshot(): List<PenPoint> = points.toList()

    companion object {
        internal const val MAX_POINTS = 2048
    }
}
