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
        counterparty: String? = "Zomato",
    ) = ParsedCsvRow(
        lineNumber = 2,
        occurredAt = at,
        type = type,
        amountPaise = amount,
        description = desc,
        counterparty = counterparty,
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

    @Test
    fun `split group match is high confidence and returns matched parts`() {
        val child1 = txn(id = "c1", amount = 60_000L, desc = "Blinkit Groceries")
        val child2 = txn(id = "c2", amount = 40_000L, desc = "Blinkit Household")
        val parentGroup = txn(id = "p1", amount = 100_000L, desc = "Blinkit").copy(splitGroupId = "p1")

        val splitPartsMap = mapOf("p1" to listOf(child1, child2))
        val r = row(amount = 100_000L, desc = "UPI/Blinkit/6262/Groceries")

        val match = ImportDedupe.match(r, listOf(parentGroup), splitPartsMap)
        assertThat(match.confidence).isEqualTo(DedupeConfidence.HIGH)
        assertThat(match.isSplitMatch).isTrue()
        assertThat(match.matchedParts).hasSize(2)
        assertThat(match.reason).contains("split group")
    }

    @Test
    fun `same-day combination match detects subset sum`() {
        val spend1 = txn(id = "s1", amount = 60_000L, desc = "Blinkit Groceries")
        val spend2 = txn(id = "s2", amount = 40_000L, desc = "Blinkit Household")

        val r = row(amount = 100_000L, desc = "UPI/Blinkit/6262/Groceries")

        val match = ImportDedupe.match(r, listOf(spend1, spend2))
        assertThat(match.confidence).isEqualTo(DedupeConfidence.HIGH)
        assertThat(match.isSplitMatch).isTrue()
        assertThat(match.matchedParts).containsExactly(spend1, spend2)
    }

    @Test
    fun `indian banking narration with UTR matches counterparty with high confidence`() {
        val existing = txn(id = "e1", amount = 14500L, desc = null).copy(counterparty = "Kartikey Gupta")
        val r = row(
            amount = 14500L,
            desc = "UPI/Kartikey Gupta/IDFB/625203830280/UPI",
            counterparty = null,
        )

        val match = ImportDedupe.match(r, listOf(existing))
        assertThat(match.confidence).isEqualTo(DedupeConfidence.HIGH)
        assertThat(match.existing?.id).isEqualTo("e1")
    }

    @Test
    fun `same day exact irregular amount without description is high confidence`() {
        val existing = txn(id = "e2", amount = 34789L, desc = "POS Swiped Store")
        val r = row(amount = 34789L, desc = "PCD 998822 MUMBAI", counterparty = null)

        val match = ImportDedupe.match(r, listOf(existing))
        assertThat(match.confidence).isEqualTo(DedupeConfidence.HIGH)
        assertThat(match.existing?.id).isEqualTo("e2")
    }
}
