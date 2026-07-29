package com.krtky.financetracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.krtky.financetracker.ui.MainActivity

class OverlayClassificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_OPEN) return
        val txnId = intent.getStringExtra(EXTRA_TXN_ID) ?: return
        if (Settings.canDrawOverlays(context)) {
            val serviceIntent = Intent(context, OverlayClassificationService::class.java).apply {
                putExtra(EXTRA_TXN_ID, txnId)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            // The normal app sheet remains available if overlay access is denied.
            val fallback = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("transactionId", txnId)
                putExtra("openClassify", true)
            }
            context.startActivity(fallback)
        }
    }

    companion object {
        const val ACTION_OPEN = "com.krtky.financetracker.OPEN_CLASSIFIER"
        const val EXTRA_TXN_ID = "txn_id"
    }
}
