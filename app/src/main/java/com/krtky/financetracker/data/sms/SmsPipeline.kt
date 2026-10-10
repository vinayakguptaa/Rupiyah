package com.krtky.financetracker.data.sms

import android.util.Log
import com.krtky.financetracker.data.classify.LocalClassifier
import com.krtky.financetracker.data.llm.LlmClient
import com.krtky.financetracker.data.llm.LlmError
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.SmsMessageEntity
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.location.LocationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Staged SMS import. Each step saves its result on the [SmsMessageEntity] row, so a failure
 * at one step never discards the message:
 *
 * 1. **Store** the raw SMS ([ingest]).
 * 2. **Filter**: bank-like sender + money movement wording, else IGNORED with a reason.
 * 3. **Local parse** (regex). Amount + direction found → the transaction is created right away.
 * 4. **Local classify** from the user's own history and merchant rules.
 * 5. **AI** only when something is missing or uncertain; the prompt carries the local guesses.
 *    Fills gaps on the created transaction, or creates it when the regex could not.
 */
@Singleton
class SmsPipeline @Inject constructor(
    db: AppDatabase,
    private val parser: TransactionParser,
    private val localClassifier: LocalClassifier,
    private val llmClient: LlmClient,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val preferences: UserPreferences,
    private val locationRepository: LocationRepository,
) {
    private val dao = db.smsMessageDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** What the regex parser and local classifier made of the message (stored as JSON). */
    @Serializable
    data class LocalSnapshot(
        val type: String? = null,
        val amountPaise: Long? = null,
        val counterparty: String? = null,
        val account: String? = null,
        val ref: String? = null,
        val categoryName: String? = null,
        val categoryConfidence: Double? = null,
        val categoryReason: String? = null,
    )

    /** What the AI returned (stored as JSON). */
    @Serializable
    data class AiSnapshot(
        val type: String? = null,
        val amountPaise: Long? = null,
        val counterparty: String? = null,
        val categoryName: String? = null,
        val agreedWithLocalCategory: Boolean? = null,
    )

    enum class Origin { LIVE, SYNC }

    /** Step 1: store the raw SMS. Returns the row id (existing id when it was already stored). */
    suspend fun ingest(sender: String, body: String, receivedAt: Long, origin: Origin): String {
        val hash = hashOf(sender, body)
        dao.findNear(hash, receivedAt - DEDUPE_WINDOW_MS, receivedAt + DEDUPE_WINDOW_MS)?.let { return it.id }
        val now = System.currentTimeMillis()
        val id = "sms-" + UUID.randomUUID().toString()
        dao.insert(
            SmsMessageEntity(
                id = id,
                sender = sender,
                body = body,
                bodyHash = hash,
                receivedAt = receivedAt,
                origin = origin.name,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    /**
     * Run every step that is still open for one message. Safe to call again: finished steps
     * are skipped, so a retry only redoes the AI step. Returns the final row.
     */
    suspend fun process(id: String, allowAi: Boolean = true): SmsMessageEntity? {
        var m = dao.getById(id) ?: return null
        // Parser / filter improvements may now handle a message that needed AI before: retry locally first.
        if (m.status == STATUS_NEEDS_AI && m.transactionId == null) m = runLocal(m.copy(status = STATUS_NEW))
        if (m.status == STATUS_NEW) m = runLocal(m)
        if (allowAi && aiOpen(m)) m = runAi(m)
        return m
    }

    /**
     * Process queued messages oldest first. Local steps always run; at most [maxAiCalls]
     * messages go to the AI so a sync does not burn through a free-tier rate limit.
     */
    suspend fun processPending(maxAiCalls: Int = DEFAULT_AI_BUDGET): Int = coroutineScope {
        val start = System.currentTimeMillis()
        // 1. Local steps for everything new: no network, a few ms per message.
        val fresh = dao.pending(PENDING_SCAN_LIMIT).filter {
            it.status == STATUS_NEW || (it.status == STATUS_NEEDS_AI && it.transactionId == null)
        }
        fresh.forEach { process(it.id, allowAi = false) }
        val localMs = System.currentTimeMillis() - start

        // 2. AI for whatever is still open, a few calls at a time.
        val aiRows = dao.pending(PENDING_SCAN_LIMIT).filter(::aiOpen).take(maxAiCalls)
        val stop = AtomicBoolean(false)
        val permits = Semaphore(AI_CONCURRENCY)
        aiRows.map { row ->
            async {
                permits.withPermit {
                    if (stop.get()) return@withPermit
                    val after = process(row.id) ?: return@withPermit
                    // A bad key / wrong model fails every call: stop instead of repeating it.
                    if (after.aiStatus == AI_FAILED && after.lastError?.startsWith(TRANSIENT_PREFIX) != true) stop.set(true)
                }
            }
        }.awaitAll()
        Log.i(
            TAG,
            "processed ${fresh.size} new locally in ${localMs}ms; AI on ${aiRows.size} in " +
                "${System.currentTimeMillis() - start - localMs}ms" + if (stop.get()) " (stopped after a hard failure)" else "",
        )
        dao.pruneIgnored(System.currentTimeMillis() - IGNORED_RETENTION_MS)
        fresh.size + aiRows.size
    }

    private fun aiOpen(m: SmsMessageEntity) =
        m.status == STATUS_NEEDS_AI || m.aiStatus == AI_PENDING || m.aiStatus == AI_FAILED

    /** Let filter improvements apply to messages it rejected earlier (local only, no AI cost). */
    suspend fun recheckIgnored(since: Long) = dao.recheckIgnored(since)

    /** Put AI work that was skipped (AI off) or failed back in the queue. */
    suspend fun requeueAi() = dao.requeueAi()

    // ---- manual overrides (from the SMS inbox) ----------------------------------------------

    /**
     * "Import anyway": skip the filter, reset earlier results and run parse → classify → AI.
     * A transaction the user removed earlier is created again.
     */
    suspend fun forceImport(id: String): SmsMessageEntity? {
        val m = dao.getById(id) ?: return null
        val removed = m.transactionId
        if (m.userOverride == OVERRIDE_REMOVED && removed != null && transactionRepository.restoreDeleted(removed)) {
            return save(m.copy(status = STATUS_IMPORTED, ignoreReason = null, userOverride = OVERRIDE_FORCE))
        }
        save(
            m.copy(
                status = STATUS_NEW,
                ignoreReason = null,
                aiStatus = AI_NONE,
                aiSummary = null,
                lastError = null,
                transactionId = null,
                userOverride = OVERRIDE_FORCE,
            ),
        )
        return process(id)
    }

    /** Run the AI step again for one message, whatever happened last time. */
    suspend fun retryAi(id: String): SmsMessageEntity? {
        val m = dao.getById(id) ?: return null
        save(
            m.copy(
                status = if (m.transactionId == null) STATUS_NEEDS_AI else m.status,
                aiStatus = AI_PENDING,
                lastError = null,
            ),
        )
        return process(id)
    }

    /** The user is adding it by hand (paste review); stop the pipeline touching it. */
    suspend fun markManual(id: String) = update(id) {
        it.copy(status = STATUS_MANUAL, userOverride = OVERRIDE_MANUAL, lastError = null)
    }

    /** "Dismiss": not a transaction / not interesting. Leaves any created transaction alone. */
    suspend fun dismiss(id: String) = update(id) {
        it.copy(status = STATUS_IGNORED, ignoreReason = "dismissed by you", userOverride = OVERRIDE_DISMISSED)
    }

    /** "Not a transaction": remove the transaction this message created. */
    suspend fun removeTransaction(id: String) {
        val m = dao.getById(id) ?: return
        if (m.status == STATUS_IMPORTED) m.transactionId?.let { transactionRepository.delete(it) }
        // Keep transactionId so "Import anyway" can restore the same row.
        save(
            m.copy(
                status = STATUS_IGNORED,
                ignoreReason = "removed by you",
                aiStatus = AI_NOT_NEEDED,
                userOverride = OVERRIDE_REMOVED,
            ),
        )
    }

    /** "Put back in queue": forget the manual decision and process normally. */
    suspend fun reset(id: String): SmsMessageEntity? {
        val m = dao.getById(id) ?: return null
        save(
            m.copy(
                status = STATUS_NEW,
                ignoreReason = null,
                aiStatus = AI_NONE,
                aiSummary = null,
                lastError = null,
                transactionId = if (m.status == STATUS_DUPLICATE) null else m.transactionId,
                userOverride = null,
            ),
        )
        return process(id)
    }

    private suspend fun update(id: String, change: (SmsMessageEntity) -> SmsMessageEntity) {
        dao.getById(id)?.let { save(change(it)) }
    }

    // ---- steps -------------------------------------------------------------------------------

    private suspend fun runLocal(start: SmsMessageEntity): SmsMessageEntity {
        val banks = accountRepository.activeBankNames()
        // Step 2: filter (skipped when the user chose "Import anyway")
        val verdict = if (start.userOverride == OVERRIDE_FORCE) {
            SmsFilter.Verdict.Candidate(SmsFilter.bankForSender(start.sender, banks))
        } else SmsFilter.check(
            sender = start.sender,
            body = start.body,
            allowSenders = splitList(preferences.smsSenders.first()),
            keywords = splitList(preferences.smsKeywords.first()),
            userBanks = banks,
        )
        if (verdict is SmsFilter.Verdict.Ignore) {
            // Clear any queued AI work from an earlier pass, or the AI step would import it anyway.
            return save(
                start.copy(status = STATUS_IGNORED, ignoreReason = verdict.reason, aiStatus = AI_NOT_NEEDED, lastError = null),
            )
        }
        var m = save(start.copy(bank = (verdict as SmsFilter.Verdict.Candidate).bank))

        // Step 3: local parse (regex only)
        val local = parser.parseSms(m.sender, m.body, m.receivedAt, useLlm = false, messageId = m.id).transaction
        if (local == null) {
            return save(m.copy(status = STATUS_NEEDS_AI, aiStatus = AI_PENDING, localSummary = null))
        }

        // Step 4: local classify
        val categories = categoryRepository.getAll()
        val guess = localClassifier.guess(local.counterparty, m.body, local.type, categories)
        val snapshot = LocalSnapshot(
            type = local.type.name,
            amountPaise = local.amountPaise,
            counterparty = local.counterparty,
            account = local.accountName,
            ref = local.externalRefId,
            categoryName = guess?.categoryName,
            categoryConfidence = guess?.confidence,
            categoryReason = guess?.reason,
        )
        val toSave = if (guess?.confident == true) {
            local.copy(
                categoryId = guess.categoryId,
                categoryName = guess.categoryName,
                classificationStatus = ClassificationStatus.CLASSIFIED,
            )
        } else local

        val insert = transactionRepository.insertFromSmsDetailed(withLocation(toSave, m))
        val needAi = guess?.confident != true || local.counterparty.isNullOrBlank()
        m = m.copy(localSummary = json.encodeToString(snapshot))
        m = when {
            insert.insertedId != null -> m.copy(
                status = STATUS_IMPORTED,
                transactionId = insert.insertedId,
                aiStatus = if (needAi) AI_PENDING else AI_NOT_NEEDED,
            )
            insert.duplicateOfId != null -> m.copy(
                status = STATUS_DUPLICATE,
                transactionId = insert.duplicateOfId,
                aiStatus = AI_NOT_NEEDED,
            )
            else -> m.copy(status = STATUS_FAILED, lastError = "Could not save the transaction", aiStatus = AI_NOT_NEEDED)
        }
        return save(m)
    }

    private suspend fun runAi(start: SmsMessageEntity): SmsMessageEntity {
        if (!llmClient.isConfigured()) {
            // Nothing lost: imported rows stay as parsed; unparsed ones wait until AI is set up.
            return save(start.copy(aiStatus = AI_SKIPPED, lastError = LlmError.NotConfigured.describe()))
        }
        val local = start.localSummary?.let { runCatching { json.decodeFromString<LocalSnapshot>(it) }.getOrNull() }
        val r = parser.parseSms(
            sender = start.sender,
            body = start.body,
            receivedAt = start.receivedAt,
            useLlm = true,
            hints = local?.let(::hintsFor),
            messageId = start.id,
        )
        val m = start.copy(attempts = start.attempts + 1)
        r.llmError?.let { err ->
            val prefix = if (err.isTransient) TRANSIENT_PREFIX else ""
            Log.w(TAG, "AI step failed for ${m.id}: ${err.describe()}")
            return save(m.copy(aiStatus = AI_FAILED, lastError = prefix + err.describe()))
        }
        if (r.rejectedByAi) {
            // Keep a transaction the regex already created; just record the disagreement.
            return save(
                if (m.transactionId == null) {
                    m.copy(status = STATUS_IGNORED, ignoreReason = "AI: not a completed transaction", aiStatus = AI_REJECTED)
                } else {
                    m.copy(aiStatus = AI_REJECTED, lastError = "AI thinks this is not a completed transaction — check it")
                },
            )
        }
        val ai = r.transaction
        if (ai == null && m.transactionId != null && m.status == STATUS_IMPORTED) {
            // The (improved) rules now reject a message that was imported earlier: flag it, don't delete it.
            return save(
                m.copy(aiStatus = AI_REJECTED, lastError = "No longer looks like a completed transaction — check it"),
            )
        }
        val aiSnap = ai?.let {
            AiSnapshot(
                type = it.type.name,
                amountPaise = it.amountPaise,
                counterparty = it.counterparty,
                categoryName = it.categoryName,
                agreedWithLocalCategory = local?.categoryName?.let { name -> name.equals(it.categoryName, true) },
            )
        }
        val withAi = m.copy(aiSummary = aiSnap?.let { json.encodeToString(it) }, lastError = null)

        if (withAi.transactionId == null) {
            if (ai == null) {
                return save(withAi.copy(status = STATUS_FAILED, aiStatus = AI_DONE, lastError = "No amount found, even with AI"))
            }
            val toSave = ai.withLocalCategoryFallback(local)
            val insert = transactionRepository.insertFromSmsDetailed(withLocation(toSave, withAi))
            return save(
                when {
                    insert.insertedId != null -> withAi.copy(status = STATUS_IMPORTED, transactionId = insert.insertedId, aiStatus = AI_DONE)
                    insert.duplicateOfId != null -> withAi.copy(status = STATUS_DUPLICATE, transactionId = insert.duplicateOfId, aiStatus = AI_DONE)
                    else -> withAi.copy(status = STATUS_FAILED, aiStatus = AI_DONE, lastError = "Could not save the transaction")
                },
            )
        }
        if (ai != null && withAi.status == STATUS_IMPORTED) {
            transactionRepository.enrichFromAi(withAi.transactionId, ai.counterparty, ai.categoryId, ai.note)
        }
        return save(withAi.copy(aiStatus = AI_DONE))
    }

    private suspend fun Transaction.withLocalCategoryFallback(local: LocalSnapshot?): Transaction {
        if (categoryId != null || local?.categoryName == null || (local.categoryConfidence ?: 0.0) < LocalClassifier.CONFIDENT) {
            return this
        }
        val cat = categoryRepository.getAll().firstOrNull { it.name == local.categoryName } ?: return this
        return copy(categoryId = cat.id, categoryName = cat.name, classificationStatus = ClassificationStatus.CLASSIFIED)
    }

    /** Tag with the current place only for live SMS, processed within minutes of arriving. */
    private suspend fun withLocation(txn: Transaction, m: SmsMessageEntity): Transaction {
        if (m.origin != Origin.LIVE.name) return txn
        if (System.currentTimeMillis() - m.receivedAt > LIVE_LOCATION_WINDOW_MS) return txn
        if (!runCatching { preferences.locationEnabled.first() }.getOrDefault(false)) return txn
        val live = runCatching { locationRepository.captureCurrent() }.getOrNull() ?: return txn
        return txn.copy(
            latitude = live.latitude,
            longitude = live.longitude,
            placeName = live.placeName,
            locationAccuracy = live.accuracy,
            locationMatchedAt = System.currentTimeMillis(),
        )
    }

    suspend fun get(id: String): SmsMessageEntity? = dao.getById(id)

    private suspend fun save(m: SmsMessageEntity): SmsMessageEntity {
        val updated = m.copy(updatedAt = System.currentTimeMillis())
        dao.update(updated)
        return updated
    }

    companion object {
        private const val TAG = "SmsPipeline"

        const val STATUS_NEW = "NEW"
        const val STATUS_IGNORED = "IGNORED"
        const val STATUS_IMPORTED = "IMPORTED"
        const val STATUS_DUPLICATE = "DUPLICATE"
        const val STATUS_NEEDS_AI = "NEEDS_AI"
        const val STATUS_FAILED = "FAILED"
        /** Added by the user through the paste review form. */
        const val STATUS_MANUAL = "MANUAL"

        const val OVERRIDE_FORCE = "FORCE_IMPORT"
        const val OVERRIDE_DISMISSED = "DISMISSED"
        const val OVERRIDE_MANUAL = "MANUAL"
        const val OVERRIDE_REMOVED = "REMOVED"

        const val AI_NONE = "NONE"
        const val AI_NOT_NEEDED = "NOT_NEEDED"
        const val AI_PENDING = "PENDING"
        const val AI_DONE = "DONE"
        const val AI_FAILED = "FAILED"
        const val AI_SKIPPED = "SKIPPED"
        const val AI_REJECTED = "REJECTED"

        /** Prefix on [SmsMessageEntity.lastError] for failures worth retrying automatically. */
        const val TRANSIENT_PREFIX = "Temporary: "

        private const val DEDUPE_WINDOW_MS = 10 * 60_000L
        private const val LIVE_LOCATION_WINDOW_MS = 15 * 60_000L
        private const val DEFAULT_AI_BUDGET = 10
        private const val AI_CONCURRENCY = 3
        private const val PENDING_SCAN_LIMIT = 500
        private const val IGNORED_RETENTION_MS = 30L * 24 * 60 * 60_000L

        fun hashOf(sender: String, body: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("${sender.trim().uppercase()}|${body.trim()}".toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun splitList(raw: String): List<String> =
            raw.split(',', '\n', ';').map { it.trim().lowercase() }.filter { it.isNotBlank() }

        /** One line per guess, so the AI can confirm or correct each one. */
        fun hintsFor(s: LocalSnapshot): String = buildString {
            s.type?.let { appendLine("type: $it") }
            s.amountPaise?.let { appendLine("amount: ${Money(it).formatInr()}") }
            s.counterparty?.let { appendLine("counterparty: $it") }
            s.account?.let { appendLine("account: $it") }
            s.categoryName?.let { appendLine("category: $it (${s.categoryReason ?: "guess"})") }
        }.trim()
    }
}
