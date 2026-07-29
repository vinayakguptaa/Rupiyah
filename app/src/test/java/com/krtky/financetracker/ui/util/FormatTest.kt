package com.krtky.financetracker.ui.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatTest {

    @Test
    fun `inr formats correctly`() {
        assertThat(10_000L.inr()).isEqualTo("₹100.00")
        assertThat((-500L).inr()).isEqualTo("-₹5.00")
    }

    @Test
    fun `inrCompact formats large values`() {
        assertThat(1_00_000L.inrCompact()).isEqualTo("₹1.0K")
        assertThat(1_00_00_000L.inrCompact()).isEqualTo("₹1.0L")
        assertThat(100_00_00_000L.inrCompact()).isEqualTo("₹1.0Cr")
        assertThat(1_00L.inrCompact()).isEqualTo("₹1.00")
    }

    // mapsUri tests require Android APIs (Uri.parse), run in instrumentation tests
}
