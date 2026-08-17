package com.krtky.financetracker.widget

import android.content.Context
import com.krtky.financetracker.data.repository.CashflowRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.TabBalance
import com.krtky.financetracker.domain.model.MonthlySummary
import com.krtky.financetracker.domain.model.MonthlyTrend
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun transactionRepository(): TransactionRepository
    fun cashflowRepository(): CashflowRepository
}

internal data class WidgetRepositories(
    val transactions: TransactionRepository,
    val cashflow: CashflowRepository,
)

internal fun widgetRepositories(context: Context): WidgetRepositories? = try {
    val ep = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetEntryPoint::class.java,
    )
    WidgetRepositories(ep.transactionRepository(), ep.cashflowRepository())
} catch (_: Exception) {
    null
}

/** Compact INR for tight widget cells (no paise). */
internal fun formatWidgetMoney(paise: Long): String {
    val sign = if (paise < 0) "−" else ""
    val rupees = abs(paise) / 100
    return "$sign₹%,d".format(Locale.getDefault(), rupees)
}

internal fun txnDisplayName(txn: Transaction): String =
    txn.counterparty?.takeIf { it.isNotBlank() }
        ?: txn.accountName?.takeIf { it.isNotBlank() }
        ?: "Transaction"

internal fun monthLabel(now: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(now))

internal fun shortDate(ts: Long): String =
    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ts))

/**
 * MoM percent matching home [com.krtky.financetracker.ui.screens.HomeScreen] `pctChange`.
 * Returns null only when both current and previous are zero (still shows 0% on hero — we return 0f).
 */
internal fun pctChangeValue(current: Long, previous: Long): Float {
    if (previous == 0L && current == 0L) return 0f
    if (previous == 0L) return 100f
    return ((current - previous).toFloat() / previous.toFloat()) * 100f
}

/** Compact arrow label like hero: ↑12% / ↓8%. */
internal fun pctChangeLabel(pct: Float): String {
    val isUp = pct >= 0f
    return "${if (isUp) "↑" else "↓"}${abs(pct).toInt()}%"
}

/** Legacy string form used by any other callers. */
internal fun pctChange(current: Long, previous: Long): String {
    if (previous == 0L && current == 0L) return ""
    return pctChangeLabel(pctChangeValue(current, previous))
}

/** Full INR (with paise) — same as home hero via [com.krtky.financetracker.domain.model.Money]. */
internal fun formatHeroMoney(paise: Long): String =
    com.krtky.financetracker.domain.model.Money(paise).formatInr()

data class OverviewSnapshot(
    val balance: String,
    val income: String,
    val expense: String,
    /** e.g. "↑100%" — empty if no previous month to compare. */
    val incomePct: String,
    val expensePct: String,
    val incomeIsUp: Boolean,
    val expenseIsUp: Boolean,
    /** Green when the change is favorable (income up / expense down). */
    val incomeChangeGood: Boolean,
    val expenseChangeGood: Boolean,
    /** Full caption: "Compared to ₹X last month" or empty. */
    val incomeCompared: String,
    val expenseCompared: String,
)

data class TxnRow(
    val name: String,
    val amount: String,
    val isExpense: Boolean,
    val date: String,
)

data class TabRow(
    val name: String,
    /** Open balance: + they owe you, − you owe them. */
    val balance: String,
    val owedToMe: Boolean,
    val settled: Boolean,
)

data class SpendRow(
    val name: String,
    val amount: String,
    val ratio: Float,
)

data class WidgetSnapshots(
    val overview: OverviewSnapshot,
    val transactions: List<TxnRow>,
    val tabs: List<TabRow>,
    val spending: List<SpendRow>,
)

internal object WidgetDataLoader {

