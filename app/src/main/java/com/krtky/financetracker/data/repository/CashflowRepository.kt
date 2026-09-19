package com.krtky.financetracker.data.repository

import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.TransactionEntity
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.CategoryNetSpend
import com.krtky.financetracker.domain.model.MonthlySummary
import com.krtky.financetracker.domain.model.SourceSpend
import com.krtky.financetracker.domain.model.SourceNetSpend
import com.krtky.financetracker.domain.model.MonthlyTrend
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.domain.model.isExcludedFromCashflow
import com.krtky.financetracker.domain.model.isTabOnlyBookkeeping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    /**
     * Net balance per account label (credits − debits + opening), matching the
     * Accounts-screen formula in [AccountRepository].
     * Unmatched cashflow rows group under "Digital"; tab-only bookkeeping is excluded.
     */
    fun observeAccountBalances(): Flow<Map<String, Long>> =
        combine(
            accountDao.observeAll(),
            txnDao.observeAll(),
        ) { accounts, txns ->
            val live = txns.filter { it.deletedAt == null }
            val byAccount = accounts.associate { acc ->
                val mine = live.filter {
                    it.accountId == acc.id && !isTabOnlyBookkeeping(it.kind)
                }
                val net = mine.sumOf { signedPaise(it) }
                acc.name.trim() to (acc.openingBalancePaise + net)
            }.toMutableMap()

            // Rows with no owning account (e.g. unmatched SMS) — keep Home's total honest.
            //
            // NOTE: "Digital" here is a DISPLAY-ONLY pseudo-bucket, not a real
            // `accounts` row. Tab-only bookkeeping (TAB_TRANSFER) is excluded so
            // openings / tab↔tab moves never inflate available balance.
            // Each *named* account's balance agrees with the Accounts screen.
            val unmatched = live.filter {
                it.accountId == null && !isTabOnlyBookkeeping(it.kind)
            }
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
     * Debits and credits include every category (Investment is a normal category).
     * Only self-transfer and tab-transfer rows are excluded — those are linked
     * account moves / tab bookkeeping, not spend.
     */
    suspend fun homeCashflowSnapshot(now: Long = System.currentTimeMillis()): HomeCashflowSnapshot {
        val (from, to) = monthBounds(now)
        val cats = categoryDao.getAll().associateBy { it.id }
        val accounts = accountDao.getAll().associateBy { it.id }
        val rows = txnDao.observeFiltered("", null, null, null, from, to, null)
            .first()
            .filter { !isExcludedFromCashflow(it.kind) }

        val debitRows = rows.filter { !isCreditType(it.type) }
        val creditRows = rows.filter { isCreditType(it.type) }
        val income = creditRows.sumOf { it.amountPaise }
        val expense = debitRows.sumOf { it.amountPaise }
        fun categoryName(id: Long?) = id?.let { cats[it]?.name } ?: "Uncategorized"
        fun sourceName(id: Long?) = id?.let { accounts[it]?.name?.trim() }?.takeIf { it.isNotEmpty() } ?: "Digital"
        fun byCategory(items: List<TransactionEntity>) = items
            .groupBy { it.categoryId }
            .map { (catId, group) ->
                CategorySpend(
                    categoryId = catId,
                    categoryName = categoryName(catId),
                    totalPaise = group.sumOf { it.amountPaise },
                    color = catId?.let { cats[it]?.color },
                )
            }
            .sortedByDescending { it.totalPaise }
        fun bySource(items: List<TransactionEntity>) = items
            .groupBy { it.accountId }
            .map { (accountId, group) ->
                SourceSpend(
                    accountId = accountId,
                    accountName = sourceName(accountId),
                    totalPaise = group.sumOf { it.amountPaise },
                )
            }
            .sortedByDescending { it.totalPaise }
        val debitByCat = byCategory(debitRows)
        val trend = computeMonthlyTrend(now)

        val netByCat = rows
            .groupBy { it.categoryId }
            .map { (catId, group) ->
                val deb = group.filter { !isCreditType(it.type) }.sumOf { it.amountPaise }
                val cred = group.filter { isCreditType(it.type) }.sumOf { it.amountPaise }
                CategoryNetSpend(
                    categoryId = catId,
                    categoryName = categoryName(catId),
                    debitPaise = deb,
                    creditPaise = cred,
                    netPaise = cred - deb,
                    color = catId?.let { cats[it]?.color },
                )
            }
            .sortedWith(
                compareBy<CategoryNetSpend> { it.netPaise >= 0 }
                    .thenBy { if (it.netPaise < 0) it.netPaise else -it.netPaise }
            )

        val netBySource = accounts.values.map { acc ->
            val group = rows.filter { it.accountId == acc.id }
            val deb = group.filter { !isCreditType(it.type) }.sumOf { it.amountPaise }
            val cred = group.filter { isCreditType(it.type) }.sumOf { it.amountPaise }
            SourceNetSpend(
                accountId = acc.id,
                accountName = acc.name.trim(),
                debitPaise = deb,
                creditPaise = cred,
                netPaise = cred - deb,
            )
        }.toMutableList()

        val unassignedDigital = rows.filter { it.accountId == null }
        if (unassignedDigital.isNotEmpty()) {
            val deb = unassignedDigital.filter { !isCreditType(it.type) }.sumOf { it.amountPaise }
            val cred = unassignedDigital.filter { isCreditType(it.type) }.sumOf { it.amountPaise }
            netBySource.add(
                SourceNetSpend(
                    accountId = null,
                    accountName = "Digital",
                    debitPaise = deb,
                    creditPaise = cred,
                    netPaise = cred - deb,
                )
            )
        }
        netBySource.sortBy { it.accountName.lowercase() }

        return HomeCashflowSnapshot(
            summary = MonthlySummary(incomePaise = income, expensePaise = expense),
            categorySpend = debitByCat,
            monthlyTrend = trend,
            expenseBySource = bySource(debitRows),
            incomeByCategory = byCategory(creditRows),
            incomeBySource = bySource(creditRows),
            categoryNetSpend = netByCat,
            sourceNetSpend = netBySource,
        )
    }

    private suspend fun computeMonthlyTrend(now: Long, months: Int = 6): List<MonthlyTrend> {
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -(months - 1))
        }
        val cats = categoryDao.getAll().associateBy { it.id }
        val rows = txnDao.observeFiltered("", null, null, null, start.timeInMillis, now, null)
            .first()
            .filter { !isExcludedFromCashflow(it.kind) }

        data class Bucket(
            var credits: Long = 0L,
            var debits: Long = 0L,
            val byCat: MutableMap<Long?, Long> = mutableMapOf(),
        )
        val byMonth = mutableMapOf<String, Bucket>()
        for (row in rows) {
            val key = monthKey(row.occurredAt)
            val bucket = byMonth.getOrPut(key) { Bucket() }
            if (isCreditType(row.type)) {
                bucket.credits += row.amountPaise
            } else {
                bucket.debits += row.amountPaise
                bucket.byCat[row.categoryId] = (bucket.byCat[row.categoryId] ?: 0L) + row.amountPaise
            }
        }
        return (0 until months).map { offset ->
            val month = Calendar.getInstance().apply {
                timeInMillis = start.timeInMillis
                add(Calendar.MONTH, offset)
            }
            val key = "%04d-%02d".format(
                month.get(Calendar.YEAR),
                month.get(Calendar.MONTH) + 1,
            )
            val bucket = byMonth[key]
            val top = bucket?.byCat?.maxByOrNull { it.value }
            MonthlyTrend(
                monthKey = key,
                incomePaise = bucket?.credits ?: 0L,
                expensePaise = bucket?.debits ?: 0L,
                topCategoryId = top?.key,
                topCategoryName = top?.key?.let { cats[it]?.name } ?: top?.let { "Uncategorized" },
                topCategoryPaise = top?.value ?: 0L,
            )
        }
    }

    private fun monthKey(ts: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        return "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
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
    val categorySpend: List<CategorySpend>,
    val monthlyTrend: List<MonthlyTrend>,
    val expenseBySource: List<SourceSpend> = emptyList(),
    val incomeByCategory: List<CategorySpend> = emptyList(),
    val incomeBySource: List<SourceSpend> = emptyList(),
    val categoryNetSpend: List<CategoryNetSpend> = emptyList(),
    val sourceNetSpend: List<SourceNetSpend> = emptyList(),
)
