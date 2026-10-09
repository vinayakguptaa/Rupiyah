package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.llm.LlmResult
import com.krtky.financetracker.data.llm.TransactionClassifier
import com.krtky.financetracker.data.sms.TransactionParser
import kotlinx.coroutines.Job
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.util.TransactionSortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    private val classifier: TransactionClassifier,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val filters = TransactionFilterState()

    val query: StateFlow<String> = _query
    val typeFilter: StateFlow<TransactionType?> = filters.type
    val paymentFilter: StateFlow<String?> = filters.payment
    val categoryFilter: StateFlow<Long?> = filters.categoryId
    val tabFilter: StateFlow<Long?> = filters.tabId
    val needsClassify: StateFlow<Boolean> = filters.needsClassify
    val sortOrder: StateFlow<TransactionSortOrder> = filters.sort
    val timeRange: StateFlow<TimeRange> = filters.range
    val customFrom: StateFlow<Long> = filters.customFrom
    val customTo: StateFlow<Long> = filters.customTo

    val categories = categoriesState(categoryRepository, transactionRepository)
    val tabs = tabsState(transactionRepository)
    /** Active + archived account names for filters (history on old banks stays findable). */
    val bankAccounts = filterAccountNamesState(accountRepository, transactionRepository)

    private data class Head(
        val q: String,
        val t: TransactionType?,
        val pay: String?,
        val cat: Long?,
        val tab: Long?,
        val needsClassify: Boolean,
    )
    private data class Tail(
        val range: TimeRange,
        val from: Long,
        val to: Long,
        val sort: TransactionSortOrder,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> =
        combine(
            combine(
                _query,
                filters.typeFlow,
                filters.paymentFlow,
                filters.categoryIdFlow,
                filters.tabIdFlow,
            ) { q, t, pay, cat, tab -> Head(q, t, pay, cat, tab, needsClassify = false) },
            combine(
                filters.rangeFlow,
                filters.customFromFlow,
                filters.customToFlow,
                filters.sortFlow,
                filters.needsClassifyFlow,
            ) { r, from, to, sort, needs ->
                Tail(r, from, to, sort) to needs
            },
        ) { head, tailNeeds ->
            val (tail, needs) = tailNeeds
            head.copy(needsClassify = needs) to tail
        }
            .flatMapLatest { (head, tail) ->
                val (from, to) = tail.range.toMillisRange(tail.from, tail.to)
                transactionRepository.observeFiltered(head.q, head.t, head.cat, head.tab, from, to)
                    .map { list ->
                        val base = if (head.needsClassify) {
                            list.filter { it.needsClassification() }
                        } else {
                            list
                        }
                        val visible = if (head.tab == null) {
                            base.filter { !it.isTabTransfer() }
                        } else {
                            base
                        }
                        applyPaymentAndSort(visible, head.pay, tail.sort)
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setType(t: TransactionType?) = filters.setType(t)
    fun setPayment(p: String?) = filters.setPayment(p)
    fun setCategory(id: Long?) = filters.setCategory(id)
    fun setTab(id: Long?) = filters.setTab(id)
    fun setNeedsClassify(on: Boolean) = filters.setNeedsClassify(on)
    fun setSortOrder(order: TransactionSortOrder) = filters.setSortOrder(order)
    fun setTimeRange(r: TimeRange) = filters.setTimeRange(r)
    fun setCustomRange(fromMillis: Long, toMillis: Long) = filters.setCustomRange(fromMillis, toMillis)
    fun clearFilters() = filters.clear(type = null, clearQuery = { _query.value = "" })
    fun delete(ids: Set<String>) = viewModelScope.launch {
        ids.forEach { transactionRepository.delete(it) }
    }

    private val _isAiClassifying = MutableStateFlow(false)
    val isAiClassifying: StateFlow<Boolean> = _isAiClassifying.asStateFlow()

    private val _aiMessages = Channel<String>(Channel.BUFFERED)
    /** One-shot user messages from Auto-Classify (shown as snackbars). */
    val aiMessages: Flow<String> = _aiMessages.receiveAsFlow()

    fun isLlmConfigured(): Boolean = classifier.isConfigured()

    /** Per-transaction "Suggest with AI" state for the classify sheet. */
    data class AiSuggestState(
        val txnId: String,
        val loading: Boolean = true,
        val suggestion: TransactionParser.AiSuggestion? = null,
        val error: String? = null,
    )
    private val _aiSuggest = MutableStateFlow<AiSuggestState?>(null)
    val aiSuggest: StateFlow<AiSuggestState?> = _aiSuggest.asStateFlow()
    private var suggestJob: Job? = null

    fun suggestWithAi(txn: Transaction) {
        suggestJob?.cancel()
        _aiSuggest.value = AiSuggestState(txn.id)
        suggestJob = viewModelScope.launch {
            _aiSuggest.value = when (val r = classifier.suggest(txn)) {
                is LlmResult.Ok -> AiSuggestState(txn.id, loading = false, suggestion = r.value)
                is LlmResult.Failed -> AiSuggestState(txn.id, loading = false, error = r.error.describe())
            }
        }
    }

    fun applyAiSuggestion() {
        val state = _aiSuggest.value ?: return
        val suggestion = state.suggestion ?: return
        _aiSuggest.value = null
        viewModelScope.launch { classifier.applySuggestion(state.txnId, suggestion) }
    }

    fun clearAiSuggestion() {
        suggestJob?.cancel()
        _aiSuggest.value = null
    }

    fun quickClassify(id: String, categoryId: Long) = viewModelScope.launch {
        transactionRepository.classify(id, categoryId, null, null)
    }

    fun skipClassification(id: String) = viewModelScope.launch {
        transactionRepository.skipClassification(id)
    }

    fun bulkClassify(ids: Set<String>, categoryId: Long) = viewModelScope.launch {
        transactionRepository.bulkClassify(ids, categoryId)
    }

    fun bulkSkip(ids: Set<String>) = viewModelScope.launch {
        transactionRepository.bulkSkipClassification(ids)
    }

    /**
     * Classify the pending rows currently shown (or just [targetIds]) with AI.
     * Runs in [viewModelScope] so leaving the screen does not drop the results;
     * each batch is saved as soon as it returns.
     */
    fun autoClassifyWithAi(targetIds: Set<String>? = null) {
        if (_isAiClassifying.value) return
        if (!classifier.isConfigured()) {
            _aiMessages.trySend("Set up the AI helper in Settings first")
            return
        }
        val pending = transactions.value.filter { it.needsClassification() }
        val targets = if (!targetIds.isNullOrEmpty()) pending.filter { it.id in targetIds } else pending
        if (targets.isEmpty()) {
            _aiMessages.trySend("Nothing waiting for a category here")
            return
        }
        _isAiClassifying.value = true
        viewModelScope.launch {
            try {
                val outcome = classifier.classifyAndSave(targets, categoryRepository.getAll())
                val count = outcome.matches.size
                val error = outcome.error
                _aiMessages.send(
                    when {
                        error != null && count > 0 -> "AI classified $count, then stopped: ${error.describe()}"
                        error != null -> error.describe()
                        count > 0 -> "AI classified $count of ${targets.size} transaction(s)"
                        else -> "AI was not confident about any of these — classify them by hand"
                    },
                )
            } finally {
                _isAiClassifying.value = false
            }
        }
    }

    suspend fun merge(ids: Set<String>): String? =
        transactionRepository.mergeTransactions(ids)
}
