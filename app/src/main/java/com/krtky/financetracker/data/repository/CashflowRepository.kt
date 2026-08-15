package com.krtky.financetracker.data.repository

import com.krtky.financetracker.data.local.db.AccountEntity
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.TransactionEntity
import com.krtky.financetracker.domain.model.CashflowMetrics
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.MonthlySummary
import com.krtky.financetracker.domain.model.MonthlyTrend
import com.krtky.financetracker.domain.model.NamedAmount
import com.krtky.financetracker.domain.model.TransactionKind
import com.krtky.financetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only cashflow metrics for Home / widgets / categories.
 *
 * Extracted from [TransactionRepository] so the ledger-write paths and the metric
 * reads stop sharing one god object. All functions are pure reads over the DAO.
 */
@Singleton
class CashflowRepository @Inject constructor(
    private val db: AppDatabase,
) {
    private val txnDao = db.transactionDao()
    private val categoryDao = db.categoryDao()
    private val accountDao = db.accountDao()

    /** Self-transfer and tab-transfer rows never enter lifestyle/credit reports. */
    private fun isExcludedFromCashflowKind(kind: String?): Boolean {
        val k = kind?.uppercase()
        return k == TransactionKind.SELF_TRANSFER.name || k == TransactionKind.TAB_TRANSFER.name
    }

    /**
     * Net balance per account label (credits − debits + opening), matching the
     * Accounts-screen formula in [AccountRepository].
     * Rows with no owning account group under "Digital".
     */
    fun observeAccountBalances(): Flow<Map<String, Long>> =
        combine(
            accountDao.observeAll(),
            txnDao.observeAll(),
        ) { accounts, txns ->
            val live = txns.filter { it.deletedAt == null }
            val byAccount = accounts.associate { acc ->
                val mine = live.filter { it.accountId == acc.id }
                val net = mine.sumOf { signedPaise(it) }
                acc.name.trim() to (acc.openingBalancePaise + net)
            }.toMutableMap()

            // Rows with no owning account (e.g. legacy unlinked spends) — keep Home's
            // total honest.
            //
            // NOTE: "Digital" here is a DISPLAY-ONLY pseudo-bucket, not a real
            // `accounts` row. It aggregates every row with no owning account so the
            // Home total stays complete, but it is deliberately absent from the
            // Accounts screen (which lists real accounts only). Each *named* account's
            // balance agrees exactly between Home and the Accounts screen.
            val unmatched = live.filter { it.accountId == null }
            if (unmatched.isNotEmpty()) {
                val digital = unmatched.sumOf { signedPaise(it) }
                byAccount["Digital"] = (byAccount["Digital"] ?: 0L) + digital
            }
            byAccount
        }

    private fun isCreditType(type: String): Boolean =
        type.uppercase() == TransactionType.CREDIT.name

    /**
     * Single-scan snapshot of the current month for the Home dashboard.
     *
     * One `observeFiltered` read over the month derives the summary (income/expense),
     * the lifestyle/investment metrics, and the debit-by-category spend list. This
     * replaces the three overlapping queries (`monthlySummary` + `cashflowMetrics` +
     * `categorySpend`) that previously each re-scanned the same rows.
     */
    suspend fun homeCashflowSnapshot(now: Long = System.currentTimeMillis()): HomeCashflowSnapshot {
        val (from, to) = monthBounds(now)
        val cats = categoryDao.getAll().associateBy { it.id }
        val investmentIds = cats.values
            .filter { it.name.equals("Investment", true) }
            .map { it.id }
            .toSet()
        val rows = txnDao.observeFiltered("", null, null, null, from, to, null)
            .first()
            .filter { !isExcludedFromCashflowKind(it.kind) }

        val income = rows.filter { isCreditType(it.type) }.sumOf { it.amountPaise }
        val expense = rows.filter { !isCreditType(it.type) }.sumOf { it.amountPaise }
        val lifestyle = rows.filter {
            !isCreditType(it.type) &&
                (it.categoryId == null || it.categoryId !in investmentIds)
        }
        val credits = rows.filter {
            isCreditType(it.type) &&
                (it.categoryId == null || it.categoryId !in investmentIds)
        }
        val investDebits = rows.filter {
            !isCreditType(it.type) &&
                it.categoryId != null &&
                it.categoryId in investmentIds
        }
        val investCredits = rows.filter {
            isCreditType(it.type) &&
                it.categoryId != null &&
                it.categoryId in investmentIds
        }
        val lifestyleByCat = lifestyle
            .groupBy { it.categoryId }
            .map { (catId, items) ->
                CategorySpend(
                    categoryId = catId,
                    categoryName = catId?.let { cats[it]?.name } ?: "Uncategorized",
                    totalPaise = items.sumOf { it.amountPaise },
                )
            }
            .sortedByDescending { it.totalPaise }
        val debitByCat = rows
            .filter { !isCreditType(it.type) }
            .groupBy { it.categoryId }
            .map { (catId, items) ->
                CategorySpend(
                    categoryId = catId,
                    categoryName = catId?.let { cats[it]?.name } ?: "Uncategorized",
                    totalPaise = items.sumOf { it.amountPaise },
                )
            }
            .sortedByDescending { it.totalPaise }
        val investByName = (investDebits + investCredits)
            .groupBy { it.counterparty?.trim().orEmpty().ifBlank { "Unnamed" } }
            .map { (name, items) ->
                NamedAmount(
                    name = name,
                    debitPaise = items.filter { !isCreditType(it.type) }.sumOf { it.amountPaise },
                    creditPaise = items.filter { isCreditType(it.type) }.sumOf { it.amountPaise },
                )
            }
            .sortedByDescending { kotlin.math.abs(it.netPaise) }
        val trend = computeMonthlyTrend(now)
        return HomeCashflowSnapshot(
            summary = MonthlySummary(incomePaise = income, expensePaise = expense),
            metrics = CashflowMetrics(
                lifestyleSpendPaise = lifestyle.sumOf { it.amountPaise },
                creditPaise = credits.sumOf { it.amountPaise },
                investedPaise = investDebits.sumOf { it.amountPaise },
                redeemedPaise = investCredits.sumOf { it.amountPaise },
                lifestyleByCategory = lifestyleByCat,
                investmentByName = investByName,
            ),
            categorySpend = debitByCat,
            monthlyTrend = trend,
        )
    }

    private suspend fun computeMonthlyTrend(now: Long, months: Int = 6): List<MonthlyTrend> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -(months - 1))
        }
        val rows = txnDao.monthlyTrend(cal.timeInMillis, now)
        val byMonth = rows.associateBy { it.monthKey }
        return (0 until months).map { offset ->
            val month = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                add(Calendar.MONTH, offset)
            }
            val key = "%04d-%02d".format(
                month.get(Calendar.YEAR),
                month.get(Calendar.MONTH) + 1,
            )
            val row = byMonth[key]
            MonthlyTrend(key, row?.incomePaise ?: 0L, row?.expensePaise ?: 0L)
        }
    }

    /** Signed amount for balance math: credits +, debits −. */
    private fun signedPaise(t: TransactionEntity): Long =
        if (isCreditType(t.type)) t.amountPaise else -t.amountPaise

    companion object {
        fun monthBounds(now: Long): Pair<Long, Long> {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val from = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            cal.add(Calendar.MILLISECOND, -1)
            return from to cal.timeInMillis
        }
    }
}

data class HomeCashflowSnapshot(
    val summary: MonthlySummary,
    val metrics: CashflowMetrics,
    val categorySpend: List<CategorySpend>,
    val monthlyTrend: List<MonthlyTrend>,
)
