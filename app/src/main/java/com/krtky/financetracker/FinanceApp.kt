package com.krtky.financetracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.notification.ClassificationNotifier
import com.krtky.financetracker.workers.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FinanceApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var classificationNotifier: ClassificationNotifier
    @Inject lateinit var userPreferences: UserPreferences

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        classificationNotifier.ensureChannel()
        WorkScheduler.scheduleAll(this)
        CoroutineScope(Dispatchers.IO).launch {
            categoryRepository.seedDefaultsIfEmpty()
            val banks = userPreferences.parseBankList(userPreferences.bankAccounts.first())
            // Cash + Settings bank list. Empty prefs do not archive migration-seeded accounts.
            accountRepository.syncFromBankList(banks)
            // Mirror active names into prefs (e.g. after Room migration seeded from paymentMethod).
            val active = accountRepository.activeBankNames()
            if (active.joinToString(",") != banks.joinToString(",")) {
                userPreferences.setBankAccounts(active.joinToString(","))
            }
            // Email ingest removed from product path — SMS + CSV + manual only.
            // Prime widgets on cold start so they are not stuck on empty chrome.
            runCatching {
                com.krtky.financetracker.widget.WidgetUpdater.refreshAll(this@FinanceApp)
            }
        }
    }
}
