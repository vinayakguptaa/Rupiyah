package com.krtky.financetracker.ui.components

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import java.util.Calendar
import java.util.Locale

/**
 * Shared mutable state for transaction entry/edit forms.
 *
 * Both [AddCashScreen] and [TransactionDetailScreen] use this to back their
 * form fields.  Screen-specific fields (self-transfer accounts, draft splits,
 * dirty-tracking, etc.) stay in the screen — this class only covers the
 * fields that behave identically.
 *
 * Created with `remember { TransactionFormState() }` so that Compose state
 * is properly scoped to the composition.
 */
class TransactionFormState(
    initialAmount: String = "",
    initialType: TransactionType = TransactionType.DEBIT,
) {
    // ── Core form fields ─────────────────────────────────────────────

    var amount by mutableStateOf(initialAmount)
    var type by mutableStateOf(initialType)
    var counterparty by mutableStateOf("")
    var note by mutableStateOf("")
    var categoryId by mutableStateOf<Long?>(null)
    var fundId by mutableStateOf<Long?>(null)

    /** For Detail edit this mirrors `addToFund`; for Add it drives the credit-to-tab toggle. */
    var addToFund by mutableStateOf(true)

    var selectedAccountId by mutableStateOf<Long?>(null)
    var useLocation by mutableStateOf(false)

    /** New receipt local URI picked on the form, or null. */
    var receiptUri by mutableStateOf<Uri?>(null)

    /** True when the user clears an existing receipt (Detail edit only). */
    var receiptCleared by mutableStateOf(false)

    // ── Expand / visibility flags ────────────────────────────────────

    var paymentExpanded by mutableStateOf(true)
    var categoryExpanded by mutableStateOf(true)
    var moreExpanded by mutableStateOf(false)

    // ── Date / time (wall-clock fields) ─────────────────────────────

    private val _now = Calendar.getInstance()
    var selectedYear by mutableStateOf(_now.get(Calendar.YEAR))
    var selectedMonth by mutableStateOf(_now.get(Calendar.MONTH))
    var selectedDay by mutableStateOf(_now.get(Calendar.DAY_OF_MONTH))
    var selectedHour by mutableStateOf(_now.get(Calendar.HOUR_OF_DAY))
    var selectedMinute by mutableStateOf(_now.get(Calendar.MINUTE))
    var selectedSecond by mutableStateOf(_now.get(Calendar.SECOND))
    var selectedMillis by mutableStateOf(_now.get(Calendar.MILLISECOND))

    // ── Picker / sheet visibility ────────────────────────────────────

    var showDatePicker by mutableStateOf(false)
    var showTimePicker by mutableStateOf(false)
    var showAmountPad by mutableStateOf(false)

    // ── Derived: display time as epoch millis ───────────────────────

    /** Same computation as the duplicate `displayWhen` in both screens. */
    fun computeDisplayWhen(): Long = Calendar.getInstance().let { c ->
        c.set(Calendar.YEAR, selectedYear)
        c.set(Calendar.MONTH, selectedMonth)
        c.set(Calendar.DAY_OF_MONTH, selectedDay)
        c.set(Calendar.HOUR_OF_DAY, selectedHour)
        c.set(Calendar.MINUTE, selectedMinute)
        c.set(Calendar.SECOND, selectedSecond)
        c.set(Calendar.MILLISECOND, selectedMillis)
        c.timeInMillis
    }

    // ── Hydration / reset ────────────────────────────────────────────

    /**
     * Populates core fields from an existing [Transaction].
     *
     * Account-ID resolution is delegated to the caller via [resolveAccountId]
     * because it depends on the active account list (which lives in the
     * ViewModel).  Date / time, receipts, and location are reset.
     */
    fun hydrateFrom(
        t: Transaction,
        resolveAccountId: (accountName: String?, isCash: Boolean) -> Long?,
    ) {
        note = t.note.orEmpty()
        counterparty = t.counterparty.orEmpty()
        categoryId = t.categoryId
        fundId = t.fundId
        addToFund = t.fundId != null || t.type == TransactionType.CREDIT
        amount = "%.2f".format(Locale.US, t.amountPaise / 100.0)
        type = t.type
        selectedAccountId = t.accountId ?: resolveAccountId(t.accountName, t.isCash)
        val cal = Calendar.getInstance().apply { timeInMillis = t.occurredAt }
        selectedYear = cal.get(Calendar.YEAR)
        selectedMonth = cal.get(Calendar.MONTH)
        selectedDay = cal.get(Calendar.DAY_OF_MONTH)
        selectedHour = cal.get(Calendar.HOUR_OF_DAY)
        selectedMinute = cal.get(Calendar.MINUTE)
        selectedSecond = cal.get(Calendar.SECOND)
        selectedMillis = cal.get(Calendar.MILLISECOND)
        useLocation = false
        receiptUri = null
        receiptCleared = false
    }

    /** Resets the form to a fresh / cleared state (used after a successful save). */
    fun resetToDefaults() {
        amount = ""
        type = TransactionType.DEBIT
        counterparty = ""
        note = ""
        categoryId = null
        fundId = null
        addToFund = true
        selectedAccountId = null
        useLocation = false
        receiptUri = null
        receiptCleared = false
        paymentExpanded = true
        categoryExpanded = true
        moreExpanded = false
        val c = Calendar.getInstance()
        selectedYear = c.get(Calendar.YEAR)
        selectedMonth = c.get(Calendar.MONTH)
        selectedDay = c.get(Calendar.DAY_OF_MONTH)
        selectedHour = c.get(Calendar.HOUR_OF_DAY)
        selectedMinute = c.get(Calendar.MINUTE)
        selectedSecond = c.get(Calendar.SECOND)
        selectedMillis = c.get(Calendar.MILLISECOND)
    }
}
