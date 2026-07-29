package com.krtky.financetracker.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import org.junit.Test

class ViewModelHelpersTest {

    private fun txn(paymentMethod: String? = null, isCash: Boolean = false) =
        Transaction(id = "1", type = TransactionType.EXPENSE, amountPaise = 100L, occurredAt = 1L, paymentMethod = paymentMethod, isCash = isCash)

    @Test
    fun `matchesPaymentFilter matches Cash exactly`() {
        val t = txn(paymentMethod = "Cash", isCash = true)
        assertThat(matchesPaymentFilter(t, "Cash")).isTrue()
        assertThat(matchesPaymentFilter(t, "Digital")).isFalse()
    }

    @Test
    fun `matchesPaymentFilter matches Digital`() {
        val t = txn(paymentMethod = "UPI")
        assertThat(matchesPaymentFilter(t, "Digital")).isTrue()
        assertThat(matchesPaymentFilter(t, "Cash")).isFalse()
    }

    @Test
    fun `matchesPaymentFilter matches exact payment method`() {
        val t = txn(paymentMethod = "HDFC")
        assertThat(matchesPaymentFilter(t, "HDFC")).isTrue()
        assertThat(matchesPaymentFilter(t, "ICICI")).isFalse()
    }

    @Test
    fun `effectiveFundId returns null when fundId is null`() {
        assertThat(effectiveFundId(TransactionType.EXPENSE, null, false)).isNull()
        assertThat(effectiveFundId(TransactionType.INCOME, null, true)).isNull()
    }

    @Test
    fun `effectiveFundId returns fundId for expense`() {
        assertThat(effectiveFundId(TransactionType.EXPENSE, 1L, false)).isEqualTo(1L)
    }

    @Test
    fun `effectiveFundId returns fundId for income only when addToFund is true`() {
        assertThat(effectiveFundId(TransactionType.INCOME, 1L, false)).isNull()
        assertThat(effectiveFundId(TransactionType.INCOME, 1L, true)).isEqualTo(1L)
    }

    @Test
    fun `normalizePaymentMethod keeps Cash`() {
        assertThat(normalizePaymentMethod("Cash", "Default", emptyList())).isEqualTo("Cash")
    }

    @Test
    fun `normalizePaymentMethod resolves Digital to default`() {
        assertThat(normalizePaymentMethod("Digital", "HDFC", listOf("SBI", "HDFC"))).isEqualTo("HDFC")
    }

    @Test
    fun `normalizePaymentMethod resolves blank to first bank when no default`() {
        assertThat(normalizePaymentMethod("", "", listOf("SBI", "HDFC"))).isEqualTo("SBI")
    }

    @Test
    fun `normalizePaymentMethod resolves blank to Digital when no banks configured`() {
        assertThat(normalizePaymentMethod("", "", emptyList())).isEqualTo("Digital")
    }

    @Test
    fun `normalizePaymentMethod keeps known payment method`() {
        assertThat(normalizePaymentMethod("ICICI", "SBI", listOf("SBI", "ICICI"))).isEqualTo("ICICI")
    }
}
