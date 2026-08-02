package com.krtky.financetracker.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.sheets.SheetsSyncService
import com.krtky.financetracker.notification.ClassificationNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class ClassificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val notifier: ClassificationNotifier,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val due = db.pendingClassificationDao().due(System.currentTimeMillis())
        due.forEach { item ->
            notifier.notifyDue(item.transactionId)
            db.pendingClassificationDao().update(
                item.copy(notifiedAt = System.currentTimeMillis(), status = "NOTIFIED", attempts = item.attempts + 1)
            )
        }
        return Result.success()
    }
}

@HiltWorker
class SheetsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sheetsSyncService: SheetsSyncService,
    private val userPreferences: UserPreferences,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!userPreferences.sheetsSyncEnabled.first()) return Result.success()
        return sheetsSyncService.sync().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            com.krtky.financetracker.widget.WidgetUpdater.refreshAll(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object WorkScheduler {
    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        // Email poll intentionally not scheduled — capture is SMS + CSV + manual.
        wm.cancelUniqueWork("email_poll")
        wm.enqueueUniquePeriodicWork(
            "classification_notify",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ClassificationWorker>(15, TimeUnit.MINUTES).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "sheets_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SheetsSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(net)
                .build(),
        )
        // Keep home-screen widgets fresh even when the UI process is not open.
        // 15 min is WorkManager's practical floor for periodic work.
        wm.enqueueUniquePeriodicWork(
            "widget_refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}
