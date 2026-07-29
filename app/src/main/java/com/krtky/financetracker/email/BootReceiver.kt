package com.krtky.financetracker.email

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.krtky.financetracker.data.prefs.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var userPreferences: UserPreferences

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_USER_PRESENT
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (userPreferences.emailPollEnabled.first()) {
                    EmailMonitorService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
