package com.krtky.financetracker.data.importcsv

import com.krtky.financetracker.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parsed statement line before account / dedupe / insert.
 */
data class ParsedCsvRow(
    val lineNumber: Int,
    val occurredAt: Long,
    val type: TransactionType,
    val amountPaise: Long,
    val description: String?,
    val counterparty: String?,
    val externalRef: String?,
    val categoryHint: String?,
    val note: String?,
    val rawLine: String,
)

data class CsvParseResult(
    val rows: List<ParsedCsvRow>,
    val headers: List<String>,
    val presetName: String,
    val errors: List<String> = emptyList(),
    val skippedLines: Int = 0,
)

/**
 * Flexible CSV statement parser for Indian bank / wallet exports and Rupiyah re-import.
 *
 * Expected shape (any subset, header auto-detected):
 * Date · Description · Debit · Credit · Amount · Type · Ref · Category · Remarks · Balance
 */
object CsvStatementParser {

    fun parse(text: String): CsvParseResult {
        val lines = splitLines(text)
        if (lines.isEmpty()) {
            return CsvParseResult(emptyList(), emptyList(), "empty", listOf("File is empty"))
        }

        val headerIndex = lines.indexOfFirst { looksLikeHeader(it) }.takeIf { it >= 0 } ?: 0
        val headerCells = parseCsvLine(lines[headerIndex]).map { it.trim() }
        val mapping = detectMapping(headerCells)
        val presetName = mapping.presetName
        val dataStart = headerIndex + 1

        val rows = mutableListOf<ParsedCsvRow>()
        val errors = mutableListOf<String>()
        var skipped = 0

        for (i in dataStart until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                skipped++
                continue
            }
            val cells = parseCsvLine(line)
            if (cells.all { it.isBlank() }) {
                skipped++
                continue
            }
            val parsed = parseRow(i + 1, cells, mapping, line)
            if (parsed != null) {
                rows += parsed
            } else {
                skipped++
                if (errors.size < 12) {
                    errors += "Line ${i + 1}: could not parse amount/date"
                }
            }
        }

        if (rows.isEmpty() && errors.isEmpty()) {
            errors += "No transactions found. Check that the file has a header row and Date + Debit/Credit (or Amount) columns."
        }

