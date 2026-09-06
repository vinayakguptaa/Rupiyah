package com.krtky.financetracker.ui.navigation

/**
 * Reorderable blocks on the Home dashboard (greeting / pending / setup stay fixed above).
 *
 * [span] is a 2-column grid unit: `2` = full width, `1` = half width (side-by-side when paired).
 */
enum class HomeSection(val id: String, val title: String) {
    HERO("hero", "Balance"),
    CATEGORY_RING("category_ring", "Debits"),
    INCOME("income", "Credits"),
    INVESTMENTS("investments", "Investments"),
    RECENT("recent", "Recent activity"),
    TABS_SUMMARY("funds_summary", "Open Tabs"),
    ;

    /** Half-width is useful for compact tiles; hero stays full for readability. */
    val allowsHalfWidth: Boolean
        get() = this == CATEGORY_RING || this == INCOME || this == INVESTMENTS || this == TABS_SUMMARY

    companion object {
        /**
         * Investments stay optional (only if already in a saved layout).
         * Recent activity is removed from Home — use the Activity tab instead.
         */
        val DEFAULT_ORDER: List<HomeSection> = listOf(
            HERO, CATEGORY_RING, INCOME, TABS_SUMMARY,
        )

        val DEFAULT_LAYOUT: List<HomeSectionConfig> =
            DEFAULT_ORDER.map { HomeSectionConfig(it, span = 2) }

        /**
         * Parses `hero:2,overview:2,...` or legacy `hero,overview,...` (all span 2).
         * Unknown ids and retired [RECENT] are skipped.
         */
        fun parseLayout(raw: String?): List<HomeSectionConfig> {
            if (raw.isNullOrBlank()) return DEFAULT_LAYOUT
            val parsed = raw.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { token ->
                    val parts = token.split(':')
                    val id = parts[0].trim()
                    val section = entries.firstOrNull { it.id == id } ?: return@mapNotNull null
                    if (section == RECENT) return@mapNotNull null
                    val span = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 2) ?: 2
                    val effectiveSpan = if (section.allowsHalfWidth) span else 2
                    HomeSectionConfig(section, effectiveSpan)
                }
            if (parsed.isEmpty()) return DEFAULT_LAYOUT
            val seen = parsed.map { it.section }.toSet()
            val missing = DEFAULT_ORDER.filter { it !in seen }.map { HomeSectionConfig(it, 2) }
            if (missing.isEmpty()) return parsed
            return parsed + missing
        }

        /** @deprecated Prefer [parseLayout]; kept for call sites that only need order. */
        fun parseOrder(raw: String?): List<HomeSection> =
            parseLayout(raw).map { it.section }

        fun serializeLayout(layout: List<HomeSectionConfig>): String =
            layout.joinToString(",") { cfg ->
                val span = if (cfg.section.allowsHalfWidth) cfg.span.coerceIn(1, 2) else 2
                "${cfg.section.id}:$span"
            }

        fun serializeOrder(order: List<HomeSection>): String =
            serializeLayout(order.map { HomeSectionConfig(it, 2) })
    }
}

data class HomeSectionConfig(
    val section: HomeSection,
    val span: Int = 2,
) {
    val effectiveSpan: Int
        get() = if (section.allowsHalfWidth) span.coerceIn(1, 2) else 2
}
