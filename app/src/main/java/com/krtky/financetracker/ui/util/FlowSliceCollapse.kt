package com.krtky.financetracker.ui.util

/**
 * Collapse a long flow breakdown into a few named slices plus optional Rest,
 * so Home charts keep honest proportions for the largest items.
 */
data class FlowSlice(
    val id: Long? = null,
    val name: String,
    val totalPaise: Long,
    /** ARGB; null → UI picks a fallback palette color. */
    val colorArgb: Long? = null,
    val isRest: Boolean = false,
)

object FlowSliceCollapse {
    const val REST_NAME = "Rest"

    /**
     * Keep largest items until cumulative share ≥ [coverage] or [maxNamed] is hit,
     * then fold the remainder into one Rest slice.
     */
    fun collapse(
        items: List<FlowSlice>,
        coverage: Double = 0.80,
        maxNamed: Int = 4,
    ): List<FlowSlice> {
        val positive = items.filter { it.totalPaise > 0L }.sortedByDescending { it.totalPaise }
        if (positive.isEmpty()) return emptyList()
        val total = positive.sumOf { it.totalPaise }
        if (total <= 0L) return emptyList()
        if (positive.size <= maxNamed) return positive

        val named = mutableListOf<FlowSlice>()
        var cumulative = 0L
        for (item in positive) {
            val wouldExceedCap = named.size >= maxNamed
            val alreadyCovered = named.isNotEmpty() && cumulative.toDouble() / total >= coverage
            if (wouldExceedCap || alreadyCovered) break
            named += item
            cumulative += item.totalPaise
        }
        if (named.isEmpty()) {
            named += positive.first()
            cumulative = positive.first().totalPaise
        }
        val restPaise = total - cumulative
        return if (restPaise > 0L) {
            named + FlowSlice(
                id = null,
                name = REST_NAME,
                totalPaise = restPaise,
                colorArgb = null,
                isRest = true,
            )
        } else {
            named
        }
    }
}

/** Stable distinct ARGB swatches for account / source slices (no DB color). */
object FlowSliceColors {
    private val SOURCE_PALETTE = longArrayOf(
        0xFF1B6CA8,
        0xFF0B6E4F,
        0xFFC45C26,
        0xFF6C3483,
        0xFF1A5276,
        0xFF117A65,
        0xFFB03A2E,
        0xFF5D6D7E,
        0xFF2874A6,
        0xFF196F3D,
    )

    fun forSourceKey(key: String): Long {
        val normalized = key.trim().lowercase()
        if (normalized.isEmpty() || normalized == "digital") return 0xFF5D6D7E
        var hash = 0
        for (c in normalized) {
            hash = 31 * hash + c.code
        }
        val idx = (hash and Int.MAX_VALUE) % SOURCE_PALETTE.size
        return SOURCE_PALETTE[idx]
    }
}
