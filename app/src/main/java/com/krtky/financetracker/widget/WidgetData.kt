package com.krtky.financetracker.widget

import android.content.Context
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.FundBalance
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
}

internal fun widgetRepository(context: Context): TransactionRepository? = try {
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetEntryPoint::class.java,
    ).transactionRepository()
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
        ?: txn.merchant?.takeIf { it.isNotBlank() }
        ?: txn.paymentMethod?.takeIf { it.isNotBlank() }
        ?: "Transaction"

internal fun monthLabel(now: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(now))

internal fun shortDate(ts: Long): String =
    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ts))

internal fun pctChange(current: Long, previous: Long): String {
    if (previous == 0L && current == 0L) return ""
    if (previous == 0L) return "↑100%"
    val pct = ((current - previous).toFloat() / previous.toFloat() * 100f)
    val arrow = if (pct >= 0) "↑" else "↓"
    return "$arrow${abs(pct).toInt()}%"
}

data class OverviewSnapshot(
    val balance: String,
    val income: String,
    val expense: String,
    val incomePct: String,
    val expensePct: String,
    val lastIncome: String,
    val lastExpense: String,
    val monthSubtitle: String,
)

data class TxnRow(
    val name: String,
    val amount: String,
    val isExpense: Boolean,
    val date: String,
)

data class FundRow(
    val name: String,
    val remaining: String,
    val limit: String,
    val ratio: Float,
    val overspent: Boolean,
)

data class SpendRow(
    val name: String,
    val amount: String,
    val ratio: Float,
)

data class WidgetSnapshots(
    val overview: OverviewSnapshot,
    val transactions: List<TxnRow>,
    val funds: List<FundRow>,
    val spending: List<SpendRow>,
)

internal object WidgetDataLoader {

    suspend fun load(context: Context): WidgetSnapshots {
        val empty = emptySnapshots()
        val repo = widgetRepository(context) ?: return empty
        return try {
            val summary = repo.monthlySummary()
            val trend = repo.monthlyTrend()
            val txns = repo.observeTransactions().first().take(5)
            val funds = repo.observeFunds().first().take(4)
            val categories = repo.categorySpend().take(4)
            WidgetSnapshots(
                overview = buildOverview(summary, trend),
                transactions = txns.map { toTxnRow(it) },
                funds = funds.map { toFundRow(it) },
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

    suspend fun loadFunds(context: Context): List<FundRow> =
        load(context).funds

    suspend fun loadSpending(context: Context): List<SpendRow> =
        load(context).spending

    private fun emptySnapshots() = WidgetSnapshots(
        overview = OverviewSnapshot(
            balance = "₹0",
            income = "₹0",
            expense = "₹0",
            incomePct = "",
            expensePct = "",
            lastIncome = "",
            lastExpense = "",
            monthSubtitle = "Income − expenses · ${monthLabel()}",
        ),
        transactions = emptyList(),
        funds = emptyList(),
        spending = emptyList(),
    )

    private fun buildOverview(
        summary: MonthlySummary,
        monthlyTrend: List<MonthlyTrend>,
    ): OverviewSnapshot {
        val prev = monthlyTrend.getOrNull(monthlyTrend.lastIndex - 1)
        val incomePct = prev?.let { pctChange(summary.incomePaise, it.incomePaise) }.orEmpty()
        val expensePct = prev?.let { pctChange(summary.expensePaise, it.expensePaise) }.orEmpty()
        return OverviewSnapshot(
            balance = formatWidgetMoney(summary.netPaise),
            income = formatWidgetMoney(summary.incomePaise),
            expense = formatWidgetMoney(summary.expensePaise),
            incomePct = incomePct,
            expensePct = expensePct,
            lastIncome = prev?.let { formatWidgetMoney(it.incomePaise) }.orEmpty(),
            lastExpense = prev?.let { formatWidgetMoney(it.expensePaise) }.orEmpty(),
            monthSubtitle = "Income − expenses · ${monthLabel()}",
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

    private fun toFundRow(fb: FundBalance): FundRow = FundRow(
        name = fb.fund.name.take(22),
        remaining = formatWidgetMoney(fb.balancePaise),
        limit = formatWidgetMoney(fb.limitPaise()),
        ratio = fb.remainingRatio(),
        overspent = fb.isOverspent(),
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
