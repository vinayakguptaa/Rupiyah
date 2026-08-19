package com.krtky.financetracker.data.repository

import androidx.room.withTransaction
import com.krtky.financetracker.data.local.db.AccountEntity
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.CategoryEntity
import com.krtky.financetracker.data.local.db.TabEntity
import com.krtky.financetracker.data.local.db.TabLedgerEntity
import com.krtky.financetracker.data.local.db.PendingClassificationEntity
import com.krtky.financetracker.data.local.db.SyncOutboxEntity
import com.krtky.financetracker.data.local.db.TransactionEntity
import com.krtky.financetracker.data.local.db.parseTransactionType
import com.krtky.financetracker.data.local.db.toDomain
import com.krtky.financetracker.data.local.db.toEntity
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.TabBalance
import com.krtky.financetracker.domain.model.TabEntryType
import com.krtky.financetracker.domain.model.SplitPart
import com.krtky.financetracker.domain.model.SplitRules
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionKind
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val db: AppDatabase,
) {
    private val txnDao = db.transactionDao()
    private val categoryDao = db.categoryDao()
    private val tabDao = db.tabDao()
    private val accountDao = db.accountDao()
    private val ledgerDao = db.tabLedgerDao()
    private val pendingDao = db.pendingClassificationDao()
    private val outboxDao = db.syncOutboxDao()

    private fun mapTxns(
        txns: List<TransactionEntity>,
        cats: List<CategoryEntity>,
        tabs: List<TabEntity>,
        accounts: List<AccountEntity>,
    ): List<Transaction> {
        val catMap = cats.associate { it.id to it.toDomain() }
        val tabMap = tabs.associate { it.id to it.name }
        val accountMap = accounts.associate { it.id to it.name }
        return txns.map {
            it.toDomain(
                category = catMap[it.categoryId],
                tabName = tabMap[it.tabId],
                accountName = accountMap[it.accountId],
            )
        }
    }

    private fun needsClassify(e: TransactionEntity): Boolean {
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
            tabDao.observeActive(),
            // All accounts (incl. archived) so history rows keep their account name.
            accountDao.observeAll(),
        ) { txns, cats, tabs, accounts ->
            mapTxns(txns, cats, tabs, accounts)
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

    /**
     * General transaction list. Split parts are standalone rows, so SQL filtering by
     * category / tab / account just works (parents were soft-deleted at split time).
     */
    fun observeFiltered(
        query: String,
        type: TransactionType?,
        categoryId: Long?,
        tabId: Long?,
        fromTs: Long,
        toTs: Long,
        accountId: Long? = null,
    ): Flow<List<Transaction>> =
        combine(
            txnDao.observeFiltered(query, type?.name, categoryId, tabId, fromTs, toTs, accountId),
            categoryDao.observeAll(),
            tabDao.observeActive(),
            accountDao.observeAll(),
        ) { txns, cats, tabs, accounts ->
            mapTxns(txns, cats, tabs, accounts)
        }

    /**
     * Tab activity: rows linked by [tabId]. Self-transfers never sit on tabs
     * (they have no tabId); tab transfers do.
     */
    fun observeForTab(
        tabId: Long,
        type: TransactionType?,
        categoryId: Long?,
        fromTs: Long,
        toTs: Long,
    ): Flow<List<Transaction>> =
        observeFiltered("", type, categoryId, tabId, fromTs, toTs)

    /**
     * Category activity. Pass [categoryId] = null for uncategorized rows.
     * Self / tab transfers are excluded (they never carry a real category).
     */
    fun observeForCategory(
        categoryId: Long?,
        type: TransactionType?,
        fromTs: Long,
        toTs: Long,
    ): Flow<List<Transaction>> =
        observeFiltered("", type, categoryId, null, fromTs, toTs).map { list ->
            list.filter { !it.isSelfTransfer() && !it.isTabTransfer() }
        }

    suspend fun getById(id: String): Transaction? {
        val e = txnDao.getById(id) ?: return null
        val cat = e.categoryId?.let { categoryDao.getById(it)?.toDomain() }
        val tab = e.tabId?.let { tabDao.getById(it)?.name }
        val account = e.accountId?.let { accountDao.getById(it)?.name }
        return e.toDomain(cat, tab, account)
    }

    /**
     * Sibling parts of a split group, given any member id. Empty when the row
     * is not part of a split group.
     */
    fun observeSplitGroup(transactionId: String): Flow<List<Transaction>> =
        combine(
            txnDao.observeAll(),
            categoryDao.observeAll(),
            tabDao.observeActive(),
            accountDao.observeAll(),
        ) { txns, cats, tabs, accounts ->
            val me = txns.firstOrNull { it.id == transactionId } ?: return@combine emptyList()
            val groupId = me.splitGroupId ?: return@combine emptyList()
            mapTxns(
                txns.filter { it.splitGroupId == groupId },
                cats,
                tabs,
                accounts,
            )
        }

    /** Suspend version of [observeSplitGroup] for editor initial load. */
    suspend fun getSplitGroup(transactionId: String): List<Transaction> {
        val me = txnDao.getById(transactionId) ?: return emptyList()
        val groupId = me.splitGroupId ?: return emptyList()
        val cats = categoryDao.getAll().associate { it.id to it.toDomain() }
        val tabs = tabDao.getAll().associate { it.id to it.name }
        val accounts = accountDao.getAll().associate { it.id to it.name }
        return txnDao.getAllNonDeleted()
            .filter { it.splitGroupId == groupId }
            .map {
                it.toDomain(
                    category = cats[it.categoryId],
                    tabName = tabs[it.tabId],
                    accountName = accounts[it.accountId],
                )
            }
    }

    /**
     * Split [transactionId] into [parts] that sum to its amount. When the target is
     * already part of a split group, its sibling parts are replaced instead.
     *
     * The parent is soft-deleted and children share [Transaction.splitGroupId].
     * Self / tab transfers cannot be split.
     *
     * @return the shared splitGroupId on success, or a failure with the reason.
     */
    suspend fun saveSplit(transactionId: String, parts: List<SplitPart>): Result<String> {
        val target = txnDao.getById(transactionId)
            ?: return Result.failure(IllegalArgumentException("Transaction not found"))
        val parentId = target.splitGroupId ?: target.id
        val parent = txnDao.getById(parentId)
            ?: return Result.failure(IllegalArgumentException("Split parent not found"))
        if (isExcludedFromCashflowKind(parent.kind)) {
            return Result.failure(IllegalArgumentException("Self/tab transfers cannot be split"))
        }
        if (parts.size < 2) {
            return Result.failure(IllegalArgumentException("A split needs at least two parts"))
        }
        if (parts.any { it.amountPaise <= 0L }) {
            return Result.failure(IllegalArgumentException("Each part must be greater than zero"))
        }
        SplitRules.validateSum(parent.amountPaise, parts.map { it.amountPaise })
            ?.let { return Result.failure(IllegalArgumentException(it)) }

        val groupId = parent.id
        val existingChildren = txnDao.getAllNonDeleted().filter { it.splitGroupId == groupId }
        val affectedTabs = (
            existingChildren.mapNotNull { it.tabId } +
                listOfNotNull(parent.tabId) +
                parts.mapNotNull { it.tabId }
            ).toSet()

        db.withTransaction {
            // Replace current parts (re-split) or the parent itself (first split).
            if (existingChildren.isEmpty()) {
                ledgerDao.deleteForTransaction(parent.id)
                txnDao.softDelete(parent.id)
                pendingDao.delete(parent.id)
                enqueueSync(parent.id)
            } else {
                existingChildren.forEach { child ->
                    ledgerDao.deleteForTransaction(child.id)
                    txnDao.softDelete(child.id)
                    pendingDao.delete(child.id)
                    enqueueSync(child.id)
                }
            }
            val partyBase = parent.counterparty
            val parentLabel = partyBase ?: "transaction"
            parts.forEachIndexed { index, part ->
                val party = part.counterparty?.trim()?.takeIf { it.isNotBlank() } ?: partyBase
                val partNote = buildString {
                    append("Split from $parentLabel")
                    if (!part.note.isNullOrBlank()) append(" · ${part.note.trim()}")
                    else if (!parent.note.isNullOrBlank()) append(" · ${parent.note.trim()}")
                }
                val child = Transaction(
                    id = UUID.randomUUID().toString(),
                    type = parseTransactionType(parent.type),
                    amountPaise = part.amountPaise,
                    currency = parent.currency,
                    occurredAt = parent.occurredAt,
                    recordedAt = System.currentTimeMillis(),
                    counterparty = party,
                    categoryId = part.categoryId,
                    tabId = part.tabId,
                    accountId = parent.accountId,
                    isCash = parent.isCash,
                    source = TransactionSource.MANUAL,
                    note = partNote.ifBlank { null },
                    classificationStatus = if (part.categoryId != null) {
                        ClassificationStatus.CLASSIFIED
                    } else {
                        ClassificationStatus.PENDING
                    },
                    latitude = parent.latitude,
                    longitude = parent.longitude,
                    placeName = parent.placeName,
                    locationAccuracy = parent.locationAccuracy,
                    locationMatchedAt = parent.locationMatchedAt,
                    splitGroupId = groupId,
                    receiptUri = if (index == 0) parent.receiptUri else null,
                )
                insertManual(child, addToTab = child.tabId != null)
            }
        }
        affectedTabs.forEach { recalculateTabLedger(it) }
        return Result.success(groupId)
    }

    /**
     * Merge a split group back into its parent: soft-delete all parts and restore
     * the parent row (it keeps its own category / tab / note).
     */
    suspend fun mergeSplitGroup(transactionId: String): Result<Unit> {
        val target = txnDao.getById(transactionId)
            ?: return Result.failure(IllegalArgumentException("Transaction not found"))
        val groupId = target.splitGroupId
            ?: return Result.failure(IllegalArgumentException("Not part of a split"))
        val parent = txnDao.getById(groupId)
            ?: return Result.failure(IllegalArgumentException("Split parent not found"))
        if (parent.deletedAt == null) {
            return Result.failure(IllegalArgumentException("Split already merged"))
        }
        val children = txnDao.getAllNonDeleted().filter { it.splitGroupId == groupId }
        if (children.isEmpty()) {
            return Result.failure(IllegalArgumentException("No split parts to merge"))
        }
        val tabs = (children.mapNotNull { it.tabId } + listOfNotNull(parent.tabId)).toSet()

        db.withTransaction {
            children.forEach { child ->
                ledgerDao.deleteForTransaction(child.id)
                txnDao.softDelete(child.id)
                pendingDao.delete(child.id)
                enqueueSync(child.id)
            }
            val reopenClassify =
                parent.categoryId == null &&
                    !parent.isSkipped &&
                    parent.classificationStatus != ClassificationStatus.SKIPPED.name
            txnDao.update(
                parent.copy(
                    deletedAt = null,
                    classificationStatus = if (reopenClassify) {
                        ClassificationStatus.PENDING.name
                    } else {
                        parent.classificationStatus
                    },
                    updatedAt = System.currentTimeMillis(),
                    version = parent.version + 1,
                    sheetsSynced = false,
                )
            )
            if (reopenClassify) scheduleClassification(parent.id)
            enqueueSync(parent.id)
        }
        tabs.forEach { recalculateTabLedger(it) }
        return Result.success(Unit)
    }

    /**
     * Combine [ids] into one transaction row. All rows must be the same type and
     * neither a transfer leg nor a split part. The combined row keeps a category /
     * tab only when every source shares the same value.
     *
     * @return the new transaction id, or null on validation failure.
     */
    suspend fun mergeTransactions(ids: Collection<String>): String? {
        val unique = ids.distinct().filter { it.isNotBlank() }
        if (unique.size < 2) return null
        val loaded = unique.mapNotNull { txnDao.getById(it) }.filter { it.deletedAt == null }
        if (loaded.size < 2) return null
        if (loaded.any { isExcludedFromCashflowKind(it.kind) }) return null
        if (loaded.any { it.transferGroupId != null || it.splitGroupId != null }) return null
        val type = parseTransactionType(loaded.first().type)
        if (loaded.any { parseTransactionType(it.type) != type }) return null

        val total = loaded.sumOf { it.amountPaise }
        if (total <= 0L) return null
        val occurredAt = loaded.maxOf { it.occurredAt }
        val categoryId = if (loaded.map { it.categoryId }.distinct().size == 1) {
            loaded.first().categoryId
        } else {
            null
        }
        val tabId = if (loaded.map { it.tabId }.distinct().size == 1) {
            loaded.first().tabId
        } else {
            null
        }
        val names = loaded.mapNotNull { it.counterparty }
        val party = when {
            names.isEmpty() -> "Merged (${loaded.size})"
            names.distinct().size == 1 -> names.first()
            else -> "${names.first()} +${names.distinct().size - 1} more"
        }
        val joinedNotes = loaded.mapNotNull { it.note?.takeIf { n -> n.isNotBlank() } }
            .distinct()
            .joinToString(" · ")
            .take(500)
        val note = buildString {
            append("Merged from ${loaded.size} transactions")
            if (joinedNotes.isNotBlank()) append(" · $joinedNotes")
        }
        val isCash = loaded.all { it.isCash }

        return db.withTransaction {
            val merged = Transaction(
                id = UUID.randomUUID().toString(),
                type = type,
                amountPaise = total,
                currency = loaded.first().currency,
                occurredAt = occurredAt,
                recordedAt = System.currentTimeMillis(),
                counterparty = party,
                categoryId = categoryId,
                tabId = tabId,
                accountId = loaded.first().accountId,
                isCash = isCash,
                source = TransactionSource.MANUAL,
                note = note,
                classificationStatus = if (categoryId != null) {
                    ClassificationStatus.CLASSIFIED
                } else {
                    ClassificationStatus.PENDING
                },
            )
            val newId = insertManual(merged, addToTab = tabId != null)
            loaded.forEach { delete(it.id) }
            newId
        }
    }

    /**
     * Insert a manual transaction, optionally replacing it with split parts.
     * When [parts] is non-empty no parent row is created — only the parts,
     * sharing [Transaction.splitGroupId] = the given txn id.
     *
     * @return the first created child id (or the parent id when unsplit).
     */
    suspend fun insertManualWithSplits(
        txn: Transaction,
        parts: List<SplitPart>,
        addToTab: Boolean = false,
    ): String = db.withTransaction {
        val groupId = txn.id.ifBlank { UUID.randomUUID().toString() }
        if (parts.isEmpty()) {
            val id = insertManual(txn.copy(id = groupId), addToTab)
            return@withTransaction id
        }
        SplitRules.validateSum(txn.amountPaise, parts.map { it.amountPaise })
            ?.let { throw IllegalArgumentException(it) }
        if (parts.any { it.amountPaise <= 0L }) {
            throw IllegalArgumentException("Each part must be greater than zero")
        }
        val partyBase = txn.counterparty
        val parentLabel = partyBase ?: "transaction"
        val firstId = parts.first().let { firstPart ->
            val party = firstPart.counterparty?.trim()?.takeIf { it.isNotBlank() } ?: partyBase
            val partNote = buildString {
                append("Split from $parentLabel")
                if (!firstPart.note.isNullOrBlank()) append(" · ${firstPart.note.trim()}")
                else if (!txn.note.isNullOrBlank()) append(" · ${txn.note.trim()}")
            }
            val child = txn.copy(
                id = UUID.randomUUID().toString(),
                amountPaise = firstPart.amountPaise,
                counterparty = party,
                categoryId = firstPart.categoryId,
                tabId = firstPart.tabId,
                note = partNote.ifBlank { null },
                splitGroupId = groupId,
                smsMessageId = null,
                externalRefId = null,
                receiptUri = txn.receiptUri,
                classificationStatus = if (firstPart.categoryId != null) {
                    ClassificationStatus.CLASSIFIED
                } else {
                    ClassificationStatus.PENDING
                },
            )
            insertManual(child, addToTab = child.tabId != null)
        }
        parts.drop(1).forEach { part ->
            val party = part.counterparty?.trim()?.takeIf { it.isNotBlank() } ?: partyBase
            val partNote = buildString {
                append("Split from $parentLabel")
                if (!part.note.isNullOrBlank()) append(" · ${part.note.trim()}")
                else if (!txn.note.isNullOrBlank()) append(" · ${txn.note.trim()}")
            }
            val child = txn.copy(
                id = UUID.randomUUID().toString(),
                amountPaise = part.amountPaise,
                counterparty = party,
                categoryId = part.categoryId,
                tabId = part.tabId,
                note = partNote.ifBlank { null },
                splitGroupId = groupId,
                smsMessageId = null,
                externalRefId = null,
                receiptUri = null,
                classificationStatus = if (part.categoryId != null) {
                    ClassificationStatus.CLASSIFIED
                } else {
                    ClassificationStatus.PENDING
                },
            )
            insertManual(child, addToTab = child.tabId != null)
        }
        firstId
    }

    suspend fun insertManual(txn: Transaction, addToTab: Boolean = false): String {
        val id = txn.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val hash = contentHash(txn.type, txn.amountPaise, txn.occurredAt, txn.counterparty, txn.externalRefId, "manual-$id")
        val entity = txn.copy(
            id = id,
            contentHash = hash,
            updatedAt = now,
            sheetsSynced = false,
            classificationStatus = if (txn.categoryId != null) ClassificationStatus.CLASSIFIED else ClassificationStatus.PENDING,
        ).toEntity()
        val rowId = txnDao.insert(entity)
        if (rowId == -1L) return id
        handleTabOnInsert(entity.id, entity.tabId, entity.type, entity.amountPaise, addToTab, entity.note)
        if (entity.classificationStatus == ClassificationStatus.PENDING.name) {
            scheduleClassification(entity.id)
        }
        enqueueSync(entity.id)
        return id
    }

    suspend fun insertFromSms(txn: Transaction): String? {
        val hash = txn.contentHash ?: contentHash(
            txn.type, txn.amountPaise, txn.occurredAt, txn.counterparty, txn.externalRefId, txn.smsMessageId
        )
        if (txn.smsMessageId != null && txnDao.findBySmsMessageId(txn.smsMessageId) != null) return null
        val duplicate = txn.externalRefId?.takeIf { it.isNotBlank() }?.let { txnDao.findByExternalRefId(it) }
            ?: txnDao.findSimilar(
                type = txn.type.name,
                amountPaise = txn.amountPaise,
                fromTs = txn.occurredAt - 10 * 60_000L,
                toTs = txn.occurredAt + 10 * 60_000L,
                targetTs = txn.occurredAt,
            )
        if (duplicate != null) {
            // A new SMS parse can be richer than an earlier one — refresh in place.
            if (duplicate.source == TransactionSource.SMS.name) {
                txnDao.update(
                    duplicate.copy(
                        source = TransactionSource.SMS.name,
                        smsMessageId = txn.smsMessageId ?: duplicate.smsMessageId,
                        externalRefId = txn.externalRefId ?: duplicate.externalRefId,
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
            party = txn.counterparty,
            externalRef = txn.externalRefId,
            extra = "import-${txn.accountId}-${txn.rawDescription?.take(40).orEmpty()}",
        )
        if (txnDao.findByContentHash(hash) != null) return null
        txn.externalRefId?.takeIf { it.isNotBlank() }?.let { ref ->
            val existing = txnDao.findByExternalRefId(ref)
            if (existing != null && existing.accountId == txn.accountId) {
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
    suspend fun getForAccount(accountId: Long): List<Transaction> {
        val cats = categoryDao.getAll().associate { it.id to it.toDomain() }
        val tabs = tabDao.getAll().associate { it.id to it.name }
        val accounts = accountDao.getAll().associate { it.id to it.name }
        return txnDao.getForAccount(accountId).map { e ->
            val cat = e.categoryId?.let { cats[it] }
            e.toDomain(
                category = cat,
                tabName = e.tabId?.let { tabs[it] },
                accountName = e.accountId?.let { accounts[it] },
            )
        }
    }

    suspend fun update(txn: Transaction) {
        val existing = txnDao.getById(txn.id) ?: return
        val oldTab = existing.tabId
        val updated = txn.copy(
            updatedAt = System.currentTimeMillis(),
            version = existing.version + 1,
            sheetsSynced = false,
            classificationStatus = if (txn.categoryId != null) ClassificationStatus.CLASSIFIED else txn.classificationStatus,
        )
        txnDao.update(updated.toEntity())
        val tabsToRebuild = mutableSetOf<Long>()
        if (oldTab != null) tabsToRebuild.add(oldTab)
        if (updated.tabId != null) tabsToRebuild.add(updated.tabId)
        if (oldTab != updated.tabId ||
            existing.amountPaise != updated.amountPaise ||
            existing.type != updated.type.name
        ) {
            tabsToRebuild.forEach { recalculateTabLedger(it) }
        }
        pendingDao.delete(txn.id)
        enqueueSync(txn.id)
    }

    suspend fun classify(
        transactionId: String,
        categoryId: Long?,
        note: String?,
        tabId: Long?,
        receiptUri: String? = null,
    ) {
        val existing = txnDao.getById(transactionId) ?: return
        val mergedNote = when {
            note.isNullOrBlank() -> existing.note
            existing.note.isNullOrBlank() -> note
            existing.note == note -> existing.note
            else -> "${existing.note} · $note"
        }
        val newTab = tabId ?: existing.tabId
        val newCat = categoryId ?: existing.categoryId
        val status = if (newCat != null || !mergedNote.isNullOrBlank() || newTab != null) {
            if (newCat != null) ClassificationStatus.CLASSIFIED.name else existing.classificationStatus
        } else existing.classificationStatus
        val updated = existing.copy(
            categoryId = newCat,
            note = mergedNote,
            tabId = newTab,
            classificationStatus = status,
            updatedAt = System.currentTimeMillis(),
            version = existing.version + 1,
            sheetsSynced = false,
            receiptUri = receiptUri ?: existing.receiptUri,
        )
        txnDao.update(updated)
        if (existing.tabId != updated.tabId) {
            // Rebuild both sides so old tab loses the spend and new tab gains it cleanly
            if (existing.tabId != null) recalculateTabLedger(existing.tabId)
            if (updated.tabId != null) recalculateTabLedger(updated.tabId)
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
            val tabsToRebuild = legs.mapNotNull { it.tabId }.toSet()
            db.withTransaction {
                for (leg in legs) {
                    ledgerDao.deleteForTransaction(leg.id)
                    txnDao.softDelete(leg.id)
                    pendingDao.delete(leg.id)
                }
            }
            tabsToRebuild.forEach { recalculateTabLedger(it) }
            legs.forEach { enqueueSync(it.id) }
            return
        }
        ledgerDao.deleteForTransaction(id)
        txnDao.softDelete(id)
        existing.tabId?.let { recalculateTabLedger(it) }
        pendingDao.delete(id)
        enqueueSync(id)
    }

    /**
     * Self transfer between owned accounts: debit on [fromAccountId], credit on [toAccountId].
     * No category / tab / splits. Both legs share [transferGroupId] and kind SELF_TRANSFER.
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

    suspend fun getRecommendedTabForCategory(categoryId: Long): Long? {
        val txns = txnDao.getAllForCategory(categoryId)
            .filter { it.tabId != null && it.deletedAt == null }
        if (txns.isEmpty()) return null
        return txns.groupBy { it.tabId }
            .maxByOrNull { (_, items) -> items.size }
            ?.key
    }

    /** categoryId -> use count (most used first from DAO). */
    fun observeCategoryUsage(): Flow<Map<Long, Long>> =
        txnDao.observeCategoryUsage().map { rows -> rows.associate { it.id to it.useCount } }

    /** accountId -> use count (most used first from DAO). */
    fun observeAccountUsage(): Flow<Map<Long, Long>> =
        txnDao.observeAccountUsage().map { rows -> rows.associate { it.id to it.useCount } }

    /**
     * Open Tab balances (tab table).
     * balance = debits − credits  (+ they owe you / − you owe them).
     * Split parts are standalone rows, so each row is counted once against its tab.
     * Self-transfers never sit on tabs.
     */
    fun observeTabs(): Flow<List<TabBalance>> = combine(
        tabDao.observeActive(),
        txnDao.observeAll(),
    ) { tabs, allTxns ->
        val byTab = allTxns
            .asSequence()
            .filter { it.kind != TransactionKind.SELF_TRANSFER.name }
            .filter { it.tabId != null }
            .groupBy { it.tabId!! }
        tabs.map { tab ->
            val rows = byTab[tab.id].orEmpty()
            val credits = rows.filter { isCreditType(it.type) }.sumOf { it.amountPaise }
            val debits = rows.filter { isDebitType(it.type) }.sumOf { it.amountPaise }
            TabBalance(
                tab = tab.toDomain(),
                balancePaise = debits - credits,
                creditedPaise = credits,
                debitedPaise = debits,
            )
        }
    }

    suspend fun addTab(name: String): Long {
        val id = tabDao.upsert(
            TabEntity(
                name = name.trim(),
            ),
        )
        // Keep ledger aligned (history); display uses transactions only
        recalculateTabLedger(id)
        return id
    }

    /** Rebuild every active tab ledger from linked transactions. */
    suspend fun repairAllTabLedgers() {
        tabDao.getAll().filter { !it.archived }.forEach { recalculateTabLedger(it.id) }
    }

    suspend fun deleteTab(tabId: Long) {
        val tab = tabDao.getById(tabId) ?: return
        tabDao.update(tab.copy(archived = true))
    }

    /** Current open balance for [tabId]: debits − credits across non-self-transfer rows. */
    private suspend fun tabBalancePaise(tabId: Long): Long? {
        val tab = tabDao.getById(tabId) ?: return null
        val rows = txnDao.getAllForTab(tabId)
            .asSequence()
            .filter { it.kind != TransactionKind.SELF_TRANSFER.name }
        val credits = rows.filter { isCreditType(it.type) }.sumOf { it.amountPaise }
        val debits = rows.filter { isDebitType(it.type) }.sumOf { it.amountPaise }
        return debits - credits
    }

    /**
     * Close a tab's open balance to zero with a bookkeeping entry.
     *
     * If they owe you, a **CREDIT** records the settlement (balance drops to zero);
     * if you owe them, a **DEBIT** records your payment. Kind is
     * [TransactionKind.TAB_TRANSFER] so the entry never enters lifestyle/credit metrics
     * or the category screens, but it stays visible in the tab's activity.
     *
     * @return true if a settlement was recorded, false if the tab is missing or already settled.
     */
    suspend fun settleTab(tabId: Long): Boolean {
        val balance = tabBalancePaise(tabId) ?: return false
        if (balance == 0L) return false
        val tab = tabDao.getById(tabId) ?: return false
        val isCredit = balance > 0L
        val amount = kotlin.math.abs(balance)
        val now = System.currentTimeMillis()
        val id = "settle_" + UUID.randomUUID().toString()
        val txn = Transaction(
            id = id,
            type = if (isCredit) TransactionType.CREDIT else TransactionType.DEBIT,
            amountPaise = amount,
            occurredAt = now,
            tabId = tabId,
            source = TransactionSource.MANUAL,
            note = "Settled · ${tab.name}",
            classificationStatus = ClassificationStatus.CLASSIFIED,
            kind = TransactionKind.TAB_TRANSFER,
        )
        val hash = contentHash(
            txn.type, txn.amountPaise, txn.occurredAt, txn.counterparty, txn.externalRefId, "manual-$id",
        )
        val entity = txn.copy(contentHash = hash, updatedAt = now, sheetsSynced = false).toEntity()
        db.withTransaction {
            txnDao.insert(entity)
            handleTabOnInsert(entity.id, entity.tabId, entity.type, entity.amountPaise, true, entity.note)
            enqueueSync(entity.id)
        }
        return true
    }

    /**
     * Move open-tab balance from [fromTabId] to [toTabId].
     *
     * Open-tab formula is `debits − credits`, so:
     * - source tab gets a **CREDIT** (balance decreases — less they owe you)
     * - destination gets a **DEBIT** (balance increases)
     *
     * Kind is [TransactionKind.TAB_TRANSFER] so these rows never enter lifestyle/credit metrics.
     */
    suspend fun transferBetweenTabs(
        fromTabId: Long,
        toTabId: Long,
        amountPaise: Long,
        note: String? = null,
    ) {
        if (fromTabId == toTabId || amountPaise <= 0L) return
        val fromName = tabDao.getById(fromTabId)?.name ?: "source"
        val toName = tabDao.getById(toTabId)?.name ?: "target"
        val now = System.currentTimeMillis()
        val transferRef = UUID.randomUUID().toString()
        // CREDIT on source reduces open-tab balance; DEBIT on dest increases it.
        val txnOut = Transaction(
            id = "${transferRef}_out",
            type = TransactionType.CREDIT,
            amountPaise = amountPaise,
            occurredAt = now,
            tabId = fromTabId,
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
            tabId = toTabId,
            source = TransactionSource.MANUAL,
            note = note ?: "Transfer from $fromName",
            externalRefId = "fund_transfer_${transferRef}_in",
            classificationStatus = ClassificationStatus.CLASSIFIED,
            kind = TransactionKind.TAB_TRANSFER,
            transferGroupId = transferRef,
        )
        db.withTransaction {
            insertManual(txnOut, addToTab = true)
            insertManual(txnIn, addToTab = true)
        }
    }

    suspend fun creditTabFromIncome(tabId: Long, transactionId: String, amountPaise: Long, note: String?) {
        val current = ledgerDao.latestBalance(tabId) ?: 0L
        // Open-tab signs: credit decreases balance (they paid you / settled).
        val after = current - amountPaise
        ledgerDao.insert(
            TabLedgerEntity(
                tabId = tabId,
                transactionId = transactionId,
                entryType = TabEntryType.CREDIT.name,
                amountPaise = amountPaise,
                balanceAfterPaise = after,
                note = note,
            )
        )
    }

    /**
     * Ledger rebuild aligned with open-tab formula:
     * `balance = debits − credits`.
     * Single baseline (zero) + every linked transaction / split part.
     */
    private suspend fun recalculateTabLedger(tabId: Long) {
        if (tabDao.getById(tabId) == null) return

        ledgerDao.deleteAllForTab(tabId)

        var runningBalance = 0L

        // Split parts are standalone rows, so tab linking is a direct row filter.
        // Scope the scan to this tab's rows instead of the whole transactions table.
        val hits = txnDao.getAllForTab(tabId)
            .asSequence()
            .filter { it.kind != TransactionKind.SELF_TRANSFER.name }
            .sortedWith(compareBy({ it.occurredAt }, { it.id }))

        for (txn in hits) {
            val isCredit = isCreditType(txn.type)
            val amount = txn.amountPaise
            // debits − credits
            runningBalance += if (isCredit) -amount else amount
            ledgerDao.insert(
                TabLedgerEntity(
                    tabId = tabId,
                    transactionId = txn.id,
                    entryType = if (isCredit) {
                        TabEntryType.CREDIT.name
                    } else {
                        TabEntryType.DEBIT.name
                    },
                    amountPaise = amount,
                    balanceAfterPaise = runningBalance,
                    note = txn.note,
                    createdAt = txn.occurredAt,
                ),
            )
        }
    }

    private suspend fun handleTabOnInsert(
        transactionId: String,
        tabId: Long?,
        type: String,
        amountPaise: Long,
        @Suppress("UNUSED_PARAMETER") addToTab: Boolean,
        note: String?,
    ) {
        if (tabId == null) return
        val current = ledgerDao.latestBalance(tabId) ?: 0L
        // Open-tab: debits raise balance (they owe you more), credits lower it.
        when {
            isCreditType(type) -> {
                val after = current - amountPaise
                ledgerDao.insert(
                    TabLedgerEntity(
                        tabId = tabId,
                        transactionId = transactionId,
                        entryType = TabEntryType.CREDIT.name,
                        amountPaise = amountPaise,
                        balanceAfterPaise = after,
                        note = note,
                    ),
                )
            }
            isDebitType(type) -> {
                val after = current + amountPaise
                ledgerDao.insert(
                    TabLedgerEntity(
                        tabId = tabId,
                        transactionId = transactionId,
                        entryType = TabEntryType.DEBIT.name,
                        amountPaise = amountPaise,
                        balanceAfterPaise = after,
                        note = note,
                    ),
                )
            }
        }
    }

    private fun isCreditType(type: String): Boolean =
        type.uppercase() == TransactionType.CREDIT.name

    private fun isDebitType(type: String): Boolean =
        type.uppercase() == TransactionType.DEBIT.name

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
            party: String?,
            externalRef: String?,
            extra: String?,
        ): String {
            val bucket = occurredAt / 120_000
            val raw = listOf(type.name, amountPaise, bucket, party.orEmpty(), externalRef.orEmpty(), extra.orEmpty())
                .joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
