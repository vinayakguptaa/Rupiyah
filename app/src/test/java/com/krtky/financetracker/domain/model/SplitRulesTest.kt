package com.krtky.financetracker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SplitRulesTest {

    @Test
    fun `empty splits is valid (clear)`() {
        assertThat(SplitRules.validateSum(10_000L, emptyList())).isNull()
    }

    @Test
    fun `exact sum is valid`() {
        assertThat(SplitRules.validateSum(10_500_00L, listOf(10_000_00L, 5_00_00L))).isNull()
    }

    @Test
    fun `under sum fails`() {
        assertThat(SplitRules.validateSum(100L, listOf(40L, 50L))).isNotNull()
    }

    @Test
    fun `over sum fails`() {
        assertThat(SplitRules.validateSum(100L, listOf(60L, 50L))).isNotNull()
    }

    @Test
    fun `zero line fails`() {
        assertThat(SplitRules.validateSum(100L, listOf(100L, 0L))).isNotNull()
    }

    @Test
    fun `negative line fails`() {
        assertThat(SplitRules.validateSum(100L, listOf(120L, -20L))).isNotNull()
    }

    @Test
    fun `remainingPaise tracks leftover`() {
        assertThat(SplitRules.remainingPaise(1000L, listOf(300L, 200L))).isEqualTo(500L)
        assertThat(SplitRules.remainingPaise(1000L, listOf(600L, 400L))).isEqualTo(0L)
        assertThat(SplitRules.remainingPaise(1000L, listOf(700L, 400L))).isEqualTo(-100L)
    }
}
