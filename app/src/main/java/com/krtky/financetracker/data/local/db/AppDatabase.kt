package com.krtky.financetracker.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        FundEntity::class,
        TransactionEntity::class,
        FundLedgerEntity::class,
        TrustedSenderEntity::class,
        EmailIngestLogEntity::class,
        LocationSampleEntity::class,
        PendingClassificationEntity::class,
        SyncOutboxEntity::class,
        SyncStateEntity::class,
    ],
    // Keep >= highest version ever installed on devices. Downgrading crashes Room
    // unless fallbackToDestructiveMigrationOnDowngrade() is set in AppModule.
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun fundDao(): FundDao
    abstract fun transactionDao(): TransactionDao
    abstract fun fundLedgerDao(): FundLedgerDao
    abstract fun trustedSenderDao(): TrustedSenderDao
    abstract fun emailIngestDao(): EmailIngestDao
    abstract fun locationSampleDao(): LocationSampleDao
    abstract fun pendingClassificationDao(): PendingClassificationDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        /**
         * Adds fixed [FundEntity.budgetPaise] and freezes current net credits as the
         * initial limit so later income credits don't inflate progress %.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE funds ADD COLUMN budgetPaise INTEGER NOT NULL DEFAULT 0",
                )
                // Opening amount only (first credit/positive adjust) = envelope limit.
                // Later income credits must not raise the limit.
                db.execSQL(
                    """
                    UPDATE funds SET budgetPaise = COALESCE((
                        SELECT amountPaise
                        FROM fund_ledger
                        WHERE fund_ledger.fundId = funds.id
                          AND (
                            entryType = 'CREDIT'
                            OR (entryType = 'ADJUSTMENT' AND amountPaise > 0)
                          )
                        ORDER BY createdAt ASC, id ASC
                        LIMIT 1
                    ), 0)
                    """.trimIndent(),
                )
            }
        }

        /**
         * Bridge for installs that already carried a v4 number with the same entity
         * set as v3 (no recurring). Real v4-with-recurring is handled in [MIGRATION_4_5].
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No-op: v3 schema is the pre-recurring baseline.
            }
        }

        /**
         * Removes the experimental recurring-payments feature that some debug builds
         * shipped as schema v4 (`recurring_payments` table + `transactions.recurringPaymentId`).
         * Preserves all other user data.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Column may be absent on pure v3→4 no-op installs — only rebuild if present.
                val hasRecurringCol = tableHasColumn(db, "transactions", "recurringPaymentId")
                if (hasRecurringCol) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `transactions_new` (
                            `id` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `amountPaise` INTEGER NOT NULL,
                            `currency` TEXT NOT NULL,
                            `occurredAt` INTEGER NOT NULL,
                            `recordedAt` INTEGER NOT NULL,
                            `merchant` TEXT,
                            `counterparty` TEXT,
                            `categoryId` INTEGER,
                            `fundId` INTEGER,
                            `paymentMethod` TEXT,
                            `source` TEXT NOT NULL,
                            `note` TEXT,
                            `isCash` INTEGER NOT NULL,
                            `classificationStatus` TEXT NOT NULL,
                            `classificationNotifiedAt` INTEGER,
                            `latitude` REAL,
                            `longitude` REAL,
                            `placeName` TEXT,
                            `locationAccuracy` REAL,
                            `locationMatchedAt` INTEGER,
                            `emailMessageId` TEXT,
                            `externalRefId` TEXT,
                            `contentHash` TEXT,
                            `sheetsSynced` INTEGER NOT NULL,
                            `deletedAt` INTEGER,
                            `updatedAt` INTEGER NOT NULL,
                            `version` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO `transactions_new` (
                            id, type, amountPaise, currency, occurredAt, recordedAt,
                            merchant, counterparty, categoryId, fundId, paymentMethod,
                            source, note, isCash, classificationStatus, classificationNotifiedAt,
                            latitude, longitude, placeName, locationAccuracy, locationMatchedAt,
                            emailMessageId, externalRefId, contentHash, sheetsSynced,
                            deletedAt, updatedAt, version
                        )
                        SELECT
                            id, type, amountPaise, currency, occurredAt, recordedAt,
                            merchant, counterparty, categoryId, fundId, paymentMethod,
                            source, note, isCash, classificationStatus, classificationNotifiedAt,
                            latitude, longitude, placeName, locationAccuracy, locationMatchedAt,
                            emailMessageId, externalRefId, contentHash, sheetsSynced,
                            deletedAt, updatedAt, version
                        FROM `transactions`
                        """.trimIndent(),
                    )
                    db.execSQL("DROP TABLE `transactions`")
                    db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_emailMessageId` " +
                            "ON `transactions` (`emailMessageId`)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_contentHash` " +
                            "ON `transactions` (`contentHash`)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_transactions_externalRefId_paymentMethod` " +
                            "ON `transactions` (`externalRefId`, `paymentMethod`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_occurredAt` " +
                            "ON `transactions` (`occurredAt`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` " +
                            "ON `transactions` (`categoryId`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_fundId` " +
                            "ON `transactions` (`fundId`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_deletedAt` " +
                            "ON `transactions` (`deletedAt`)",
                    )
                }
                db.execSQL("DROP TABLE IF EXISTS `recurring_payments`")
            }

            private fun tableHasColumn(
                db: SupportSQLiteDatabase,
                table: String,
                column: String,
            ): Boolean {
                db.query("PRAGMA table_info(`$table`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    if (nameIdx < 0) return false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIdx) == column) return true
                    }
                }
                return false
            }
        }

        /** Optional receipt photo path/URI on transactions. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptUri TEXT")
            }
        }
    }
}
