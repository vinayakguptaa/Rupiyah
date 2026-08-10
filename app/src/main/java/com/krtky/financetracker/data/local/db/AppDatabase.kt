package com.krtky.financetracker.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        AccountEntity::class,
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
    version = 9,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
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
        }

        /** Optional receipt photo path/URI on transactions. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptUri TEXT")
            }
        }

        /**
         * Cashflow foundation:
         * - accounts table
         * - transaction accountId / kind / transferGroupId / isSkipped / rawDescription
         * - EXPENSE→DEBIT, INCOME→CREDIT
         * - link legacy paymentMethod rows to accounts by name
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `accounts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `openingBalancePaise` INTEGER NOT NULL,
                        `archived` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )

                if (!tableHasColumn(db, "transactions", "accountId")) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN accountId INTEGER")
                }
                if (!tableHasColumn(db, "transactions", "isSkipped")) {
                    db.execSQL(
                        "ALTER TABLE transactions ADD COLUMN isSkipped INTEGER NOT NULL DEFAULT 0",
                    )
                }
                if (!tableHasColumn(db, "transactions", "kind")) {
                    db.execSQL(
                        "ALTER TABLE transactions ADD COLUMN kind TEXT NOT NULL DEFAULT 'NORMAL'",
                    )
                }
                if (!tableHasColumn(db, "transactions", "transferGroupId")) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN transferGroupId TEXT")
                }
                if (!tableHasColumn(db, "transactions", "rawDescription")) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN rawDescription TEXT")
                }

                db.execSQL("UPDATE transactions SET type = 'DEBIT' WHERE type = 'EXPENSE'")
                db.execSQL("UPDATE transactions SET type = 'CREDIT' WHERE type = 'INCOME'")
                db.execSQL(
                    "UPDATE transactions SET isSkipped = 1 WHERE classificationStatus = 'SKIPPED'",
                )
                db.execSQL(
                    """
                    UPDATE transactions
                    SET counterparty = merchant
                    WHERE (counterparty IS NULL OR counterparty = '')
                      AND merchant IS NOT NULL AND merchant != ''
                    """.trimIndent(),
                )

                // Seed accounts from distinct payment methods + Cash (case-normalized uniqueness)
                db.execSQL(
                    """
                    INSERT INTO accounts (name, kind, currency, openingBalancePaise, archived, sortOrder, createdAt)
                    SELECT
                        MIN(TRIM(paymentMethod)) AS name,
                        CASE
                            WHEN LOWER(TRIM(paymentMethod)) = 'cash' THEN 'CASH'
                            WHEN LOWER(TRIM(paymentMethod)) LIKE '%card%' THEN 'CARD'
                            WHEN LOWER(TRIM(paymentMethod)) LIKE '%wallet%'
                              OR LOWER(TRIM(paymentMethod)) LIKE '%pay%' THEN 'WALLET'
                            ELSE 'BANK'
                        END AS kind,
                        'INR',
                        0,
                        0,
                        10,
                        strftime('%s','now') * 1000
                    FROM transactions
                    WHERE paymentMethod IS NOT NULL
                      AND TRIM(paymentMethod) != ''
                      AND LOWER(TRIM(paymentMethod)) != 'digital'
                      AND LOWER(TRIM(paymentMethod)) != 'upi'
                    GROUP BY LOWER(TRIM(paymentMethod))
                    """.trimIndent(),
                )
                // Ensure Cash exists if any isCash or Cash paymentMethod rows
                db.execSQL(
                    """
                    INSERT INTO accounts (name, kind, currency, openingBalancePaise, archived, sortOrder, createdAt)
                    SELECT 'Cash', 'CASH', 'INR', 0, 0, 0, strftime('%s','now') * 1000
                    WHERE NOT EXISTS (
                        SELECT 1 FROM accounts WHERE name = 'Cash' COLLATE NOCASE
                    )
                      AND EXISTS (
                        SELECT 1 FROM transactions
                        WHERE deletedAt IS NULL
                          AND (isCash = 1 OR LOWER(TRIM(IFNULL(paymentMethod,''))) = 'cash')
                      )
                    """.trimIndent(),
                )

                // Link transactions to accounts by payment method name
                db.execSQL(
                    """
                    UPDATE transactions
                    SET accountId = (
                        SELECT a.id FROM accounts a
                        WHERE a.name = TRIM(transactions.paymentMethod) COLLATE NOCASE
                        LIMIT 1
                    )
                    WHERE accountId IS NULL
                      AND paymentMethod IS NOT NULL
                      AND TRIM(paymentMethod) != ''
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE transactions
                    SET accountId = (
                        SELECT a.id FROM accounts a
                        WHERE a.name = 'Cash' COLLATE NOCASE
                        LIMIT 1
                    )
                    WHERE accountId IS NULL AND isCash = 1
                    """.trimIndent(),
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_accountId` " +
                        "ON `transactions` (`accountId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_transferGroupId` " +
                        "ON `transactions` (`transferGroupId`)",
                )
            }
        }

        /** Phase 3: transaction_splits for multi-category / multi-tab allocations. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_splits` (
                        `id` TEXT NOT NULL,
                        `transactionId` TEXT NOT NULL,
                        `amountPaise` INTEGER NOT NULL,
                        `categoryId` INTEGER,
                        `counterparty` TEXT,
                        `fundId` INTEGER,
                        `note` TEXT,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transaction_splits_transactionId` " +
                        "ON `transaction_splits` (`transactionId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transaction_splits_categoryId` " +
                        "ON `transaction_splits` (`categoryId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transaction_splits_fundId` " +
                        "ON `transaction_splits` (`fundId`)",
                )
            }
        }

        /**
         * Phase 4: replace child-table splits with parent-replacement split rows.
         *
         * Every `transaction_splits` line becomes a real transaction row sharing
         * `splitGroupId` = the original parent id. Parents that had splits are
         * soft-deleted (children carry the data), a remainder child preserves any
         * amount not covered by the lines, and the now-unused table is dropped.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `splitGroupId` TEXT")

                val now = "CAST(strftime('%s','now') AS INTEGER) * 1000"
                db.execSQL(
                    """
                    INSERT INTO `transactions` (
                        `id`, `type`, `amountPaise`, `currency`, `occurredAt`, `recordedAt`,
                        `merchant`, `counterparty`, `categoryId`, `fundId`, `accountId`,
                        `paymentMethod`, `source`, `note`, `isCash`, `classificationStatus`,
                        `isSkipped`, `kind`, `transferGroupId`, `rawDescription`,
                        `classificationNotifiedAt`, `latitude`, `longitude`, `placeName`,
                        `locationAccuracy`, `locationMatchedAt`, `emailMessageId`,
                        `externalRefId`, `contentHash`, `sheetsSynced`, `deletedAt`,
                        `updatedAt`, `version`, `receiptUri`, `splitGroupId`
                    )
                    SELECT
                        'mig_' || s.`id`,
                        p.`type`, s.`amountPaise`, p.`currency`, p.`occurredAt`, p.`recordedAt`,
                        p.`merchant`,
                        COALESCE(s.`counterparty`, p.`counterparty`, p.`merchant`),
                        s.`categoryId`, s.`fundId`, p.`accountId`, p.`paymentMethod`, p.`source`,
                        COALESCE(s.`note`, p.`note`), p.`isCash`, p.`classificationStatus`,
                        p.`isSkipped`, p.`kind`, p.`transferGroupId`, p.`rawDescription`,
                        p.`classificationNotifiedAt`, p.`latitude`, p.`longitude`, p.`placeName`,
                        p.`locationAccuracy`, p.`locationMatchedAt`, NULL, NULL, NULL, 0, NULL,
                        $now, 1, p.`receiptUri`, p.`id`
                    FROM `transaction_splits` s
                    INNER JOIN `transactions` p ON p.`id` = s.`transactionId`
                    """.trimIndent(),
                )

                // Remainder child for any amount the split lines did not cover.
                db.execSQL(
                    """
                    INSERT INTO `transactions` (
                        `id`, `type`, `amountPaise`, `currency`, `occurredAt`, `recordedAt`,
                        `merchant`, `counterparty`, `categoryId`, `fundId`, `accountId`,
                        `paymentMethod`, `source`, `note`, `isCash`, `classificationStatus`,
                        `isSkipped`, `kind`, `transferGroupId`, `rawDescription`,
                        `classificationNotifiedAt`, `latitude`, `longitude`, `placeName`,
                        `locationAccuracy`, `locationMatchedAt`, `emailMessageId`,
                        `externalRefId`, `contentHash`, `sheetsSynced`, `deletedAt`,
                        `updatedAt`, `version`, `receiptUri`, `splitGroupId`
                    )
                    SELECT
                        'mig_remainder_' || p.`id`,
                        p.`type`, p.`amountPaise` - COALESCE((
                            SELECT SUM(s.`amountPaise`) FROM `transaction_splits` s
                            WHERE s.`transactionId` = p.`id`
                        ), 0),
                        p.`currency`, p.`occurredAt`, p.`recordedAt`, p.`merchant`,
                        p.`counterparty`, p.`categoryId`, p.`fundId`, p.`accountId`,
                        p.`paymentMethod`, p.`source`, p.`note`, p.`isCash`, p.`classificationStatus`,
                        p.`isSkipped`, p.`kind`, p.`transferGroupId`, p.`rawDescription`,
                        p.`classificationNotifiedAt`, p.`latitude`, p.`longitude`, p.`placeName`,
                        p.`locationAccuracy`, p.`locationMatchedAt`, NULL, NULL, NULL, 0, NULL,
                        $now, 1, p.`receiptUri`, p.`id`
                    FROM `transactions` p
                    WHERE p.`id` IN (SELECT DISTINCT s.`transactionId` FROM `transaction_splits` s)
                      AND p.`amountPaise` > COALESCE((
                            SELECT SUM(s.`amountPaise`) FROM `transaction_splits` s
                            WHERE s.`transactionId` = p.`id`
                      ), 0)
                    """.trimIndent(),
                )

                // Soft-delete parents that had splits; children now carry the data.
                db.execSQL(
                    """
                    UPDATE `transactions`
                    SET `deletedAt` = $now
                    WHERE `id` IN (SELECT DISTINCT s.`transactionId` FROM `transaction_splits` s)
                    """.trimIndent(),
                )

                db.execSQL("DROP TABLE IF EXISTS `transaction_splits`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_splitGroupId` " +
                        "ON `transactions` (`splitGroupId`)",
                )
            }
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
}
