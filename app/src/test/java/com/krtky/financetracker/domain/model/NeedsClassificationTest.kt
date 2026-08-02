package com.krtky.financetracker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NeedsClassificationTest {

    private fun txn(
        categoryId: Long? = null,
        isSkipped: Boolean = false,
        status: ClassificationStatus = ClassificationStatus.PENDING,
        kind: TransactionKind = TransactionKind.NORMAL,
    ) = Transaction(
        id = "t1",
        type = TransactionType.DEBIT,
        amountPaise = 100L,
        occurredAt = 1L,
        categoryId = categoryId,
        isSkipped = isSkipped,
        classificationStatus = status,
        kind = kind,
    )

    @Test
    fun `pending without category needs classify`() {
        assertThat(txn().needsClassification()).isTrue()
    }

    @Test
    fun `classified status does not need classify even without category`() {
        assertThat(txn(status = ClassificationStatus.CLASSIFIED).needsClassification()).isFalse()
    }

    @Test
    fun `self and tab transfers never need classify`() {
        assertThat(txn(kind = TransactionKind.SELF_TRANSFER).needsClassification()).isFalse()
        assertThat(txn(kind = TransactionKind.TAB_TRANSFER).needsClassification()).isFalse()
    }

    @Test
    fun `skipped does not need classify`() {
        assertThat(txn(isSkipped = true, status = ClassificationStatus.SKIPPED).needsClassification()).isFalse()
    }

    @Test
    fun `has category does not need classify`() {
        assertThat(txn(categoryId = 3L, status = ClassificationStatus.CLASSIFIED).needsClassification()).isFalse()
    }
}
