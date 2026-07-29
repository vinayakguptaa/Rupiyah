package com.krtky.financetracker.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmountNumpadTest {

    @Test
    fun applyNumpadKey_digitsAndDecimal() {
        assertThat(applyNumpadKey("", "1")).isEqualTo("1")
        assertThat(applyNumpadKey("1", "2")).isEqualTo("12")
        assertThat(applyNumpadKey("12", ".")).isEqualTo("12.")
        assertThat(applyNumpadKey("12.", "5")).isEqualTo("12.5")
        assertThat(applyNumpadKey("12.5", "0")).isEqualTo("12.50")
        // max 2 decimal places
        assertThat(applyNumpadKey("12.50", "9")).isEqualTo("12.50")
        // only one decimal
        assertThat(applyNumpadKey("12.5", ".")).isEqualTo("12.5")
    }

    @Test
    fun applyNumpadKey_backspaceAndLeadingZero() {
        assertThat(applyNumpadKey("12", "⌫")).isEqualTo("1")
        assertThat(applyNumpadKey("1", "⌫")).isEqualTo("")
        assertThat(applyNumpadKey("0", "5")).isEqualTo("5")
        assertThat(applyNumpadKey("", ".")).isEqualTo("0.")
    }

    @Test
    fun normalizeAmount_stripsTrailingDot() {
        assertThat(normalizeAmount("100.")).isEqualTo("100")
        assertThat(normalizeAmount("12.50")).isEqualTo("12.5")
        assertThat(normalizeAmount("12.5")).isEqualTo("12.5")
        assertThat(normalizeAmount("")).isEqualTo("")
    }
}
