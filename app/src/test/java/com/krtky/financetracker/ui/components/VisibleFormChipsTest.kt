package com.krtky.financetracker.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VisibleFormChipsTest {

    @Test
    fun `expanded or short list returns all items`() {
        val items = listOf(1, 2, 3)
        assertThat(visibleFormChips(items, expanded = true, collapsedCount = 2) { false })
            .isEqualTo(items)
        assertThat(visibleFormChips(items, expanded = false, collapsedCount = 5) { false })
            .isEqualTo(items)
    }

    @Test
    fun `collapsed keeps selected when outside head`() {
        val items = (1..8).toList()
        val visible = visibleFormChips(items, expanded = false, collapsedCount = 6) { it == 8 }
        assertThat(visible).hasSize(6)
        assertThat(visible).contains(8)
        assertThat(visible).doesNotContain(6)
    }

    @Test
    fun `collapsed keeps head when selected already visible`() {
        val items = (1..8).toList()
        val visible = visibleFormChips(items, expanded = false, collapsedCount = 6) { it == 2 }
        assertThat(visible).isEqualTo(listOf(1, 2, 3, 4, 5, 6))
    }
}
