package com.krtky.financetracker.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE isQuickAction = 1 ORDER BY sortOrder LIMIT 6")
    suspend fun getQuickActions(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE archived = 1 ORDER BY sortOrder, name")
    fun observeArchived(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY archived ASC, sortOrder, name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AccountEntity): Long

    @Update
    suspend fun update(entity: AccountEntity)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}

@Dao
interface FundDao {
    @Query("SELECT * FROM funds WHERE archived = 0 ORDER BY name")
    fun observeActive(): Flow<List<FundEntity>>

    @Query("SELECT * FROM funds ORDER BY name")
    suspend fun getAll(): List<FundEntity>

    @Query("SELECT * FROM funds WHERE id = :id")
    suspend fun getById(id: Long): FundEntity?

    @Query("SELECT * FROM funds WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): FundEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FundEntity): Long

    @Update
    suspend fun update(entity: FundEntity)
}

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
        ORDER BY occurredAt DESC, recordedAt DESC, id DESC
        """
    )
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
        ORDER BY occurredAt DESC, recordedAt DESC, id DESC
        """
    )
    suspend fun getAllNonDeleted(): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND (
            accountId = :accountId
            OR (accountId IS NULL AND paymentMethod = :accountName COLLATE NOCASE)
          )
        ORDER BY occurredAt DESC
        """
    )
    suspend fun getForAccount(accountId: Long, accountName: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND (:query = '' OR merchant LIKE '%' || :query || '%' OR counterparty LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' OR paymentMethod LIKE '%' || :query || '%' OR rawDescription LIKE '%' || :query || '%')
          AND (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:fundId IS NULL OR fundId = :fundId)
          AND (:accountId IS NULL OR accountId = :accountId)
          AND occurredAt >= :fromTs AND occurredAt <= :toTs
        ORDER BY occurredAt DESC, recordedAt DESC, id DESC
        """
    )
    fun observeFiltered(
        query: String,
        type: String?,
        categoryId: Long?,
        fundId: Long?,
        fromTs: Long,
        toTs: Long,
        accountId: Long?,
    ): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query(
        """
        UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt, sheetsSynced = 0, version = version + 1
        WHERE id = :id
        """
    )
    suspend fun softDelete(id: String, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM transactions WHERE emailMessageId = :messageId LIMIT 1")
    suspend fun findByEmailMessageId(messageId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE contentHash = :hash LIMIT 1")
    suspend fun findByContentHash(hash: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL AND externalRefId = :ref LIMIT 1")
    suspend fun findByExternalRefId(ref: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL AND type = :type AND amountPaise = :amountPaise AND occurredAt BETWEEN :fromTs AND :toTs ORDER BY ABS(occurredAt - :targetTs) LIMIT 1")
    suspend fun findSimilar(type: String, amountPaise: Long, fromTs: Long, toTs: Long, targetTs: Long): TransactionEntity?

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0) FROM transactions
        WHERE deletedAt IS NULL
          AND type = :type
          AND (kind IS NULL OR kind = 'NORMAL')
          AND occurredAt >= :fromTs AND occurredAt <= :toTs
        """
    )
    suspend fun sumByType(type: String, fromTs: Long, toTs: Long): Long

    @Query(
        """
        SELECT categoryId AS categoryId,
               COALESCE((SELECT name FROM categories c WHERE c.id = t.categoryId), 'Uncategorized') AS categoryName,
               SUM(amountPaise) AS totalPaise
        FROM transactions t
        WHERE deletedAt IS NULL
          AND type IN ('DEBIT', 'EXPENSE')
          AND (kind IS NULL OR kind = 'NORMAL')
          AND occurredAt >= :fromTs AND occurredAt <= :toTs
        GROUP BY categoryId
        ORDER BY totalPaise DESC
        """
    )
    suspend fun categorySpend(fromTs: Long, toTs: Long): List<CategorySpendRow>

    @Query(
        """
        SELECT categoryId AS id, COUNT(*) AS useCount
        FROM transactions
        WHERE deletedAt IS NULL AND categoryId IS NOT NULL
        GROUP BY categoryId
        ORDER BY useCount DESC
        """
    )
    fun observeCategoryUsage(): Flow<List<UsageCountRow>>

    @Query(
        """
        SELECT paymentMethod AS id, COUNT(*) AS useCount
        FROM transactions
        WHERE deletedAt IS NULL AND paymentMethod IS NOT NULL AND paymentMethod != ''
        GROUP BY paymentMethod
        ORDER BY useCount DESC
        """
    )
    fun observePaymentMethodUsage(): Flow<List<UsageCountStringRow>>

    @Query(
        """
        SELECT accountId AS id, COUNT(*) AS useCount
        FROM transactions
        WHERE deletedAt IS NULL AND accountId IS NOT NULL
        GROUP BY accountId
        ORDER BY useCount DESC
        """
    )
    fun observeAccountUsage(): Flow<List<UsageCountRow>>

    @Query(
        """
        SELECT strftime('%Y-%m', occurredAt / 1000, 'unixepoch', 'localtime') AS monthKey,
               COALESCE(SUM(CASE WHEN type IN ('CREDIT', 'INCOME') THEN amountPaise ELSE 0 END), 0) AS incomePaise,
               COALESCE(SUM(CASE WHEN type IN ('DEBIT', 'EXPENSE') THEN amountPaise ELSE 0 END), 0) AS expensePaise
        FROM transactions
        WHERE deletedAt IS NULL
          AND (kind IS NULL OR kind = 'NORMAL')
          AND occurredAt >= :fromTs AND occurredAt <= :toTs
        GROUP BY monthKey
        ORDER BY monthKey ASC
        """
    )
    suspend fun monthlyTrend(fromTs: Long, toTs: Long): List<MonthlyTrendRow>

    @Query(
        """
        SELECT * FROM transactions
        WHERE transferGroupId = :groupId AND deletedAt IS NULL
        """
    )
    suspend fun getByTransferGroup(groupId: String): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId AND deletedAt IS NULL
        ORDER BY occurredAt DESC
        """
    )
    suspend fun getAllForAccount(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE sheetsSynced = 0 AND deletedAt IS NULL")
    suspend fun getUnsynced(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE sheetsSynced = 0")
    suspend fun getDirtyIncludingDeleted(): List<TransactionEntity>

    @Query("UPDATE transactions SET sheetsSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("""
        SELECT * FROM transactions 
        WHERE fundId = :fundId 
          AND deletedAt IS NULL 
        ORDER BY occurredAt ASC
    """)
    suspend fun getAllForFund(fundId: Long): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions
        WHERE categoryId = :categoryId
          AND deletedAt IS NULL
        ORDER BY occurredAt DESC
    """)
    suspend fun getAllForCategory(categoryId: Long): List<TransactionEntity>
}

data class CategorySpendRow(
    val categoryId: Long?,
    val categoryName: String,
    val totalPaise: Long,
)

data class MonthlyTrendRow(
    val monthKey: String,
    val incomePaise: Long,
    val expensePaise: Long,
)

data class UsageCountRow(
    val id: Long,
    val useCount: Long,
)

data class UsageCountStringRow(
    val id: String,
    val useCount: Long,
)

@Dao
interface FundLedgerDao {
    @Insert
    suspend fun insert(entity: FundLedgerEntity): Long

    @Query("SELECT * FROM fund_ledger WHERE fundId = :fundId ORDER BY createdAt DESC")
    fun observeForFund(fundId: Long): Flow<List<FundLedgerEntity>>

    @Query("SELECT * FROM fund_ledger WHERE fundId = :fundId ORDER BY createdAt DESC")
    suspend fun getForFund(fundId: Long): List<FundLedgerEntity>

    @Query("SELECT balanceAfterPaise FROM fund_ledger WHERE fundId = :fundId ORDER BY id DESC LIMIT 1")
    suspend fun latestBalance(fundId: Long): Long?

    @Query("SELECT * FROM fund_ledger ORDER BY id DESC")
    fun observeAll(): Flow<List<FundLedgerEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE
                WHEN entryType = 'CREDIT' THEN amountPaise
                WHEN entryType = 'ADJUSTMENT' AND amountPaise > 0 THEN amountPaise
                ELSE 0
            END
        ), 0)
        FROM fund_ledger WHERE fundId = :fundId
        """
    )
    suspend fun totalCredits(fundId: Long): Long

    @Query(
        """
        SELECT COALESCE(ABS(SUM(
            CASE
                WHEN entryType = 'DEBIT' THEN amountPaise
                WHEN entryType = 'ADJUSTMENT' AND amountPaise < 0 THEN amountPaise
                ELSE 0
            END
        )), 0)
        FROM fund_ledger WHERE fundId = :fundId
        """
    )
    suspend fun totalDebits(fundId: Long): Long

    @Query("DELETE FROM fund_ledger WHERE transactionId = :transactionId")
    suspend fun deleteForTransaction(transactionId: String)

    @Query("DELETE FROM fund_ledger WHERE fundId = :fundId")
    suspend fun deleteAllForFund(fundId: Long)
}

@Dao
interface TrustedSenderDao {
    @Query("SELECT * FROM trusted_senders ORDER BY walletLabel, emailPattern")
    fun observeAll(): Flow<List<TrustedSenderEntity>>

    @Query("SELECT * FROM trusted_senders WHERE enabled = 1")
    suspend fun getEnabled(): List<TrustedSenderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrustedSenderEntity): Long

    @Query("DELETE FROM trusted_senders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EmailIngestDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: EmailIngestLogEntity): Long

    @Query("SELECT * FROM email_ingest_log WHERE messageId = :messageId LIMIT 1")
    suspend fun findByMessageId(messageId: String): EmailIngestLogEntity?

    @Update
    suspend fun update(entity: EmailIngestLogEntity)

    @Query("SELECT * FROM email_ingest_log ORDER BY createdAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<EmailIngestLogEntity>>
}

@Dao
interface LocationSampleDao {
    @Insert
    suspend fun insert(entity: LocationSampleEntity): Long

    @Query(
        """
        SELECT * FROM location_samples
        WHERE capturedAt BETWEEN :fromTs AND :toTs
        ORDER BY ABS(capturedAt - :targetTs) ASC
        LIMIT 1
        """
    )
    suspend fun findClosest(fromTs: Long, toTs: Long, targetTs: Long): LocationSampleEntity?

    @Query("SELECT * FROM location_samples ORDER BY capturedAt DESC LIMIT 1")
    suspend fun latest(): LocationSampleEntity?

    @Query("DELETE FROM location_samples WHERE capturedAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}

@Dao
interface PendingClassificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingClassificationEntity)

    @Query("SELECT * FROM pending_classification WHERE status = 'SCHEDULED' AND scheduledAt <= :now")
    suspend fun due(now: Long): List<PendingClassificationEntity>

    @Query("DELETE FROM pending_classification WHERE transactionId = :id")
    suspend fun delete(id: String)

    @Update
    suspend fun update(entity: PendingClassificationEntity)
}

@Dao
interface SyncOutboxDao {
    @Insert
    suspend fun insert(entity: SyncOutboxEntity): Long

    @Query("SELECT * FROM sync_outbox ORDER BY id ASC LIMIT 100")
    suspend fun peek(): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sync_outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)
}

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SyncStateEntity)
}
