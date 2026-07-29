package com.krtky.financetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.krtky.financetracker.data.email.EmailIngestService
import com.krtky.financetracker.data.email.TransactionParser
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.notification.ClassificationNotifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var preferences: UserPreferences
    @Inject lateinit var parser: TransactionParser
    @Inject lateinit var emailIngestService: EmailIngestService
    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var notifier: ClassificationNotifier

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                if (!preferences.smsEnabled.first()) return@launch
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) return@launch
                val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
                val body = messages.joinToString("") { it.messageBody.orEmpty() }
                if (body.isBlank()) return@launch
                // Prefer configured filters, but never drop money-looking SMS when monitoring is on.
                if (!shouldInspect(sender, body)) return@launch
                val receivedAt = System.currentTimeMillis()
                val txn = parser.parseSms(sender, body, receivedAt) ?: return@launch
                val id = transactionRepository.insertFromEmail(
                    emailIngestService.attachLocation(txn, preferCurrent = true),
                )
                if (id != null) notifier.notifyPayment(id, "SMS payment", sender)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    private suspend fun shouldInspect(sender: String, body: String): Boolean {
        val normalizedSender = sender.trim().lowercase()
        val text = body.lowercase()
        val senders = preferences.smsSenders.first()
            .split(',', '\n', ';')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        val keywords = preferences.smsKeywords.first()
            .split(',', '\n', ';')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        val senderAllowed = senders.isEmpty() ||
            senders.any { normalizedSender == it || normalizedSender.contains(it) || it.contains(normalizedSender) }
        val keywordMatched = keywords.isEmpty() || keywords.any { text.contains(it) }
        val looksLikeMoney = MONEY_HINT.containsMatchIn(body)

        // Catch bank SMS even when sender IDs are messy (AX-HDFCBK vs HDFCBK).
        if (looksLikeMoney && (senderAllowed || keywordMatched || senders.isEmpty())) return true
        return senderAllowed && keywordMatched
    }

    companion object {
        private val MONEY_HINT = Regex(
            """(?:₹|rs\.?|inr|upi|debited|credited|spent|paid|withdrawn|a/c|acct|account|txn|transaction)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
