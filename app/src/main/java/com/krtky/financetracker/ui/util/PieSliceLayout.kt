package com.krtky.financetracker.ui.util

/**
 * Donut-slice angles. Tiny categories (1–2%) get a minimum sweep so round
 * stroke caps don't swallow them; leftover degrees stay with the larger slices.
 */
object PieSliceLayout {

    /**
     * @param weights non-negative slice sizes (paise, counts, …)
     * @param usableDegrees ring degrees after gaps (typically 360 − n·gap)
     * @param minSweepDegrees floor per slice; reduced if n·min would overflow
     */
    fun sweeps(
        weights: List<Float>,
        usableDegrees: Float,
        minSweepDegrees: Float,
    ): List<Float> {
        if (weights.isEmpty()) return emptyList()
        val n = weights.size
        val usable = usableDegrees.coerceAtLeast(0f)
        if (usable <= 0f) return List(n) { 0f }
        val sum = weights.sum().coerceAtLeast(1e-6f)
        val min = minOf(minSweepDegrees, usable / n)
        val raw = weights.map { usable * (it / sum) }
        if (raw.all { it + 0.01f >= min }) return raw

        val out = FloatArray(n)
        val big = mutableListOf<Int>()
        var locked = 0f
        raw.forEachIndexed { i, sweep ->
            if (sweep < min) {
                out[i] = min
                locked += min
            } else {
                big += i
            }
        }
        val leftover = (usable - locked).coerceAtLeast(0f)
        val bigWeight = big.sumOf { weights[it].toDouble() }.toFloat()
        if (big.isEmpty() || bigWeight <= 0f) {
            val t = out.sum()
            if (t <= 0f) return List(n) { usable / n }
            return out.map { it * usable / t }
        }
        big.forEach { i ->
            out[i] = leftover * (weights[i] / bigWeight)
        }
        return out.toList()
    }

    data class Arc(val startDeg: Float, val sweepDeg: Float)

    /**
     * Layout from -90° (12 o'clock), inserting [gapDeg] between slices.
     * Sweep list already includes the min-size treatment.
     */
    fun arcs(
        weights: List<Float>,
        gapDeg: Float,
        minSweepDeg: Float,
        startDeg: Float = -90f,
    ): List<Arc> {
        if (weights.isEmpty()) return emptyList()
        val n = weights.size
        val usable = (360f - gapDeg * n).coerceAtLeast(1f)
        val sweeps = sweeps(weights, usable, minSweepDeg)
        var angle = startDeg + gapDeg / 2f
        return sweeps.map { sweep ->
            val arc = Arc(angle, sweep)
            angle += sweep + gapDeg
            arc
        }
    }
}