    suspend fun load(context: Context): WidgetSnapshots {
        val empty = emptySnapshots()
        val repos = widgetRepositories(context) ?: return empty
        return try {
            val repo = repos.transactions
            val cashflow = repos.cashflow
            val snapshot = cashflow.homeCashflowSnapshot()
            val summary = snapshot.summary
            val trend = snapshot.monthlyTrend
            val txns = repo.observeTransactions().first().take(5)
            val tabs = repo.observeTabs().first().take(4)
            val categories = snapshot.categorySpend.take(4)
            WidgetSnapshots(
                overview = buildOverview(summary, trend),
                transactions = txns.map { toTxnRow(it) },
                tabs = tabs.map { toTabRow(it) },
                spending = buildSpending(categories),
            )
        } catch (_: Exception) {
            empty
        }
    }

    suspend fun loadOverview(context: Context): OverviewSnapshot =
        load(context).overview

    suspend fun loadTransactions(context: Context): List<TxnRow> =
        load(context).transactions

    suspend fun loadTabs(context: Context): List<TabRow> =
        load(context).tabs

    suspend fun loadSpending(context: Context): List<SpendRow> =
        load(context).spending

    private fun emptySnapshots() = WidgetSnapshots(
        overview = OverviewSnapshot(
            balance = formatHeroMoney(0L),
            income = formatHeroMoney(0L),
            expense = formatHeroMoney(0L),
            incomePct = "",
            expensePct = "",
            incomeIsUp = true,
            expenseIsUp = true,
            incomeChangeGood = true,
            expenseChangeGood = true,
            incomeCompared = "",
            expenseCompared = "",
        ),
        transactions = emptyList(),
        tabs = emptyList(),
        spending = emptyList(),
    )

    private fun buildOverview(
        summary: MonthlySummary,
        monthlyTrend: List<MonthlyTrend>,
    ): OverviewSnapshot {
        val prev = monthlyTrend.getOrNull(monthlyTrend.lastIndex - 1)
        val incomePctVal = prev?.let { pctChangeValue(summary.incomePaise, it.incomePaise) }
        val expensePctVal = prev?.let { pctChangeValue(summary.expensePaise, it.expensePaise) }
        val incomeIsUp = (incomePctVal ?: 0f) >= 0f
        val expenseIsUp = (expensePctVal ?: 0f) >= 0f
        val lastInc = prev?.let { formatHeroMoney(it.incomePaise) }
        val lastExp = prev?.let { formatHeroMoney(it.expensePaise) }
        return OverviewSnapshot(
            balance = formatHeroMoney(summary.netPaise),
            income = formatHeroMoney(summary.incomePaise),
            expense = formatHeroMoney(summary.expensePaise),
            incomePct = incomePctVal?.let { pctChangeLabel(it) }.orEmpty(),
            expensePct = expensePctVal?.let { pctChangeLabel(it) }.orEmpty(),
            incomeIsUp = incomeIsUp,
            expenseIsUp = expenseIsUp,
            // Income: up is good; expense: up is bad
            incomeChangeGood = incomeIsUp,
            expenseChangeGood = !expenseIsUp,
            incomeCompared = lastInc?.let { "Compared to $it last month" }.orEmpty(),
            expenseCompared = lastExp?.let { "Compared to $it last month" }.orEmpty(),
        )
    }

    private fun toTxnRow(txn: Transaction): TxnRow {
        val isExpense = txn.type == TransactionType.DEBIT
        val sign = if (isExpense) "−" else "+"
        return TxnRow(
            name = txnDisplayName(txn).take(28),
            amount = "$sign${formatWidgetMoney(txn.amountPaise).removePrefix("−")}",
            isExpense = isExpense,
            date = shortDate(txn.occurredAt),
        )
    }

    private fun toTabRow(fb: TabBalance): TabRow = TabRow(
        name = fb.tab.name.take(22),
        balance = formatWidgetMoney(fb.balancePaise),
        owedToMe = fb.theyOweYou(),
        settled = fb.isSettled(),
    )

    private fun buildSpending(categories: List<CategorySpend>): List<SpendRow> {
        val max = categories.maxOfOrNull { it.totalPaise }?.coerceAtLeast(1L) ?: 1L
        return categories.map {
            SpendRow(
                name = it.categoryName.take(22),
                amount = formatWidgetMoney(it.totalPaise),
                ratio = (it.totalPaise.toFloat() / max.toFloat()).coerceIn(0.08f, 1f),
            )
        }
    }
}
