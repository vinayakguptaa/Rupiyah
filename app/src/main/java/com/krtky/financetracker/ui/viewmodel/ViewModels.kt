package com.krtky.financetracker.ui.viewmodel

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.email.EmailIngestService
import com.krtky.financetracker.data.email.ImapEmailClient
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.data.repository.TrustedSenderRepository
import com.krtky.financetracker.data.sheets.SheetsSyncService
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.domain.model.MonthlySummary
import com.krtky.financetracker.domain.model.MonthlyTrend
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.domain.model.TrustedSender
import com.krtky.financetracker.email.EmailMonitorService
import com.krtky.financetracker.location.LocationRepository
import com.krtky.financetracker.location.LocationTrackingService
import com.krtky.financetracker.ui.theme.ThemeMode
import com.krtky.financetracker.ui.theme.ThemePreset
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.long
import kotlinx.serialization.json.int
import kotlinx.serialization.json.double
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.CategoryEntity
import com.krtky.financetracker.data.local.db.FundEntity
import com.krtky.financetracker.data.local.db.FundLedgerEntity
import com.krtky.financetracker.data.local.db.TransactionEntity
import com.krtky.financetracker.data.local.db.TrustedSenderEntity
import com.krtky.financetracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val transactionRepository: TransactionRepository,
    private val emailIngest: EmailIngestService,
    private val uiMessenger: com.krtky.financetracker.ui.UiMessenger,
    userPreferences: UserPreferences,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _initialLoaded = MutableStateFlow(false)
    val initialLoaded: StateFlow<Boolean> = _initialLoaded

    val summary: StateFlow<MonthlySummary> = refresh.map {
        transactionRepository.monthlySummary()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthlySummary(0, 0))

    val funds: StateFlow<List<FundBalance>> = transactionRepository.observeFunds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<Transaction>> = transactionRepository.observeTransactions()
        .map { it.take(15) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val paymentBalances: StateFlow<Map<String, Long>> = transactionRepository.observeTransactions()
        .map { transactions ->
            transactions
                .groupBy { if (it.isCash) "Cash" else "Digital" }
                .mapValues { (_, items) ->
                    items.sumOf { if (it.type == TransactionType.INCOME) it.amountPaise else -it.amountPaise }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val categorySpend: StateFlow<List<CategorySpend>> = refresh.map {
        transactionRepository.categorySpend()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthlyTrend: StateFlow<List<MonthlyTrend>> = refresh.map {
        transactionRepository.monthlyTrend()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val displayName: StateFlow<String> = userPreferences.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        viewModelScope.launch {
            transactionRepository.observeTransactions().collect { refresh.value++ }
        }
        viewModelScope.launch {
            transactionRepository.observeTransactions().first()
            _initialLoaded.value = true
        }
        // Update home screen widgets when summary data changes
        viewModelScope.launch {
            summary.collect { monthlySummary ->
                WidgetUpdater.updateWidgetData(
                    context = context,
                    totalBalancePaise = monthlySummary.netPaise,
                    monthlySpentPaise = monthlySummary.expensePaise,
                    monthlyIncomePaise = monthlySummary.incomePaise
                )
            }
        }
        // Update funds widget
        viewModelScope.launch {
            funds.collect { fundList ->
                WidgetUpdater.updateFundsWidget(context, fundList)
            }
        }
        // Update category widget
        viewModelScope.launch {
            categorySpend.collect { categoryList ->
                val totalSpent = summary.value.expensePaise
                WidgetUpdater.updateCategoryWidget(context, categoryList, totalSpent)
            }
        }
    }

    fun refreshNow() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                refresh.value++
                val result = runCatching { emailIngest.ingest(force = true) }.getOrElse { e ->
                    uiMessenger.show(e.message?.take(80) ?: "Couldn't reach Gmail")
                    null
                }
                if (result != null && result.error != null) {
                    val msg = result.error
                    if (!msg.contains("not configured", ignoreCase = true) &&
                        !msg.contains("No trusted", ignoreCase = true)
                    ) {
                        uiMessenger.show(msg.take(80))
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

enum class TimeRange { TODAY, WEEK, MONTH, YEAR, ALL, CUSTOM }

fun TimeRange.toMillisRange(
    customFromMillis: Long = 0L,
    customToMillis: Long = System.currentTimeMillis(),
): Pair<Long, Long> {
    if (this == TimeRange.CUSTOM) {
        val start = Calendar.getInstance().apply {
            timeInMillis = customFromMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = customToMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return minOf(start, end) to maxOf(start, end)
    }
    val cal = Calendar.getInstance()
    val to = cal.timeInMillis + 86_400_000L
    when (this) {
        TimeRange.TODAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        TimeRange.WEEK -> cal.add(Calendar.DAY_OF_YEAR, -7)
        TimeRange.MONTH -> cal.add(Calendar.MONTH, -1)
        TimeRange.YEAR -> cal.add(Calendar.YEAR, -1)
        TimeRange.ALL -> cal.add(Calendar.YEAR, -10)
        TimeRange.CUSTOM -> Unit
    }
    return cal.timeInMillis to to
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val _type = MutableStateFlow<TransactionType?>(null)
    private val _payment = MutableStateFlow<String?>(null)
    private val _range = MutableStateFlow(TimeRange.MONTH)
    private val _customFrom = MutableStateFlow(
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis,
    )
    private val _customTo = MutableStateFlow(System.currentTimeMillis())
    val query: StateFlow<String> = _query
    val typeFilter: StateFlow<TransactionType?> = _type
    val paymentFilter: StateFlow<String?> = _payment
    val timeRange: StateFlow<TimeRange> = _range
    val customFrom: StateFlow<Long> = _customFrom
    val customTo: StateFlow<Long> = _customTo

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> =
        combine(_query, _type, _payment, _range, _customFrom) { q, t, pay, r, from ->
            Triple(q, t, Triple(pay, r, from))
        }
            .combine(_customTo) { triple, to -> triple to to }
            .flatMapLatest { (triple, cTo) ->
                val (q, t, rest) = triple
                val (pay, r, cFrom) = rest
                val (from, to) = r.toMillisRange(cFrom, cTo)
                transactionRepository.observeFiltered(q, t, null, null, from, to)
                    .map { list ->
                        if (pay == null) list
                        else list.filter { txn ->
                            val method = when {
                                txn.isCash || txn.paymentMethod.equals("Cash", true) -> "Cash"
                                else -> "Digital"
                            }
                            method == pay
                        }
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setType(t: TransactionType?) { _type.value = t }
    fun setPayment(p: String?) { _payment.value = p }
    fun setTimeRange(r: TimeRange) { _range.value = r }
    fun setCustomRange(fromMillis: Long, toMillis: Long) {
        _customFrom.value = fromMillis
        _customTo.value = toMillis
        _range.value = TimeRange.CUSTOM
    }
    fun clearFilters() {
        _type.value = null
        _payment.value = null
        _range.value = TimeRange.MONTH
        _query.value = ""
    }
    fun delete(ids: Set<String>) = viewModelScope.launch {
        ids.forEach { transactionRepository.delete(it) }
    }
}

@HiltViewModel
class AddCashViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
    userPreferences: UserPreferences,
) : ViewModel() {
    val categories = combine(
        categoryRepository.observeAll(),
        transactionRepository.observeCategoryUsage(),
    ) { cats, usage ->
        cats.sortedWith(
            compareByDescending<com.krtky.financetracker.domain.model.Category> { usage[it.id] ?: 0L }
                .thenBy { it.sortOrder }
                .thenBy { it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val funds = transactionRepository.observeFunds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bankAccounts = combine(
        userPreferences.bankAccounts,
        transactionRepository.observePaymentMethodUsage(),
    ) { raw, usage ->
        userPreferences.parseBankList(raw).sortedByDescending { usage[it] ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val defaultPaymentMethod = userPreferences.defaultPaymentMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Cash")

    suspend fun save(
        amountText: String,
        type: TransactionType,
        categoryId: Long?,
        fundId: Long?,
        note: String,
        counterparty: String = "",
        paymentMethod: String,
        useLocation: Boolean,
        addToFund: Boolean,
        occurredAt: Long = System.currentTimeMillis(),
    ): Boolean {
        val money = Money.fromRupeesString(amountText) ?: return false
        val loc = if (useLocation) locationRepository.captureCurrent() else null
        val party = counterparty.ifBlank { null }
        val txn = Transaction(
            id = UUID.randomUUID().toString(),
            type = type,
            amountPaise = money.paise,
            occurredAt = occurredAt,
            merchant = party,
            counterparty = party,
            categoryId = categoryId,
            fundId = fundId,
            paymentMethod = paymentMethod,
            source = TransactionSource.MANUAL,
            note = note.ifBlank { null },
            isCash = paymentMethod == "Cash",
            classificationStatus = if (categoryId != null) ClassificationStatus.CLASSIFIED else ClassificationStatus.PENDING,
            latitude = loc?.latitude,
            longitude = loc?.longitude,
            placeName = loc?.placeName,
            locationAccuracy = loc?.accuracy,
            locationMatchedAt = if (loc != null) System.currentTimeMillis() else null,
        )
        transactionRepository.insertManual(txn, addToFund = addToFund && type == TransactionType.INCOME)
        return true
    }
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
    userPreferences: UserPreferences,
) : ViewModel() {
    private val _txn = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _txn
    val categories = combine(
        categoryRepository.observeAll(),
        transactionRepository.observeCategoryUsage(),
    ) { cats, usage ->
        cats.sortedWith(
            compareByDescending<com.krtky.financetracker.domain.model.Category> { usage[it.id] ?: 0L }
                .thenBy { it.sortOrder }
                .thenBy { it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val funds = transactionRepository.observeFunds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bankAccounts = combine(
        userPreferences.bankAccounts,
        transactionRepository.observePaymentMethodUsage(),
    ) { raw, usage ->
        userPreferences.parseBankList(raw).sortedByDescending { usage[it] ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(id: String) {
        viewModelScope.launch { _txn.value = transactionRepository.getById(id) }
    }

    suspend fun save(
        amountText: String,
        type: TransactionType,
        occurredAt: Long,
        paymentMethod: String,
        categoryId: Long?,
        fundId: Long?,
        note: String,
        counterparty: String = "",
        useCurrentLocation: Boolean,
    ): Boolean {
        val t = _txn.value ?: return false
        val amount = Money.fromRupeesString(amountText) ?: return false
        val location = if (useCurrentLocation) locationRepository.captureCurrent() else null
        transactionRepository.update(
            t.copy(
                amountPaise = amount.paise,
                type = type,
                occurredAt = occurredAt,
                paymentMethod = paymentMethod,
                isCash = paymentMethod == "Cash",
                categoryId = categoryId,
                fundId = fundId,
                note = note.ifBlank { null },
                counterparty = counterparty.ifBlank { null },
                merchant = counterparty.ifBlank { t.merchant },
                latitude = location?.latitude ?: t.latitude,
                longitude = location?.longitude ?: t.longitude,
                placeName = location?.placeName ?: t.placeName,
                locationAccuracy = location?.accuracy ?: t.locationAccuracy,
                locationMatchedAt = if (location != null) System.currentTimeMillis() else t.locationMatchedAt,
            )
        )
        return true
    }

    suspend fun delete() {
        _txn.value?.id?.let { transactionRepository.delete(it) }
    }
}

@HiltViewModel
class FundsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    val funds = transactionRepository.observeFunds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun create(name: String, openingAmountText: String = "") {
        if (name.isBlank()) return
        val id = transactionRepository.addFund(name.trim())
        val opening = Money.fromRupeesString(openingAmountText)
        if (opening != null && opening.paise != 0L) {
            transactionRepository.adjustFund(id, opening.paise, "Opening balance")
        }
    }

    suspend fun adjust(fundId: Long, amountText: String) {
        val money = Money.fromRupeesString(amountText) ?: return
        transactionRepository.adjustFund(fundId, money.paise, "Manual adjustment")
    }

    suspend fun delete(fundId: Long) {
        transactionRepository.deleteFund(fundId)
    }
}

@HiltViewModel
class FundDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    private val fundIdFlow = MutableStateFlow<Long?>(null)
    private val _fund = MutableStateFlow<FundBalance?>(null)
    private val _range = MutableStateFlow(TimeRange.MONTH)
    val fund: StateFlow<FundBalance?> = _fund
    val timeRange: StateFlow<TimeRange> = _range

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> =
        combine(fundIdFlow, _range) { id, r -> id to r }
            .flatMapLatest { (id, r) ->
                if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
                else {
                    val (from, to) = r.toMillisRange()
                    transactionRepository.observeFiltered("", null, null, id, from, to)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(id: Long) {
        fundIdFlow.value = id
        viewModelScope.launch {
            _fund.value = transactionRepository.observeFunds().first().firstOrNull { it.fund.id == id }
        }
    }

    fun setTimeRange(r: TimeRange) { _range.value = r }

    suspend fun deleteFund(): Boolean {
        val id = fundIdFlow.value ?: return false
        transactionRepository.deleteFund(id)
        return true
    }
}

@HiltViewModel
class ClassifyViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    private val _txnId = MutableStateFlow<String?>(null)
    private val _txn = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _txn
    val categories = combine(
        categoryRepository.observeAll(),
        transactionRepository.observeCategoryUsage(),
    ) { cats, usage ->
        cats.sortedWith(
            compareByDescending<com.krtky.financetracker.domain.model.Category> { usage[it.id] ?: 0L }
                .thenBy { it.sortOrder }
                .thenBy { it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val funds = transactionRepository.observeFunds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open(id: String) {
        _txnId.value = id
        viewModelScope.launch { _txn.value = transactionRepository.getById(id) }
    }

    fun clear() {
        _txnId.value = null
        _txn.value = null
    }

    suspend fun save(categoryId: Long?, fundId: Long?, note: String) {
        val id = _txnId.value ?: return
        transactionRepository.classify(id, categoryId, note.ifBlank { null }, fundId)
        clear()
    }
}

data class SettingsUiState(
    val llmApiKeySet: Boolean = false,
    val llmBaseUrl: String = SecureStore.DEFAULT_LLM_BASE,
    val llmModel: String = SecureStore.DEFAULT_LLM_MODEL,
    val gmail: String = "",
    val gmailPassSet: Boolean = false,
    val emailPoll: Boolean = false,
    val location: Boolean = false,
    val sheetsSync: Boolean = false,
    val sheetId: String = "",
    val sheetTokenSet: Boolean = false,
    val displayName: String = "",
    val profileEmail: String = "",
    val profilePhone: String = "",
    val themeMode: ThemeMode = ThemeMode.MATERIAL_YOU,
    val themePreset: ThemePreset = ThemePreset.INDIGO,
    val themeCustomPrimary: String = "#4253D4",
    val themeCustomSecondary: String = "#5B647A",
    val themeCustomTertiary: String = "#7153A8",
    val smsEnabled: Boolean = false,
    val smsSenders: String = "",
    val smsKeywords: String = "debited,credited,spent,paid,sent,received,transaction,INR,Rs,UPI",
    val bankAccounts: String = "HDFC,ICICI,SBI,Axis",
    val defaultPaymentMethod: String = "Cash",
    val devUnlocked: Boolean = false,
    val llmSystemPrompt: String = "",
    val classificationDelayMin: Long = 15L,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStore: SecureStore,
    private val userPreferences: UserPreferences,
    private val trustedSenderRepository: TrustedSenderRepository,
    private val categoryRepository: CategoryRepository,
    private val emailIngestService: EmailIngestService,
    private val sheetsSyncService: SheetsSyncService,
    private val imapEmailClient: ImapEmailClient,
    private val db: AppDatabase,
    private val uiMessenger: com.krtky.financetracker.ui.UiMessenger,
) : ViewModel() {
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SettingsUiState> = _state
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    val senders = trustedSenderRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            combine(
                combine(
                    userPreferences.emailPollEnabled,
                    userPreferences.locationEnabled,
                    userPreferences.sheetsSyncEnabled,
                ) { e, l, s -> Triple(e, l, s) },
                combine(
                    userPreferences.smsEnabled,
                    userPreferences.smsSenders,
                    userPreferences.smsKeywords,
                ) { sms, senders, keywords -> Triple(sms, senders, keywords) },
                combine(
                    userPreferences.bankAccounts,
                    userPreferences.defaultPaymentMethod,
                    userPreferences.devUnlocked,
                    userPreferences.classificationDelayMin,
                ) { banks, defPay, dev, delay -> listOf(banks, defPay, dev, delay) },
            ) { main, sms, extra -> Triple(main, sms, extra) }.collect { (main, sms, extra) ->
                val (e, l, s) = main
                val cur = _state.value
                _state.value = loadState().copy(
                    emailPoll = e,
                    location = l,
                    sheetsSync = s,
                    smsEnabled = sms.first,
                    smsSenders = sms.second,
                    smsKeywords = sms.third,
                    bankAccounts = extra[0] as String,
                    defaultPaymentMethod = extra[1] as String,
                    devUnlocked = extra[2] as Boolean,
                    classificationDelayMin = extra[3] as Long,
                    displayName = cur.displayName,
                    profileEmail = cur.profileEmail,
                    profilePhone = cur.profilePhone,
                )
            }
        }
        viewModelScope.launch {
            combine(
                userPreferences.themeMode,
                userPreferences.themePreset,
                userPreferences.themeCustomPrimary,
                userPreferences.themeCustomSecondary,
                userPreferences.themeCustomTertiary,
            ) { mode, preset, primary, secondary, tertiary ->
                arrayOf(mode, preset, primary, secondary, tertiary)
            }.collect { values ->
                _state.value = _state.value.copy(
                    themeMode = values[0] as ThemeMode,
                    themePreset = values[1] as ThemePreset,
                    themeCustomPrimary = values[2] as String,
                    themeCustomSecondary = values[3] as String,
                    themeCustomTertiary = values[4] as String,
                )
            }
        }
        viewModelScope.launch {
            combine(
                userPreferences.displayName,
                userPreferences.profileEmail,
                userPreferences.profilePhone,
            ) { n, e, p -> Triple(n, e, p) }.collect { (n, e, p) ->
                _state.value = _state.value.copy(displayName = n, profileEmail = e, profilePhone = p)
            }
        }
    }

    private fun loadState() = SettingsUiState(
        llmApiKeySet = !secureStore.llmApiKey.isNullOrBlank(),
        llmBaseUrl = secureStore.llmBaseUrl,
        llmModel = secureStore.llmModel,
        gmail = secureStore.gmailAddress.orEmpty(),
        gmailPassSet = !secureStore.gmailAppPassword.isNullOrBlank(),
        sheetId = secureStore.sheetsSpreadsheetId.orEmpty(),
        sheetTokenSet = !secureStore.sheetsAccessToken.isNullOrBlank(),
        themeMode = runBlocking { userPreferences.themeMode.first() },
        themePreset = runBlocking { userPreferences.themePreset.first() },
        themeCustomPrimary = runBlocking { userPreferences.themeCustomPrimary.first() },
        themeCustomSecondary = runBlocking { userPreferences.themeCustomSecondary.first() },
        themeCustomTertiary = runBlocking { userPreferences.themeCustomTertiary.first() },
        smsEnabled = runBlocking { userPreferences.smsEnabled.first() },
        smsSenders = runBlocking { userPreferences.smsSenders.first() },
        smsKeywords = runBlocking { userPreferences.smsKeywords.first() },
        bankAccounts = runBlocking { userPreferences.bankAccounts.first() },
        defaultPaymentMethod = runBlocking { userPreferences.defaultPaymentMethod.first() },
        devUnlocked = runBlocking { userPreferences.devUnlocked.first() },
        llmSystemPrompt = secureStore.llmSystemPrompt,
        classificationDelayMin = runBlocking { userPreferences.classificationDelayMin.first() },
    )

    fun saveProfile(name: String, email: String, phone: String) = viewModelScope.launch {
        userPreferences.setProfile(name, email, phone)
        _status.value = "Profile saved"
    }

    fun setSmsEnabled(enabled: Boolean) = viewModelScope.launch {
        userPreferences.setSmsEnabled(enabled)
    }

    fun saveSmsRules(senders: String, keywords: String) = viewModelScope.launch {
        userPreferences.setSmsRules(senders, keywords)
        _status.value = "SMS rules saved"
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        userPreferences.setThemeMode(mode)
    }

    fun setThemePreset(preset: ThemePreset) = viewModelScope.launch {
        userPreferences.setThemePreset(preset)
    }

    fun setThemeCustomColors(primary: String, secondary: String, tertiary: String) = viewModelScope.launch {
        userPreferences.setThemeCustomColors(primary, secondary, tertiary)
    }

    fun saveLlm(base: String, model: String, key: String?) {
        secureStore.llmBaseUrl = base.ifBlank { SecureStore.DEFAULT_LLM_BASE }
        secureStore.llmModel = model.ifBlank { SecureStore.DEFAULT_LLM_MODEL }
        if (key != null) secureStore.llmApiKey = key
        _state.value = loadState().copy(
            emailPoll = _state.value.emailPoll,
            location = _state.value.location,
            sheetsSync = _state.value.sheetsSync,
        )
        _status.value = "LLM settings saved"
    }

    fun saveGmail(address: String, password: String?) {
        secureStore.gmailAddress = address.trim().lowercase()
        // Strip spaces from app password (Google shows them in groups of 4)
        if (password != null) secureStore.gmailAppPassword = password.replace(" ", "").trim()
        _state.value = loadState().copy(
            emailPoll = _state.value.emailPoll,
            location = _state.value.location,
            sheetsSync = _state.value.sheetsSync,
        )
        _status.value = "Gmail saved — tap Test connection"
    }

    suspend fun testGmail() {
        _status.value = "Testing IMAP…"
        val r = imapEmailClient.testConnection()
        _status.value = r.fold(
            onSuccess = { it },
            onFailure = { it.message ?: "Connection failed" },
        )
    }

    fun saveSheets(id: String, token: String?) {
        secureStore.sheetsSpreadsheetId = id.trim()
        if (token != null) secureStore.sheetsAccessToken = token.trim()
        _state.value = loadState().copy(
            emailPoll = _state.value.emailPoll,
            location = _state.value.location,
            sheetsSync = _state.value.sheetsSync,
        )
        _status.value = "Sheets settings saved"
    }

    fun googleSignInIntent(context: Context): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SHEETS_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    suspend fun completeGoogleSignIn(context: Context, data: Intent?): Boolean {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val token = withContext(Dispatchers.IO) {
                GoogleAuthUtil.getToken(context, account.account!!, "oauth2:$SHEETS_SCOPE")
            }
            secureStore.sheetsAccessToken = token
            _state.value = loadState().copy(
                emailPoll = _state.value.emailPoll,
                location = _state.value.location,
                sheetsSync = _state.value.sheetsSync,
            )
            _status.value = "Google connected as ${account.email.orEmpty()}"
            true
        } catch (e: Exception) {
            _status.value = e.message ?: "Google sign-in failed"
            false
        }
    }

    suspend fun createSheetsSpreadsheet(title: String): Result<String> {
        _status.value = "Creating spreadsheet…"
        return sheetsSyncService.createSpreadsheet(title).also { result ->
            _status.value = result.fold(
                onSuccess = { id ->
                    secureStore.sheetsSpreadsheetId = id
                    "Spreadsheet created"
                },
                onFailure = { it.message ?: "Create spreadsheet failed" },
            )
            if (result.isSuccess) {
                _state.value = loadState().copy(
                    emailPoll = _state.value.emailPoll,
                    location = _state.value.location,
                    sheetsSync = _state.value.sheetsSync,
                )
            }
        }
    }

    fun setEmailPoll(context: Context, v: Boolean) = viewModelScope.launch {
        userPreferences.setEmailPollEnabled(v)
        if (v) {
            EmailMonitorService.start(context)
            // Prompt battery unrestricted so background IDLE survives
            try {
                val pm = context.getSystemService(android.os.PowerManager::class.java)
                val pkg = context.packageName
                if (!pm.isIgnoringBatteryOptimizations(pkg)) {
                    val i = android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:$pkg"),
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                }
            } catch (_: Exception) {
            }
            _status.value = "Live email monitor started — allow unrestricted battery if asked"
        } else {
            EmailMonitorService.stop(context)
            _status.value = "Email monitor stopped"
        }
    }
    fun setSheets(v: Boolean) = viewModelScope.launch { userPreferences.setSheetsSyncEnabled(v) }

    fun setLocation(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setLocationEnabled(enabled)
            val intent = Intent(context, LocationTrackingService::class.java)
            if (enabled) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }

    fun addSender(email: String, label: String) {
        if (email.isBlank()) return
        viewModelScope.launch {
            trustedSenderRepository.upsert(
                TrustedSender(emailPattern = email.trim(), walletLabel = label.ifBlank { "Wallet" })
            )
        }
    }

    fun deleteSender(id: Long) = viewModelScope.launch { trustedSenderRepository.delete(id) }

    fun addCategory(name: String, icon: String = "category", quick: Boolean = true) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.upsert(
                Category(name = name.trim(), icon = icon, isQuickAction = quick),
            )
            _status.value = "Category added"
        }
    }

    fun updateCategory(id: Long, name: String, icon: String, quick: Boolean) {
        if (id <= 0 || name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.upsert(
                Category(id = id, name = name.trim(), icon = icon, isQuickAction = quick),
            )
            _status.value = "Category updated"
        }
    }

    fun deleteCategory(id: Long) = viewModelScope.launch {
        categoryRepository.delete(id)
        _status.value = "Category deleted"
    }

    fun saveBankAccounts(raw: String) = viewModelScope.launch {
        userPreferences.setBankAccounts(raw)
        _status.value = "Bank accounts saved"
    }

    fun saveDefaultPaymentMethod(method: String) = viewModelScope.launch {
        userPreferences.setDefaultPaymentMethod(method)
        _status.value = "Default method saved"
    }

    fun unlockDev() = viewModelScope.launch {
        userPreferences.setDevUnlocked(true)
        _status.value = "Developer options unlocked"
    }

    fun saveSystemPrompt(prompt: String) {
        secureStore.llmSystemPrompt = prompt.ifBlank { SecureStore.DEFAULT_LLM_SYSTEM }
        _state.value = _state.value.copy(llmSystemPrompt = secureStore.llmSystemPrompt)
        _status.value = "System prompt saved"
    }

    fun resetSystemPrompt() {
        secureStore.llmSystemPrompt = SecureStore.DEFAULT_LLM_SYSTEM
        _state.value = _state.value.copy(llmSystemPrompt = SecureStore.DEFAULT_LLM_SYSTEM)
        _status.value = "System prompt reset"
    }

    fun setClassificationDelay(min: Long) = viewModelScope.launch {
        userPreferences.setClassificationDelayMin(min.coerceIn(0L, 240L))
        _status.value = "Classification delay updated"
    }

    suspend fun pollNow() {
        _status.value = "Polling…"
        val r = emailIngestService.ingest(force = true)
        val msg = r.error ?: "Created ${r.created}, skipped ${r.skipped}"
        _status.value = msg
        if (r.error != null) uiMessenger.show(r.error.take(80))
    }

    suspend fun processPaste(sender: String, subject: String, body: String) {
        _status.value = "Processing…"
        val id = emailIngestService.processPastedEmail(sender, subject, body)
        _status.value = if (id != null) "Transaction created" else "Could not parse / duplicate / not trusted"
    }

    suspend fun syncSheetsNow() {
        _status.value = "Syncing…"
        val r = sheetsSyncService.sync()
        _status.value = r.fold(
            onSuccess = { "Synced $it row(s)" },
            onFailure = {
                val m = it.message ?: "Sync failed"
                uiMessenger.show(m.take(80))
                m
            },
        )
    }

    companion object {
        private const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
    }

    fun setStatus(msg: String?) {
        _status.value = msg
    }

    suspend fun exportData(context: Context, uri: Uri): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val json = buildJsonObject {
                    put("version", 1)
                    put("secure_store", buildJsonObject {
                        put("llm_api_key", secureStore.llmApiKey.orEmpty())
                        put("llm_base_url", secureStore.llmBaseUrl)
                        put("llm_model", secureStore.llmModel)
                        put("gmail_address", secureStore.gmailAddress.orEmpty())
                        put("gmail_app_password", secureStore.gmailAppPassword.orEmpty())
                        put("sheets_spreadsheet_id", secureStore.sheetsSpreadsheetId.orEmpty())
                        put("sheets_access_token", secureStore.sheetsAccessToken.orEmpty())
                    })
                    put("user_prefs", buildJsonObject {
                        put("location_enabled", userPreferences.locationEnabled.first())
                        put("email_poll_enabled", userPreferences.emailPollEnabled.first())
                        put("sheets_sync_enabled", userPreferences.sheetsSyncEnabled.first())
                        put("classification_delay_min", userPreferences.classificationDelayMin.first())
                        put("theme_mode", userPreferences.themeMode.first().name)
                        put("theme_preset", userPreferences.themePreset.first().name)
                        put("theme_custom_primary", userPreferences.themeCustomPrimary.first())
                        put("theme_custom_secondary", userPreferences.themeCustomSecondary.first())
                        put("theme_custom_tertiary", userPreferences.themeCustomTertiary.first())
                    })
                    val senders = trustedSenderRepository.observeAll().first()
                    put("trusted_senders", buildJsonArray {
                        senders.forEach { s ->
                            add(buildJsonObject {
                                put("emailPattern", s.emailPattern)
                                put("walletLabel", s.walletLabel)
                                put("enabled", s.enabled)
                            })
                        }
                    })
                    val categories = db.categoryDao().getAll()
                    put("categories", buildJsonArray {
                        categories.forEach { c ->
                            add(buildJsonObject {
                                put("id", c.id)
                                put("name", c.name)
                                put("icon", c.icon)
                                put("color", c.color)
                                put("sortOrder", c.sortOrder)
                                put("isSystem", c.isSystem)
                                put("isQuickAction", c.isQuickAction)
                            })
                        }
                    })
                    val funds = db.fundDao().getAll()
                    put("funds", buildJsonArray {
                        for (f in funds) {
                            val ledger = db.fundLedgerDao().getForFund(f.id)
                            add(buildJsonObject {
                                put("id", f.id)
                                put("name", f.name)
                                put("archived", f.archived)
                                put("createdAt", f.createdAt)
                                put("ledger", buildJsonArray {
                                    ledger.forEach { l ->
                                        add(buildJsonObject {
                                            put("entryType", l.entryType)
                                            put("amountPaise", l.amountPaise)
                                            put("balanceAfterPaise", l.balanceAfterPaise)
                                            put("note", l.note.orEmpty())
                                            put("createdAt", l.createdAt)
                                        })
                                    }
                                })
                            })
                        }
                    })
                    val transactions = db.transactionDao().observeAll().first()
                    put("transactions", buildJsonArray {
                        transactions.forEach { t ->
                            add(buildJsonObject {
                                put("id", t.id)
                                put("type", t.type)
                                put("amountPaise", t.amountPaise)
                                put("currency", t.currency)
                                put("occurredAt", t.occurredAt)
                                put("recordedAt", t.recordedAt)
                                put("merchant", t.merchant.orEmpty())
                                put("counterparty", t.counterparty.orEmpty())
                                put("categoryId", t.categoryId ?: -1L)
                                put("fundId", t.fundId ?: -1L)
                                put("paymentMethod", t.paymentMethod.orEmpty())
                                put("source", t.source)
                                put("note", t.note.orEmpty())
                                put("isCash", t.isCash)
                                put("classificationStatus", t.classificationStatus)
                                put("classificationNotifiedAt", t.classificationNotifiedAt ?: -1L)
                                put("latitude", t.latitude ?: 0.0)
                                put("longitude", t.longitude ?: 0.0)
                                put("placeName", t.placeName.orEmpty())
                                put("locationAccuracy", (t.locationAccuracy ?: 0f).toDouble())
                                put("locationMatchedAt", t.locationMatchedAt ?: -1L)
                                put("emailMessageId", t.emailMessageId.orEmpty())
                                put("externalRefId", t.externalRefId.orEmpty())
                                put("contentHash", t.contentHash.orEmpty())
                                put("sheetsSynced", t.sheetsSynced)
                                put("deletedAt", t.deletedAt ?: -1L)
                                put("updatedAt", t.updatedAt)
                                put("version", t.version)
                            })
                        }
                    })
                }
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toString().toByteArray())
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun importData(context: Context, uri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { isStream ->
                    isStream.readBytes().toString(Charsets.UTF_8)
                } ?: return@withContext Result.failure(IllegalStateException("Failed to read file"))

                val jsonObj = Json.parseToJsonElement(content).jsonObject

                // 1. Secure Store
                val secure = jsonObj["secure_store"]?.jsonObject
                if (secure != null) {
                    secure["llm_api_key"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.llmApiKey = it }
                    secure["llm_base_url"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.llmBaseUrl = it }
                    secure["llm_model"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.llmModel = it }
                    secure["gmail_address"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.gmailAddress = it }
                    secure["gmail_app_password"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.gmailAppPassword = it }
                    secure["sheets_spreadsheet_id"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.sheetsSpreadsheetId = it }
                    secure["sheets_access_token"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.sheetsAccessToken = it }
                }

                // 2. User Prefs
                val prefs = jsonObj["user_prefs"]?.jsonObject
                if (prefs != null) {
                    prefs["location_enabled"]?.jsonPrimitive?.boolean?.let { userPreferences.setLocationEnabled(it) }
                    prefs["email_poll_enabled"]?.jsonPrimitive?.boolean?.let { userPreferences.setEmailPollEnabled(it) }
                    prefs["sheets_sync_enabled"]?.jsonPrimitive?.boolean?.let { userPreferences.setSheetsSyncEnabled(it) }
                    prefs["classification_delay_min"]?.jsonPrimitive?.long?.let { userPreferences.setClassificationDelayMin(it) }
                    prefs["theme_mode"]?.jsonPrimitive?.content?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }?.let { userPreferences.setThemeMode(it) }
                    prefs["theme_preset"]?.jsonPrimitive?.content?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() }?.let { userPreferences.setThemePreset(it) }
                    val customPrimary = prefs["theme_custom_primary"]?.jsonPrimitive?.content
                    val customSecondary = prefs["theme_custom_secondary"]?.jsonPrimitive?.content
                    val customTertiary = prefs["theme_custom_tertiary"]?.jsonPrimitive?.content
                    if (customPrimary != null || customSecondary != null || customTertiary != null) {
                        userPreferences.setThemeCustomColors(
                            customPrimary ?: userPreferences.themeCustomPrimary.first(),
                            customSecondary ?: userPreferences.themeCustomSecondary.first(),
                            customTertiary ?: userPreferences.themeCustomTertiary.first(),
                        )
                    }
                }

                db.runInTransaction {
                    runBlocking {
                        db.openHelper.writableDatabase.execSQL("DELETE FROM transactions")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM categories")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM funds")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM fund_ledger")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM trusted_senders")

                        // 4. Import Senders
                        jsonObj["trusted_senders"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            db.trustedSenderDao().upsert(
                                TrustedSenderEntity(
                                    emailPattern = obj["emailPattern"]?.jsonPrimitive?.content.orEmpty(),
                                    walletLabel = obj["walletLabel"]?.jsonPrimitive?.content ?: "Wallet",
                                    enabled = obj["enabled"]?.jsonPrimitive?.boolean ?: true
                                )
                            )
                        }

                        // 5. Import Categories
                        jsonObj["categories"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            db.categoryDao().upsert(
                                CategoryEntity(
                                    id = obj["id"]?.jsonPrimitive?.long ?: 0L,
                                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                                    icon = obj["icon"]?.jsonPrimitive?.content ?: "category",
                                    color = obj["color"]?.jsonPrimitive?.long ?: 0xFF0B6E4F,
                                    sortOrder = obj["sortOrder"]?.jsonPrimitive?.int ?: 0,
                                    isSystem = obj["isSystem"]?.jsonPrimitive?.boolean ?: false,
                                    isQuickAction = obj["isQuickAction"]?.jsonPrimitive?.boolean ?: false
                                )
                            )
                        }

                        // 6. Import Funds & Ledger
                        jsonObj["funds"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            val fundId = db.fundDao().upsert(
                                FundEntity(
                                    id = obj["id"]?.jsonPrimitive?.long ?: 0L,
                                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                                    archived = obj["archived"]?.jsonPrimitive?.boolean ?: false,
                                    createdAt = obj["createdAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
                                )
                            )
                            obj["ledger"]?.jsonArray?.forEach { led ->
                                val lObj = led.jsonObject
                                db.fundLedgerDao().insert(
                                    FundLedgerEntity(
                                        fundId = fundId,
                                        entryType = lObj["entryType"]?.jsonPrimitive?.content ?: "ADJUSTMENT",
                                        amountPaise = lObj["amountPaise"]?.jsonPrimitive?.long ?: 0L,
                                        balanceAfterPaise = lObj["balanceAfterPaise"]?.jsonPrimitive?.long ?: 0L,
                                        note = lObj["note"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                        createdAt = lObj["createdAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        }

                        // 7. Import Transactions
                        jsonObj["transactions"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            val catId = obj["categoryId"]?.jsonPrimitive?.long?.takeIf { it != -1L }
                            val fId = obj["fundId"]?.jsonPrimitive?.long?.takeIf { it != -1L }
                            db.transactionDao().insert(
                                TransactionEntity(
                                    id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                                    type = obj["type"]?.jsonPrimitive?.content ?: "EXPENSE",
                                    amountPaise = obj["amountPaise"]?.jsonPrimitive?.long ?: 0L,
                                    currency = obj["currency"]?.jsonPrimitive?.content ?: "INR",
                                    occurredAt = obj["occurredAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                                    recordedAt = obj["recordedAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                                    merchant = obj["merchant"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    counterparty = obj["counterparty"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    categoryId = catId,
                                    fundId = fId,
                                    paymentMethod = obj["paymentMethod"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    source = obj["source"]?.jsonPrimitive?.content ?: "MANUAL",
                                    note = obj["note"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    isCash = obj["isCash"]?.jsonPrimitive?.boolean ?: false,
                                    classificationStatus = obj["classificationStatus"]?.jsonPrimitive?.content ?: "PENDING",
                                    classificationNotifiedAt = obj["classificationNotifiedAt"]?.jsonPrimitive?.long?.takeIf { it != -1L },
                                    latitude = obj["latitude"]?.jsonPrimitive?.double?.takeIf { it != 0.0 },
                                    longitude = obj["longitude"]?.jsonPrimitive?.double?.takeIf { it != 0.0 },
                                    placeName = obj["placeName"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    locationAccuracy = obj["locationAccuracy"]?.jsonPrimitive?.double?.toFloat()?.takeIf { it != 0f },
                                    locationMatchedAt = obj["locationMatchedAt"]?.jsonPrimitive?.long?.takeIf { it != -1L },
                                    emailMessageId = obj["emailMessageId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    externalRefId = obj["externalRefId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    contentHash = obj["contentHash"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    sheetsSynced = obj["sheetsSynced"]?.jsonPrimitive?.boolean ?: false,
                                    deletedAt = obj["deletedAt"]?.jsonPrimitive?.long?.takeIf { it != -1L },
                                    updatedAt = obj["updatedAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                                    version = obj["version"]?.jsonPrimitive?.int ?: 1
                                )
                            )
                        }
                    }
                }
                Result.success("Imported configuration successfully!")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
