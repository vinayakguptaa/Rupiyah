package com.krtky.financetracker.email

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.krtky.financetracker.R
import com.krtky.financetracker.data.email.EmailIngestService
import com.krtky.financetracker.data.email.EmailSource
import com.krtky.financetracker.data.email.GmailApiClient
import com.krtky.financetracker.data.email.ImapEmailClient
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.TrustedSenderRepository
import com.krtky.financetracker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground mail watcher.
 * - IMAP: continuous IDLE + backup poll of the full inbox path
 * - Gmail OAuth: mailbox history watch — only process new trusted-sender mail
 * Requires AI helper ready ([SecureStore.isLlmReady]) to parse mail into spends.
 */
@AndroidEntryPoint
class EmailMonitorService : Service() {

    @Inject lateinit var imap: ImapEmailClient
    @Inject lateinit var gmailApi: GmailApiClient
    @Inject lateinit var ingest: EmailIngestService
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var secureStore: SecureStore
    @Inject lateinit var trustedSenderRepository: TrustedSenderRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var pollJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var statusText: String = "Starting…"
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "financetracker:email").apply {
            setReferenceCounted(false)
        }
        registerNetworkCallback()
        startLoop()
        startBackupPoll()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (loopJob?.isActive != true) startLoop()
        if (pollJob?.isActive != true) startBackupPoll()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart(5_000L)
        super.onTaskRemoved(rootIntent)
    }

    private suspend fun mailConfigured(): Boolean {
        return when (userPreferences.emailSource.first()) {
            EmailSource.GMAIL_OAUTH -> gmailApi.isConfigured()
            EmailSource.IMAP -> imap.isConfigured()
        }
    }

    /**
     * IMAP: continuous IDLE + immediate process.
     * Gmail: users.watch setup + history.list change detection → trusted senders only.
     */
    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                val enabled = try {
                    userPreferences.emailPollEnabled.first()
                } catch (_: Exception) {
                    false
                }
                if (!enabled || !mailConfigured()) {
                    updateStatus("Paused — enable in Settings")
                    delay(20_000)
                    continue
                }
                if (!secureStore.isLlmReady()) {
                    updateStatus("Paused — set up AI helper")
                    delay(30_000)
                    continue
                }
                if (trustedSenderRepository.getEnabled().isEmpty()) {
                    updateStatus("Add a trusted sender first")
                    delay(30_000)
                    continue
                }
                val source = try {
                    userPreferences.emailSource.first()
                } catch (_: Exception) {
                    EmailSource.IMAP
                }
                try {
                    when (source) {
                        EmailSource.GMAIL_OAUTH -> runGmailWatchCycle()
                        EmailSource.IMAP -> runImapCycle()
                    }
                } catch (e: Exception) {
                    releaseWake()
                    updateStatus("Retrying: ${e.message?.take(36) ?: "error"}")
                    delay(30_000)
                }
            }
        }
    }

    /**
     * Gmail path: register/renew watch cursor, then only react to history changes.
     * Does not re-scan the whole inbox every cycle.
     */
    private suspend fun runGmailWatchCycle() {
        updateStatus("Gmail watch…")
        acquireWake(60_000)
        try {
            val setup = gmailApi.ensureWatch()
            updateStatus(
                if (setup.watchRegistered) "Gmail watch active"
                else "Gmail history watch",
            )
            val delta = gmailApi.fetchChangesForTrusted()
            if (delta.emails.isNotEmpty()) {
                updateStatus("New mail — processing…")
                val startOfDay = startOfToday()
                val todayMail = delta.emails.filter { it.receivedAt >= startOfDay }
                val r = if (todayMail.isNotEmpty()) {
                    ingest.ingestRaw(todayMail)
                } else {
                    EmailIngestService.IngestResult(0, delta.emails.size, null)
                }
                updateStatus(
                    if (r.created > 0) "Imported ${r.created} payment(s)"
                    else "Gmail watch · no payments",
                )
            } else {
                updateStatus("Gmail watch · waiting for mail")
            }
        } finally {
            releaseWake()
        }
        // Cheap history check — not a full message list poll
        delay(GMAIL_HISTORY_INTERVAL_MS)
    }

    /** IMAP path: constant poll/IDLE loop. */
    private suspend fun runImapCycle() {
        updateStatus("Checking mail…")
        acquireWake(45_000)
        val quick = ingest.ingest(force = true)
        if (quick.created > 0) updateStatus("Imported ${quick.created} payment(s)")
        releaseWake()

        val idleMs = 12 * 60_000L
        updateStatus("Watching inbox (IMAP)…")
        val trusted = trustedSenderRepository.getEnabled()
        val newMail = withContext(Dispatchers.IO) {
            try {
                imap.idleOnce(
                    trusted = trusted,
                    onStatus = { },
                    idleTimeoutMs = idleMs,
                )
            } catch (_: Exception) {
                emptyList()
            }
        }

        if (newMail.isNotEmpty()) {
            updateStatus("New mail — processing…")
            acquireWake(45_000)
            val startOfDay = startOfToday()
            val todayMail = newMail.filter { it.receivedAt >= startOfDay }
            val r = ingest.ingestRaw(todayMail)
            if (r.created == 0) ingest.ingest(force = true)
            releaseWake()
            updateStatus(
                if (r.created > 0) "Imported ${r.created} payment(s)"
                else "Watching inbox (IMAP)…",
            )
        }
        delay(5_000)
    }

    /**
     * IMAP-only fallback if IDLE is broken on the OEM.
     * Gmail uses history watch in the main loop — no full-inbox backup poll.
     */
    private fun startBackupPoll() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(8 * 60_000L)
                val enabled = try {
                    userPreferences.emailPollEnabled.first()
                } catch (_: Exception) {
                    false
                }
                if (!enabled || !mailConfigured()) continue
                if (trustedSenderRepository.getEnabled().isEmpty()) continue
                val source = try {
                    userPreferences.emailSource.first()
                } catch (_: Exception) {
                    EmailSource.IMAP
                }
                // Full ingest poll only for IMAP; Gmail stays on history watch
                if (source != EmailSource.IMAP) continue
                try {
                    acquireWake(30_000)
                    val r = ingest.ingest(force = true)
                    if (r.created > 0) updateStatus("Imported ${r.created} payment(s)")
                } catch (_: Exception) {
                } finally {
                    releaseWake()
                }
            }
        }
    }

    private fun startOfToday(): Long =
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        if (!userPreferences.emailPollEnabled.first() || !mailConfigured()) return@launch
                        try {
                            when (userPreferences.emailSource.first()) {
                                EmailSource.GMAIL_OAUTH -> {
                                    gmailApi.ensureWatch()
                                    val delta = gmailApi.fetchChangesForTrusted()
                                    if (delta.emails.isNotEmpty()) {
                                        val start = startOfToday()
                                        ingest.ingestRaw(delta.emails.filter { it.receivedAt >= start })
                                    }
                                }
                                EmailSource.IMAP -> ingest.ingest(force = true)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            networkCallback = cb
            cm.registerNetworkCallback(req, cb)
        } catch (_: Exception) {
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        try {
            val am = getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getService(
                this,
                99,
                Intent(this, EmailMonitorService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val at = SystemClock.elapsedRealtime() + delayMs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            }
        } catch (_: Exception) {
        }
    }

    private fun acquireWake(ms: Long) {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock?.acquire(ms)
        } catch (_: Exception) {
        }
    }

    private fun releaseWake() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
    }

    private fun updateStatus(text: String) {
        statusText = text
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Email monitor",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps watching Gmail for payment emails"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Email monitor")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        loopJob?.cancel()
        pollJob?.cancel()
        releaseWake()
        try {
            networkCallback?.let {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {
        }
        // Try come back if still enabled
        scope.launch {
            try {
                if (userPreferences.emailPollEnabled.first()) {
                    scheduleRestart(8_000L)
                }
            } catch (_: Exception) {
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "email_monitor"
        const val NOTIF_ID = 41
        /** How often to query Gmail history.list for changes (not a full inbox poll). */
        private const val GMAIL_HISTORY_INTERVAL_MS = 45_000L

        fun start(context: Context) {
            val i = Intent(context, EmailMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EmailMonitorService::class.java))
        }
    }
}
