package com.krtky.financetracker.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * One received bank SMS and how far the import pipeline got with it.
 * Stored before any parsing so nothing is lost when a later step fails.
 */
@Entity(
    tableName = "sms_messages",
    indices = [
        Index(value = ["bodyHash"]),
        Index(value = ["receivedAt"]),
        Index(value = ["status"]),
    ],
)
data class SmsMessageEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val body: String,
    /** sha-256 of sender + body; with [receivedAt] it dedupes live vs synced copies. */
    val bodyHash: String,
    val receivedAt: Long,
    /** LIVE (broadcast) | SYNC (read from inbox). */
    val origin: String,
    /** NEW | IGNORED | IMPORTED | DUPLICATE | NEEDS_AI | FAILED */
    val status: String = "NEW",
    val ignoreReason: String? = null,
    /** Bank / card guessed from the sender id. */
    val bank: String? = null,
    /** JSON snapshot of the local parse + category guess. */
    val localSummary: String? = null,
    /** NONE | NOT_NEEDED | PENDING | DONE | FAILED | SKIPPED | REJECTED */
    val aiStatus: String = "NONE",
    /** JSON snapshot of what the AI returned. */
    val aiSummary: String? = null,
    val lastError: String? = null,
    val transactionId: String? = null,
    val attempts: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    /** What the user decided, which later runs respect: FORCE_IMPORT | DISMISSED | MANUAL | REMOVED. */
    val userOverride: String? = null,
)

data class SmsStatusCount(val status: String, val n: Int)

@Dao
interface SmsMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SmsMessageEntity): Long

    @Update
    suspend fun update(entity: SmsMessageEntity)

    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getById(id: String): SmsMessageEntity?

    @Query(
        "SELECT * FROM sms_messages WHERE bodyHash = :hash AND receivedAt BETWEEN :fromTs AND :toTs LIMIT 1",
    )
    suspend fun findNear(hash: String, fromTs: Long, toTs: Long): SmsMessageEntity?

    @Query("SELECT * FROM sms_messages ORDER BY receivedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SmsMessageEntity>>

    /** Work left: unprocessed, waiting for AI, or AI enrichment that failed / is queued. */
    @Query(
        """
        SELECT * FROM sms_messages
        WHERE status IN ('NEW', 'NEEDS_AI') OR aiStatus IN ('PENDING', 'FAILED')
        ORDER BY receivedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun pending(limit: Int): List<SmsMessageEntity>

    @Query("SELECT status, COUNT(*) AS n FROM sms_messages GROUP BY status")
    fun observeCounts(): Flow<List<SmsStatusCount>>

    @Query(
        "SELECT COUNT(*) FROM sms_messages WHERE status IN ('NEW', 'NEEDS_AI') OR aiStatus IN ('PENDING', 'FAILED')",
    )
    fun observePendingCount(): Flow<Int>

    @Query(
        """
        UPDATE sms_messages SET aiStatus = 'PENDING', lastError = NULL
        WHERE aiStatus IN ('SKIPPED', 'FAILED') AND status IN ('IMPORTED', 'NEEDS_AI')
        """,
    )
    suspend fun requeueAi(): Int

    /** Re-run the (cheap, local) filter on messages it rejected, unless the user decided or AI rejected. */
    @Query(
        """
        UPDATE sms_messages SET status = 'NEW', ignoreReason = NULL
        WHERE status = 'IGNORED' AND userOverride IS NULL AND aiStatus != 'REJECTED' AND receivedAt >= :since
        """,
    )
    suspend fun recheckIgnored(since: Long): Int

    @Query("DELETE FROM sms_messages WHERE status = 'IGNORED' AND receivedAt < :cutoff")
    suspend fun pruneIgnored(cutoff: Long)
}
