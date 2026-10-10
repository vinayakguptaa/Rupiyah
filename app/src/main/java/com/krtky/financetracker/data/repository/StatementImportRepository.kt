package com.krtky.financetracker.data.repository

import android.content.Context
import android.net.Uri
import com.krtky.financetracker.data.classify.LocalClassifier
import com.krtky.financetracker.data.importcsv.CsvHeaderRolesParser
import com.krtky.financetracker.data.importcsv.CsvStatementParser
import com.krtky.financetracker.data.importcsv.DedupeConfidence
import com.krtky.financetracker.data.importcsv.ImportDedupe
import com.krtky.financetracker.data.importcsv.ParsedCsvRow
import com.krtky.financetracker.data.importcsv.enrichTransaction
import com.krtky.financetracker.data.importcsv.shouldEnrichExisting
import com.krtky.financetracker.data.llm.LlmClient
import com.krtky.financetracker.data.llm.TransactionClassifier
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionKind
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
    val matchedParts: List<Transaction> = emptyList(),
    val isSplitMatch: Boolean = false,
    val sameDayTransactions: List<Transaction> = emptyList(),
    val nearbyTransactions: List<Transaction> = emptyList(),
    val action: ImportRowAction,
    val categoryName: String? = null,
    val categoryIcon: String? = null,
    val categoryColor: Long? = null,
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
    private val llmClient: LlmClient,
    private val classifier: TransactionClassifier,
    private val localClassifier: LocalClassifier,
) {
    private val txnDao = db.transactionDao()

    suspend fun readUriText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Could not open file")
    }

    suspend fun parseDocument(
        uri: Uri,
        fileName: String,
        password: String? = null,
    ): com.krtky.financetracker.data.importcsv.CsvParseResult = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)?.lowercase()
        val lowerName = fileName.lowercase()

        when {
            lowerName.endsWith(".pdf") || mime == "application/pdf" -> {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    com.krtky.financetracker.data.importcsv.PdfStatementParser.parse(context, stream, password)
                } ?: error("Could not open PDF file")
            }
            lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") ||
                mime?.contains("spreadsheetml") == true || mime?.contains("excel") == true -> {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    com.krtky.financetracker.data.importcsv.ExcelStatementParser.parse(stream, password)
                } ?: error("Could not open Excel file")
            }
            else -> {
                val text = readUriText(uri)
                parseWithOptionalLlmMapping(text)
            }
        }
    }

    suspend fun buildPreview(
        accountId: Long,
        uri: Uri,
        fileName: String,
        password: String? = null,
    ): ImportPreview = withContext(Dispatchers.IO) {
        val account = accountRepository.getById(accountId)
            ?: error("Account not found")
        val parsed = parseDocument(uri, fileName, password)
        val allActive = transactionRepository.getForAccount(account.id)
        val unsplit = allActive.filter { it.splitGroupId == null }
        val splitChildren = allActive.filter { it.splitGroupId != null }
        val splitPartsMap = splitChildren.groupBy { it.splitGroupId!! }

        val splitGroupCandidates = splitPartsMap.map { (groupId, parts) ->
            val totalPaise = parts.sumOf { it.amountPaise }
            val parentEntity = txnDao.getById(groupId)
            val first = parts.first()
            Transaction(
                id = groupId,
                type = parentEntity?.let { com.krtky.financetracker.data.local.db.parseTransactionType(it.type) } ?: first.type,
                amountPaise = totalPaise,
                occurredAt = parentEntity?.occurredAt ?: first.occurredAt,
                recordedAt = parentEntity?.recordedAt ?: first.recordedAt,
                counterparty = parentEntity?.counterparty ?: first.counterparty,
                accountId = account.id,
                source = parentEntity?.let { runCatching { TransactionSource.valueOf(it.source) }.getOrNull() } ?: TransactionSource.MANUAL,
                note = parts.joinToString(" + ") { "${it.categoryName ?: it.counterparty ?: "Part"}: ₹${it.amountPaise / 100}" },
                splitGroupId = groupId,
                externalRefId = parentEntity?.externalRefId ?: first.externalRefId,
                rawDescription = parentEntity?.rawDescription ?: first.rawDescription,
            )
        }
        val dedupeCandidates = unsplit + splitGroupCandidates
        val categories = categoryRepository.getAll()

        val rowCategoryMap = mutableMapOf<Int, Category>()

        // 1. Direct hint from CSV parser if any
        parsed.rows.forEachIndexed { index, row ->
            val direct = row.categoryHint?.let { hint ->
                categories.firstOrNull { it.name.equals(hint, ignoreCase = true) }
            }
            if (direct != null) {
                rowCategoryMap[index] = direct
            }
        }

        // 2. Your own history / merchant rules (no AI)
        parsed.rows.forEachIndexed { index, row ->
            if (index in rowCategoryMap) return@forEachIndexed
            val g = localClassifier.guess(row.counterparty, row.description, row.type, categories)
            if (g?.confident == true) categories.firstOrNull { it.id == g.categoryId }?.let { rowCategoryMap[index] = it }
        }

        // 3. AI classification for rows still without a category
        var aiNote: String? = null
        if (classifier.isConfigured() && categories.isNotEmpty()) {
            val texts = parsed.rows.withIndex()
                .filter { (index, _) -> index !in rowCategoryMap }
                .mapNotNull { (index, row) ->
                    TransactionClassifier.describe(row.counterparty, row.description)?.let { index to it }
                }
                .toMap()
            if (texts.isNotEmpty()) {
                val outcome = classifier.classify(texts, categories)
                rowCategoryMap.putAll(outcome.matches)
                aiNote = outcome.error?.let { "AI categories incomplete: ${it.describe()}" }
            }
        }

        val consumedIds = mutableSetOf<String>()
        val rows = parsed.rows.mapIndexed { index, row ->
            val assignedCat = rowCategoryMap[index]
            val updatedParsed = if (assignedCat != null && row.categoryHint == null) {
                row.copy(categoryHint = assignedCat.name)
            } else {
                row
            }
            val match = ImportDedupe.match(updatedParsed, dedupeCandidates, splitPartsMap)
            val sameDay = allActive.filter { ImportDedupe.sameDay(it.occurredAt, row.occurredAt) }
            val nearby = allActive.filter { kotlin.math.abs(it.occurredAt - row.occurredAt) <= 36 * 60 * 60_000L }

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
                parsed = updatedParsed,
                confidence = effectiveConfidence,
                matchReason = if (alreadyConsumed) {
                    "Same amount & date as an earlier line — imported as new"
                } else {
                    match.reason
                },
                matchedTxnId = match.existing?.id,
                matchedSummary = match.existing?.let { summarize(it) },
                matchedParts = match.matchedParts,
                isSplitMatch = match.isSplitMatch,
                sameDayTransactions = sameDay,
                nearbyTransactions = nearby,
                action = defaultAction,
                categoryName = assignedCat?.name,
                categoryIcon = assignedCat?.icon,
                categoryColor = assignedCat?.color,
            )
        }

        ImportPreview(
            account = account,
            fileName = fileName,
            presetName = parsed.presetName,
            headers = parsed.headers,
            rows = rows,
            parseErrors = listOfNotNull(aiNote) + parsed.errors,
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

    /**
     * Heuristic mapping first (bank CSVs are Date / narration / Debit / Credit).
     * If AI is on, one small call maps headers + up to 3 sample rows — not the file.
     */
    private suspend fun parseWithOptionalLlmMapping(
        text: String,
    ): com.krtky.financetracker.data.importcsv.CsvParseResult {
        val inspect = CsvStatementParser.inspect(text)
        val heuristic = CsvStatementParser.detectMapping(inspect.headers)
        val llmMapping = suggestLlmMapping(inspect)
        val mapping = when {
            llmMapping != null && CsvHeaderRolesParser.isUsable(llmMapping) -> llmMapping
            else -> heuristic
        }
        return CsvStatementParser.parse(text, mapping)
    }

    private suspend fun suggestLlmMapping(
        inspect: CsvStatementParser.Inspect,
    ): CsvStatementParser.ColumnMapping? {
        if (!llmClient.isConfigured()) return null
        if (inspect.headers.isEmpty()) return null
        val user = buildString {
            appendLine("HEADERS:")
            inspect.headers.forEachIndexed { i, h -> appendLine("$i. $h") }
            appendLine()
            appendLine("SAMPLE ROWS (do not parse these into transactions; they only show column meaning):")
            inspect.sampleDataLines.take(3).forEach { line ->
                appendLine(line.take(400))
            }
        }
        val raw = llmClient.completeJson(
            system = CsvHeaderRolesParser.LLM_SYSTEM,
            user = user,
        ).getOrNull() ?: return null
        val roles = CsvHeaderRolesParser.fromJson(raw) ?: return null
        return CsvHeaderRolesParser.toMapping(inspect.headers, roles)
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
        val cleanParty = row.counterparty?.takeIf {
            val lower = it.trim().lowercase(java.util.Locale.US)
            it.isNotBlank() && lower !in setOf("dr", "cr", "debit", "credit", "d", "c")
        }
        val txn = Transaction(
            id = id,
            type = row.type,
            amountPaise = row.amountPaise,
            occurredAt = row.occurredAt,
            counterparty = cleanParty,
            categoryId = catId,
            accountId = account.id,
            source = TransactionSource.IMPORT,
            note = row.note,
            isCash = isCash,
            classificationStatus = if (catId != null || row.kind != TransactionKind.NORMAL) {
                ClassificationStatus.CLASSIFIED
            } else {
                ClassificationStatus.PENDING
            },
            rawDescription = row.description,
            externalRefId = ref,
            accountName = account.name,
            kind = row.kind,
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
