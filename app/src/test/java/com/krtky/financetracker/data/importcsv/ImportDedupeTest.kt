package com.krtky.financetracker.data.importcsv

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import org.junit.Test

class ImportDedupeTest {

    private fun txn(
        id: String = "1",
        amount: Long = 10_000L,
        type: TransactionType = TransactionType.DEBIT,
        at: Long = 1_700_000_000_000L,
        ref: String? = null,
        desc: String? = "UPI ZOMATO BLR",
    ) = Transaction(
        id = id,
        type = type,
        amountPaise = amount,
        occurredAt = at,
        source = TransactionSource.SMS,
        rawDescription = desc,
        counterparty = "Zomato",
        externalRefId = ref,
    )

    private fun row(
        amount: Long = 10_000L,
        type: TransactionType = TransactionType.DEBIT,
        at: Long = 1_700_000_000_000L,
        ref: String? = null,
        desc: String? = "UPI-ZOMATO-BLR",
    ) = ParsedCsvRow(
        lineNumber = 2,
        occurredAt = at,
        type = type,
        amountPaise = amount,
        description = desc,
        counterparty = "Zomato",
        externalRef = ref,
        categoryHint = null,
        note = null,
        rawLine = "",
    )

    @Test
    fun `same ref is high confidence`() {
        val match = ImportDedupe.match(
            row(ref = "UTR99"),
            listOf(txn(ref = "UTR99", desc = "other")),
        )
        assertThat(match.confidence).isEqualTo(DedupeConfidence.HIGH)
        assertThat(match.existing?.id).isEqualTo("1")
    }

    @Test
    fun `similar desc same day is high`() {
        val match = ImportDedupe.match(
            row(desc = "UPI ZOMATO BLR FOOD ORDER"),
            listOf(txn(desc = "UPI ZOMATO BLR")),
        )
        assertThat(match.confidence).isEqualTo(DedupeConfidence.HIGH)
    }


    @Test
    fun `no candidates is low`() {
        val match = ImportDedupe.match(row(), emptyList())
        assertThat(match.confidence).isEqualTo(DedupeConfidence.LOW)
    }

    @Test
    fun `same amount far apart is low`() {
        val far = 1_700_000_000_000L + 30L * 24 * 60 * 60_000L
        val match = ImportDedupe.match(
            row(at = far, desc = "totally different merchant xyz"),
            listOf(txn(desc = "something else")),
        )
        assertThat(match.confidence).isEqualTo(DedupeConfidence.LOW)
    }

    @Test
    fun `description similarity scores related strings`() {
        val s = ImportDedupe.descriptionSimilarity(
            "UPI-ZOMATO-MUMBAI",
            "UPI ZOMATO MUMBAI FOOD",
        )
        assertThat(s).isGreaterThan(0.3f)
    }

    @Test
    fun `shouldEnrich when statement has richer ref`() {
        val existing = txn(ref = null, desc = "short")
        val r = row(ref = "UTR1", desc = "much longer bank narration from statement file")
        assertThat(shouldEnrichExisting(existing, r)).isTrue()
    }
}
