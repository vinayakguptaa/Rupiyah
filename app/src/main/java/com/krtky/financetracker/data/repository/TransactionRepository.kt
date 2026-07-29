package com.krtky.financetracker.data.repository

import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.FundLedgerEntity
import com.krtky.financetracker.data.local.db.PendingClassificationEntity
import com.krtky.financetracker.data.local.db.SyncOutboxEntity
import com.krtky.financetracker.data.local.db.toDomain
import com.krtky.financetracker.data.local.db.toEntity
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.FundEntryType
import com.krtky.financetracker.domain.model.MonthlySummary
import com.krtky.financetracker.domain.model.MonthlyTrend
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val db: AppDatabase,
) {
    private val txnDao = db.transactionDao()
    private val categoryDao = db.categoryDao()
    private val fundDao = db.fundDao()
    private val ledgerDao = db.fundLedgerDao()
    private val pendingDao = db.pendingClassificationDao()
    private val outboxDao = db.syncOutboxDao()

    fun observeTransactions(): Flow<List<Transaction>> =
        combine(
            txnDao.observeAll(),
            categoryDao.observeAll(),
            fundDao.observeActive(),
        ) { txns, cats, funds ->
            val catMap = cats.associate { it.id to it.toDomain() }
            val fundMap = funds.associate { it.id to it.name }
            txns.map { it.toDomain(catMap[it.categoryId], fundMap[it.fundId]) }
        }

    /** Count of transactions awaiting category assignment. */
    fun observePendingClassificationCount(): Flow<Int> =
        txnDao.observeAll().map { list ->
            list.count { it.classificationStatus == ClassificationStatus.PENDING.name }
        }

    /** Oldest pending txn id for the Home classify chip. */
    fun observeFirstPendingClassificationId(): Flow<String?> =
        txnDao.observeAll().map { list ->
            list
                .filter { it.classificationStatus == ClassificationStatus.PENDING.name }
                .minByOrNull { it.occurredAt }
                ?.id
        }

    fun observeFiltered(
        query: String,
        type: TransactionType?,
        categoryId: Long?,
        fundId: Long?,
        fromTs: Long,
        toTs: Long,
    ): Flow<List<Transaction>> =
        combine(
            txnDao.observeFiltered(query, type?.name, categoryId, fundId, fromTs, toTs),
            categoryDao.observeAll(),
            fundDao.observeActive(),
        ) { txns, cats, funds ->
            val catMap = cats.associate { it.id to it.toDomain() }
            val fundMap = funds.associate { it.id to it.name }
            txns.map { it.toDomain(catMap[it.categoryId], fundMap[it.fundId]) }
        }

    suspend fun getById(id: String): Transaction? {
        val e = txnDao.getById(id) ?: return null
        val cat = e.categoryId?.let { categoryDao.getById(it)?.toDomain() }
        val fund = e.fundId?.let { fundDao.getById(it)?.name }
        return e.toDomain(cat, fund)
    }

    suspend fun insertManual(txn: Transaction, addToFund: Boolean = false): String {
        val id = txn.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val hash = contentHash(txn.type, txn.amountPaise, txn.occurredAt, txn.merchant, txn.externalRefId, "manual-$id")
        val entity = txn.copy(
            id = id,
            contentHash = hash,
            updatedAt = now,
            sheetsSynced = false,
            classificationStatus = if (txn.categoryId != null) ClassificationStatus.CLASSIFIED else ClassificationStatus.PENDING,
        ).toEntity()
        val rowId = txnDao.insert(entity)
        if (rowId == -1L) return id
        handleFundOnInsert(entity.id, entity.fundId, entity.type, entity.amountPaise, addToFund, entity.note)
        if (entity.classificationStatus == ClassificationStatus.PENDING.name) {
            scheduleClassification(entity.id)
        }
        enqueueSync(entity.id)
        return id
    }

    suspend fun insertFromEmail(txn: Transaction): String? {
        val hash = txn.contentHash ?: contentHash(
            txn.type, txn.amountPaise, txn.occurredAt, txn.merchant, txn.externalRefId, txn.emailMessageId
        )
        if (txn.emailMessageId != null && txnDao.findByEmailMessageId(txn.emailMessageId) != null) return null
        val duplicate = txn.externalRefId?.takeIf { it.isNotBlank() }?.let { txnDao.findByExternalRefId(it) }
            ?: txnDao.findSimilar(
                type = txn.type.name,
                amountPaise = txn.amountPaise,
                fromTs = txn.occurredAt - 10 * 60_000L,
                toTs = txn.occurredAt + 10 * 60_000L,
                targetTs = txn.occurredAt,
            )
        if (duplicate != null) {
            // Email is the richer source, so it replaces an earlier SMS record.
            if (duplicate.source == TransactionSource.SMS.name && txn.source == TransactionSource.EMAIL) {
                txnDao.update(
                    duplicate.copy(
                        source = TransactionSource.EMAIL.name,
                        emailMessageId = txn.emailMessageId ?: duplicate.emailMessageId,
                        externalRefId = txn.externalRefId ?: duplicate.externalRefId,
                        merchant = txn.merchant ?: duplicate.merchant,
                        counterparty = txn.counterparty ?: duplicate.counterparty,
                        updatedAt = System.currentTimeMillis(),
                        sheetsSynced = false,
                    )
                )
            }
            return null
        }
        if (txnDao.findByContentHash(hash) != null) return null
        val id = txn.id.ifBlank { UUID.randomUUID().toString() }
        val entity = txn.copy(id = id, contentHash = hash, sheetsSynced = false).toEntity()
        val rowId = txnDao.insert(entity)
        if (rowId == -1L) return null
        scheduleClassification(id)
        enqueueSync(id)
        return id
    }

    suspend fun update(txn: Transaction) {
        val existing = txnDao.getById(txn.id) ?: return
        val oldFund = existing.fundId
        val updated = txn.copy(
            updatedAt = System.currentTimeMillis(),
            version = existing.version + 1,
            sheetsSynced = false,
            classificationStatus = if (txn.categoryId != null) ClassificationStatus.CLASSIFIED else txn.classificationStatus,
        )
        txnDao.update(updated.toEntity())
        if (oldFund != updated.fundId || existing.amountPaise != updated.amountPaise || existing.type != updated.type.name) {
            // Rebuild fund pots from amount + linked transactions
            if (oldFund != null) recalculateFundLedger(oldFund)
            if (updated.fundId != null && updated.fundId != oldFund) {
                recalculateFundLedger(updated.fundId)
            }
        }
        pendingDao.delete(txn.id)
        enqueueSync(txn.id)
    }

    suspend fun classify(
        transactionId: String,
        categoryId: Long?,
        note: String?,
        fundId: Long?,
        receiptUri: String? = null,
    ) {
        val existing = txnDao.getById(transactionId) ?: return
        val mergedNote = when {
            note.isNullOrBlank() -> existing.note
            existing.note.isNullOrBlank() -> note
            existing.note == note -> existing.note
            else -> "${existing.note} · $note"
        }
        val newFund = fundId ?: existing.fundId
        val newCat = categoryId ?: existing.categoryId
        val status = if (newCat != null || !mergedNote.isNullOrBlank() || newFund != null) {
            if (newCat != null) ClassificationStatus.CLASSIFIED.name else existing.classificationStatus
        } else existing.classificationStatus
        val updated = existing.copy(
            categoryId = newCat,
            note = mergedNote,
            fundId = newFund,
            classificationStatus = status,
            updatedAt = System.currentTimeMillis(),
            version = existing.version + 1,
            sheetsSynced = false,
            receiptUri = receiptUri ?: existing.receiptUri,
        )
        txnDao.update(updated)
        if (existing.fundId != updated.fundId) {
            // Rebuild both sides so old fund loses the spend and new fund gains it cleanly
            if (existing.fundId != null) recalculateFundLedger(existing.fundId)
            if (updated.fundId != null) recalculateFundLedger(updated.fundId)
        }
        if (newCat != null) pendingDao.delete(transactionId)
        enqueueSync(transactionId)
    }

    suspend fun delete(id: String) {
        val existing = txnDao.getById(id)
        ledgerDao.deleteForTransaction(id)
        txnDao.softDelete(id)
        existing?.fundId?.let { recalculateFundLedger(it) }
        pendingDao.delete(id)
        enqueueSync(id)
    }

    suspend fun monthlySummary(now: Long = System.currentTimeMillis()): MonthlySummary {
        val (from, to) = monthBounds(now)
        return MonthlySummary(
            incomePaise = txnDao.sumByType(TransactionType.INCOME.name, from, to),
            expensePaise = txnDao.sumByType(TransactionType.EXPENSE.name, from, to),
        )
    }

    suspend fun categorySpend(now: Long = System.currentTimeMillis()): List<CategorySpend> {
        val (from, to) = monthBounds(now)
        return txnDao.categorySpend(from, to).map {
            CategorySpend(it.categoryId, it.categoryName, it.totalPaise)
        }
    }

    suspend fun monthlyTrend(months: Int = 6, now: Long = System.currentTimeMillis()): List<MonthlyTrend> {
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

    /**
     * Fund math (only two inputs):
     *   amount you set  +  income txns  −  expense txns
     * Ledger is kept for history but is NOT the display source of truth.
     */
    fun observeFunds(): Flow<List<FundBalance>> = combine(
        fundDao.observeActive(),
        txnDao.observeAll(),
    ) { funds, allTxns ->
        funds.map { fund ->
            val fundTxns = allTxns.filter { it.fundId == fund.id && it.deletedAt == null }
            val income = fundTxns
                .filter { it.type == TransactionType.INCOME.name }
                .sumOf { it.amountPaise }
            val expense = fundTxns
                .filter { it.type == TransactionType.EXPENSE.name }
                .sumOf { it.amountPaise }
            val amount = fund.budgetPaise.coerceAtLeast(0L)
            // remaining = set amount + money in − money out
            val balance = amount + income - expense
            FundBalance(
                fund = fund.toDomain(),
                balancePaise = balance,
                creditedPaise = income,
                debitedPaise = expense,
                openingPaise = amount,
            )
        }
    }

    suspend fun getRecommendedFundForCategory(categoryId: Long): Long? {
        val txns = txnDao.getAllForCategory(categoryId)
            .filter { it.fundId != null && it.deletedAt == null }
        if (txns.isEmpty()) return null
        return txns.groupBy { it.fundId }
            .maxByOrNull { (_, items) -> items.size }
            ?.key
    }

    /** categoryId -> use count (most used first from DAO). */
    fun observeCategoryUsage(): Flow<Map<Long, Long>> =
        txnDao.observeCategoryUsage().map { rows -> rows.associate { it.id to it.useCount } }

    /** paymentMethod label -> use count. */
    fun observePaymentMethodUsage(): Flow<Map<String, Long>> =
        txnDao.observePaymentMethodUsage().map { rows ->
            rows.associate { it.id to it.useCount }
        }

    /**
     * Net balance per payment method / account (income − expense).
     * Cash is included; soft-deleted rows are ignored by [observeTransactions].
     */
    fun observeAccountBalances(): Flow<Map<String, Long>> =
        observeTransactions().map { txns ->
            txns.groupBy { t ->
                when {
                    t.isCash || t.paymentMethod.equals("Cash", true) -> "Cash"
                    t.paymentMethod.isNullOrBlank() -> "Digital"
                    t.paymentMethod.equals("UPI", true) -> "Digital"
                    else -> t.paymentMethod.trim()
                }
            }.mapValues { (_, items) ->
                items.sumOf { t ->
                    if (t.type == TransactionType.INCOME) t.amountPaise else -t.amountPaise
                }
            }
        }

    suspend fun addFund(name: String, budgetPaise: Long = 0L): Long {
        val amount = budgetPaise.coerceAtLeast(0L)
        val id = fundDao.upsert(
            com.krtky.financetracker.data.local.db.FundEntity(
                name = name.trim(),
                budgetPaise = amount,
            ),
        )
        // Keep ledger aligned (history); display uses budget + transactions only
        recalculateFundLedger(id)
        return id
    }

    /** Absolute fund amount / limit. Restarts the baseline; txns still apply on top. */
    suspend fun setFundBudget(fundId: Long, budgetPaise: Long) {
        val fund = fundDao.getById(fundId) ?: return
        val amount = budgetPaise.coerceAtLeast(0L)
        fundDao.update(fund.copy(budgetPaise = amount))
        recalculateFundLedger(fundId)
    }

    /** Rebuild every active fund ledger from budget + linked transactions. */
    suspend fun repairAllFundLedgers() {
        fundDao.getAll().filter { !it.archived }.forEach { f ->
            // If amount was never stored, try to infer from old opening adjustment once
            if (f.budgetPaise <= 0L) {
                val manuals = ledgerDao.getForFund(f.id)
                    .filter { it.transactionId == null && it.amountPaise > 0 }
                    .minByOrNull { it.id }
                if (manuals != null) {
                    fundDao.update(f.copy(budgetPaise = manuals.amountPaise))
                }
            }
            recalculateFundLedger(f.id)
        }
    }

    suspend fun deleteFund(fundId: Long) {
        val fund = fundDao.getById(fundId) ?: return
        fundDao.update(fund.copy(archived = true))
    }

    /**
     * @deprecated Prefer [setFundBudget] (absolute amount). Kept as absolute set so
     * old "adjust" UI that passes a full rupee amount resets the baseline correctly.
     */
    suspend fun adjustFund(fundId: Long, amountPaise: Long, note: String?) {
        // Treat as SET amount (not delta) — matches "edit fund and set amount"
        setFundBudget(fundId, amountPaise.coerceAtLeast(0L))
    }

    suspend fun transferBetweenFunds(
        fromFundId: Long,
        toFundId: Long,
        amountPaise: Long,
        note: String? = null,
    ) {
        if (fromFundId == toFundId || amountPaise <= 0L) return
        val fromName = fundDao.getById(fromFundId)?.name ?: "source"
        val toName = fundDao.getById(toFundId)?.name ?: "target"
        val now = System.currentTimeMillis()
        val transferRef = UUID.randomUUID().toString()
        val txnOut = Transaction(
            id = "${transferRef}_out",
            type = TransactionType.EXPENSE,
            amountPaise = amountPaise,
            occurredAt = now,
            fundId = fromFundId,
            source = TransactionSource.MANUAL,
            note = note ?: "Transfer to $toName",
            externalRefId = "fund_transfer_$transferRef",
            classificationStatus = ClassificationStatus.CLASSIFIED,
        )
        val txnIn = Transaction(
            id = "${transferRef}_in",
            type = TransactionType.INCOME,
            amountPaise = amountPaise,
            occurredAt = now,
            fundId = toFundId,
            source = TransactionSource.MANUAL,
            note = note ?: "Transfer from $fromName",
            externalRefId = "fund_transfer_$transferRef",
            classificationStatus = ClassificationStatus.CLASSIFIED,
        )
        insertManual(txnOut, addToFund = true)
        insertManual(txnIn, addToFund = true)
    }

    suspend fun creditFundFromIncome(fundId: Long, transactionId: String, amountPaise: Long, note: String?) {
        val current = ledgerDao.latestBalance(fundId) ?: 0L
        val after = current + amountPaise
        ledgerDao.insert(
            FundLedgerEntity(
                fundId = fundId,
                transactionId = transactionId,
                entryType = FundEntryType.CREDIT.name,
                amountPaise = amountPaise,
                balanceAfterPaise = after,
                note = note,
            )
        )
    }

    /**
     * Ledger rebuild: single baseline (= fund amount) + every linked transaction.
     * No stacked manual adjustments — amount is the only starting number.
     */
    private suspend fun recalculateFundLedger(fundId: Long) {
        val fund = fundDao.getById(fundId) ?: return
        val baseline = fund.budgetPaise.coerceAtLeast(0L)

        ledgerDao.deleteAllForFund(fundId)

        var runningBalance = baseline
        if (baseline > 0L) {
            ledgerDao.insert(
                FundLedgerEntity(
                    fundId = fundId,
                    transactionId = null,
                    entryType = FundEntryType.ADJUSTMENT.name,
                    amountPaise = baseline,
                    balanceAfterPaise = runningBalance,
                    note = "Fund amount",
                    createdAt = fund.createdAt,
                ),
            )
        }

        val transactions = txnDao.getAllForFund(fundId)
            .sortedWith(compareBy({ it.occurredAt }, { it.id }))

        for (txn in transactions) {
            val isCredit = txn.type == TransactionType.INCOME.name
            val amount = txn.amountPaise
            runningBalance += if (isCredit) amount else -amount
            ledgerDao.insert(
                FundLedgerEntity(
                    fundId = fundId,
                    transactionId = txn.id,
                    entryType = if (isCredit) {
                        FundEntryType.CREDIT.name
                    } else {
                        FundEntryType.DEBIT.name
                    },
                    amountPaise = amount,
                    balanceAfterPaise = runningBalance,
                    note = txn.note,
                    createdAt = txn.occurredAt,
                ),
            )
        }
    }

    private suspend fun handleFundOnInsert(
        transactionId: String,
        fundId: Long?,
        type: String,
        amountPaise: Long,
        @Suppress("UNUSED_PARAMETER") addToFund: Boolean,
        note: String?,
    ) {
        if (fundId == null) return
        val current = ledgerDao.latestBalance(fundId) ?: 0L
        // Selecting a fund always moves the envelope: income credits, expense debits.
        // (Previously income required a separate addToFund flag — refunds often never landed.)
        when (type) {
            TransactionType.INCOME.name -> {
                val after = current + amountPaise
                ledgerDao.insert(
                    FundLedgerEntity(
                        fundId = fundId,
                        transactionId = transactionId,
                        entryType = FundEntryType.CREDIT.name,
                        amountPaise = amountPaise,
                        balanceAfterPaise = after,
                        note = note,
                    ),
                )
            }
            TransactionType.EXPENSE.name -> {
                val after = current - amountPaise
                ledgerDao.insert(
                    FundLedgerEntity(
                        fundId = fundId,
                        transactionId = transactionId,
                        entryType = FundEntryType.DEBIT.name,
                        amountPaise = amountPaise,
                        balanceAfterPaise = after,
                        note = note,
                    ),
                )
            }
        }
    }

    private suspend fun scheduleClassification(transactionId: String, delayMin: Long = 15) {
        pendingDao.upsert(
            PendingClassificationEntity(
                transactionId = transactionId,
                scheduledAt = System.currentTimeMillis() + delayMin * 60_000,
            )
        )
    }

    private suspend fun enqueueSync(id: String) {
        outboxDao.insert(SyncOutboxEntity(entityType = "transaction", entityId = id, operation = "UPSERT"))
    }

    companion object {
        fun contentHash(
            type: TransactionType,
            amountPaise: Long,
            occurredAt: Long,
            merchant: String?,
            externalRef: String?,
            extra: String?,
        ): String {
            val bucket = occurredAt / 120_000
            val raw = listOf(type.name, amountPaise, bucket, merchant.orEmpty(), externalRef.orEmpty(), extra.orEmpty())
                .joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

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
