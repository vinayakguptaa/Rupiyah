package com.krtky.financetracker.data.local.db

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.*
import org.junit.Test

class MappersTest {

    @Test
    fun `CategoryEntity toDomain maps all fields`() {
        val entity = CategoryEntity(id = 5, name = "Food", icon = "restaurant", color = 0xFFE67E22, sortOrder = 1, isSystem = true, isQuickAction = true)
        val domain = entity.toDomain()
        assertThat(domain.id).isEqualTo(5)
        assertThat(domain.name).isEqualTo("Food")
        assertThat(domain.icon).isEqualTo("restaurant")
        assertThat(domain.sortOrder).isEqualTo(1)
        assertThat(domain.isSystem).isTrue()
        assertThat(domain.isQuickAction).isTrue()
    }

    @Test
    fun `Category toEntity maps all fields`() {
        val domain = Category(id = 3, name = "Travel", icon = "flight", color = 0xFF3498DB, sortOrder = 2, isSystem = true, isQuickAction = false)
        val entity = domain.toEntity()
        assertThat(entity.id).isEqualTo(3)
        assertThat(entity.name).isEqualTo("Travel")
        assertThat(entity.icon).isEqualTo("flight")
        assertThat(entity.sortOrder).isEqualTo(2)
    }

    @Test
    fun `FundEntity toDomain maps correctly`() {
        val entity = FundEntity(id = 1, name = "Groceries", archived = false, budgetPaise = 50_00_00L)
        val domain = entity.toDomain()
        assertThat(domain.name).isEqualTo("Groceries")
        assertThat(domain.budgetPaise).isEqualTo(50_00_00L)
        assertThat(domain.archived).isFalse()
    }

    @Test
    fun `Fund toEntity maps correctly`() {
        val domain = Fund(id = 2, name = "Fuel", budgetPaise = 30_00_00L)
        val entity = domain.toEntity()
        assertThat(entity.name).isEqualTo("Fuel")
        assertThat(entity.budgetPaise).isEqualTo(30_00_00L)
    }

    @Test
    fun `TransactionEntity toDomain maps all fields`() {
        val now = System.currentTimeMillis()
        val entity = TransactionEntity(
            id = "txn-1", type = "DEBIT", amountPaise = 5_00_00L, occurredAt = now, recordedAt = now,
            source = "MANUAL", classificationStatus = "PENDING", updatedAt = now,
            merchant = "Swiggy", paymentMethod = "UPI", note = "Dinner",
        )
        val domain = entity.toDomain()
        assertThat(domain.id).isEqualTo("txn-1")
        assertThat(domain.type).isEqualTo(TransactionType.DEBIT)
        assertThat(domain.amountPaise).isEqualTo(5_00_00L)
        assertThat(domain.merchant).isEqualTo("Swiggy")
        assertThat(domain.paymentMethod).isEqualTo("UPI")
        assertThat(domain.note).isEqualTo("Dinner")
    }

    @Test
    fun `TransactionEntity toDomain uses counterparty fallback`() {
        val now = System.currentTimeMillis()
        val entity = TransactionEntity(
            id = "txn-2", type = "DEBIT", amountPaise = 1000L, occurredAt = now, recordedAt = now,
            source = "MANUAL", classificationStatus = "CLASSIFIED", updatedAt = now,
            merchant = "Amazon", counterparty = null,
        )
        val domain = entity.toDomain()
        assertThat(domain.counterparty).isEqualTo("Amazon")
    }

    @Test
    fun `Transaction toEntity maps all fields`() {
        val txn = Transaction(
            id = "txn-3", type = TransactionType.CREDIT, amountPaise = 1_00_00_00L,
            occurredAt = 1_000_000L, source = TransactionSource.EMAIL, paymentMethod = "HDFC",
            categoryId = 1, fundId = 2, note = "Salary",
        )
        val entity = txn.toEntity()
        assertThat(entity.type).isEqualTo("CREDIT")
        assertThat(entity.source).isEqualTo("EMAIL")
        assertThat(entity.categoryId).isEqualTo(1)
        assertThat(entity.fundId).isEqualTo(2)
        assertThat(entity.note).isEqualTo("Salary")
    }

    @Test
    fun `TrustedSenderEntity roundtrip`() {
        val entity = TrustedSenderEntity(id = 1, emailPattern = "alerts@bank.com", walletLabel = "Bank", enabled = true)
        val domain = entity.toDomain()
        assertThat(domain.emailPattern).isEqualTo("alerts@bank.com")
        assertThat(domain.walletLabel).isEqualTo("Bank")
        assertThat(domain.enabled).isTrue()

        val back = domain.toEntity()
        assertThat(back.emailPattern).isEqualTo("alerts@bank.com")
    }
}
