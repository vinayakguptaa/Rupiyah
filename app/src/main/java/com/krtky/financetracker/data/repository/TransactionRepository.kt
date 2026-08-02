package com.krtky.financetracker.data.repository

import androidx.room.withTransaction
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.FundLedgerEntity
import com.krtky.financetracker.data.local.db.PendingClassificationEntity
import com.krtky.financetracker.data.local.db.SyncOutboxEntity
import com.krtky.financetracker.data.local.db.TransactionEntity
import com.krtky.financetracker.data.local.db.TransactionSplitEntity
import com.krtky.financetracker.data.local.db.parseTransactionKind
import com.krtky.financetracker.data.local.db.parseTransactionType
import com.krtky.financetracker.data.local.db.toDomain
import com.krtky.financetracker.data.local.db.toEntity
import com.krtky.financetracker.domain.model.CashflowMetrics
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.EffectiveAllocation
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.FundEntryType
import com.krtky.financetracker.domain.model.MonthlySummary
import com.krtky.financetracker.domain.model.MonthlyTrend
import com.krtky.financetracker.domain.model.NamedAmount
import com.krtky.financetracker.domain.model.SplitRules
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionKind
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionSplit
import com.krtky.financetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val accountDao = db.accountDao()
    private val ledgerDao = db.fundLedgerDao()
    private val splitDao = db.transactionSplitDao()
    private val pendingDao = db.pendingClassificationDao()
    private val outboxDao = db.syncOutboxDao()

    private fun mapTxns(
        txns: List<TransactionEntity>,
        cats: List<com.krtky.financetracker.data.local.db.CategoryEntity>,
        funds: List<com.krtky.financetracker.data.local.db.FundEntity>,
        accounts: List<com.krtky.financetracker.data.local.db.AccountEntity>,
        splitCounts: Map<String, Int> = emptyMap(),
    ): List<Transaction> {
        val catMap = cats.associate { it.id to it.toDomain() }
        val fundMap = funds.associate { it.id to it.name }
        val accountMap = accounts.associate { it.id to it.name }
        return txns.map {
            it.toDomain(
                category = catMap[it.categoryId],
                fundName = fundMap[it.fundId],
                accountName = accountMap[it.accountId],
                splitCount = splitCounts[it.id] ?: 0,
            )
        }
    }

    private fun splitCountMap(
        rows: List<com.krtky.financetracker.data.local.db.SplitCountRow>,
    ): Map<String, Int> = rows.associate { it.transactionId to it.cnt }

    private fun needsClassify(e: com.krtky.financetracker.data.local.db.TransactionEntity): Boolean {
        if (e.isSkipped || e.classificationStatus == ClassificationStatus.SKIPPED.name) return false
        if (e.classificationStatus == ClassificationStatus.CLASSIFIED.name) return false
        if (isExcludedFromCashflowKind(e.kind)) return false
        if (e.categoryId != null) return false
        return true
    }

    /** Self-transfer and tab-transfer rows never enter lifestyle/credit reports. */
    private fun isExcludedFromCashflowKind(kind: String?): Boolean {
        val k = kind?.uppercase()
        return k == TransactionKind.SELF_TRANSFER.name || k == TransactionKind.TAB_TRANSFER.name
    }

    fun observeTransactions(): Flow<List<Transaction>> =
        combine(
            txnDao.observeAll(),
            categoryDao.observeAll(),
            fundDao.observeActive(),
            accountDao.observeActive(),
            splitDao.observeSplitCounts(),
        ) { txns, cats, funds, accounts, counts ->
            mapTxns(txns, cats, funds, accounts, splitCountMap(counts))
        }

    /** Count of transactions awaiting category assignment. */
    fun observePendingClassificationCount(): Flow<Int> =
        txnDao.observeAll().map { list -> list.count { needsClassify(it) } }

    /** Oldest pending txn id for the Home classify chip. */
    fun observeFirstPendingClassificationId(): Flow<String?> =
        txnDao.observeAll().map { list ->
            list
                .filter { needsClassify(it) }
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
        accountId: Long? = null,
    ): Flow<List<Transaction>> {
        // Tab filter must include split-linked rows (parent fundId alone is insufficient).
        if (fundId != null) {
            return observeForTab(fundId, type, categoryId, fromTs, toTs).map { list ->
                list.filter { t ->
                    matchesQuery(t, query) &&
                        (accountId == null || t.accountId == accountId)
                }
            }
        }
        // Category filter must include split-line categories (parent categoryId alone is insufficient).
        if (categoryId != null) {
            return observeForCategory(categoryId, type, fromTs, toTs).map { list ->
                list.filter { t ->
                    matchesQuery(t, query) &&
                        (accountId == null || t.accountId == accountId)
                }
            }
        }
        return combine(
            txnDao.observeFiltered(query, type?.name, null, null, fromTs, toTs, accountId),
            categoryDao.observeAll(),
            fundDao.observeActive(),
            accountDao.observeActive(),
            splitDao.observeSplitCounts(),
        ) { txns, cats, funds, accounts, counts ->
            mapTxns(txns, cats, funds, accounts, splitCountMap(counts))
        }
    }

    private fun matchesQuery(t: Transaction, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return listOf(
            t.merchant,
            t.counterparty,
            t.note,
            t.paymentMethod,
            t.rawDescription,
            t.categoryName,
            t.accountName,
            t.fundName,
        ).any { it?.contains(q, ignoreCase = true) == true }
    }

    /**
     * Tab activity: parents linked by [fundId], or by a split on that tab.
     * When the link is via split(s), [Transaction.amountPaise] is the allocation sum for the tab.
     */
    fun observeForTab(
        fundId: Long,
        type: TransactionType?,
        categoryId: Long?,
        fromTs: Long,
        toTs: Long,
    ): Flow<List<Transaction>> =
        combine(
            txnDao.observeAll(),
            splitDao.observeAll(),
            categoryDao.observeAll(),
            fundDao.observeActive(),
            accountDao.observeActive(),
        ) { txns, allSplits, cats, funds, accounts ->
            val catMap = cats.associate { it.id to it.toDomain() }
            val fundMap = funds.associate { it.id to it.name }
            val accountMap = accounts.associate { it.id to it.name }
            val splitsByTxn = allSplits.groupBy { it.transactionId }
            val result = mutableListOf<Transaction>()
            for (e in txns) {
                if (e.deletedAt != null) continue
                if (e.occurredAt < fromTs || e.occurredAt > toTs) continue
                if (type != null && parseTransactionType(e.type) != type) continue
                // Self-transfers never sit on tabs; tab transfers do.
                if (e.kind == TransactionKind.SELF_TRANSFER.name) continue
                val splits = splitsByTxn[e.id].orEmpty()
                if (splits.isNotEmpty()) {
                    val matching = splits.filter { s ->
                        s.fundId == fundId &&
                            (categoryId == null || s.categoryId == categoryId)
                    }
                    if (matching.isEmpty()) continue
                    val amount = matching.sumOf { it.amountPaise }
                    val first = matching.first()
                    val cat = first.categoryId?.let { catMap[it] }
                    result.add(
                        e.toDomain(
                            category = cat,
                            fundName = fundMap[fundId],
                            accountName = accountMap[e.accountId],
                            splitCount = splits.size,
                        ).copy(
                            amountPaise = amount,
                            categoryId = first.categoryId,
                            fundId = fundId,
                            counterparty = first.counterparty ?: e.counterparty ?: e.merchant,
                            note = first.note ?: e.note,
                        ),
                    )
                } else {
                    if (e.fundId != fundId) continue
                    if (categoryId != null && e.categoryId != categoryId) continue
                    result.add(
                        e.toDomain(
                            category = catMap[e.categoryId],
                            fundName = fundMap[e.fundId],
                            accountName = accountMap[e.accountId],
                            splitCount = 0,
                        ),
                    )
                }
            }
            result.sortedWith(
                compareByDescending<Transaction> { it.occurredAt }
                    .thenByDescending { it.recordedAt }
                    .thenByDescending { it.id },
            )
        }

    /**
     * Category activity: unsplit parents with [categoryId], or parents that have split line(s)
     * in that category. When linked via split(s), [Transaction.amountPaise] is the allocation
     * sum for the category (aligned with Home categorySpend / cashflow).
     *
     * Pass [categoryId] = null for uncategorized allocations (parent or split line).
     */
    fun observeForCategory(
        categoryId: Long?,
        type: TransactionType?,
        fromTs: Long,
        toTs: Long,
    ): Flow<List<Transaction>> =
        combine(
            txnDao.observeAll(),
            splitDao.observeAll(),
            categoryDao.observeAll(),
            fundDao.observeActive(),
            accountDao.observeActive(),
        ) { txns, allSplits, cats, funds, accounts ->
            val catMap = cats.associate { it.id to it.toDomain() }
            val fundMap = funds.associate { it.id to it.name }
            val accountMap = accounts.associate { it.id to it.name }
            val splitsByTxn = allSplits.groupBy { it.transactionId }
            val result = mutableListOf<Transaction>()
            for (e in txns) {
                if (e.deletedAt != null) continue
                if (e.occurredAt < fromTs || e.occurredAt > toTs) continue
                if (type != null && parseTransactionType(e.type) != type) continue
                if (isExcludedFromCashflowKind(e.kind)) continue
                val splits = splitsByTxn[e.id].orEmpty()
                if (splits.isNotEmpty()) {
                    val matching = splits.filter { it.categoryId == categoryId }
                    if (matching.isEmpty()) continue
                    val amount = matching.sumOf { it.amountPaise }
                    val first = matching.first()
                    val cat = categoryId?.let { catMap[it] }
                    result.add(
                        e.toDomain(
                            category = cat,
                            fundName = first.fundId?.let { fundMap[it] } ?: e.fundId?.let { fundMap[it] },
                            accountName = accountMap[e.accountId],
                            splitCount = splits.size,
                        ).copy(
                            amountPaise = amount,
                            categoryId = categoryId,
                            fundId = first.fundId ?: e.fundId,
                            counterparty = first.counterparty ?: e.counterparty ?: e.merchant,
                            note = first.note ?: e.note,
                        ),
                    )
                } else {
                    if (e.categoryId != categoryId) continue
                    result.add(
                        e.toDomain(
                            category = catMap[e.categoryId],
                            fundName = e.fundId?.let { fundMap[it] },
                            accountName = accountMap[e.accountId],
                            splitCount = 0,
                        ),
                    )
                }
            }
            result.sortedWith(
                compareByDescending<Transaction> { it.occurredAt }
                    .thenByDescending { it.recordedAt }
                    .thenByDescending { it.id },
            )
        }

    suspend fun getById(id: String): Transaction? {
        val e = txnDao.getById(id) ?: return null
        val cat = e.categoryId?.let { categoryDao.getById(it)?.toDomain() }
        val fund = e.fundId?.let { fundDao.getById(it)?.name }
        val account = e.accountId?.let { accountDao.getById(it)?.name }
        val splitCount = splitDao.getForTransaction(id).size
        return e.toDomain(cat, fund, account, splitCount)
    }

    fun observeSplits(transactionId: String): Flow<List<TransactionSplit>> =
        combine(
            splitDao.observeForTransaction(transactionId),
            categoryDao.observeAll(),
            fundDao.observeActive(),
        ) { splits, cats, funds ->
            val catNames = cats.associate { it.id to it.name }
            val fundNames = funds.associate { it.id to it.name }
            splits.map {
                it.toDomain(
                    categoryName = it.categoryId?.let { id -> catNames[id] },
                    fundName = it.fundId?.let { id -> fundNames[id] },
                )
            }
        }

    suspend fun getSplits(transactionId: String): List<TransactionSplit> {
        val cats = categoryDao.getAll().associate { it.id to it.name }
        val funds = fundDao.getAll().associate { it.id to it.name }
        return splitDao.getForTransaction(transactionId).map {
            it.toDomain(
                categoryName = it.categoryId?.let { id -> cats[id] },
                fundName = it.fundId?.let { id -> funds[id] },
            )
        }
    }

    /**
     * Replace all splits for [transactionId]. Empty list clears splits.
     * Sum must equal parent amount; self-transfers / tab-transfers cannot be split.
     * Delete + upsert (+ optional parent status update) run in one Room transaction.
     */
    suspend fun setSplits(transactionId: String, splits: List<TransactionSplit>): Result<Unit> {
        val parent = txnDao.getById(transactionId)
            ?: return Result.failure(IllegalArgumentException("Transaction not found"))
        if (parent.deletedAt != null) {
            return Result.failure(IllegalArgumentException("Transaction is deleted"))
        }
        if (isExcludedFromCashflowKind(parent.kind)) {
            return Result.failure(IllegalArgumentException("Self/tab transfers cannot be split"))
        }
        val oldSplits = splitDao.getForTransaction(transactionId)
        val affectedFunds = (
            oldSplits.mapNotNull { it.fundId } +
                splits.mapNotNull { it.fundId } +
                listOfNotNull(parent.fundId)
            ).toSet()

        if (splits.isEmpty()) {
            db.withTransaction {
                splitDao.deleteForTransaction(transactionId)
                // Parent may have been CLASSIFIED only because split lines had categories.
                // Without a parent category, re-open classify so it re-enters the queue.
                val shouldReopenClassify =
                    parent.categoryId == null &&
                        !parent.isSkipped &&
                        parent.classificationStatus != ClassificationStatus.SKIPPED.name &&
                        !isExcludedFromCashflowKind(parent.kind) &&
                        parent.classificationStatus == ClassificationStatus.CLASSIFIED.name
                if (shouldReopenClassify) {
                    txnDao.update(
                        parent.copy(
                            classificationStatus = ClassificationStatus.PENDING.name,
                            updatedAt = System.currentTimeMillis(),
                            version = parent.version + 1,
                            sheetsSynced = false,
                        ),
                    )
                }
            }
            val reopened =
                parent.categoryId == null &&
                    !parent.isSkipped &&
                    parent.classificationStatus != ClassificationStatus.SKIPPED.name &&
                    !isExcludedFromCashflowKind(parent.kind) &&
                    parent.classificationStatus == ClassificationStatus.CLASSIFIED.name
            if (reopened) {
                scheduleClassification(transactionId)
            }
            affectedFunds.forEach { recalculateFundLedger(it) }
            enqueueSync(transactionId)
            return Result.success(Unit)
        }

        val err = SplitRules.validateSum(parent.amountPaise, splits.map { it.amountPaise })
        if (err != null) return Result.failure(IllegalArgumentException(err))

        val entities = splits.mapIndexed { index, s ->
            TransactionSplitEntity(
                id = s.id.ifBlank { UUID.randomUUID().toString() },
                transactionId = transactionId,
                amountPaise = s.amountPaise,
                categoryId = s.categoryId,
                counterparty = s.counterparty?.takeIf { it.isNotBlank() },
                fundId = s.fundId,
                note = s.note?.takeIf { it.isNotBlank() },
                sortOrder = index,
            )
        }
        db.withTransaction {
            splitDao.deleteForTransaction(transactionId)
            splitDao.upsertAll(entities)
            // Parent with splits is classified for queue purposes if any line has a category
            if (entities.any { it.categoryId != null } && parent.categoryId == null) {
                txnDao.update(
                    parent.copy(
                        classificationStatus = ClassificationStatus.CLASSIFIED.name,
                        updatedAt = System.currentTimeMillis(),
                        version = parent.version + 1,
                        sheetsSynced = false,
                    ),
                )
                pendingDao.delete(transactionId)
            }
        }
        affectedFunds.forEach { recalculateFundLedger(it) }
        enqueueSync(transactionId)
        return Result.success(Unit)
    }

    /**
     * Insert a parent and optionally replace its splits atomically.
     * Used by Add Cash so a crash cannot leave a parent without its split lines.
     */
    suspend fun insertManualWithSplits(
        txn: Transaction,
        splits: List<TransactionSplit>,
        addToFund: Boolean = false,
    ): String = db.withTransaction {
        val id = insertManual(txn, addToFund = if (splits.isNotEmpty()) false else addToFund)
        if (splits.isNotEmpty()) {
            setSplits(id, splits.map { it.copy(transactionId = id) }).getOrThrow()
        }
        id
    }

    suspend fun clearSplits(transactionId: String): Result<Unit> =
        setSplits(transactionId, emptyList())

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

    /**
     * Insert a bank-statement CSV row. Caller is responsible for dedupe decisions;
     * still guards unique contentHash / externalRef collisions.
     * @return new id, or null if ignored as hard duplicate.
     */
    suspend fun insertFromImport(txn: Transaction): String? {
        val hash = txn.contentHash ?: contentHash(
            type = txn.type,
            amountPaise = txn.amountPaise,
            occurredAt = txn.occurredAt,
            merchant = txn.merchant,
            externalRef = txn.externalRefId,
            extra = "import-${txn.accountId}-${txn.rawDescription?.take(40).orEmpty()}",
        )
        if (txnDao.findByContentHash(hash) != null) return null
        txn.externalRefId?.takeIf { it.isNotBlank() }?.let { ref ->
            val existing = txnDao.findByExternalRefId(ref)
            if (existing != null &&
                (existing.accountId == txn.accountId ||
                    existing.paymentMethod.equals(txn.paymentMethod, true))
            ) {
                return null
            }
        }
        val id = txn.id.ifBlank { UUID.randomUUID().toString() }
        val status = if (txn.categoryId != null) {
            ClassificationStatus.CLASSIFIED
        } else {
            ClassificationStatus.PENDING
        }
        val entity = txn.copy(
            id = id,
            source = TransactionSource.IMPORT,
            contentHash = hash,
            sheetsSynced = false,
            classificationStatus = status,
            updatedAt = System.currentTimeMillis(),
        ).toEntity()
        val rowId = txnDao.insert(entity)
        if (rowId == -1L) return null
        if (status == ClassificationStatus.PENDING) {
            scheduleClassification(id)
        }
        enqueueSync(id)
        return id
    }

    /** Non-deleted domain transactions for an account (dedupe candidates). */
    suspend fun getForAccount(accountId: Long, accountName: String): List<Transaction> {
        val cats = categoryDao.getAll().associate { it.id to it.toDomain() }
        val funds = fundDao.getAll().associate { it.id to it.name }
        val accounts = accountDao.getAll().associate { it.id to it.name }
        return txnDao.getForAccount(accountId, accountName).map { e ->
            val cat = e.categoryId?.let { cats[it] }
            e.toDomain(
                category = cat,
                fundName = e.fundId?.let { funds[it] },
                accountName = e.accountId?.let { accounts[it] } ?: e.paymentMethod,
                splitCount = 0,
            )
        }
    }

    suspend fun update(txn: Transaction) {
        val existing = txnDao.getById(txn.id) ?: return
        val oldFund = existing.fundId
        val existingSplits = splitDao.getForTransaction(txn.id)
        val hasSplits = existingSplits.isNotEmpty()
        // Parent amount + direction are bank truth while splits exist.
        val lockedAmount = if (hasSplits) existing.amountPaise else txn.amountPaise
        val lockedType = if (hasSplits) parseTransactionType(existing.type) else txn.type
        val updated = txn.copy(
            amountPaise = lockedAmount,
            type = lockedType,
            updatedAt = System.currentTimeMillis(),
            version = existing.version + 1,
            sheetsSynced = false,
            classificationStatus = if (txn.categoryId != null) ClassificationStatus.CLASSIFIED else txn.classificationStatus,
        )
        txnDao.update(updated.toEntity())
        val fundsToRebuild = mutableSetOf<Long>()
        if (oldFund != null) fundsToRebuild.add(oldFund)
        if (updated.fundId != null) fundsToRebuild.add(updated.fundId)
        // Split fund links only change via setSplits; rebuild when amount/type/fund change.
        if (oldFund != updated.fundId ||
            existing.amountPaise != updated.amountPaise ||
            existing.type != updated.type.name
        ) {
            // Unsplit parent fund change: rebuild old + new. Split parents lock amount/type
            // so note-only edits skip this path.
            if (!hasSplits) {
                fundsToRebuild.forEach { recalculateFundLedger(it) }
            } else if (oldFund != updated.fundId) {
                // Parent fundId changed while splits exist — parent fund is unused for ledgers
                // when splits are present; still rebuild any parent-linked funds if set.
                fundsToRebuild.forEach { recalculateFundLedger(it) }
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
        val existing = txnDao.getById(id) ?: return
        // Self-transfer / tab-transfer: remove both linked legs
        val groupId = existing.transferGroupId
        val linkedKind = existing.kind == TransactionKind.SELF_TRANSFER.name ||
            existing.kind == TransactionKind.TAB_TRANSFER.name
        if (linkedKind && !groupId.isNullOrBlank()) {
            val legs = txnDao.getByTransferGroup(groupId)
            val fundsToRebuild = legs.mapNotNull { it.fundId }.toSet()
            db.withTransaction {
                for (leg in legs) {
                    ledgerDao.deleteForTransaction(leg.id)
                    splitDao.deleteForTransaction(leg.id)
                    txnDao.softDelete(leg.id)
                    pendingDao.delete(leg.id)
                }
            }
            fundsToRebuild.forEach { recalculateFundLedger(it) }
            legs.forEach { enqueueSync(it.id) }
            return
        }
        val splitFunds = splitDao.getForTransaction(id).mapNotNull { it.fundId }.toSet()
        ledgerDao.deleteForTransaction(id)
        splitDao.deleteForTransaction(id)
        txnDao.softDelete(id)
        (splitFunds + listOfNotNull(existing.fundId)).forEach { recalculateFundLedger(it) }
        pendingDao.delete(id)
        enqueueSync(id)
    }

    /**
     * Self transfer between owned accounts: debit on [fromAccountId], credit on [toAccountId].
     * No category / fund / splits. Both legs share [transferGroupId] and kind SELF_TRANSFER.
     */
    suspend fun createSelfTransfer(
        amountPaise: Long,
        fromAccountId: Long,
        toAccountId: Long,
        note: String? = null,
        occurredAt: Long = System.currentTimeMillis(),
    ): String? {
        if (amountPaise <= 0L || fromAccountId == toAccountId) return null
        val from = accountDao.getById(fromAccountId) ?: return null
        val to = accountDao.getById(toAccountId) ?: return null
        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val debitId = "${groupId}_out"
        val creditId = "${groupId}_in"
        val fromLabel = from.name
        val toLabel = to.name
        val debit = Transaction(
            id = debitId,
            type = TransactionType.DEBIT,
            amountPaise = amountPaise,
            occurredAt = occurredAt,
            recordedAt = now,
            accountId = fromAccountId,
            paymentMethod = fromLabel,
            isCash = from.kind == "CASH",
            source = TransactionSource.MANUAL,
            note = note ?: "Self transfer to $toLabel",
            counterparty = toLabel,
            classificationStatus = ClassificationStatus.CLASSIFIED,
            kind = TransactionKind.SELF_TRANSFER,
            transferGroupId = groupId,
            externalRefId = "self_transfer_$groupId",
        )
        val credit = Transaction(
            id = creditId,
            type = TransactionType.CREDIT,
            amountPaise = amountPaise,
            occurredAt = occurredAt,
            recordedAt = now,
            accountId = toAccountId,
            paymentMethod = toLabel,
            isCash = to.kind == "CASH",
            source = TransactionSource.MANUAL,
            note = note ?: "Self transfer from $fromLabel",
            counterparty = fromLabel,
            classificationStatus = ClassificationStatus.CLASSIFIED,
            kind = TransactionKind.SELF_TRANSFER,
            transferGroupId = groupId,
            externalRefId = "self_transfer_${groupId}_in",
        )
        db.withTransaction {
            insertManual(debit)
            insertManual(credit)
        }
        return groupId
    }

    suspend fun skipClassification(transactionId: String) {
        val existing = txnDao.getById(transactionId) ?: return
        txnDao.update(
            existing.copy(
                isSkipped = true,
                classificationStatus = ClassificationStatus.SKIPPED.name,
                updatedAt = System.currentTimeMillis(),
                version = existing.version + 1,
                sheetsSynced = false,
            ),
        )
        pendingDao.delete(transactionId)
        enqueueSync(transactionId)
    }

    suspend fun monthlySummary(now: Long = System.currentTimeMillis()): MonthlySummary {
        val (from, to) = monthBounds(now)
        return MonthlySummary(
            incomePaise = txnDao.sumByType(TransactionType.CREDIT.name, from, to),
            expensePaise = txnDao.sumByType(TransactionType.DEBIT.name, from, to),
        )
    }

    /**
     * Debit totals by category for the current month, using **effective allocations**
     * (splits replace parent). Self/tab transfers are excluded via [buildEffectiveAllocations].
     */
    suspend fun categorySpend(now: Long = System.currentTimeMillis()): List<CategorySpend> {
        val (from, to) = monthBounds(now)
        val cats = categoryDao.getAll().associateBy { it.id }
        val entities = txnDao.observeFiltered("", null, null, null, from, to, null).first()
        val splitsByTxn = splitDao.getAll().groupBy { it.transactionId }
        val allocations = buildEffectiveAllocations(entities, splitsByTxn)
            .filter { it.type == TransactionType.DEBIT }
        return allocations
            .groupBy { it.categoryId }
            .map { (catId, rows) ->
                CategorySpend(
                    categoryId = catId,
                    categoryName = catId?.let { cats[it]?.name } ?: "Uncategorized",
                    totalPaise = rows.sumOf { it.amountPaise },
                )
            }
            .sortedByDescending { it.totalPaise }
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
     * Open Tab balances (fund table).
     * balance = opening + debits − credits  (+ they owe you / − you owe them).
     * Uses **effective allocations** (splits if present, else parent). Self-transfers never on tabs.
     */
    fun observeFunds(): Flow<List<FundBalance>> = combine(
        fundDao.observeActive(),
        txnDao.observeAll(),
        splitDao.observeAll(),
    ) { funds, allTxns, allSplits ->
        val parentMap = allTxns.associateBy { it.id }
        val splitsByTxn = allSplits.groupBy { it.transactionId }
        funds.map { fund ->
            var credits = 0L
            var debits = 0L
            // Split lines on this tab
            for (s in allSplits) {
                if (s.fundId != fund.id) continue
                val p = parentMap[s.transactionId] ?: continue
                if (p.deletedAt != null || p.kind == TransactionKind.SELF_TRANSFER.name) continue
                if (isCreditType(p.type)) credits += s.amountPaise else debits += s.amountPaise
            }
            // Unsplit parents with fundId (ignore parent fund when splits exist)
            for (t in allTxns) {
                if (t.fundId != fund.id || t.deletedAt != null) continue
                if (t.kind == TransactionKind.SELF_TRANSFER.name) continue
                if (splitsByTxn.containsKey(t.id)) continue
                if (isCreditType(t.type)) credits += t.amountPaise else debits += t.amountPaise
            }
            val opening = fund.budgetPaise.coerceAtLeast(0L)
            val balance = opening + debits - credits
            FundBalance(
                fund = fund.toDomain(),
                balancePaise = balance,
                creditedPaise = credits,
                debitedPaise = debits,
                openingPaise = opening,
            )
        }
    }

    /**
     * Month cashflow metrics for Home:
     * lifestyle spend (debits − Investment − self transfer), credits, investment by Name.
     * Uses **effective allocations** so split parents are not double-counted.
     */
    suspend fun cashflowMetrics(now: Long = System.currentTimeMillis()): CashflowMetrics {
        val (from, to) = monthBounds(now)
        val cats = categoryDao.getAll().associateBy { it.id }
        val investmentIds = cats.values
            .filter { it.name.equals("Investment", true) }
            .map { it.id }
            .toSet()
        val entities = txnDao.observeFiltered("", null, null, null, from, to, null).first()
        val splitsByTxn = splitDao.getAll().groupBy { it.transactionId }
        val allocations = buildEffectiveAllocations(entities, splitsByTxn)
        return cashflowMetricsFromAllocations(
            allocations = allocations,
            investmentCategoryIds = investmentIds,
            categoryNames = cats.mapValues { it.value.name },
        )
    }

    /**
     * Expand parents into report rows: splits if present, else the parent.
     * Self-transfer and tab-transfer legs are omitted (excluded from lifestyle/credit metrics).
     */
    internal fun buildEffectiveAllocations(
        entities: List<TransactionEntity>,
        splitsByTxn: Map<String, List<TransactionSplitEntity>>,
    ): List<EffectiveAllocation> {
        val out = ArrayList<EffectiveAllocation>(entities.size)
        for (e in entities) {
            if (e.deletedAt != null) continue
            if (isExcludedFromCashflowKind(e.kind)) continue
            val splits = splitsByTxn[e.id].orEmpty()
            if (splits.isNotEmpty()) {
                for (s in splits) {
                    out.add(
                        EffectiveAllocation(
                            transactionId = e.id,
                            type = parseTransactionType(e.type),
                            amountPaise = s.amountPaise,
                            categoryId = s.categoryId,
                            counterparty = s.counterparty ?: e.counterparty ?: e.merchant,
                            fundId = s.fundId,
                            occurredAt = e.occurredAt,
                            kind = parseTransactionKind(e.kind),
                            isSplit = true,
                            splitId = s.id,
                            note = s.note,
                        ),
                    )
                }
            } else {
                out.add(
                    EffectiveAllocation(
                        transactionId = e.id,
                        type = parseTransactionType(e.type),
                        amountPaise = e.amountPaise,
                        categoryId = e.categoryId,
                        counterparty = e.counterparty ?: e.merchant,
                        fundId = e.fundId,
                        occurredAt = e.occurredAt,
                        kind = parseTransactionKind(e.kind),
                        isSplit = false,
                        note = e.note,
                    ),
                )
            }
        }
        return out
    }

    internal fun cashflowMetricsFromAllocations(
        allocations: List<EffectiveAllocation>,
        investmentCategoryIds: Set<Long>,
        categoryNames: Map<Long, String>,
    ): CashflowMetrics {
        val lifestyle = allocations.filter {
            it.type == TransactionType.DEBIT &&
                (it.categoryId == null || it.categoryId !in investmentCategoryIds)
        }
        // Lifestyle credits exclude Investment redemptions (reported under redeemedPaise).
        val credits = allocations.filter {
            it.type == TransactionType.CREDIT &&
                (it.categoryId == null || it.categoryId !in investmentCategoryIds)
        }
        val investDebits = allocations.filter {
            it.type == TransactionType.DEBIT &&
                it.categoryId != null &&
                it.categoryId in investmentCategoryIds
        }
        val investCredits = allocations.filter {
            it.type == TransactionType.CREDIT &&
                it.categoryId != null &&
                it.categoryId in investmentCategoryIds
        }
        val lifestyleByCat = lifestyle
            .groupBy { it.categoryId }
            .map { (catId, rows) ->
                CategorySpend(
                    categoryId = catId,
                    categoryName = catId?.let { categoryNames[it] } ?: "Uncategorized",
                    totalPaise = rows.sumOf { it.amountPaise },
                )
            }
            .sortedByDescending { it.totalPaise }
        val investByName = (investDebits + investCredits)
            .groupBy { it.counterparty?.trim().orEmpty().ifBlank { "Unnamed" } }
            .map { (name, rows) ->
                NamedAmount(
                    name = name,
                    debitPaise = rows.filter { it.type == TransactionType.DEBIT }.sumOf { it.amountPaise },
                    creditPaise = rows.filter { it.type == TransactionType.CREDIT }.sumOf { it.amountPaise },
                )
            }
            .sortedByDescending { kotlin.math.abs(it.netPaise) }
        return CashflowMetrics(
            lifestyleSpendPaise = lifestyle.sumOf { it.amountPaise },
            creditPaise = credits.sumOf { it.amountPaise },
            investedPaise = investDebits.sumOf { it.amountPaise },
            redeemedPaise = investCredits.sumOf { it.amountPaise },
            lifestyleByCategory = lifestyleByCat,
            investmentByName = investByName,
        )
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
     * Net balance per account name (credits − debits), including self-transfer legs.
     * Prefers [Transaction.accountName]; falls back to payment method labels.
     */
    fun observeAccountBalances(): Flow<Map<String, Long>> =
        observeTransactions().map { txns ->
            txns.groupBy { t ->
                when {
                    !t.accountName.isNullOrBlank() -> t.accountName.trim()
                    t.isCash || t.paymentMethod.equals("Cash", true) -> "Cash"
                    t.paymentMethod.isNullOrBlank() -> "Digital"
                    t.paymentMethod.equals("UPI", true) -> "Digital"
                    else -> t.paymentMethod.trim()
                }
            }.mapValues { (_, items) ->
                items.sumOf { t ->
                    if (t.type == TransactionType.CREDIT) t.amountPaise else -t.amountPaise
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

    /**
     * Move open-tab balance from [fromFundId] to [toFundId].
     *
     * Open-tab formula is `opening + debits − credits`, so:
     * - source tab gets a **CREDIT** (balance decreases — less they owe you)
     * - destination gets a **DEBIT** (balance increases)
     *
     * Kind is [TransactionKind.TAB_TRANSFER] so these rows never enter lifestyle/credit metrics.
     */
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
        // CREDIT on source reduces open-tab balance; DEBIT on dest increases it.
        val txnOut = Transaction(
            id = "${transferRef}_out",
            type = TransactionType.CREDIT,
            amountPaise = amountPaise,
            occurredAt = now,
            fundId = fromFundId,
            source = TransactionSource.MANUAL,
            note = note ?: "Transfer to $toName",
            externalRefId = "fund_transfer_${transferRef}_out",
            classificationStatus = ClassificationStatus.CLASSIFIED,
            kind = TransactionKind.TAB_TRANSFER,
            transferGroupId = transferRef,
        )
        val txnIn = Transaction(
            id = "${transferRef}_in",
            type = TransactionType.DEBIT,
            amountPaise = amountPaise,
            occurredAt = now,
            fundId = toFundId,
            source = TransactionSource.MANUAL,
            note = note ?: "Transfer from $fromName",
            externalRefId = "fund_transfer_${transferRef}_in",
            classificationStatus = ClassificationStatus.CLASSIFIED,
            kind = TransactionKind.TAB_TRANSFER,
            transferGroupId = transferRef,
        )
        db.withTransaction {
            insertManual(txnOut, addToFund = true)
            insertManual(txnIn, addToFund = true)
        }
    }

    suspend fun creditFundFromIncome(fundId: Long, transactionId: String, amountPaise: Long, note: String?) {
        val current = ledgerDao.latestBalance(fundId) ?: 0L
        // Open-tab signs: credit decreases balance (they paid you / settled).
        val after = current - amountPaise
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
     * Ledger rebuild aligned with open-tab formula:
     * `balance = opening + debits − credits`.
     * Single baseline (= fund amount) + every linked transaction / split allocation.
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

        // Effective allocations on this tab (split lines or unsplit parents).
        // Self-transfers never sit on tabs; tab transfers do.
        val allTxns = txnDao.observeAll().first()
            .filter { it.deletedAt == null && it.kind != TransactionKind.SELF_TRANSFER.name }
        val splitsByTxn = splitDao.getAll().groupBy { it.transactionId }
        data class LedgerHit(
            val txn: TransactionEntity,
            val amountPaise: Long,
            val note: String?,
        )
        val hits = mutableListOf<LedgerHit>()
        for (txn in allTxns) {
            val splits = splitsByTxn[txn.id].orEmpty()
            if (splits.isNotEmpty()) {
                val amount = splits.filter { it.fundId == fundId }.sumOf { it.amountPaise }
                if (amount > 0L) {
                    hits.add(
                        LedgerHit(
                            txn = txn,
                            amountPaise = amount,
                            note = splits.firstOrNull { it.fundId == fundId }?.note ?: txn.note,
                        ),
                    )
                }
            } else if (txn.fundId == fundId) {
                hits.add(LedgerHit(txn, txn.amountPaise, txn.note))
            }
        }
        hits.sortWith(compareBy({ it.txn.occurredAt }, { it.txn.id }))

        for (hit in hits) {
            val isCredit = isCreditType(hit.txn.type)
            val amount = hit.amountPaise
            // opening + debits − credits
            runningBalance += if (isCredit) -amount else amount
            ledgerDao.insert(
                FundLedgerEntity(
                    fundId = fundId,
                    transactionId = hit.txn.id,
                    entryType = if (isCredit) {
                        FundEntryType.CREDIT.name
                    } else {
                        FundEntryType.DEBIT.name
                    },
                    amountPaise = amount,
                    balanceAfterPaise = runningBalance,
                    note = hit.note,
                    createdAt = hit.txn.occurredAt,
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
        // Open-tab: debits raise balance (they owe you more), credits lower it.
        when {
            isCreditType(type) -> {
                val after = current - amountPaise
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
            isDebitType(type) -> {
                val after = current + amountPaise
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

    private fun isCreditType(type: String): Boolean {
        val t = type.uppercase()
        return t == TransactionType.CREDIT.name || t == "INCOME"
    }

    private fun isDebitType(type: String): Boolean {
        val t = type.uppercase()
        return t == TransactionType.DEBIT.name || t == "EXPENSE"
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
