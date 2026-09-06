package com.krtky.financetracker.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeSectionTest {

    @Test
    fun `default layout omits investments and recent`() {
        val ids = HomeSection.DEFAULT_LAYOUT.map { it.section }
        assertThat(ids).doesNotContain(HomeSection.INVESTMENTS)
        assertThat(ids).doesNotContain(HomeSection.RECENT)
        assertThat(ids).containsExactly(
            HomeSection.HERO,
            HomeSection.CATEGORY_RING,
            HomeSection.INCOME,
            HomeSection.TABS_SUMMARY,
        ).inOrder()
    }

    @Test
    fun `blank or unknown tokens fall back to default without investments or recent`() {
        assertThat(HomeSection.parseLayout(null)).isEqualTo(HomeSection.DEFAULT_LAYOUT)
        assertThat(HomeSection.parseLayout("")).isEqualTo(HomeSection.DEFAULT_LAYOUT)
        assertThat(HomeSection.parseLayout("not_a_section")).isEqualTo(HomeSection.DEFAULT_LAYOUT)
    }

    @Test
    fun `saved investments section is kept`() {
        val layout = HomeSection.parseLayout("hero:2,investments:1,recent:2")
        assertThat(layout.map { it.section }).contains(HomeSection.INVESTMENTS)
        assertThat(layout.map { it.section }).doesNotContain(HomeSection.RECENT)
        assertThat(layout.first { it.section == HomeSection.INVESTMENTS }.span).isEqualTo(1)
    }

    @Test
    fun `legacy saved layout without new defaults gets missing default sections`() {
        val layout = HomeSection.parseLayout("hero:2,category_ring:2")
        val ids = layout.map { it.section }
        assertThat(ids).contains(HomeSection.INCOME)
        assertThat(ids).contains(HomeSection.TABS_SUMMARY)
        assertThat(ids).doesNotContain(HomeSection.RECENT)
        assertThat(ids).doesNotContain(HomeSection.INVESTMENTS)
    }
}
