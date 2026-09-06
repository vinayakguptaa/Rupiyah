package com.krtky.financetracker.data.importcsv

import com.krtky.financetracker.domain.model.TransactionKind
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parsed row from an Activity CSV export (Settings restore / Downloads export).
 * Names are resolved to ids at restore time.
 */
data class ActivityCsvRow(
    val id: String,
    val occurredAt: Long,
    val type: TransactionType,
    val amountPaise: Long,
    val name: String?,
    val categoryName: String?,
    val tabName: String?,
    val accountName: String?,
    val isCash: Boolean,
    val note: String?,
    val placeName: String?,
    val source: TransactionSource,
    val kind: TransactionKind,
    val splitGroupId: String?,
    val transferGroupId: String?,
)

data class ActivityCsvParseResult(
    val rows: List<ActivityCsvRow>,
    val errors: List<String>,
    /** True when the file used the pre-fix header that inserted a phantom Counterparty column. */
    val usedLegacyShiftedLayout: Boolean,
)

/**
 * Parse Rupiyah Activity CSV exports for backup restore.
 *
 * Accepts:
 * - Current format ([com.krtky.financetracker.ui.util.ActivityCsvFormat.HEADERS])
 * - Legacy buggy exports that advertised a Counterparty column but omitted its value
 *   (every field after Name was shifted one column left; Transaction ID sat under Source).
 */
object ActivityCsvParser {

    fun looksLikeActivityCsv(text: String): Boolean {
        val first = CsvStatementParser.splitLines(text).firstOrNull() ?: return false
        val headers = CsvStatementParser.parseCsvLine(first).map { normalizeHeader(it) }
        if (headers.isEmpty()) return false
        val hasDate = "date" in headers
        val hasAmount = headers.any { it.startsWith("amount") }
        val hasTxnId = headers.any { it == "transaction id" || it == "transactionid" || it == "id" }
        val hasName = "name" in headers || "counterparty" in headers
        return hasDate && hasAmount && hasTxnId && hasName
    }

    fun parse(text: String): ActivityCsvParseResult {
        val lines = CsvStatementParser.splitLines(text)
        if (lines.isEmpty()) {
            return ActivityCsvParseResult(emptyList(), listOf("Empty CSV"), false)
        }
        val rawHeaders = CsvStatementParser.parseCsvLine(lines.first())
        val headers = rawHeaders.map { normalizeHeader(it) }
        val index = headers.mapIndexed { i, h -> h to i }.toMap()

        val sample = lines.drop(1).firstOrNull()?.let { CsvStatementParser.parseCsvLine(it) }
        val legacyShifted = isLegacyShiftedLayout(headers, sample)

        val errors = mutableListOf<String>()
        val rows = mutableListOf<ActivityCsvRow>()
        lines.drop(1).forEachIndexed { offset, line ->
            val lineNo = offset + 2
            val cells = CsvStatementParser.parseCsvLine(line)
            val mapped = if (legacyShifted) {
                mapLegacyShifted(cells)
            } else {
                mapByHeader(cells, index)
            } ?: run {
                errors += "Line $lineNo: could not read row"
                return@forEachIndexed
            }
            if (mapped.id.isBlank()) {
                errors += "Line $lineNo: missing Transaction ID"
                return@forEachIndexed
            }
            if (mapped.amountPaise == null || mapped.amountPaise <= 0L) {
                errors += "Line $lineNo: invalid amount"
                return@forEachIndexed
            }
            if (mapped.occurredAt == null) {
                errors += "Line $lineNo: invalid date/time"
                return@forEachIndexed
            }
            rows += ActivityCsvRow(
                id = mapped.id.trim(),
                occurredAt = mapped.occurredAt,
                type = mapped.type,
                amountPaise = mapped.amountPaise,
                name = mapped.name,
                categoryName = mapped.categoryName,
                tabName = mapped.tabName,
                accountName = mapped.accountName,
                isCash = mapped.isCash,
                note = mapped.note,
                placeName = mapped.placeName,
                source = mapped.source,
                kind = mapped.kind,
                splitGroupId = mapped.splitGroupId,
                transferGroupId = mapped.transferGroupId,
            )
        }
        return ActivityCsvParseResult(rows, errors, legacyShifted)
    }

    private data class Mapped(
        val id: String,
        val occurredAt: Long?,
        val type: TransactionType,
        val amountPaise: Long?,
        val name: String?,
        val categoryName: String?,
        val tabName: String?,
        val accountName: String?,
        val isCash: Boolean,
        val note: String?,
        val placeName: String?,
        val source: TransactionSource,
        val kind: TransactionKind,
        val splitGroupId: String?,
        val transferGroupId: String?,
    )

    /**
     * Legacy header (14 cols) with only 13 data fields:
     * Date,Time,Type,Amount,Name, [phantom Counterparty], Category,Tab,Account,Cash,Note,Place,Source,TxnId
     * Actual data: Date,Time,Type,Amount,Name,Category,Tab,Account,Cash,Note,Place,Source,TxnId
     */
    private fun isLegacyShiftedLayout(headers: List<String>, sample: List<String>?): Boolean {
        if (sample == null) return false
        if ("counterparty" !in headers) return false
        // Phantom column present and row is short by one, or Source looks like a UUID.
        if (headers.size == sample.size + 1) return true
        val sourceIdx = headers.indexOf("source")
        if (sourceIdx in sample.indices) {
            val sourceVal = sample[sourceIdx]
            if (UUID_RE.matches(sourceVal.trim())) return true
        }
        return false
    }

