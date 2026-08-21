package com.krtky.financetracker.ui.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PieSliceLayoutTest {

    @Test
    fun `proportional when every slice already exceeds min`() {
        val sweeps = PieSliceLayout.sweeps(
            weights = listOf(50f, 30f, 20f),
            usableDegrees = 300f,
            minSweepDegrees = 10f,
        )
        assertThat(sweeps[0]).isWithin(0.1f).of(150f)
        assertThat(sweeps[1]).isWithin(0.1f).of(90f)
        assertThat(sweeps[2]).isWithin(0.1f).of(60f)
    }

    @Test
    fun `tiny slice gets min sweep and large slice keeps the rest`() {
        val sweeps = PieSliceLayout.sweeps(
            weights = listOf(98f, 2f),
            usableDegrees = 300f,
            minSweepDegrees = 18f,
        )
        assertThat(sweeps[1]).isWithin(0.2f).of(18f)
        assertThat(sweeps[0]).isWithin(0.2f).of(282f)
        assertThat(sweeps.sum()).isWithin(0.2f).of(300f)
    }

    @Test
    fun `many tiny slices shrink the floor so they still fit`() {
        val n = 20
        val sweeps = PieSliceLayout.sweeps(
            weights = List(n) { 1f },
            usableDegrees = 200f,
            minSweepDegrees = 30f,
        )
        assertThat(sweeps).hasSize(n)
        sweeps.forEach { assertThat(it).isWithin(0.2f).of(10f) }
    }
}
