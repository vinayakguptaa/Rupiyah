package com.krtky.financetracker.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.krtky.financetracker.workers.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint

/**
 * Reschedule background work after boot / update.
 * (Moved out of the retired `email` package — capture is SMS + CSV + manual only.)
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        WorkScheduler.scheduleAll(context.applicationContext)
    }
}