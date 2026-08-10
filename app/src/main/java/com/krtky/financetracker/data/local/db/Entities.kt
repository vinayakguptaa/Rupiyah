package com.krtky.financetracker.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "category",
    val color: Long = 0xFF0B6E4F,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isQuickAction: Boolean = false,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = "BANK",
    val currency: String = "INR",
    val openingBalancePaise: Long = 0L,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "funds")
data class FundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Envelope budget / limit in paise. Progress uses this, not lifetime credits. */
    val budgetPaise: Long = 0L,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["emailMessageId"], unique = true),
        Index(value = ["contentHash"], unique = true),
        Index(value = ["externalRefId", "paymentMethod"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["categoryId"]),
        Index(value = ["fundId"]),
        Index(value = ["accountId"]),
        Index(value = ["transferGroupId"]),
        Index(value = ["splitGroupId"]),
        Index(value = ["deletedAt"]),
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    /** DEBIT | CREDIT (legacy rows migrated from EXPENSE | INCOME). */
    val type: String,
    val amountPaise: Long,
    val currency: String = "INR",
    val occurredAt: Long,
    val recordedAt: Long,
    val merchant: String? = null,
    val counterparty: String? = null,
    val categoryId: Long? = null,
    val fundId: Long? = null,
    val accountId: Long? = null,
    val paymentMethod: String? = null,
    val source: String,
    val note: String? = null,
    val isCash: Boolean = false,
    val classificationStatus: String = "PENDING",
    val isSkipped: Boolean = false,
    /** NORMAL | SELF_TRANSFER | TAB_TRANSFER */
    val kind: String = "NORMAL",
    val transferGroupId: String? = null,
    val rawDescription: String? = null,
    val classificationNotifiedAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String? = null,
    val locationAccuracy: Float? = null,
    val locationMatchedAt: Long? = null,
    val emailMessageId: String? = null,
    val externalRefId: String? = null,
    val contentHash: String? = null,
    val sheetsSynced: Boolean = false,
    val deletedAt: Long? = null,
    val updatedAt: Long,
    val version: Int = 1,
    /** Relative path under app files (`receipts/…`) or content URI string. */
    val receiptUri: String? = null,
    /** Shared id for split-transaction parts; null if not a split child. */
    val splitGroupId: String? = null,
)

@Entity(tableName = "fund_ledger")
data class FundLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fundId: Long,
    val transactionId: String? = null,
    val entryType: String,
    val amountPaise: Long,
    val balanceAfterPaise: Long,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "trusted_senders",
    indices = [Index(value = ["emailPattern"], unique = true)]
)
data class TrustedSenderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emailPattern: String,
    val walletLabel: String,
    val enabled: Boolean = true,
)

@Entity(
    tableName = "email_ingest_log",
    indices = [Index(value = ["messageId"], unique = true)]
)
data class EmailIngestLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String,
    val sender: String,
    val subject: String?,
    val receivedAt: Long,
    val processStatus: String,
    val parseError: String? = null,
    val transactionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "location_samples")
data class LocationSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val capturedAt: Long,
    val placeName: String? = null,
    val source: String = "FUSED",
)

@Entity(tableName = "pending_classification")
data class PendingClassificationEntity(
    @PrimaryKey val transactionId: String,
    val scheduledAt: Long,
    val notifiedAt: Long? = null,
    val attempts: Int = 0,
    val status: String = "SCHEDULED",
)

@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
