package com.krtky.financetracker.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.krtky.financetracker.R
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassificationNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val db: AppDatabase,
) {
    fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        // Recreate channel with heads-up defaults
        if (Build.VERSION.SDK_INT >= 26) {
            nm.deleteNotificationChannel(CHANNEL_ID)
        }
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Payments",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Instant alerts when a payment email is detected"
                enableVibration(true)
                enableLights(true)
                lightColor = Color.GREEN
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    suspend fun notifyPayment(
        transactionId: String,
        emailSubject: String? = null,
        emailSender: String? = null,
    ) {
        ensureChannel()
        val txn = transactionRepository.getById(transactionId) ?: return
        val amount = Money(txn.amountPaise).formatInr()
        val party = (txn.counterparty ?: txn.merchant)?.trim().orEmpty()
        val isIn = txn.type == TransactionType.CREDIT
        val title = if (isIn) "Received $amount" else "Paid $amount"
        val line = when {
            party.isNotBlank() && isIn -> "From $party"
            party.isNotBlank() -> "To $party"
            else -> txn.paymentMethod ?: "Payment"
        }
        val place = txn.placeName?.takeIf { it.isNotBlank() }
        val summary = buildString {
            append(line)
            if (!txn.paymentMethod.isNullOrBlank()) append(" · ${txn.paymentMethod}")
            if (place != null) append(" · $place")
        }
        val big = buildString {
            append(summary)
            if (!emailSubject.isNullOrBlank()) append("\n").append(emailSubject.trim())
            if (!txn.note.isNullOrBlank()) append("\nNote: ").append(txn.note)
            append("\nTap to classify · Reply to add a note")
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.krtky.financetracker.OPEN_CLASSIFIER"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("transactionId", transactionId)
            putExtra("openClassify", true)
        }
        val openPi = PendingIntent.getActivity(
            context,
            transactionId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big).setSummaryText(if (isIn) "Income" else "Expense"))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setColor(if (isIn) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
            .setWhen(txn.occurredAt)
            .setShowWhen(true)
            .setNumber(1)

        val remoteInput = RemoteInput.Builder(ClassificationActionReceiver.KEY_REPLY)
            .setLabel("Add note…")
            .build()
        val replyIntent = Intent(context, ClassificationActionReceiver::class.java).apply {
            action = ClassificationActionReceiver.ACTION_REPLY
            putExtra(ClassificationActionReceiver.EXTRA_TXN_ID, transactionId)
        }
        val replyPi = PendingIntent.getBroadcast(
            context,
            transactionId.hashCode() + 900,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        builder.addAction(
            NotificationCompat.Action.Builder(0, "Note", replyPi)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()
        )

        val fund = db.fundDao().getAll().firstOrNull { !it.archived }
        if (fund != null) {
            val intent = Intent(context, ClassificationActionReceiver::class.java).apply {
                action = ClassificationActionReceiver.ACTION_FUND
                putExtra(ClassificationActionReceiver.EXTRA_TXN_ID, transactionId)
                putExtra(ClassificationActionReceiver.EXTRA_FUND_ID, fund.id)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                transactionId.hashCode() + 200,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, fund.name, pi)
        }

        val quick = categoryRepository.getQuickActions().firstOrNull()
        if (quick != null) {
            val intent = Intent(context, ClassificationActionReceiver::class.java).apply {
                action = ClassificationActionReceiver.ACTION_CATEGORY
                putExtra(ClassificationActionReceiver.EXTRA_TXN_ID, transactionId)
                putExtra(ClassificationActionReceiver.EXTRA_CATEGORY_ID, quick.id)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                transactionId.hashCode() + 50,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, quick.name, pi)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        NotificationManagerCompat.from(context).notify(NOTIF_BASE + (transactionId.hashCode() and 0xFFFF), builder.build())

        val entity = db.transactionDao().getById(transactionId) ?: return
        db.transactionDao().update(
            entity.copy(classificationNotifiedAt = System.currentTimeMillis())
        )
    }

    suspend fun notifyDue(transactionId: String) = notifyPayment(transactionId)

    companion object {
        const val CHANNEL_ID = "payments_v2"
        private const val NOTIF_BASE = 10_000
    }
}
