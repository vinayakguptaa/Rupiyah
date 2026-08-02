package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.CashflowMetrics
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.MonthlySummary
import com.krtky.financetracker.domain.model.MonthlyTrend
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.ui.UiMessenger
import com.krtky.financetracker.ui.navigation.HomeSection
import com.krtky.financetracker.ui.navigation.HomeSectionConfig
import com.krtky.financetracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupChecklistState(
    val visible: Boolean = false,
    val gmailDone: Boolean = false, // kept for UI compatibility → means AI ready
    val sendersDone: Boolean = false, // kept → means banks configured
    val firstTxnDone: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val uiMessenger: UiMessenger,
    private val userPreferences: UserPreferences,
    private val secureStore: SecureStore,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _initialLoaded = MutableStateFlow(false)
    val initialLoaded: StateFlow<Boolean> = _initialLoaded

    val summary: StateFlow<MonthlySummary> = refresh.map {
        transactionRepository.monthlySummary()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthlySummary(0, 0))

    /** Lifestyle / credits / investment for the current month. */
    val cashflow: StateFlow<CashflowMetrics> = refresh.map {
        transactionRepository.cashflowMetrics()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CashflowMetrics(0, 0, 0, 0),
    )

    val funds: StateFlow<List<FundBalance>> = transactionRepository.observeFunds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Open tabs only (non-zero balance), for Home strip. */
    val openTabs: StateFlow<List<FundBalance>> = funds
        .map { list -> list.filter { it.balancePaise != 0L } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<Transaction>> = transactionRepository.observeTransactions()
        .map { it.take(15) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Net balance per account label (Cash, Digital, HDFC, …). */
    val paymentBalances: StateFlow<Map<String, Long>> =
        transactionRepository.observeAccountBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val categorySpend: StateFlow<List<CategorySpend>> = refresh.map {
        transactionRepository.categorySpend()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthlyTrend: StateFlow<List<MonthlyTrend>> = refresh.map {
        transactionRepository.monthlyTrend()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val displayName: StateFlow<String> = userPreferences.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val hideBalances: StateFlow<Boolean> = userPreferences.hideBalances
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pendingCount: StateFlow<Int> =
        transactionRepository.observePendingClassificationCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val firstPendingId: StateFlow<String?> =
        transactionRepository.observeFirstPendingClassificationId()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val setupChecklist: StateFlow<SetupChecklistState> = combine(
        userPreferences.setupChecklistDismissed,
        transactionRepository.observeTransactions(),
        accountRepository.observeActive(),
        userPreferences.smsEnabled,
    ) { dismissed, txns, accounts, smsOn ->
        val aiReady = secureStore.isLlmReady()
        val banksDone = accounts.any { !it.name.equals("Cash", true) }
        val firstTxnDone = txns.isNotEmpty()
        // Lightweight onboarding: AI (for SMS) + a bank + first txn (or SMS on is enough progress)
        val allDone = (aiReady || smsOn || firstTxnDone) && banksDone && firstTxnDone
        SetupChecklistState(
            visible = !dismissed && !allDone,
            gmailDone = aiReady,
            sendersDone = banksDone,
            firstTxnDone = firstTxnDone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupChecklistState())

    val homeSectionLayout: StateFlow<List<HomeSectionConfig>> = userPreferences.homeSectionOrder
        .map { HomeSection.parseLayout(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSection.DEFAULT_LAYOUT)

    /** Section order only (derived). */
    val homeSectionOrder: StateFlow<List<HomeSection>> = homeSectionLayout
        .map { list -> list.map { it.section } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSection.DEFAULT_ORDER)

    init {
        viewModelScope.launch {
            transactionRepository.observeTransactions().collect {
                refresh.value++
                if (!_initialLoaded.value) _initialLoaded.value = true
            }
        }
        // Refresh widgets when home data changes (and once on open).
        viewModelScope.launch {
            combine(summary, recent, funds, categorySpend) { _, _, _, _ -> }
                .collect {
                    WidgetUpdater.refreshAll(context)
                }
        }
    }

    fun setHideBalances(hidden: Boolean) {
        viewModelScope.launch {
            userPreferences.setHideBalances(hidden)
        }
    }

    fun dismissSetupChecklist() {
        viewModelScope.launch {
            userPreferences.setSetupChecklistDismissed(true)
        }
    }

    fun setHomeSectionLayout(layout: List<HomeSectionConfig>) {
        viewModelScope.launch {
            userPreferences.setHomeSectionOrder(HomeSection.serializeLayout(layout))
        }
    }

    fun setHomeSectionOrder(order: List<HomeSection>) {
        val spans = homeSectionLayout.value.associate { it.section to it.span }
        setHomeSectionLayout(
            order.map { section ->
                HomeSectionConfig(section, spans[section] ?: 2)
            },
        )
    }

    fun moveHomeSection(fromIndex: Int, toIndex: Int) {
        val current = homeSectionLayout.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        setHomeSectionLayout(current)
    }

    /** Toggle section width between full (2) and half (1). No-op if half-width is not allowed. */
    fun toggleHomeSectionSpan(section: HomeSection) {
        if (!section.allowsHalfWidth) return
        val current = homeSectionLayout.value.toMutableList()
        val idx = current.indexOfFirst { it.section == section }
        if (idx < 0) return
        val cfg = current[idx]
        val next = if (cfg.effectiveSpan == 2) 1 else 2
        current[idx] = cfg.copy(span = next)
        setHomeSectionLayout(current)
    }

    fun refreshNow() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                refresh.value++
                WidgetUpdater.refreshAll(context)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

