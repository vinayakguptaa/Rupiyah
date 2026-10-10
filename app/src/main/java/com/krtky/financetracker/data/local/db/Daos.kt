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
interface TabDao {
    @Query("SELECT * FROM tabs WHERE archived = 0 ORDER BY name")
    fun observeActive(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE archived = 1 ORDER BY name")
    fun observeArchived(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY name")
    suspend fun getAll(): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE id = :id")
    suspend fun getById(id: Long): TabEntity?

    @Query("SELECT * FROM tabs WHERE id = :id")
    fun observeById(id: Long): Flow<TabEntity?>

    @Query("SELECT * FROM tabs WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TabEntity): Long

    @Update
    suspend fun update(entity: TabEntity)
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
          AND accountId = :accountId
        ORDER BY occurredAt DESC
        """
    )
    suspend fun getForAccount(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND (:query = '' OR counterparty LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' OR rawDescription LIKE '%' || :query || '%')
          AND (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:tabId IS NULL OR tabId = :tabId)
          AND (:tabId IS NOT NULL OR COALESCE(kind, 'NORMAL') != 'TAB_TRANSFER')
          AND (
            (:unassignedOnly = 0 AND (:accountId IS NULL OR accountId = :accountId)) OR
            (:unassignedOnly = 1 AND accountId IS NULL AND isCash = 0 AND COALESCE(kind, 'NORMAL') != 'TAB_TRANSFER')
          )
          AND occurredAt >= :fromTs AND occurredAt <= :toTs
        ORDER BY occurredAt DESC, recordedAt DESC, id DESC
        """
    )
    fun observeFiltered(
        query: String,
        type: String?,
        categoryId: Long?,
        tabId: Long?,
        fromTs: Long,
        toTs: Long,
        accountId: Long?,
        unassignedOnly: Boolean = false,
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

    @Query("SELECT * FROM transactions WHERE smsMessageId = :messageId LIMIT 1")
    suspend fun findBySmsMessageId(messageId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE contentHash = :hash LIMIT 1")
    suspend fun findByContentHash(hash: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL AND externalRefId = :ref LIMIT 1")
    suspend fun findByExternalRefId(ref: String): TransactionEntity?

    /**
     * Same type + amount close in time. Rows on two different known accounts are separate legs
     * (e.g. SBI debit paying a OneCard bill), so they never match each other.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL AND type = :type AND amountPaise = :amountPaise
          AND occurredAt BETWEEN :fromTs AND :toTs
          AND (:accountId IS NULL OR accountId IS NULL OR accountId = :accountId)
        ORDER BY ABS(occurredAt - :targetTs) LIMIT 1
        """
    )
    suspend fun findSimilar(
        type: String,
        amountPaise: Long,
        fromTs: Long,
        toTs: Long,
        targetTs: Long,
        accountId: Long? = null,
    ): TransactionEntity?

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
          AND type = 'DEBIT'
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
        SELECT tabId AS id, COUNT(*) AS useCount
        FROM transactions
        WHERE deletedAt IS NULL AND tabId IS NOT NULL
        GROUP BY tabId
        ORDER BY useCount DESC
        """
    )
    fun observeTabUsage(): Flow<List<UsageCountRow>>

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE deletedAt IS NULL
          AND categoryId IS NULL
          AND isSkipped = 0
          AND classificationStatus != 'SKIPPED'
          AND classificationStatus != 'CLASSIFIED'
          AND (kind IS NULL OR (kind != 'SELF_TRANSFER' AND kind != 'TAB_TRANSFER'))
        """
    )
    fun observePendingClassificationCount(): Flow<Int>

    @Query(
        """
        SELECT id FROM transactions
        WHERE deletedAt IS NULL
          AND categoryId IS NULL
          AND isSkipped = 0
          AND classificationStatus != 'SKIPPED'
          AND classificationStatus != 'CLASSIFIED'
          AND (kind IS NULL OR (kind != 'SELF_TRANSFER' AND kind != 'TAB_TRANSFER'))
        ORDER BY occurredAt ASC
        LIMIT 1
        """
    )
    fun observeFirstPendingClassificationId(): Flow<String?>

    @Query(
        """
        SELECT accountId, 
               SUM(CASE WHEN type = 'CREDIT' THEN amountPaise ELSE -amountPaise END) AS netPaise,
               COUNT(*) AS txnCount
        FROM transactions
        WHERE deletedAt IS NULL AND (kind IS NULL OR kind != 'TAB_TRANSFER')
        GROUP BY accountId
        """
    )
    fun observeAccountNets(): Flow<List<AccountNetDto>>

    @Query(
        """
        SELECT tabId,
               COALESCE(SUM(CASE WHEN type = 'DEBIT' THEN amountPaise ELSE 0 END), 0) AS debitsPaise,
               COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amountPaise ELSE 0 END), 0) AS creditsPaise
        FROM transactions
        WHERE deletedAt IS NULL
          AND tabId IS NOT NULL
          AND (kind IS NULL OR kind != 'SELF_TRANSFER')
        GROUP BY tabId
        """
    )
    fun observeTabAggregates(): Flow<List<TabAggregateDto>>

    @Query(
        """
        SELECT COUNT(*) AS count,
               COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amountPaise ELSE -amountPaise END), 0) AS netPaise
        FROM transactions
        WHERE deletedAt IS NULL
          AND accountId IS NULL
          AND isCash = 0
          AND (kind IS NULL OR kind != 'TAB_TRANSFER')
        """
    )
    fun observeUnassignedDigital(): Flow<UnassignedDigitalDto>

    @Query(
        """
        SELECT strftime('%Y-%m', occurredAt / 1000, 'unixepoch', 'localtime') AS monthKey,
               COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amountPaise ELSE 0 END), 0) AS incomePaise,
               COALESCE(SUM(CASE WHEN type = 'DEBIT' THEN amountPaise ELSE 0 END), 0) AS expensePaise
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

    @Query("SELECT * FROM transactions WHERE sheetsSynced = 0 AND deletedAt IS NULL AND COALESCE(kind, 'NORMAL') != 'TAB_TRANSFER'")
    suspend fun getUnsynced(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE sheetsSynced = 0 AND COALESCE(kind, 'NORMAL') != 'TAB_TRANSFER'")
    suspend fun getDirtyIncludingDeleted(): List<TransactionEntity>

    @Query("UPDATE transactions SET sheetsSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("""
        SELECT * FROM transactions 
        WHERE tabId = :tabId 
          AND deletedAt IS NULL 
        ORDER BY occurredAt ASC
    """)
    suspend fun getAllForTab(tabId: Long): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions
        WHERE categoryId = :categoryId
          AND deletedAt IS NULL
        ORDER BY occurredAt DESC
    """)
    suspend fun getAllForCategory(categoryId: Long): List<TransactionEntity>
}

data class AccountNetDto(
    val accountId: Long?,
    val netPaise: Long,
    val txnCount: Long = 0L,
)

data class TabAggregateDto(
    val tabId: Long,
    val debitsPaise: Long,
    val creditsPaise: Long,
)

data class UnassignedDigitalDto(
    val count: Int,
    val netPaise: Long,
)

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

    @Query("DELETE FROM pending_classification WHERE transactionId IN (SELECT id FROM transactions WHERE kind = 'TAB_TRANSFER')")
    suspend fun deleteForTabTransfers()

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

    @Query("DELETE FROM sync_outbox WHERE entityType = 'transaction' AND entityId IN (SELECT id FROM transactions WHERE kind = 'TAB_TRANSFER')")
    suspend fun deleteForTabTransfers()

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
