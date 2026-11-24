package cz.craftplan.app

/** Test-build measurements. Canvas timings describe CPU work, not GPU frame presentation. */
internal class EditorStressMetrics {
    private var lastDraw = 0L
    private var drawMax = 0L
    private var hitMax = 0L
    private var preparationMax = 0L
    private var drawCount = 0
    private var hitCount = 0
    private var gestureGapMax = 0L
    private var gestureGapsOver50ms = 0

    fun reset() {
        lastDraw = 0
        drawMax = 0
        hitMax = 0
        preparationMax = 0
        drawCount = 0
        hitCount = 0
        gestureGapMax = 0
        gestureGapsOver50ms = 0
    }

    fun draw(started: Long, finished: Long, duringGesture: Boolean) {
        drawCount++
        drawMax = maxOf(drawMax, finished - started)
        if (duringGesture && lastDraw != 0L) {
            val gap = started - lastDraw
            gestureGapMax = maxOf(gestureGapMax, gap)
            if (gap > 50_000_000) gestureGapsOver50ms++
        }
        lastDraw = if (duringGesture) started else 0
    }

    fun beginGesture() { lastDraw = 0 }

    fun hit(elapsed: Long) {
        hitCount++
        hitMax = maxOf(hitMax, elapsed)
    }

    fun prepareScene(elapsed: Long) {
        preparationMax = maxOf(preparationMax, elapsed)
    }

    fun snapshot() = Snapshot(
        drawMax / 1_000_000.0, drawCount, hitMax / 1_000_000.0, hitCount,
        gestureGapMax / 1_000_000.0, gestureGapsOver50ms, preparationMax / 1_000_000.0,
    )

    data class Snapshot(
        val maxDrawMs: Double, val draws: Int,
        val maxHitMs: Double, val hits: Int,
        val maxGestureGapMs: Double, val gapsOver50ms: Int,
        val maxPreparationMs: Double,
    )
}
