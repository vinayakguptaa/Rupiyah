package com.krtky.financetracker.data.repository

import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.toDomain
import com.krtky.financetracker.data.local.db.toEntity
import com.krtky.financetracker.domain.model.TrustedSender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrustedSenderRepository @Inject constructor(
    db: AppDatabase,
) {
    private val dao = db.trustedSenderDao()

    fun observeAll(): Flow<List<TrustedSender>> = dao.observeAll().map { it.map { e -> e.toDomain() } }

    suspend fun getEnabled(): List<TrustedSender> = dao.getEnabled().map { it.toDomain() }

    suspend fun upsert(sender: TrustedSender): Long = dao.upsert(sender.toEntity())

    suspend fun delete(id: Long) = dao.delete(id)

    fun matches(senderEmail: String, patterns: List<TrustedSender>): TrustedSender? {
        val normalized = senderEmail.lowercase().trim()
        val enabled = patterns.filter { it.enabled }
        return enabled.firstOrNull { s ->
            val p = s.emailPattern.lowercase().trim()
            when {
                p.isBlank() -> false
                normalized == p -> true
                // domain-only pattern: e.g. bank.com matches alerts@bank.com
                !p.contains("@") && normalized.endsWith("@$p") -> true
                // full email or substring
                normalized.contains(p) -> true
                p.contains("@") && normalized.endsWith(p.substringAfter("@")) &&
                    p.substringBefore("@") in listOf("*", "", "noreply", "no-reply", "alerts") -> true
                else -> false
            }
        }
    }
}
