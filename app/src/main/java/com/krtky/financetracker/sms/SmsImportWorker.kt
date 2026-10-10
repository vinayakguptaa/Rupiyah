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
import com.krtky.financetracker.data.sms.SmsInboxReader
import com.krtky.financetracker.data.sms.SmsPipeline
import com.krtky.financetracker.notification.ClassificationNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * One live SMS: store it, then run the pipeline (filter → local parse → local classify → AI).
 * The transaction is saved as soon as the regex can read it; a transient AI failure is
 * retried once to fill in the rest.
 */
@HiltWorker
class SmsImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferences: UserPreferences,
    private val pipeline: SmsPipeline,
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

        val id = pipeline.ingest(sender, body, receivedAt, SmsPipeline.Origin.LIVE)
        val before = pipeline.get(id)
        val after = pipeline.process(id) ?: return Result.success()
        Log.i(TAG, "SMS ${after.id}: status=${after.status} ai=${after.aiStatus}" +
            (after.ignoreReason?.let { " ($it)" } ?: "") + (after.lastError?.let { " error=$it" } ?: ""))

        val newlyImported = before?.status != SmsPipeline.STATUS_IMPORTED && after.status == SmsPipeline.STATUS_IMPORTED
        if (newlyImported) after.transactionId?.let { notifier.notifyPayment(it, "SMS payment") }

        val transient = after.aiStatus == SmsPipeline.AI_FAILED &&
            after.lastError?.startsWith(SmsPipeline.TRANSIENT_PREFIX) == true
        return if (transient && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    /** Only used on API < 31, where expedited work runs as a short foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(applicationContext, "Reading bank SMS…")

    companion object {
        private const val TAG = "SmsImportWorker"
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val KEY_RECEIVED_AT = "received_at"
        private const val MAX_RETRIES = 1

        // WorkManager Data is capped at 10 KB; bank SMS bodies are far smaller.
        private const val MAX_BODY_CHARS = 3_000

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

/**
 * "Sync SMS": read the last [KEY_HOURS] hours of the inbox, store every message, then run the
 * pipeline on everything still open (AI calls capped per run). Also retries queued AI work.
 */
@HiltWorker
class SmsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reader: SmsInboxReader,
    private val pipeline: SmsPipeline,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val hours = inputData.getInt(KEY_HOURS, 0)
        var read = 0
        if (hours > 0) {
            val since = System.currentTimeMillis() - hours * 60L * 60_000L
            val messages = reader.readSince(since)
            messages.forEach { pipeline.ingest(it.sender, it.body, it.receivedAt, SmsPipeline.Origin.SYNC) }
            read = messages.size
            pipeline.recheckIgnored(since)
        }
        if (inputData.getBoolean(KEY_REQUEUE_AI, false)) pipeline.requeueAi()
        val processed = pipeline.processPending()
        Log.i(TAG, "SMS sync: read $read message(s) from the last $hours h, processed $processed")
        return Result.success(workDataOf(KEY_READ to read, KEY_PROCESSED to processed))
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(applicationContext, "Syncing bank SMS…")

    companion object {
        private const val TAG = "SmsSyncWorker"
        const val UNIQUE_NAME = "sms-sync"
        private const val KEY_HOURS = "hours"
        private const val KEY_REQUEUE_AI = "requeue_ai"
        const val KEY_READ = "read"
        const val KEY_PROCESSED = "processed"

        /** [hours] = 0 only processes what is already stored (e.g. "Retry with AI"). */
        fun enqueue(context: Context, hours: Int, requeueAi: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
                .setInputData(workDataOf(KEY_HOURS to hours, KEY_REQUEUE_AI to requeueAi))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** One inbox action that may call the AI ("Import anyway", "Retry", "Put back in queue"). */
@HiltWorker
class SmsActionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: SmsPipeline,
) : CoroutineWorker(context, params) {

    enum class Action { FORCE_IMPORT, RETRY_AI, RESET }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val action = inputData.getString(KEY_ACTION)?.let { runCatching { Action.valueOf(it) }.getOrNull() }
            ?: return Result.success()
        val after = when (action) {
            Action.FORCE_IMPORT -> pipeline.forceImport(id)
            Action.RETRY_AI -> pipeline.retryAi(id)
            Action.RESET -> pipeline.reset(id)
        }
        Log.i(TAG, "$action $id → status=${after?.status} ai=${after?.aiStatus}")
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(applicationContext, "Reading bank SMS…")

    companion object {
        private const val TAG = "SmsActionWorker"
        private const val KEY_ID = "id"
        private const val KEY_ACTION = "action"

        fun enqueue(context: Context, id: String, action: Action) {
            val request = OneTimeWorkRequestBuilder<SmsActionWorker>()
                .setInputData(workDataOf(KEY_ID to id, KEY_ACTION to action.name))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sms-action-$id", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

private const val FG_CHANNEL_ID = "background_work"
private const val FG_NOTIFICATION_ID = 7301

private fun foregroundInfo(context: Context, title: String): ForegroundInfo {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
        NotificationChannel(FG_CHANNEL_ID, "Background processing", NotificationManager.IMPORTANCE_MIN),
    )
    val notification = NotificationCompat.Builder(context, FG_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .build()
    return ForegroundInfo(FG_NOTIFICATION_ID, notification)
}
