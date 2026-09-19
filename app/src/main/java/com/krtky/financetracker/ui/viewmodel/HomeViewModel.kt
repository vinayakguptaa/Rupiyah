package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CashflowRepository
import com.krtky.financetracker.data.repository.HomeCashflowSnapshot
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.TabBalance
import com.krtky.financetracker.domain.model.MonthlySummary
import com.krtky.financetracker.ui.UiMessenger
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
    val aiReady: Boolean = false,
    val banksDone: Boolean = false,
    val firstTxnDone: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val transactionRepository: TransactionRepository,
    private val cashflowRepository: CashflowRepository,
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

    /**
     * One cashflow snapshot for the current month: summary + debit/credit
     * breakdowns by category and source.
     */
    val homeCashflow: StateFlow<HomeCashflowSnapshot> = refresh.map {
        cashflowRepository.homeCashflowSnapshot()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeCashflowSnapshot(
            MonthlySummary(0, 0),
            emptyList(),
            emptyList(),
        ),
    )

    val tabs: StateFlow<List<TabBalance>> = transactionRepository.observeTabs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Open tabs only (non-zero balance), for Home strip. */
    val openTabs: StateFlow<List<TabBalance>> = tabs
        .map { list -> list.filter { it.balancePaise != 0L } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Net balance per account label (Cash, Digital, HDFC, …). */
    val paymentBalances: StateFlow<Map<String, Long>> =
        cashflowRepository.observeAccountBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    val activeAccounts = accountRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val setupChecklist: StateFlow<SetupChecklistState> = combine(
        userPreferences.setupChecklistDismissed,
        transactionRepository.observeTransactions(),
        accountRepository.observeActive(),
        userPreferences.smsEnabled,
    ) { dismissed, txns, accounts, smsOn ->
        val aiReady = secureStore.isLlmReady()
        val banksDone = accounts.any { !it.name.equals("Cash", true) }
        val firstTxnDone = txns.isNotEmpty()
        val allDone = (aiReady || smsOn || firstTxnDone) && banksDone && firstTxnDone
        SetupChecklistState(
            visible = !dismissed && !allDone,
            aiReady = aiReady,
            banksDone = banksDone,
            firstTxnDone = firstTxnDone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupChecklistState())

    init {
        viewModelScope.launch {
            transactionRepository.observeTransactions().collect {
                refresh.value++
                if (!_initialLoaded.value) _initialLoaded.value = true
            }
        }
        viewModelScope.launch {
            combine(homeCashflow, tabs) { _, _ -> }
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
