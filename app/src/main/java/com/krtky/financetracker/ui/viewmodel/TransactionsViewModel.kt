package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.llm.LlmClient
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.util.TransactionSortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val llmClient: LlmClient,
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

    fun isLlmConfigured(): Boolean = llmClient.isConfigured()

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

    suspend fun autoClassifyWithAi(targetIds: Set<String>? = null): Result<Int> {
        if (!llmClient.isConfigured()) {
            return Result.failure(IllegalStateException("AI helper is not configured"))
        }
        val list = transactions.value.filter { it.needsClassification() }
        val targets = if (!targetIds.isNullOrEmpty()) list.filter { it.id in targetIds } else list
        if (targets.isEmpty()) return Result.success(0)

        _isAiClassifying.value = true
        return try {
            val allCategories = categoryRepository.getAll()
            val categoryNames = allCategories.map { it.name }
            if (categoryNames.isEmpty()) return Result.success(0)

            val unclassifiedRows = targets.mapNotNull { t ->
                val text = t.rawDescription?.takeIf { it.isNotBlank() }
                    ?: t.counterparty?.takeIf { it.isNotBlank() }
                    ?: t.note?.takeIf { it.isNotBlank() }
                if (text != null) t to text else null
            }
            if (unclassifiedRows.isEmpty()) return Result.success(0)

            val distinctDescriptions = unclassifiedRows.map { it.second }.distinct()
            val aiClassifications = mutableMapOf<String, String>()

            distinctDescriptions.chunked(20).forEach { batch ->
                val res = llmClient.batchClassifyDescriptions(batch, categoryNames)
                aiClassifications.putAll(res)
            }

            var classifiedCount = 0
            if (aiClassifications.isNotEmpty()) {
                for ((txn, desc) in unclassifiedRows) {
                    val catName = aiClassifications[desc]
                        ?: aiClassifications.entries.firstOrNull { (k, _) ->
                            k.equals(desc, ignoreCase = true) || desc.contains(k, ignoreCase = true) || k.contains(desc, ignoreCase = true)
                        }?.value
                    if (catName != null) {
                        val matchedCat = allCategories.firstOrNull { it.name.equals(catName, ignoreCase = true) }
                        if (matchedCat != null) {
                            transactionRepository.classify(txn.id, matchedCat.id, null, null)
                            classifiedCount++
                        }
                    }
                }
            }
            Result.success(classifiedCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isAiClassifying.value = false
        }
    }

    suspend fun merge(ids: Set<String>): String? =
        transactionRepository.mergeTransactions(ids)
}
