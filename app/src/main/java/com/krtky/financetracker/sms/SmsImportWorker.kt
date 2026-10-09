package com.krtky.financetracker.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.krtky.financetracker.R
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.data.sms.TransactionParser
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.location.LocationRepository
import com.krtky.financetracker.notification.ClassificationNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Parses one bank SMS off the broadcast thread: regex first, enriched by AI when configured.
 * A transient AI failure (rate limit, timeout, offline) is retried once; after that the
 * regex-only result is saved so the transaction is never lost.
 */
@HiltWorker
class SmsImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferences: UserPreferences,
    private val parser: TransactionParser,
    private val transactionRepository: TransactionRepository,
    private val locationRepository: LocationRepository,
    private val notifier: ClassificationNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val receivedAt = inputData.getLong(KEY_RECEIVED_AT, System.currentTimeMillis())
        if (body.isBlank()) return Result.success()

        if (!preferences.smsEnabled.first()) {
            Log.w(TAG, "SMS import skipped: SMS reading is off in settings")
            return Result.success()
        }
        if (!shouldInspect(sender, body)) {
            Log.d(TAG, "SMS from '$sender' filtered out (sender/keyword rules)")
            return Result.success()
        }

        val parse = parser.parseSms(sender, body, receivedAt)
        val llmError = parse.llmError
        if (llmError != null) {
            Log.w(TAG, "AI parse failed (attempt ${runAttemptCount + 1}): ${llmError.describe()}")
            if (llmError.isTransient && runAttemptCount < MAX_RETRIES) return Result.retry()
        }
        val txn = parse.transaction
        if (txn == null) {
            Log.d(TAG, "No completed transaction in SMS from '$sender'")
            return Result.success()
        }
        val id = transactionRepository.insertFromSms(attachLocation(txn))
        if (id != null) {
            Log.i(TAG, "SMS transaction saved (${if (llmError == null) "AI+regex" else "regex only"})")
            notifier.notifyPayment(id, "SMS payment")
        } else {
            Log.i(TAG, "SMS transaction matched an existing row; not inserted")
        }
        return Result.success()
    }

    private suspend fun attachLocation(txn: Transaction): Transaction {
        val locationOn = runCatching { preferences.locationEnabled.first() }.getOrDefault(false)
        if (!locationOn) return txn
        val live = runCatching { locationRepository.captureCurrent() }.getOrNull() ?: return txn
        return txn.copy(
            latitude = live.latitude,
            longitude = live.longitude,
            placeName = live.placeName,
            locationAccuracy = live.accuracy,
            locationMatchedAt = System.currentTimeMillis(),
        )
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

    /** Only used on API < 31, where expedited work runs as a short foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(FG_CHANNEL_ID, "Background processing", NotificationManager.IMPORTANCE_MIN),
        )
        val notification = NotificationCompat.Builder(applicationContext, FG_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Reading bank SMS…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
        return ForegroundInfo(FG_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "SmsImportWorker"
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val KEY_RECEIVED_AT = "received_at"
        private const val MAX_RETRIES = 1
        private const val FG_CHANNEL_ID = "background_work"
        private const val FG_NOTIFICATION_ID = 7301

        // WorkManager Data is capped at 10 KB; bank SMS bodies are far smaller.
        private const val MAX_BODY_CHARS = 3_000

        private val MONEY_HINT = Regex(
            """(?:₹|rs\.?|inr|upi|debited|credited|spent|paid|withdrawn|a/c|acct|account|txn|transaction)""",
            RegexOption.IGNORE_CASE,
        )

        fun enqueue(context: Context, sender: String, body: String, receivedAt: Long) {
            val request = OneTimeWorkRequestBuilder<SmsImportWorker>()
                .setInputData(
                    workDataOf(
                        KEY_SENDER to sender,
                        KEY_BODY to body.take(MAX_BODY_CHARS),
                        KEY_RECEIVED_AT to receivedAt,
                    ),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            // One job per message; a duplicate broadcast for the same SMS is ignored.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sms-import-$receivedAt-${body.hashCode()}",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
