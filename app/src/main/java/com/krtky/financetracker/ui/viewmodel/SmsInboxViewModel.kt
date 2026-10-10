package com.krtky.financetracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.krtky.financetracker.data.llm.LlmClient
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.SmsMessageEntity
import com.krtky.financetracker.data.sms.SmsInboxReader
import com.krtky.financetracker.data.sms.SmsPipeline
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.sms.SmsActionWorker
import com.krtky.financetracker.sms.SmsSyncWorker
import com.krtky.financetracker.ui.util.formatDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class SmsInboxViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDatabase,
    private val pipeline: SmsPipeline,
    private val reader: SmsInboxReader,
    private val llmClient: LlmClient,
) : ViewModel() {
    private val dao = db.smsMessageDao()
    private val json = Json { ignoreUnknownKeys = true }

    enum class Tone { GOOD, NEUTRAL, WARN, BAD }

    /** What the user can do with one message, depending on where the pipeline left it. */
    enum class RowAction(val label: String) {
        IMPORT_ANYWAY("Import anyway"),
        REVIEW("Review & add"),
        RETRY("Retry"),
        RETRY_AI("Retry AI"),
        OPEN("Open"),
        NOT_A_TRANSACTION("Not a transaction"),
        DISMISS("Dismiss"),
        RESET("Put back in queue"),
    }

    data class Row(
        val id: String,
        val sender: String,
        val time: String,
        val statusLabel: String,
        val tone: Tone,
        /** "−₹250.00 · Swiggy · Food" from the best available parse. */
        val headline: String?,
        /** Why it was ignored / what failed / where the category came from. */
        val detail: String?,
        val body: String,
        val ignored: Boolean,
        val transactionId: String?,
        val actions: List<RowAction>,
        /** An action was started and the background job has not updated the row yet. */
        val busy: Boolean,
    )

    data class Summary(
        val imported: Int = 0,
        val needsAi: Int = 0,
        val ignored: Int = 0,
        val failed: Int = 0,
        val duplicates: Int = 0,
        val pendingAi: Int = 0,
    )

    private val _showIgnored = MutableStateFlow(false)
    val showIgnored: StateFlow<Boolean> = _showIgnored

    /** id → updatedAt when an action was started; cleared once the row changes. */
    private val busy = MutableStateFlow<Map<String, Long>>(emptyMap())

    val rows: StateFlow<List<Row>> = combine(dao.observeRecent(RECENT_LIMIT), _showIgnored, busy) { list, showIgnored, busyMap ->
        list.map { toRow(it, busyMap[it.id] == it.updatedAt) }.filter { showIgnored || !it.ignored }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<Summary> = combine(dao.observeCounts(), dao.observePendingCount()) { counts, pending ->
        val c = counts.associate { it.status to it.n }
        Summary(
            imported = c[SmsPipeline.STATUS_IMPORTED] ?: 0,
            needsAi = c[SmsPipeline.STATUS_NEEDS_AI] ?: 0,
            ignored = c[SmsPipeline.STATUS_IGNORED] ?: 0,
            failed = c[SmsPipeline.STATUS_FAILED] ?: 0,
            duplicates = c[SmsPipeline.STATUS_DUPLICATE] ?: 0,
            pendingAi = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Summary())

    val syncing: StateFlow<Boolean> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(SmsSyncWorker.UNIQUE_NAME)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun hasReadPermission(): Boolean = reader.hasPermission()
    fun isAiConfigured(): Boolean = llmClient.isConfigured()
    fun setShowIgnored(show: Boolean) { _showIgnored.value = show }

    fun sync(hours: Int) = SmsSyncWorker.enqueue(context, hours)

    fun retryAi() = SmsSyncWorker.enqueue(context, hours = 0, requeueAi = true)

    /**
     * Run [action] on one message. [onOpen] opens a transaction; [onReview] opens the paste review
     * form with the SMS text so the user can fix fields and save it themselves.
     */
    fun act(row: Row, action: RowAction, onOpen: (String) -> Unit, onReview: (String) -> Unit) {
        when (action) {
            RowAction.OPEN -> row.transactionId?.let(onOpen)
            RowAction.REVIEW -> {
                viewModelScope.launch { pipeline.markManual(row.id) }
                onReview(row.body)
            }
            RowAction.DISMISS -> viewModelScope.launch { pipeline.dismiss(row.id) }
            RowAction.NOT_A_TRANSACTION -> viewModelScope.launch { pipeline.removeTransaction(row.id) }
            RowAction.IMPORT_ANYWAY -> background(row, SmsActionWorker.Action.FORCE_IMPORT)
            RowAction.RETRY, RowAction.RETRY_AI -> background(row, SmsActionWorker.Action.RETRY_AI)
            RowAction.RESET -> background(row, SmsActionWorker.Action.RESET)
        }
    }

    private fun background(row: Row, action: SmsActionWorker.Action) {
        viewModelScope.launch {
            dao.getById(row.id)?.let { busy.value = busy.value + (row.id to it.updatedAt) }
            SmsActionWorker.enqueue(context, row.id, action)
        }
    }

    private fun toRow(m: SmsMessageEntity, busy: Boolean): Row {
        val local = m.localSummary?.let { runCatching { json.decodeFromString<SmsPipeline.LocalSnapshot>(it) }.getOrNull() }
        val ai = m.aiSummary?.let { runCatching { json.decodeFromString<SmsPipeline.AiSnapshot>(it) }.getOrNull() }
        val type = ai?.type ?: local?.type
        val amount = ai?.amountPaise ?: local?.amountPaise
        val party = ai?.counterparty ?: local?.counterparty
        val category = ai?.categoryName ?: local?.categoryName?.takeIf { (local.categoryConfidence ?: 0.0) >= 0.7 }
        val headline = if (amount != null) {
            listOfNotNull(
                (if (type == "CREDIT") "+" else "−") + Money(amount).formatInr(),
                party,
                category,
            ).joinToString(" · ")
        } else null

        val (label, tone) = when (m.status) {
            SmsPipeline.STATUS_IMPORTED -> when (m.aiStatus) {
                SmsPipeline.AI_DONE -> "Imported · AI checked" to Tone.GOOD
                SmsPipeline.AI_PENDING -> "Imported · AI queued" to Tone.GOOD
                SmsPipeline.AI_FAILED -> "Imported · AI failed" to Tone.WARN
                SmsPipeline.AI_SKIPPED -> "Imported · AI off" to Tone.GOOD
                SmsPipeline.AI_REJECTED -> "Imported · AI disagrees" to Tone.WARN
                else -> "Imported" to Tone.GOOD
            }
            SmsPipeline.STATUS_DUPLICATE -> "Already in app" to Tone.NEUTRAL
            SmsPipeline.STATUS_NEEDS_AI -> (if (m.aiStatus == SmsPipeline.AI_FAILED) "Needs AI · failed" else "Needs AI") to Tone.WARN
            SmsPipeline.STATUS_FAILED -> "Failed" to Tone.BAD
            SmsPipeline.STATUS_IGNORED -> "Ignored" to Tone.NEUTRAL
            SmsPipeline.STATUS_MANUAL -> "Handled by you" to Tone.NEUTRAL
            else -> "Queued" to Tone.NEUTRAL
        }
        val detail = when {
            m.status == SmsPipeline.STATUS_IGNORED -> m.ignoreReason
            m.lastError != null -> m.lastError.removePrefix(SmsPipeline.TRANSIENT_PREFIX)
            ai?.agreedWithLocalCategory == false && local?.categoryName != null ->
                "AI changed category from ${local.categoryName}"
            local?.categoryReason != null && ai == null -> "Category: ${local.categoryReason}"
            else -> null
        }
        return Row(
            id = m.id,
            sender = m.sender,
            time = m.receivedAt.formatDateTime(),
            statusLabel = label,
            tone = tone,
            headline = headline,
            detail = detail,
            body = m.body,
            ignored = m.status == SmsPipeline.STATUS_IGNORED,
            transactionId = m.transactionId,
            actions = actionsFor(m),
            busy = busy,
        )
    }

    private fun actionsFor(m: SmsMessageEntity): List<RowAction> = when (m.status) {
        SmsPipeline.STATUS_IGNORED -> listOfNotNull(
            RowAction.IMPORT_ANYWAY,
            RowAction.REVIEW,
            RowAction.RESET.takeIf { m.userOverride == SmsPipeline.OVERRIDE_DISMISSED },
        )
        SmsPipeline.STATUS_NEEDS_AI, SmsPipeline.STATUS_FAILED -> listOf(RowAction.RETRY, RowAction.REVIEW, RowAction.DISMISS)
        SmsPipeline.STATUS_IMPORTED -> listOfNotNull(
            RowAction.OPEN,
            RowAction.RETRY_AI.takeIf {
                m.aiStatus in setOf(SmsPipeline.AI_FAILED, SmsPipeline.AI_REJECTED, SmsPipeline.AI_SKIPPED)
            },
            RowAction.NOT_A_TRANSACTION,
        )
        SmsPipeline.STATUS_DUPLICATE -> listOf(RowAction.OPEN, RowAction.RESET, RowAction.REVIEW)
        SmsPipeline.STATUS_MANUAL -> listOf(RowAction.RESET)
        else -> emptyList()
    }

    companion object {
        private const val RECENT_LIMIT = 150
    }
}
