package com.krtky.financetracker.data.repository

import android.content.Context
import android.net.Uri
import com.krtky.financetracker.data.importcsv.CsvStatementParser
import com.krtky.financetracker.data.importcsv.DedupeConfidence
import com.krtky.financetracker.data.importcsv.ImportDedupe
import com.krtky.financetracker.data.importcsv.ParsedCsvRow
import com.krtky.financetracker.data.importcsv.enrichTransaction
import com.krtky.financetracker.data.importcsv.shouldEnrichExisting
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class ImportRowAction {
    /** Insert as new IMPORT transaction. */
    IMPORT,
    /** Skip insert; optionally enrich matched existing row. */
    SKIP_MERGE,
    /** Force import even if medium match exists. */
    IMPORT_ANYWAY,
}

data class ImportPreviewRow(
    val id: String,
    val parsed: ParsedCsvRow,
    val confidence: DedupeConfidence,
    val matchReason: String,
    val matchedTxnId: String?,
    val matchedSummary: String?,
    val action: ImportRowAction,
)

data class ImportPreview(
    val account: Account,
    val fileName: String,
    val presetName: String,
    val headers: List<String>,
    val rows: List<ImportPreviewRow>,
    val parseErrors: List<String>,
    val skippedLines: Int,
)

data class ImportCommitResult(
    val imported: Int,
    val merged: Int,
    val skipped: Int,
    val failed: Int,
)

@Singleton
class StatementImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) {
    private val txnDao = db.transactionDao()

    suspend fun readUriText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Could not open file")
    }

    suspend fun buildPreview(
        accountId: Long,
        uri: Uri,
        fileName: String,
    ): ImportPreview = withContext(Dispatchers.IO) {
        val account = accountRepository.getById(accountId)
            ?: error("Account not found")
        val text = readUriText(uri)
        val parsed = CsvStatementParser.parse(text)
        val existing = loadCandidates(account)
        val categories = categoryRepository.getAll()

        // B2: within-file candidate consumption. Two statement lines that both
        // HIGH-match the SAME existing txn must not both skip-merge: the first
        // consumes the candidate, later same-key lines are downgraded to IMPORT.
        val consumedIds = mutableSetOf<String>()
        val rows = parsed.rows.map { row ->
            val match = ImportDedupe.match(row, existing)
            val alreadyConsumed = match.existing?.id?.let { it in consumedIds } == true
            val effectiveConfidence = when {
                match.confidence != DedupeConfidence.HIGH -> match.confidence
                alreadyConsumed -> DedupeConfidence.LOW
                else -> {
                    match.existing?.id?.let { consumedIds.add(it) }
                    DedupeConfidence.HIGH
                }
            }
            val defaultAction = when (effectiveConfidence) {
                DedupeConfidence.HIGH -> ImportRowAction.SKIP_MERGE
                DedupeConfidence.MEDIUM -> ImportRowAction.SKIP_MERGE
                DedupeConfidence.LOW -> ImportRowAction.IMPORT
            }
            ImportPreviewRow(
                id = UUID.randomUUID().toString(),
                parsed = row,
                confidence = effectiveConfidence,
                matchReason = if (alreadyConsumed) {
                    "Same amount & date as an earlier line — imported as new"
                } else {
                    match.reason
                },
                matchedTxnId = match.existing?.id,
                matchedSummary = match.existing?.let { summarize(it) },
                action = defaultAction,
            )
        }

        // Resolve category hints (not used for action, but available for commit)
        @Suppress("UNUSED_VARIABLE")
        val _cats = categories

        ImportPreview(
            account = account,
            fileName = fileName,
            presetName = parsed.presetName,
            headers = parsed.headers,
            rows = rows,
            parseErrors = parsed.errors,
            skippedLines = parsed.skippedLines,
        )
    }

    suspend fun commit(
        accountId: Long,
        rows: List<ImportPreviewRow>,
    ): ImportCommitResult = withContext(Dispatchers.IO) {
        val account = accountRepository.getById(accountId)
            ?: return@withContext ImportCommitResult(0, 0, 0, rows.size)
        val categories = categoryRepository.getAll()
        var imported = 0
        var merged = 0
        var skipped = 0
        var failed = 0

        for (row in rows) {
            when (row.action) {
                ImportRowAction.SKIP_MERGE -> {
                    val existingId = row.matchedTxnId
                    if (existingId != null) {
                        val existing = transactionRepository.getById(existingId)
                        if (existing != null && shouldEnrichExisting(existing, row.parsed)) {
                            val updated = enrichTransaction(existing, row.parsed)
                            transactionRepository.update(updated)
                            merged++
                        } else {
                            skipped++
                        }
                    } else {
                        skipped++
                    }
                }
                ImportRowAction.IMPORT, ImportRowAction.IMPORT_ANYWAY -> {
                    val ok = insertImportRow(account, row.parsed, categories)
                    if (ok) imported++ else failed++
                }
            }
        }

        ImportCommitResult(imported, merged, skipped, failed)
    }

    private suspend fun loadCandidates(account: Account): List<Transaction> =
        transactionRepository.getForAccount(account.id)

    private suspend fun insertImportRow(
        account: Account,
        row: ParsedCsvRow,
        categories: List<Category>,
    ): Boolean {
        val catId = row.categoryHint?.let { hint ->
            categories.firstOrNull { it.name.equals(hint, ignoreCase = true) }?.id
        }
        val isCash = account.kind.name == "CASH" || account.name.equals("Cash", true)
        val id = UUID.randomUUID().toString()
        val ref = row.externalRef?.takeIf { it.isNotBlank() }
        if (ref != null) {
            val clash = txnDao.findByExternalRefId(ref)
            if (clash != null && clash.accountId == account.id) {
                return false
            }
        }
        val txn = Transaction(
            id = id,
            type = row.type,
            amountPaise = row.amountPaise,
            occurredAt = row.occurredAt,
            counterparty = row.counterparty,
            categoryId = catId,
            accountId = account.id,
            source = TransactionSource.IMPORT,
            note = row.note,
            isCash = isCash,
            classificationStatus = if (catId != null) {
                ClassificationStatus.CLASSIFIED
            } else {
                ClassificationStatus.PENDING
            },
            rawDescription = row.description,
            externalRefId = ref,
            accountName = account.name,
        )
        return try {
            transactionRepository.insertFromImport(txn) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun summarize(t: Transaction): String {
        val dir = if (t.type == TransactionType.DEBIT) "Debit" else "Credit"
        val name = t.displayName() ?: t.accountName ?: "Txn"
        return "$dir · ${t.amountPaise / 100.0} · $name"
    }
}
