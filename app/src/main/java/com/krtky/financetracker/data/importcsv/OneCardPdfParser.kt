package com.krtky.financetracker.data.importcsv

import com.krtky.financetracker.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object OneCardPdfParser {

    fun isOneCard(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return lower.contains("bobcard one credit card") ||
                lower.contains("one credit card statement") ||
                (lower.contains("onecard") && lower.contains("transaction history"))
    }

    fun parse(text: String): CsvParseResult {
        val yearPattern = Pattern.compile("""Statement\s*\([^\)]*?(\d{4})\s*-\s*[^\)]*?(\d{4})\)""", Pattern.CASE_INSENSITIVE)
        val yearMatcher = yearPattern.matcher(text)
        val year = if (yearMatcher.find()) {
            yearMatcher.group(2)?.toIntOrNull() ?: 2026
        } else {
            val datePattern = Pattern.compile("""Statement\s*Date\s*[\r\n\s]+[0-9]{1,2}\s+[A-Za-z]{3}\s+(\d{4})""", Pattern.CASE_INSENSITIVE)
            val dm = datePattern.matcher(text)
            if (dm.find()) dm.group(1)?.toIntOrNull() ?: 2026 else 2026
        }

        val startMarker = "TRANSACTION HISTORY"
        val startIdx = text.indexOf(startMarker, ignoreCase = true)
        if (startIdx < 0) {
            return CsvParseResult(emptyList(), emptyList(), "OneCard PDF", listOf("No transaction section found"))
        }

        val endMarkers = listOf("EMI SUMMARY", "IMPORTANT INFORMATION")
        var endIdx = text.length
        for (em in endMarkers) {
            val pos = text.indexOf(em, startIdx, ignoreCase = true)
            if (pos in 0 until endIdx) {
                endIdx = pos
            }
        }

        val section = text.substring(startIdx, endIdx)
        val lines = section.lines().map { it.trimEnd() }

        val dateRegex = Regex("""^\s*(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b""", RegexOption.IGNORE_CASE)
        val headerDiscard = Regex("""^(?:TRANSACTION HISTORY|Date\s+Merchant|Type\s+Reward|Transaction\s+Type|Amount \(Rs\.\)|\x0c)""", RegexOption.IGNORE_CASE)
        val amtRegex = Regex("""([0-9,]+\.\d{2})\s*$""")

        val months = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
        )

        val rows = mutableListOf<ParsedCsvRow>()
        var lineNum = 1

        var pendingMerchant = ""
        var pendingAmount: String? = null

        val lineCount = lines.size
        var i = 0

        while (i < lineCount) {
            val raw = lines[i]
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || headerDiscard.containsMatchIn(trimmed)) {
                i++
                continue
            }

            val dateMatch = dateRegex.find(raw)
            if (dateMatch != null) {
                val day = dateMatch.groupValues[1].toIntOrNull() ?: 1
                val monStr = dateMatch.groupValues[2].lowercase(Locale.US)
                val mon = months[monStr] ?: 1

                val calStr = String.format(Locale.US, "%04d-%02d-%02d", year, mon, day)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
                val occurredAt = sdf.parse(calStr)?.time ?: System.currentTimeMillis()

                val rest = raw.substring(dateMatch.range.last + 1).trim()
                val lineAmtMatch = amtRegex.find(rest)
                val lineAmt = lineAmtMatch?.groupValues?.get(1)
                val cleanRest = if (lineAmtMatch != null) rest.substring(0, lineAmtMatch.range.first).trim() else rest

                val parts = cleanRest.split(Regex("""\s{2,}""")).filter { it.isNotBlank() }
                val lineMerchant = parts.firstOrNull().orEmpty()

                val merchant = when {
                    pendingMerchant.isNotBlank() -> pendingMerchant
                    lineMerchant.isNotBlank() -> lineMerchant
                    else -> "Card Spend"
                }

                val candidates = listOfNotNull(lineAmt, pendingAmount)
                var amountStr = candidates.maxByOrNull { it.replace(",", "").toDoubleOrNull() ?: 0.0 }
                val notes = mutableListOf<String>()

                var j = i + 1
                while (j < lineCount) {
                    val nextRaw = lines[j]
                    val nextTrimmed = nextRaw.trim()
                    if (nextTrimmed.isEmpty()) {
                        j++
                        continue
                    }
                    if (dateRegex.containsMatchIn(nextRaw) || headerDiscard.containsMatchIn(nextTrimmed)) {
                        break
                    }

                    if (nextTrimmed.contains("Forex Fee", ignoreCase = true) ||
                        nextTrimmed.contains("GST @", ignoreCase = true) ||
                        nextTrimmed.contains("EMI Interest", ignoreCase = true) ||
                        nextTrimmed.contains("EMI Principal", ignoreCase = true) ||
                        nextTrimmed.startsWith("EMI ") ||
                        amountStr == null
                    ) {
                        notes.add(nextTrimmed)
                        val subAmt = amtRegex.find(nextTrimmed)
                        if (subAmt != null) {
                            val subVal = subAmt.groupValues[1]
                            val curVal = amountStr?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                            val newVal = subVal.replace(",", "").toDoubleOrNull() ?: 0.0
                            if (newVal > curVal) {
                                amountStr = subVal
                            }
                        }
                        j++
                    } else {
                        break
                    }
                }

                val paise = CsvStatementParser.parseMoneyPaise(amountStr) ?: 0L
                val isCredit = raw.contains("repayment", ignoreCase = true) ||
                        raw.contains("paid via", ignoreCase = true) ||
                        merchant.contains("repayment", ignoreCase = true) ||
                        merchant.contains("paid via", ignoreCase = true)

                val type = if (isCredit) TransactionType.CREDIT else TransactionType.DEBIT
                val noteText = notes.joinToString("; ").takeIf { it.isNotBlank() }

                rows.add(
                    ParsedCsvRow(
                        lineNumber = lineNum++,
                        occurredAt = occurredAt,
                        type = type,
                        amountPaise = paise,
                        description = merchant,
                        counterparty = merchant,
                        externalRef = null,
                        categoryHint = if (isCredit) "Repayment" else null,
                        note = noteText,
                        rawLine = raw
                    )
                )

                pendingMerchant = ""
                pendingAmount = null
                i = j
            } else {
                val subAmt = amtRegex.find(trimmed)
                if (subAmt != null) {
                    pendingAmount = subAmt.groupValues[1]
                    val clean = trimmed.substring(0, subAmt.range.first).trim()
                    if (clean.isNotBlank() && !clean.contains("EMI Principal", ignoreCase = true)) {
                        pendingMerchant = clean
                    }
                } else if (!trimmed.contains("EMI Principal", ignoreCase = true) &&
                    !trimmed.contains("Transaction", ignoreCase = true) &&
                    !trimmed.contains("Reward Points", ignoreCase = true)
                ) {
                    pendingMerchant = trimmed
                }
                i++
            }
        }

        return CsvParseResult(
            rows = rows,
            headers = listOf("Date", "Merchant Name", "Transaction Type", "Amount"),
            presetName = "OneCard PDF",
            errors = emptyList(),
            skippedLines = 0
        )
    }
}
