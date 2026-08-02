package com.krtky.financetracker.ui.util

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import org.junit.Test

class CsvExportTest {

    @Test
    fun `csvSummaryLine summarizes correctly`() {
        val txns = listOf(
            Transaction(id = "1", type = TransactionType.CREDIT, amountPaise = 1_00_00_00L, occurredAt = 1L, source = TransactionSource.MANUAL, classificationStatus = ClassificationStatus.CLASSIFIED),
            Transaction(id = "2", type = TransactionType.DEBIT, amountPaise = 50_00_00L, occurredAt = 2L, source = TransactionSource.MANUAL, classificationStatus = ClassificationStatus.CLASSIFIED),
        )
        val summary = csvSummaryLine(txns)
        assertThat(summary).contains("2 txns")
        assertThat(summary).contains("income")
        assertThat(summary).contains("expense")
    }
}
