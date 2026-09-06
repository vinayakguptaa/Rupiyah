package com.krtky.financetracker.ui.util

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionKind
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

    @Test
    fun `buildTransactionsCsv aligns headers with values`() {
        val txn = Transaction(
            id = "c850bf02-26ee-4036-85b5-ea2dc804f1b3",
            type = TransactionType.CREDIT,
            amountPaise = 970_216L,
            occurredAt = 1_725_041_327_000L,
            source = TransactionSource.PASTE,
            classificationStatus = ClassificationStatus.CLASSIFIED,
            counterparty = "Kartikey Gupta",
            categoryName = "Settlement",
            tabName = "Kartikey",
            accountName = "Kotak",
            isCash = false,
            note = "Received via UPI",
            kind = TransactionKind.NORMAL,
            splitGroupId = "sg",
            transferGroupId = "tg",
        )
        val csv = buildTransactionsCsv(listOf(txn))
        val lines = csv.lines().filter { it.isNotBlank() }
        assertThat(lines.first()).isEqualTo(ActivityCsvFormat.HEADERS.joinToString(","))
        // No phantom Counterparty column.
        assertThat(lines.first()).doesNotContain("Counterparty")

        val cols = lines[1].split(',')
        assertThat(cols).hasSize(ActivityCsvFormat.HEADERS.size)
        assertThat(cols[4]).isEqualTo("Kartikey Gupta") // Name
        assertThat(cols[5]).isEqualTo("Settlement") // Category
        assertThat(cols[6]).isEqualTo("Kartikey") // Tab
        assertThat(cols[7]).isEqualTo("Kotak") // Account
        assertThat(cols[8]).isEqualTo("Digital") // Cash vs Digital
        assertThat(cols[9]).isEqualTo("Received via UPI") // Note
        assertThat(cols[11]).isEqualTo("PASTE") // Source
        assertThat(cols[12]).isEqualTo("NORMAL") // Kind
        assertThat(cols[13]).isEqualTo("sg")
        assertThat(cols[14]).isEqualTo("tg")
        assertThat(cols[15]).isEqualTo(txn.id)
    }

    @Test
    fun `csvEscape quotes commas and middle-dot notes stay intact`() {
        val escaped = csvEscape("a, b · c")
        assertThat(escaped).isEqualTo("\"a, b · c\"")
    }
}
