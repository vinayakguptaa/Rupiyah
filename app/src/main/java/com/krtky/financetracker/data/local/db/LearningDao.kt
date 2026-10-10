package com.krtky.financetracker.data.local.db

import androidx.room.Dao
import androidx.room.Query

/** Past categorised rows used to learn "this payee → that category". */
data class LearningRow(
    val counterparty: String?,
    val rawDescription: String?,
    val categoryId: Long,
    val type: String,
)

@Dao
interface LearningDao {
    @Query(
        """
        SELECT counterparty, rawDescription, categoryId, type FROM transactions
        WHERE deletedAt IS NULL
          AND categoryId IS NOT NULL
          AND isSkipped = 0
          AND COALESCE(kind, 'NORMAL') = 'NORMAL'
        ORDER BY occurredAt DESC
        LIMIT :limit
        """,
    )
    suspend fun recentClassified(limit: Int): List<LearningRow>
}
