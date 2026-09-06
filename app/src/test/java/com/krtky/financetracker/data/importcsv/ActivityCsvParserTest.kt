package com.krtky.financetracker.data.importcsv

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionKind
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.util.buildTransactionsCsv
import org.junit.Test

class ActivityCsvParserTest {

    @Test
    fun `looksLikeActivityCsv detects export headers`() {
        val csv = buildTransactionsCsv(listOf(sampleTxn()))
        assertThat(ActivityCsvParser.looksLikeActivityCsv(csv)).isTrue()
        assertThat(ActivityCsvParser.looksLikeActivityCsv("""{"version":5}""")).isFalse()
    }

    @Test
    fun `round trip preserves id category tab account and kind`() {
        val txn = sampleTxn(
            id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            counterparty = "Zepto",
            categoryName = "Groceries",
            tabName = "Poojan",
            accountName = "SBI",
            isCash = false,
            note = "Split from parent · snacks",
            kind = TransactionKind.NORMAL,
            splitGroupId = "split-1",
            transferGroupId = null,
        )
        val csv = buildTransactionsCsv(listOf(txn))
        val parsed = ActivityCsvParser.parse(csv)
        assertThat(parsed.errors).isEmpty()
        assertThat(parsed.usedLegacyShiftedLayout).isFalse()
        assertThat(parsed.rows).hasSize(1)
        val row = parsed.rows.single()
        assertThat(row.id).isEqualTo(txn.id)
        assertThat(row.name).isEqualTo("Zepto")
        assertThat(row.categoryName).isEqualTo("Groceries")
        assertThat(row.tabName).isEqualTo("Poojan")
        assertThat(row.accountName).isEqualTo("SBI")
        assertThat(row.isCash).isFalse()
        assertThat(row.note).isEqualTo("Split from parent · snacks")
        assertThat(row.kind).isEqualTo(TransactionKind.NORMAL)
        assertThat(row.splitGroupId).isEqualTo("split-1")
        assertThat(row.type).isEqualTo(TransactionType.DEBIT)
        assertThat(row.amountPaise).isEqualTo(12_345L)
    }

    @Test
    fun `legacy shifted Counterparty header remaps columns`() {
        // Real-world buggy export: 14 headers, 13 fields; Category sits under Counterparty.
        val csv = """
            Date,Time,Type,Amount (INR),Name,Counterparty,Category,Tab,Account,Cash vs Digital,Note,Place,Source,Transaction ID
            2026-08-30,20:28:47,CREDIT,9702.16,Kartikey Gupta,Settlement,Kartikey,Kotak,Digital,Received via UPI from Kartikey Gupta,,PASTE,c850bf02-26ee-4036-85b5-ea2dc804f1b3
            2026-08-28,17:41:50,DEBIT,3000.00,"Diksha, Sakhi",Family,,Cash,Cash,"Split from Diksha · Raksha Bandhan",,MANUAL,d931be30-7bde-4682-a188-f1cee67afdc3
        """.trimIndent()

        assertThat(ActivityCsvParser.looksLikeActivityCsv(csv)).isTrue()
        val parsed = ActivityCsvParser.parse(csv)
        assertThat(parsed.usedLegacyShiftedLayout).isTrue()
        assertThat(parsed.errors).isEmpty()
        assertThat(parsed.rows).hasSize(2)

        val first = parsed.rows[0]
        assertThat(first.id).isEqualTo("c850bf02-26ee-4036-85b5-ea2dc804f1b3")
        assertThat(first.name).isEqualTo("Kartikey Gupta")
        assertThat(first.categoryName).isEqualTo("Settlement")
        assertThat(first.tabName).isEqualTo("Kartikey")
        assertThat(first.accountName).isEqualTo("Kotak")
        assertThat(first.isCash).isFalse()
        assertThat(first.note).isEqualTo("Received via UPI from Kartikey Gupta")
        assertThat(first.placeName).isNull()
        assertThat(first.source).isEqualTo(TransactionSource.PASTE)
        assertThat(first.type).isEqualTo(TransactionType.CREDIT)
        assertThat(first.amountPaise).isEqualTo(970_216L)

        val second = parsed.rows[1]
        assertThat(second.categoryName).isEqualTo("Family")
        assertThat(second.tabName).isNull()
        assertThat(second.accountName).isEqualTo("Cash")
        assertThat(second.isCash).isTrue()
        assertThat(second.note).contains("·")
        assertThat(second.source).isEqualTo(TransactionSource.MANUAL)
    }

    @Test
    fun `legacy self-transfer ids infer kind and group`() {
        val csv = """
            Date,Time,Type,Amount (INR),Name,Counterparty,Category,Tab,Account,Cash vs Digital,Note,Place,Source,Transaction ID
            2026-08-01,10:00:00,DEBIT,500.00,Kotak,Transfer,,SBI,Digital,Self transfer to Kotak,,MANUAL,64495bf7-83c8-47c9-a090-1a2720e68a17_out
            2026-08-01,10:00:00,CREDIT,500.00,SBI,Transfer,,Kotak,Digital,Self transfer from SBI,,MANUAL,64495bf7-83c8-47c9-a090-1a2720e68a17_in
        """.trimIndent()
        val parsed = ActivityCsvParser.parse(csv)
        assertThat(parsed.usedLegacyShiftedLayout).isTrue()
        assertThat(parsed.rows).hasSize(2)
        assertThat(parsed.rows[0].kind).isEqualTo(TransactionKind.SELF_TRANSFER)
        assertThat(parsed.rows[0].transferGroupId)
            .isEqualTo("64495bf7-83c8-47c9-a090-1a2720e68a17")
        assertThat(parsed.rows[1].kind).isEqualTo(TransactionKind.SELF_TRANSFER)
        assertThat(parsed.rows[1].transferGroupId)
            .isEqualTo("64495bf7-83c8-47c9-a090-1a2720e68a17")
        // Remapped: Transfer is category, not under phantom Counterparty.
        assertThat(parsed.rows[0].categoryName).isEqualTo("Transfer")
        assertThat(parsed.rows[0].accountName).isEqualTo("SBI")
    }

    @Test
    fun `trims name and accepts BOM`() {
        val body = buildTransactionsCsv(
            listOf(sampleTxn(counterparty = "  Mukesh Gupta  ")),
        )
        val withBom = "\uFEFF$body"
        val parsed = ActivityCsvParser.parse(withBom)
        assertThat(parsed.rows.single().name).isEqualTo("Mukesh Gupta")
    }

    private fun sampleTxn(
        id: String = "11111111-2222-3333-4444-555555555555",
        counterparty: String? = "Merchant",
        categoryName: String? = "Food",
        tabName: String? = null,
        accountName: String? = "Kotak",
        isCash: Boolean = false,
        note: String? = null,
        kind: TransactionKind = TransactionKind.NORMAL,
        splitGroupId: String? = null,
        transferGroupId: String? = null,
    ) = Transaction(
        id = id,
        type = TransactionType.DEBIT,
        amountPaise = 12_345L,
        occurredAt = 1_725_000_000_000L,
        source = TransactionSource.SMS,
        classificationStatus = ClassificationStatus.CLASSIFIED,
        counterparty = counterparty,
        categoryName = categoryName,
        tabName = tabName,
        accountName = accountName,
        isCash = isCash,
        note = note,
        kind = kind,
        splitGroupId = splitGroupId,
        transferGroupId = transferGroupId,
    )
}
