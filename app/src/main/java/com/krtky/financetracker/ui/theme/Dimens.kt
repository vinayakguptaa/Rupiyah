package com.krtky.financetracker.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared layout tokens for Rupiyah tab and stack screens.
 *
 * Prefer these over one-off spacing so Home / Activity / Tabs / Settings stay aligned.
 * See plan §3.1 — design tokens.
 */
object Dimens {
    /** Horizontal padding for tab roots and most content columns. */
    val ScreenHorizontal: Dp = 16.dp

    /** Space under status bar before the first content block. */
    val ScreenTop: Dp = 8.dp

    /** Vertical gap between major sections / list blocks. */
    val SectionGap: Dp = 12.dp

    /** Tight gap inside a card or row group. */
    val CardInnerGap: Dp = 8.dp

    /**
     * Bottom inset for scroll content on main tabs so lists clear the floating
     * [com.krtky.financetracker.ui.components.FloatingBottomNav] dock + side FAB.
     *
     * Use as LazyColumn `contentPadding` bottom or a trailing [Spacer] height.
     * Do not invent per-screen values (96 / 100 / 104.dp).
     */
    val NavBarContentInset: Dp = 104.dp
}

/**
 * Convenience padding for tab-root lists (status top + dock bottom).
 * Horizontal is left to each screen (often already padded in the header).
 */
object NavContentInsets {
    val bottom: Dp get() = Dimens.NavBarContentInset

    fun listPadding(
        horizontal: Dp = Dimens.ScreenHorizontal,
        top: Dp = Dimens.ScreenTop,
        bottom: Dp = Dimens.NavBarContentInset,
    ): PaddingValues = PaddingValues(
        start = horizontal,
        end = horizontal,
        top = top,
        bottom = bottom,
    )
}