    private fun mapLegacyShifted(cells: List<String>): Mapped? {
        if (cells.size < 13) return null
        fun cell(i: Int) = cells.getOrNull(i).orEmpty()
        val id = cell(12).trim()
        val inferred = inferTransferFromId(id)
        return Mapped(
            id = id,
            occurredAt = parseOccurredAt(cell(0), cell(1)),
            type = parseType(cell(2)),
            amountPaise = CsvStatementParser.parseMoneyPaise(cell(3)),
            name = cell(4).trim().takeIf { it.isNotBlank() },
            categoryName = cell(5).trim().takeIf { it.isNotBlank() },
            tabName = cell(6).trim().takeIf { it.isNotBlank() },
            accountName = cell(7).trim().takeIf { it.isNotBlank() },
            isCash = parseIsCash(cell(8), cell(7)),
            note = cell(9).trim().takeIf { it.isNotBlank() },
            placeName = cell(10).trim().takeIf { it.isNotBlank() },
            source = parseSource(cell(11)),
            kind = inferred?.first ?: TransactionKind.NORMAL,
            splitGroupId = null,
            transferGroupId = inferred?.second,
        )
    }

    private fun mapByHeader(cells: List<String>, index: Map<String, Int>): Mapped? {
        fun col(vararg keys: String): String {
            for (k in keys) {
                val i = index[k] ?: continue
                return cells.getOrNull(i).orEmpty()
            }
            return ""
        }
        val id = col("transaction id", "transactionid", "id").trim()
        val date = col("date")
        val time = col("time")
        val occurred = col("occurred at", "occurredat", "datetime")
        val amount = col("amount (inr)", "amount", "amount inr")
        val cashDigital = col("cash vs digital", "cashvsdigital", "is cash")
        val account = col("account")
        return Mapped(
            id = id,
            occurredAt = when {
                occurred.isNotBlank() -> parseOccurredAt(occurred, "")
                else -> parseOccurredAt(date, time)
            },
            type = parseType(col("type")),
            amountPaise = CsvStatementParser.parseMoneyPaise(amount),
            name = col("name", "counterparty").trim().takeIf { it.isNotBlank() },
            categoryName = col("category").trim().takeIf { it.isNotBlank() },
            tabName = col("tab", "fund").trim().takeIf { it.isNotBlank() },
            accountName = account.trim().takeIf { it.isNotBlank() },
            isCash = parseIsCash(cashDigital, account),
            note = col("note", "remarks").trim().takeIf { it.isNotBlank() },
            placeName = col("place", "placename").trim().takeIf { it.isNotBlank() },
            source = parseSource(col("source")),
            kind = run {
                val explicit = col("kind").trim()
                if (explicit.isNotBlank()) {
                    parseKind(explicit)
                } else {
                    inferTransferFromId(id)?.first ?: TransactionKind.NORMAL
                }
            },
            splitGroupId = col("split group id", "splitgroupid").trim().takeIf { it.isNotBlank() },
            transferGroupId = col("transfer group id", "transfergroupid").trim()
                .takeIf { it.isNotBlank() }
                ?: inferTransferFromId(id)?.second,
        )
    }

    /** Self-transfer legs use `{groupId}_out` / `{groupId}_in`. */
    private fun inferTransferFromId(id: String): Pair<TransactionKind, String>? {
        val trimmed = id.trim()
        return when {
            trimmed.endsWith("_out") && trimmed.length > 4 ->
                TransactionKind.SELF_TRANSFER to trimmed.removeSuffix("_out")
            trimmed.endsWith("_in") && trimmed.length > 3 ->
                TransactionKind.SELF_TRANSFER to trimmed.removeSuffix("_in")
            else -> null
        }
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim().lowercase(Locale.US).replace('_', ' ').replace(Regex("\\s+"), " ")

    private fun parseType(raw: String): TransactionType = when (raw.trim().uppercase(Locale.US)) {
        "CREDIT", "INCOME", "CR" -> TransactionType.CREDIT
        else -> TransactionType.DEBIT
    }

    private fun parseSource(raw: String): TransactionSource =
        runCatching { TransactionSource.valueOf(raw.trim().uppercase(Locale.US)) }
            .getOrDefault(TransactionSource.IMPORT)

    private fun parseKind(raw: String): TransactionKind = when (raw.trim().uppercase(Locale.US)) {
        "SELF_TRANSFER" -> TransactionKind.SELF_TRANSFER
        "TAB_TRANSFER" -> TransactionKind.TAB_TRANSFER
        else -> TransactionKind.NORMAL
    }

    private fun parseIsCash(cashDigital: String, account: String): Boolean {
        val v = cashDigital.trim().lowercase(Locale.US)
        return when {
            v == "cash" || v == "true" || v == "yes" || v == "1" -> true
            v == "digital" || v == "false" || v == "no" || v == "0" -> false
            else -> account.trim().equals("Cash", ignoreCase = true)
        }
    }

    private fun parseOccurredAt(datePart: String, timePart: String): Long? {
        val date = datePart.trim()
        val time = timePart.trim().ifBlank { "00:00:00" }
        if (date.isBlank()) return null
        val combined = if (date.contains(' ') || date.contains('T')) {
            date.replace('T', ' ').take(19)
        } else {
            "$date $time"
        }
        for (pattern in DATE_PATTERNS) {
            val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                // Device-local wall clock matches how export formats with SimpleDateFormat.
                timeZone = TimeZone.getDefault()
            }
            val t = runCatching { fmt.parse(combined)?.time }.getOrNull()
            if (t != null) return t
        }
        return null
    }

    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "dd-MM-yyyy HH:mm:ss",
        "dd/MM/yyyy HH:mm:ss",
        "yyyy-MM-dd",
    )

    private val UUID_RE =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
}
