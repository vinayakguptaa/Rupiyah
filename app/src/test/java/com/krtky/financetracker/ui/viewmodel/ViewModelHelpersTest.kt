package com.krtky.financetracker.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import org.junit.Test

class ViewModelHelpersTest {

    private fun txn(accountName: String? = null, isCash: Boolean = false) =
        Transaction(id = "1", type = TransactionType.DEBIT, amountPaise = 100L, occurredAt = 1L, accountName = accountName, isCash = isCash)

    @Test
    fun `matchesPaymentFilter matches Cash exactly`() {
        val t = txn(accountName = "Cash", isCash = true)
        assertThat(matchesPaymentFilter(t, "Cash")).isTrue()
        assertThat(matchesPaymentFilter(t, "Digital")).isFalse()
    }

    @Test
    fun `matchesPaymentFilter matches Digital`() {
        val t = txn(accountName = "UPI")
        assertThat(matchesPaymentFilter(t, "Digital")).isTrue()
        assertThat(matchesPaymentFilter(t, "Cash")).isFalse()
    }

    @Test
    fun `matchesPaymentFilter matches Digital unassigned`() {
        val unassigned = txn(accountName = null)
        val hdfc = txn(accountName = "HDFC").copy(accountId = 12L)
        assertThat(matchesPaymentFilter(unassigned, PAYMENT_DIGITAL_UNASSIGNED)).isTrue()
        assertThat(matchesPaymentFilter(hdfc, PAYMENT_DIGITAL_UNASSIGNED)).isFalse()
        assertThat(matchesPaymentFilter(unassigned, "Digital")).isTrue()
    }

    @Test
    fun `matchesPaymentFilter matches exact payment method`() {
        val t = txn(accountName = "HDFC")
        assertThat(matchesPaymentFilter(t, "HDFC")).isTrue()
        assertThat(matchesPaymentFilter(t, "ICICI")).isFalse()
    }

    @Test
    fun `effectiveTabId returns null when tabId is null`() {
        assertThat(effectiveTabId(TransactionType.DEBIT, null, false)).isNull()
        assertThat(effectiveTabId(TransactionType.CREDIT, null, true)).isNull()
    }

    @Test
    fun `effectiveTabId returns tabId for expense`() {
        assertThat(effectiveTabId(TransactionType.DEBIT, 1L, false)).isEqualTo(1L)
    }

    @Test
    fun `effectiveTabId returns tabId for income only when addToTab is true`() {
        assertThat(effectiveTabId(TransactionType.CREDIT, 1L, false)).isNull()
        assertThat(effectiveTabId(TransactionType.CREDIT, 1L, true)).isEqualTo(1L)
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
