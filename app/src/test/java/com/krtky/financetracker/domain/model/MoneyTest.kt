package com.krtky.financetracker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {

    @Test
    fun `fromRupees converts correctly`() {
        assertThat(Money.fromRupees(100.0).paise).isEqualTo(10_000L)
        assertThat(Money.fromRupees(0.0).paise).isEqualTo(0L)
        assertThat(Money.fromRupees(99.99).paise).isEqualTo(9_999L)
    }

    @Test
    fun `formatInr formats correctly`() {
        assertThat(Money(10_000L).formatInr()).isEqualTo("₹100.00")
        assertThat(Money(1_00_000L).formatInr()).isEqualTo("₹1,000.00")
        assertThat(Money(-500L).formatInr()).isEqualTo("-₹5.00")
        assertThat(Money(0L).formatInr()).isEqualTo("₹0.00")
        assertThat(Money(1_23_45_678L).formatInr()).isEqualTo("₹123,456.78")
    }

    @Test
    fun `fromRupeesString parses various formats`() {
        assertThat(Money.fromRupeesString("100")?.paise).isEqualTo(10_000L)
        assertThat(Money.fromRupeesString("₹1,500")?.paise).isEqualTo(1_50_000L)
        assertThat(Money.fromRupeesString("Rs. 250.50")?.paise).isEqualTo(25_050L)
        assertThat(Money.fromRupeesString("INR 75")?.paise).isEqualTo(7_500L)
        assertThat(Money.fromRupeesString("invalid")).isNull()
        assertThat(Money.fromRupeesString("")).isNull()
    }

    @Test
    fun `toRupees returns correct double`() {
        assertThat(Money(10_000L).toRupees()).isWithin(0.001).of(100.0)
        assertThat(Money(1_234L).toRupees()).isWithin(0.001).of(12.34)
    }
}
