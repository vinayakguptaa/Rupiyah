package com.krtky.financetracker.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads recent messages from the phone's SMS inbox for "Sync SMS" (needs READ_SMS). */
@Singleton
class SmsInboxReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class InboxSms(val sender: String, val body: String, val receivedAt: Long)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    suspend fun readSince(fromMillis: Long, limit: Int = 500): List<InboxSms> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val out = mutableListOf<InboxSms>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(fromMillis.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext() && out.size < limit) {
                val body = c.getString(iBody).orEmpty()
                if (body.isBlank()) continue
                out += InboxSms(c.getString(iAddr).orEmpty(), body, c.getLong(iDate))
            }
        }
        out
    }
}
