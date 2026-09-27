package com.krtky.financetracker.data.importcsv

import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import java.util.Locale
import kotlin.math.abs

enum class DedupeConfidence {
    /** Same ref or near-identical bank truth → auto-merge / skip insert. */
    HIGH,
    /** Likely same txn but not certain → user decides. */
    MEDIUM,
    /** No match → import as new. */
    LOW,
}

data class DedupeMatch(
    val confidence: DedupeConfidence,
    val existing: Transaction? = null,
    val matchedParts: List<Transaction> = emptyList(),
    val isSplitMatch: Boolean = false,
    val reason: String = "",
)

/**
 * Match a parsed statement row against existing transactions for one account.
 *
 * Spec: account + amount + date ± window + ref + description similarity + split group awareness.
 */
object ImportDedupe {

    private const val HIGH_WINDOW_MS = 36 * 60 * 60_000L // ±36h
    private const val MED_WINDOW_MS = 3 * 24 * 60 * 60_000L // ±3d

    fun match(
        row: ParsedCsvRow,
        candidates: List<Transaction>,
        splitPartsMap: Map<String, List<Transaction>> = emptyMap(),
    ): DedupeMatch {
        if (candidates.isEmpty()) {
            return DedupeMatch(DedupeConfidence.LOW, reason = "No existing transactions")
        }

        fun buildMatch(conf: DedupeConfidence, c: Transaction, defaultReason: String): DedupeMatch {
            val groupId = c.splitGroupId
            val parts = if (groupId != null) splitPartsMap[groupId].orEmpty() else emptyList()
            val isSplit = parts.isNotEmpty()
            val finalReason = if (isSplit) "Matches split group (${parts.size} parts)" else defaultReason
            return DedupeMatch(
                confidence = conf,
                existing = c,
                matchedParts = parts,
                isSplitMatch = isSplit,
                reason = finalReason,
            )
        }

        val ref = row.externalRef?.trim()?.takeIf { it.isNotBlank() }
        if (ref != null) {
            val byRef = candidates.firstOrNull { existing ->
                existing.externalRefId?.equals(ref, ignoreCase = true) == true
            }
            if (byRef != null) {
                return buildMatch(
                    DedupeConfidence.HIGH,
                    byRef,
                    defaultReason = "Same reference ($ref)",
                )
            }
        }

        val amountType = candidates.filter {
            it.amountPaise == row.amountPaise && it.type == row.type
        }
        if (amountType.isEmpty()) {
            // Check same-day combination match (subset sum for 2 or 3 same-day transactions)
            val sameDaySameDir = candidates.filter {
                sameDay(it.occurredAt, row.occurredAt) && it.type == row.type
            }
            val combo = findSubsetSum(sameDaySameDir, row.amountPaise)
            if (combo != null && combo.size in 2..4) {
                val totalDescScore = combo.map {
                    descriptionSimilarity(row.description ?: row.counterparty, it.rawDescription ?: it.counterparty ?: it.note)
                }.maxOrNull() ?: 0f
                if (totalDescScore >= 0.35f || combo.all { !it.counterparty.isNullOrBlank() }) {
                    return DedupeMatch(
                        confidence = DedupeConfidence.HIGH,
                        existing = combo.first(),
                        matchedParts = combo,
                        isSplitMatch = true,
                        reason = "Matches combination of ${combo.size} same-day spends",
                    )
                }
            }
            return DedupeMatch(DedupeConfidence.LOW, reason = "No amount/direction match")
        }

        // High: same amount/type within window + strong desc/ref signal
        val near = amountType.filter { abs(it.occurredAt - row.occurredAt) <= HIGH_WINDOW_MS }
        for (c in near.sortedBy { abs(it.occurredAt - row.occurredAt) }) {
            val descScore = descriptionSimilarity(
                row.description ?: row.counterparty,
                c.rawDescription ?: c.counterparty ?: c.note,
            )
            // Strong description match → HIGH.
            if (descScore >= 0.72f) {
                return buildMatch(
                    DedupeConfidence.HIGH,
                    c,
                    defaultReason = "Same amount & date · similar description",
                )
            }
            // Attaching a brand-new statement ref to a near twin that carries no ref yet
            if (ref != null && c.externalRefId.isNullOrBlank()) {
                return buildMatch(
                    DedupeConfidence.HIGH,
                    c,
                    defaultReason = "Same amount & date · attaching statement reference",
                )
            }
            // Same calendar day + exact amount:
            // In personal banking, same-day exact amount transactions are duplicates >80% of the time,
            // especially with description overlap or non-round amounts.
            if (sameDay(c.occurredAt, row.occurredAt)) {
                val isIrregularAmount = (row.amountPaise % 100 != 0L) || (row.amountPaise % 50000L != 0L)
                if (descScore >= 0.20f || isIrregularAmount) {
                    return buildMatch(
                        DedupeConfidence.HIGH,
                        c,
                        defaultReason = if (descScore >= 0.20f) {
                            "Same day, amount · related description"
                        } else {
                            "Same day & exact irregular amount"
                        },
                    )
                }
            }
        }

        // Medium: amount/type within wider window, weak or no desc
        val wider = amountType.filter { abs(it.occurredAt - row.occurredAt) <= MED_WINDOW_MS }
        val best = wider.minByOrNull { abs(it.occurredAt - row.occurredAt) }
        if (best != null) {
            val descScore = descriptionSimilarity(
                row.description ?: row.counterparty,
                best.rawDescription ?: best.counterparty ?: best.note,
            )
            return buildMatch(
                DedupeConfidence.MEDIUM,
                best,
                defaultReason = if (descScore > 0.2f) {
                    "Similar amount & nearby date — confirm"
                } else {
                    "Same amount nearby — may be a different transaction"
                },
            )
        }

        return DedupeMatch(DedupeConfidence.LOW, reason = "No close match")
    }

