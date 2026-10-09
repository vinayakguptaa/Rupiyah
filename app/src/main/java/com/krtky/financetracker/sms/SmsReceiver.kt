package com.krtky.financetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Hands each incoming SMS to [SmsImportWorker]. Parsing (and the AI call) never runs
 * inside the broadcast, whose time limit would kill slow network calls.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return
        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        if (body.isBlank()) return
        Log.d(TAG, "SMS from '$sender' queued for import")
        SmsImportWorker.enqueue(context, sender, body, System.currentTimeMillis())
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