        return CsvParseResult(
            rows = rows,
            headers = headerCells,
            presetName = presetName,
            errors = errors,
            skippedLines = skipped,
        )
    }

    // --- mapping -----------------------------------------------------------------

    data class ColumnMapping(
        val presetName: String,
        val date: Int? = null,
        val time: Int? = null,
        val description: Int? = null,
        val debit: Int? = null,
        val credit: Int? = null,
        val amount: Int? = null,
        val type: Int? = null,
        val ref: Int? = null,
        val category: Int? = null,
        val note: Int? = null,
        val name: Int? = null,
        val counterparty: Int? = null,
    )

    private fun detectMapping(headers: List<String>): ColumnMapping {
        val norm = headers.map { normalizeHeader(it) }

        fun idx(vararg keys: String): Int? {
            for (k in keys) {
                val i = norm.indexOfFirst { it == k || it.contains(k) }
                if (i >= 0) return i
            }
            return null
        }

        // Rupiyah export
        if (norm.any { it == "amount (inr)" || it == "amount inr" } &&
            norm.any { it == "type" } &&
            norm.any { it == "date" }
        ) {
            return ColumnMapping(
                presetName = "Rupiyah export",
                date = idx("date"),
                time = idx("time"),
                description = idx("note"),
                amount = idx("amount (inr)", "amount inr", "amount"),
                type = idx("type"),
                ref = idx("transaction id", "ref"),
                category = idx("category"),
                note = idx("note"),
                name = idx("name"),
                counterparty = idx("counterparty"),
            )
        }

        val debit = idx("debit", "withdrawal", "withdrawals", "dr", "money out", "spent")
        val credit = idx("credit", "deposit", "deposits", "cr", "money in", "received")
        val amount = idx("amount", "txn amount", "transaction amount", "value")
        val type = idx("type", "dr/cr", "debit/credit", "transaction type", "cr/dr")
        val date = idx("date", "txn date", "transaction date", "value date", "posting date", "tran date")
        val time = idx("time")
        val desc = idx(
            "description", "narration", "particulars", "details", "remarks",
            "transaction remarks", "narrative", "memo",
        )
        val ref = idx(
            "ref", "reference", "utr", "cheque", "chq", "transaction id",
            "txn id", "rrn", "ref no", "reference no", "instrument",
        )
        val category = idx("category")
        val note = idx("remarks", "note", "comments", "memo")
        val name = idx("name", "payee", "merchant", "party")
        val counterparty = idx("counterparty")

        val preset = when {
            debit != null && credit != null -> "Debit / Credit columns"
            amount != null && type != null -> "Amount + Type"
            amount != null -> "Signed amount"
            else -> "Generic"
        }

        return ColumnMapping(
            presetName = preset,
            date = date,
            time = time,
            description = desc,
            debit = debit,
            credit = credit,
            amount = amount,
            type = type,
            ref = ref,
            category = category,
            note = if (note != desc) note else null,
            name = name,
            counterparty = counterparty,
        )
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim().lowercase(Locale.US)
            .replace('\u00a0', ' ')
            .replace(Regex("[._/\\\\]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun looksLikeHeader(line: String): Boolean {
        val n = normalizeHeader(line)
        val hits = listOf("date", "debit", "credit", "amount", "description", "narration", "type")
            .count { n.contains(it) }
        return hits >= 2
    }

    // --- row parse ---------------------------------------------------------------

    private fun parseRow(
        lineNumber: Int,
        cells: List<String>,
        m: ColumnMapping,
        rawLine: String,
    ): ParsedCsvRow? {
        fun cell(i: Int?): String? =
            i?.let { cells.getOrNull(it)?.trim()?.takeIf { s -> s.isNotEmpty() } }

        val dateStr = cell(m.date) ?: return null
        val timeStr = cell(m.time)
        val occurredAt = parseDateTime(dateStr, timeStr) ?: return null

        val debitPaise = parseMoneyPaise(cell(m.debit))
        val creditPaise = parseMoneyPaise(cell(m.credit))
        val amountRaw = cell(m.amount)
        val typeHint = cell(m.type)

        val (type, amountPaise) = resolveDirectionAndAmount(
            debitPaise = debitPaise,
            creditPaise = creditPaise,
            amountRaw = amountRaw,
            typeHint = typeHint,
            description = cell(m.description),
        ) ?: return null

        if (amountPaise <= 0L) return null

        val desc = cell(m.description)
        val party = cell(m.counterparty) ?: cell(m.name) ?: guessParty(desc)
        val ref = cell(m.ref)
        val category = cell(m.category)
        val note = cell(m.note)

        return ParsedCsvRow(
            lineNumber = lineNumber,
            occurredAt = occurredAt,
            type = type,
            amountPaise = amountPaise,
            description = desc,
            counterparty = party,
            externalRef = ref,
            categoryHint = category,
            note = note,
            rawLine = rawLine.take(500),
        )
    }

    internal fun resolveDirectionAndAmount(
        debitPaise: Long?,
        creditPaise: Long?,
        amountRaw: String?,
        typeHint: String?,
        description: String?,
    ): Pair<TransactionType, Long>? {
        if (debitPaise != null && debitPaise > 0L && (creditPaise == null || creditPaise == 0L)) {
            return TransactionType.DEBIT to debitPaise
        }
        if (creditPaise != null && creditPaise > 0L && (debitPaise == null || debitPaise == 0L)) {
            return TransactionType.CREDIT to creditPaise
        }
        if (debitPaise != null && creditPaise != null && debitPaise > 0L && creditPaise > 0L) {
            // Prefer non-zero exclusive; if both set, treat larger as signal is wrong — skip
            return null
        }

        // Signed amount only when the sign is explicit (-, +, or parentheses).
        // A plain positive "100" with no type column must NOT default to CREDIT:
        // a spend would be imported as income (inflating balances).
        val signed = if (hasExplicitSign(amountRaw)) parseSignedMoneyPaise(amountRaw) else null
        if (signed != null && signed != 0L) {
            return if (signed < 0) {
                TransactionType.DEBIT to kotlin.math.abs(signed)
            } else {
                val fromType = typeFromHint(typeHint, description) ?: TransactionType.CREDIT
                fromType to signed
            }
        }

        val abs = parseMoneyPaise(amountRaw) ?: return null
        if (abs <= 0L) return null
        val type = typeFromHint(typeHint, description) ?: return null
        return type to abs
    }

    /** True when the amount carries an explicit sign or accounting parentheses. */
    internal fun hasExplicitSign(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        var s = raw.trim()
            .replace(",", "")
            .replace("₹", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace("INR", "", ignoreCase = true)
            .replace(" ", "")
        val parenNeg = s.startsWith("(") && s.endsWith(")")
        return parenNeg || s.startsWith("+") || s.startsWith("-")
    }

    private fun typeFromHint(typeHint: String?, description: String?): TransactionType? {
        val t = typeHint?.trim()?.lowercase(Locale.US).orEmpty()
        when {
            t.isEmpty() -> Unit
            t in setOf("debit", "dr", "d", "expense", "withdrawal", "withdraw", "out", "spent", "payment") ->
                return TransactionType.DEBIT
            t in setOf("credit", "cr", "c", "income", "deposit", "in", "received", "refund") ->
                return TransactionType.CREDIT
            t.contains("debit") || t.contains("withdraw") || t.contains("expense") ->
                return TransactionType.DEBIT
            t.contains("credit") || t.contains("deposit") || t.contains("income") ->
                return TransactionType.CREDIT
        }
        val d = description?.lowercase(Locale.US).orEmpty()
        return when {
            d.contains("credited") || d.contains("received") || d.contains("salary") ->
                TransactionType.CREDIT
            d.contains("debited") || d.contains("spent") || d.contains("paid") ->
                TransactionType.DEBIT
            else -> null
        }
    }

    private fun guessParty(description: String?): String? {
        if (description.isNullOrBlank()) return null
        // UPI: "UPI-NAME-..." or "to NAME"
        val upi = Regex("""UPI[-/ ]+([A-Za-z0-9 .&']{2,40})""", RegexOption.IGNORE_CASE)
            .find(description)?.groupValues?.getOrNull(1)?.trim()
        if (!upi.isNullOrBlank()) return upi.take(40)
        return description.take(48).trim().takeIf { it.isNotBlank() }
    }

    // --- money / date ------------------------------------------------------------

    /** Absolute amount in paise; strips currency symbols. */
    fun parseMoneyPaise(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
            .replace(",", "")
            .replace("₹", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace("INR", "", ignoreCase = true)
            .replace(" ", "")
            .replace("(", "")
            .replace(")", "")
        if (s.startsWith("+")) s = s.drop(1)
        if (s.startsWith("-")) s = s.drop(1)
        if (s.isBlank() || s == "-" || s == "0" || s == "0.00") return 0L
        val d = s.toDoubleOrNull() ?: return null
        return Math.round(d * 100.0)
    }

    fun parseSignedMoneyPaise(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
            .replace(",", "")
            .replace("₹", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace("INR", "", ignoreCase = true)
            .replace(" ", "")
        val parenNeg = s.startsWith("(") && s.endsWith(")")
        s = s.replace("(", "").replace(")", "")
        val neg = parenNeg || s.startsWith("-")
        if (s.startsWith("+") || s.startsWith("-")) s = s.drop(1)
        if (s.isBlank()) return null
        val d = s.toDoubleOrNull() ?: return null
        val paise = Math.round(d * 100.0)
        return if (neg) -paise else paise
    }

    private val dateFormats = listOf(
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "dd-MM-yyyy",
        "dd/MM/yyyy",
        "dd-MM-yy",
        "dd/MM/yy",
        "dd MMM yyyy",
        "dd-MMM-yyyy",
        "dd/MMM/yyyy",
        "MMM dd, yyyy",
        "MM/dd/yyyy",
        "d/M/yyyy",
        "d-M-yyyy",
        "yyyyMMdd",
    )

    private val dateTimeFormats = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "dd-MM-yyyy HH:mm:ss",
        "dd/MM/yyyy HH:mm:ss",
        "dd-MM-yyyy HH:mm",
        "dd/MM/yyyy HH:mm",
        "yyyy-MM-dd HH:mm",
    )

    fun parseDateTime(dateStr: String, timeStr: String?): Long? {
        val combined = if (!timeStr.isNullOrBlank()) {
            "${dateStr.trim()} ${timeStr.trim()}"
        } else {
            dateStr.trim()
        }
        for (pattern in dateTimeFormats + dateFormats) {
            val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                // Wall-clock local for bank statements
                timeZone = TimeZone.getDefault()
            }
            val t = runCatching { fmt.parse(combined)?.time }.getOrNull()
            if (t != null) return t
        }
        // Excel serial date (days since 1899-12-30)
        val serial = dateStr.trim().toDoubleOrNull()
        if (serial != null && serial in 20_000.0..80_000.0) {
            val epoch = ((serial - 25569.0) * 86_400_000.0).toLong()
            return epoch
        }
        return null
    }

    // --- CSV split ---------------------------------------------------------------

    fun splitLines(text: String): List<String> {
        // Strip BOM
        val cleaned = text.removePrefix("\uFEFF")
        return cleaned.split('\n', '\r').map { it.trimEnd() }.filter { it.isNotEmpty() || true }
            .let { all ->
                // Re-join empty only if truly blank file parts — keep non-empty after strip
                all.filter { line -> line.isNotBlank() }
            }
    }

    /** RFC4180-ish field split with quotes. */
    fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        var inQuotes = false
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out += sb.toString()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
