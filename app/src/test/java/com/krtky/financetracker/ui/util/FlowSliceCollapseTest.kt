package com.krtky.financetracker.ui.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FlowSliceCollapseTest {

    @Test
    fun `few items stay uncollapsed`() {
        val items = listOf(
            FlowSlice(name = "A", totalPaise = 50),
            FlowSlice(name = "B", totalPaise = 30),
            FlowSlice(name = "C", totalPaise = 20),
        )
        val out = FlowSliceCollapse.collapse(items)
        assertThat(out.map { it.name }).containsExactly("A", "B", "C").inOrder()
        assertThat(out.none { it.isRest }).isTrue()
    }

    @Test
    fun `stops at 80 percent even before maxNamed`() {
        // 70 + 15 = 85% with 2 named; rest is 15
        val items = listOf(
            FlowSlice(name = "Food", totalPaise = 70_00),
            FlowSlice(name = "Travel", totalPaise = 15_00),
            FlowSlice(name = "Other1", totalPaise = 5_00),
            FlowSlice(name = "Other2", totalPaise = 5_00),
            FlowSlice(name = "Other3", totalPaise = 5_00),
        )
        val out = FlowSliceCollapse.collapse(items, coverage = 0.80, maxNamed = 4)
        assertThat(out).hasSize(3)
        assertThat(out[0].name).isEqualTo("Food")
        assertThat(out[1].name).isEqualTo("Travel")
        assertThat(out[2].isRest).isTrue()
        assertThat(out[2].totalPaise).isEqualTo(15_00)
    }

    @Test
    fun `caps at four named then Rest`() {
        // Five equal 20% slices: need all five to reach 80%, but maxNamed=4 → Rest
        val items = (1..5).map { FlowSlice(name = "C$it", totalPaise = 20_00) }
        val out = FlowSliceCollapse.collapse(items, coverage = 0.80, maxNamed = 4)
        assertThat(out.filter { !it.isRest }).hasSize(4)
        assertThat(out.last().isRest).isTrue()
        assertThat(out.last().totalPaise).isEqualTo(20_00)
        assertThat(out.sumOf { it.totalPaise }).isEqualTo(100_00)
    }

    @Test
    fun `drops zero amounts`() {
        val out = FlowSliceCollapse.collapse(
            listOf(
                FlowSlice(name = "A", totalPaise = 10),
                FlowSlice(name = "Z", totalPaise = 0),
            ),
        )
        assertThat(out.map { it.name }).containsExactly("A")
    }

    @Test
    fun `source color is stable for same key`() {
        val a = FlowSliceColors.forSourceKey("Kotak")
        val b = FlowSliceColors.forSourceKey(" kotak ")
        assertThat(a).isEqualTo(b)
        assertThat(FlowSliceColors.forSourceKey("")).isEqualTo(FlowSliceColors.forSourceKey("Digital"))
    }
}
