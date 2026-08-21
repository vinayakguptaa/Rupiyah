package com.krtky.financetracker.data.importcsv

import kotlinx.serialization.Serializable

/**
 * Header names (not values) for each parser role. Produced by heuristics or
 * a one-shot LLM call on headers + a few sample rows — never on the whole file.
 */
@Serializable
data class CsvHeaderRoles(
    val date: String? = null,
    val time: String? = null,
    val description: String? = null,
    val debit: String? = null,
    val credit: String? = null,
    val amount: String? = null,
    val type: String? = null,
    val ref: String? = null,
    val category: String? = null,
    val note: String? = null,
    val name: String? = null,
    val counterparty: String? = null,
    val investment: String? = null,
    val transfer: String? = null,
)

object CsvHeaderRolesParser {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun fromJson(raw: String): CsvHeaderRoles? =
        runCatching { json.decodeFromString(CsvHeaderRoles.serializer(), raw) }.getOrNull()

    fun toMapping(
        headers: List<String>,
        roles: CsvHeaderRoles,
        presetName: String = "AI column map",
    ): CsvStatementParser.ColumnMapping {
        fun col(name: String?): Int? {
            val want = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val exact = headers.indexOfFirst { it.equals(want, ignoreCase = true) }
            if (exact >= 0) return exact
            val nWant = CsvStatementParser.normalizeHeaderPublic(want)
            return headers.indexOfFirst { CsvStatementParser.normalizeHeaderPublic(it) == nWant }
                .takeIf { it >= 0 }
        }
        return CsvStatementParser.ColumnMapping(
            presetName = presetName,
            date = col(roles.date),
            time = col(roles.time),
            description = col(roles.description),
            debit = col(roles.debit),
            credit = col(roles.credit),
            amount = col(roles.amount),
            type = col(roles.type),
            ref = col(roles.ref),
            category = col(roles.category),
            note = col(roles.note),
            name = col(roles.name),
            counterparty = col(roles.counterparty),
            investment = col(roles.investment),
            transfer = col(roles.transfer),
        )
    }

    fun isUsable(mapping: CsvStatementParser.ColumnMapping): Boolean {
        if (mapping.date == null) return false
        val hasMoney = mapping.debit != null || mapping.credit != null ||
            mapping.amount != null || mapping.investment != null || mapping.transfer != null
        return hasMoney
    }

    const val LLM_SYSTEM = """You map a CSV statement to import column roles.
Return a JSON object only. Each value must be an EXACT header string from HEADERS, or null.
Never invent headers. Never parse or rewrite row data.

Roles:
date, time, description, debit, credit, amount, type, ref, category, note, name, counterparty, investment, transfer

Bank / wallet exports are planar: Date + narration + Debit and Credit (or Amount + DR/CR). Prefer Debit/Credit columns over a single Amount when both exist.
Do not map Balance, Opening, Closing, or Available as amount/debit/credit.
investment = a dedicated invested-amount column (not lifestyle spend). transfer = a dedicated transfer column (not income).
type = a column whose cells are DR/CR, Debit/Credit, Expense/Income, Transfer, or Investment.
If a column is unused, set it to null."""
}