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
    val reason: String = "",
)

/**
 * Match a parsed statement row against existing transactions for one account.
 *
 * Spec: account + amount + date ± window + ref + description similarity.
 */
object ImportDedupe {

    private const val HIGH_WINDOW_MS = 36 * 60 * 60_000L // ±36h
    private const val MED_WINDOW_MS = 3 * 24 * 60 * 60_000L // ±3d

    fun match(
        row: ParsedCsvRow,
        candidates: List<Transaction>,
    ): DedupeMatch {
        if (candidates.isEmpty()) {
            return DedupeMatch(DedupeConfidence.LOW, reason = "No existing transactions")
        }

        val ref = row.externalRef?.trim()?.takeIf { it.isNotBlank() }
        if (ref != null) {
            val byRef = candidates.firstOrNull { existing ->
                existing.externalRefId?.equals(ref, ignoreCase = true) == true
            }
            if (byRef != null) {
                return DedupeMatch(
                    DedupeConfidence.HIGH,
                    byRef,
                    reason = "Same reference ($ref)",
                )
            }
        }

        val amountType = candidates.filter {
            it.amountPaise == row.amountPaise && it.type == row.type
        }
        if (amountType.isEmpty()) {
            return DedupeMatch(DedupeConfidence.LOW, reason = "No amount/direction match")
        }

        // High: same amount/type within window + strong desc/ref signal
        val near = amountType.filter { abs(it.occurredAt - row.occurredAt) <= HIGH_WINDOW_MS }
        for (c in near.sortedBy { abs(it.occurredAt - row.occurredAt) }) {
            val descScore = descriptionSimilarity(
                row.description ?: row.counterparty,
                c.rawDescription ?: c.counterparty ?: c.merchant ?: c.note,
            )
            if (descScore >= 0.72f || (ref != null && c.externalRefId.isNullOrBlank())) {
                // Strong description match, or we're attaching a new ref to a near twin
                if (descScore >= 0.72f) {
                    return DedupeMatch(
                        DedupeConfidence.HIGH,
                        c,
                        reason = "Same amount & date · similar description",
                    )
                }
            }
            // Same calendar day + exact amount without needing desc (SMS often short)
            if (sameDay(c.occurredAt, row.occurredAt) && descScore >= 0.45f) {
                return DedupeMatch(
                    DedupeConfidence.HIGH,
                    c,
                    reason = "Same day, amount, direction · related description",
                )
            }
        }

        // Medium: amount/type within wider window, weak or no desc
        val wider = amountType.filter { abs(it.occurredAt - row.occurredAt) <= MED_WINDOW_MS }
        val best = wider.minByOrNull { abs(it.occurredAt - row.occurredAt) }
        if (best != null) {
            val descScore = descriptionSimilarity(
                row.description ?: row.counterparty,
                best.rawDescription ?: best.counterparty ?: best.merchant ?: best.note,
            )
            return DedupeMatch(
                DedupeConfidence.MEDIUM,
                best,
                reason = if (descScore > 0.2f) {
                    "Similar amount & nearby date — confirm"
                } else {
                    "Same amount nearby — may be a different transaction"
                },
            )
        }

        return DedupeMatch(DedupeConfidence.LOW, reason = "No close match")
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
     * 0..1 similarity: token Jaccard + containment.
     */
    fun descriptionSimilarity(a: String?, b: String?): Float {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        val inter = ta.intersect(tb).size.toFloat()
        val union = ta.union(tb).size.toFloat().coerceAtLeast(1f)
        val jaccard = inter / union
        val na = normalize(a)
        val nb = normalize(b)
        val contain = when {
            na.isEmpty() || nb.isEmpty() -> 0f
            na.contains(nb) || nb.contains(na) -> 0.85f
            else -> 0f
        }
        return maxOf(jaccard, contain)
    }

    private fun normalize(s: String?): String =
        s?.lowercase(Locale.US)
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
    )
}

/** Whether a statement row should enrich an existing SMS/manual row on merge. */
fun shouldEnrichExisting(existing: Transaction, row: ParsedCsvRow): Boolean {
    val richerDesc = !row.description.isNullOrBlank() &&
        (existing.rawDescription.isNullOrBlank() ||
            (row.description!!.length > (existing.rawDescription?.length ?: 0) + 8))
    val richerRef = !row.externalRef.isNullOrBlank() && existing.externalRefId.isNullOrBlank()
    val richerParty = !row.counterparty.isNullOrBlank() &&
        existing.counterparty.isNullOrBlank() &&
        existing.merchant.isNullOrBlank()
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
            ?: existing.merchant?.takeIf { it.isNotBlank() }
            ?: row.counterparty,
        merchant = existing.merchant ?: row.counterparty,
        note = existing.note?.takeIf { it.isNotBlank() } ?: row.note,
        updatedAt = System.currentTimeMillis(),
        sheetsSynced = false,
    )
}