    internal fun findSubsetSum(txns: List<Transaction>, targetPaise: Long): List<Transaction>? {
        if (txns.size < 2 || targetPaise <= 0L) return null
        val pool = txns.filter { it.amountPaise < targetPaise }
        for (i in 0 until pool.size) {
            for (j in i + 1 until pool.size) {
                if (pool[i].amountPaise + pool[j].amountPaise == targetPaise) {
                    return listOf(pool[i], pool[j])
                }
            }
        }
        if (pool.size in 3..12) {
            for (i in 0 until pool.size) {
                for (j in i + 1 until pool.size) {
                    for (k in j + 1 until pool.size) {
                        if (pool[i].amountPaise + pool[j].amountPaise + pool[k].amountPaise == targetPaise) {
                            return listOf(pool[i], pool[j], pool[k])
                        }
                    }
                }
            }
        }
        return null
    }

    fun sameDay(a: Long, b: Long): Boolean {
        val cal = java.util.Calendar.getInstance()
        fun dayKey(t: Long): Int {
            cal.timeInMillis = t
            return cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        }
        return dayKey(a) == dayKey(b)
    }

    /**
     * 0..1 similarity: token Jaccard + subset containment overlap.
     */
    fun descriptionSimilarity(a: String?, b: String?): Float {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        val inter = ta.intersect(tb).size.toFloat()
        val union = ta.union(tb).size.toFloat().coerceAtLeast(1f)
        val jaccard = inter / union
        val minSize = minOf(ta.size, tb.size).toFloat().coerceAtLeast(1f)
        val overlap = inter / minSize
        val na = normalize(a)
        val nb = normalize(b)
        val contain = when {
            na.isEmpty() || nb.isEmpty() -> 0f
            na.contains(nb) || nb.contains(na) -> 0.85f
            else -> 0f
        }
        return maxOf(jaccard, overlap * 0.80f, contain)
    }

    private fun normalize(s: String?): String =
        s?.lowercase(Locale.US)
            ?.replace(Regex("""\b\d{10,16}\b"""), " ")
            ?.replace(Regex("[^a-z0-9 ]"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()

    private fun tokens(s: String?): Set<String> =
        normalize(s)
            .split(' ')
            .filter { it.length >= 2 && it !in STOP }
            .toSet()

    private val STOP = setOf(
        "upi", "to", "from", "and", "the", "for", "ref", "no", "inr", "rs",
        "payment", "paid", "via", "bank", "neft", "imps", "rtgs", "txn",
        "pos", "pcd", "ecom", "token", "token_ecom", "bbps", "ach", "atl", "atw", "null",
    )
}

/** Whether a statement row should enrich an existing SMS/manual row on merge. */
fun shouldEnrichExisting(existing: Transaction, row: ParsedCsvRow): Boolean {
    val richerDesc = !row.description.isNullOrBlank() &&
        (existing.rawDescription.isNullOrBlank() ||
            (row.description!!.length > (existing.rawDescription?.length ?: 0) + 8))
    val richerRef = !row.externalRef.isNullOrBlank() && existing.externalRefId.isNullOrBlank()
    val richerParty = !row.counterparty.isNullOrBlank() &&
        existing.counterparty.isNullOrBlank()
    return richerDesc || richerRef || richerParty
}

fun enrichTransaction(existing: Transaction, row: ParsedCsvRow): Transaction {
    return existing.copy(
        rawDescription = when {
            !row.description.isNullOrBlank() &&
                (existing.rawDescription.isNullOrBlank() ||
                    row.description!!.length > (existing.rawDescription?.length ?: 0)) ->
                row.description
            else -> existing.rawDescription
        },
        externalRefId = existing.externalRefId?.takeIf { it.isNotBlank() }
            ?: row.externalRef?.takeIf { it.isNotBlank() },
        counterparty = existing.counterparty?.takeIf { it.isNotBlank() }
            ?: row.counterparty,
        note = existing.note?.takeIf { it.isNotBlank() } ?: row.note,
        updatedAt = System.currentTimeMillis(),
        sheetsSynced = false,
    )
}
