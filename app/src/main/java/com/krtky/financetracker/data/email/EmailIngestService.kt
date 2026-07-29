package com.krtky.financetracker.data.email

import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.EmailIngestLogEntity
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.data.repository.TrustedSenderRepository
import com.krtky.financetracker.domain.model.EmailProcessStatus
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.location.LocationRepository
import com.krtky.financetracker.notification.ClassificationNotifier
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailIngestService @Inject constructor(
    private val imap: ImapEmailClient,
    private val gmailApi: GmailApiClient,
    private val parser: TransactionParser,
    private val transactionRepository: TransactionRepository,
    private val trustedSenderRepository: TrustedSenderRepository,
    private val userPreferences: UserPreferences,
    private val secureStore: SecureStore,
    private val locationRepository: LocationRepository,
    private val db: AppDatabase,
    private val notifier: ClassificationNotifier,
) {
    suspend fun isEmailConfigured(): Boolean =
        when (userPreferences.emailSource.first()) {
            EmailSource.GMAIL_OAUTH -> gmailApi.isConfigured()
            EmailSource.IMAP -> imap.isConfigured()
        }

    suspend fun ingest(force: Boolean = false, sinceHours: Long = 24): IngestResult {
        if (!secureStore.isLlmReady()) {
            return IngestResult(
                0,
                0,
                "AI helper not set up — Settings → AI helper (required for bank emails)",
            )
        }
        val source = userPreferences.emailSource.first()
        val configured = when (source) {
            EmailSource.GMAIL_OAUTH -> gmailApi.isConfigured()
            EmailSource.IMAP -> imap.isConfigured()
        }
        if (!configured) {
            return IngestResult(
                0,
                0,
                when (source) {
                    EmailSource.GMAIL_OAUTH ->
                        "Gmail OAuth not connected — Settings → Bank emails → Connect with Google"
                    EmailSource.IMAP ->
                        "Gmail IMAP not configured — save email + App Password"
                },
            )
        }
        val trusted = trustedSenderRepository.getEnabled()
        if (trusted.isEmpty()) return IngestResult(0, 0, "No trusted senders configured")

        // Only today's mail (local midnight → now), regardless of sinceHours
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val emails = try {
            when (source) {
                EmailSource.GMAIL_OAUTH -> gmailApi.fetchRecent(startOfDay)
                EmailSource.IMAP -> imap.fetchRecent(startOfDay)
            }
        } catch (e: Exception) {
            return IngestResult(0, 0, e.message ?: "Email fetch failed")
        }
        return processEmails(emails, trusted)
    }

    /** Process emails already fetched (e.g. IMAP IDLE push). */
    suspend fun ingestRaw(emails: List<RawEmail>): IngestResult {
        if (emails.isEmpty()) return IngestResult(0, 0, null)
        if (!secureStore.isLlmReady()) {
            return IngestResult(0, 0, "AI helper not set up — required to parse bank emails")
        }
        val trusted = trustedSenderRepository.getEnabled()
        if (trusted.isEmpty()) return IngestResult(0, 0, "No trusted senders configured")
        return processEmails(emails, trusted)
    }

    private suspend fun processEmails(
        emails: List<RawEmail>,
        trusted: List<com.krtky.financetracker.domain.model.TrustedSender>,
    ): IngestResult {
        var created = 0
        var skipped = 0
        for (email in emails) {
            val existing = db.emailIngestDao().findByMessageId(email.messageId)
            if (existing != null && existing.processStatus != EmailProcessStatus.FAILED.name) {
                skipped++
                continue
            }
            val logId = db.emailIngestDao().insert(
                EmailIngestLogEntity(
                    messageId = email.messageId,
                    sender = email.sender,
                    subject = email.subject,
                    receivedAt = email.receivedAt,
                    processStatus = EmailProcessStatus.NEW.name,
                )
            )
            try {
                val wallet = gmailApi.resolveWallet(email.sender, trusted)
                val txn = parser.parse(email, wallet)
                if (txn == null) {
                    updateLog(logId, existing?.id, email, EmailProcessStatus.IGNORED, "Not a payment email / parse failed", null)
                    skipped++
                    continue
                }
                val withLocation = attachLocation(txn)
                val id = transactionRepository.insertFromEmail(withLocation)
                updateLog(
                    logId, existing?.id, email,
                    if (id == null) EmailProcessStatus.DUPLICATE else EmailProcessStatus.PARSED,
                    null, id,
                )
                if (id != null) {
                    created++
                    notifier.notifyPayment(
                        transactionId = id,
                        emailSubject = email.subject,
                        emailSender = email.sender,
                    )
                } else skipped++
            } catch (e: Exception) {
                updateLog(logId, existing?.id, email, EmailProcessStatus.FAILED, e.message?.take(300), null)
            }
        }
        userPreferences.setLastEmailPollAt(System.currentTimeMillis())
        return IngestResult(created, skipped, null)
    }

    suspend fun processPastedEmail(sender: String, subject: String, body: String): String? {
        if (!secureStore.isLlmReady()) return null
        val trusted = trustedSenderRepository.getEnabled()
        val match = trustedSenderRepository.matches(sender, trusted)
            ?: trusted.firstOrNull()
            ?: return null
        val email = RawEmail(
            messageId = "paste-${System.currentTimeMillis()}-${body.hashCode()}",
            sender = sender,
            subject = subject,
            body = body,
            receivedAt = System.currentTimeMillis(),
        )
        db.emailIngestDao().insert(
            EmailIngestLogEntity(
                messageId = email.messageId,
                sender = email.sender,
                subject = email.subject,
                receivedAt = email.receivedAt,
                processStatus = EmailProcessStatus.NEW.name,
            )
        )
        val txn = parser.parse(email, match.walletLabel) ?: return null
        val id = transactionRepository.insertFromEmail(attachLocation(txn))
        if (id != null) notifier.notifyPayment(id, subject, sender)
        return id
    }

    private suspend fun updateLog(
        logId: Long,
        existingId: Long?,
        email: RawEmail,
        status: EmailProcessStatus,
        error: String?,
        txnId: String?,
    ) {
        db.emailIngestDao().update(
            EmailIngestLogEntity(
                id = if (logId > 0) logId else existingId ?: 0,
                messageId = email.messageId,
                sender = email.sender,
                subject = email.subject,
                receivedAt = email.receivedAt,
                processStatus = status.name,
                parseError = error,
                transactionId = txnId,
            )
        )
    }

    /** Match stored samples, else live GPS capture when location is enabled. */
    suspend fun attachLocation(txn: Transaction, preferCurrent: Boolean = false): Transaction {
        val t = txn.occurredAt
        val locationOn = try {
            userPreferences.locationEnabled.first()
        } catch (_: Exception) {
            false
        }
        if (preferCurrent && locationOn) {
            val live = runCatching { locationRepository.captureCurrent() }.getOrNull()
            if (live != null) {
                return txn.copy(
                    latitude = live.latitude,
                    longitude = live.longitude,
                    placeName = live.placeName,
                    locationAccuracy = live.accuracy,
                    locationMatchedAt = System.currentTimeMillis(),
                )
            }
        }
        val sample = db.locationSampleDao().findClosest(
            fromTs = t - 2 * 60 * 60_000L,
            toTs = t + 30 * 60_000L,
            targetTs = t,
        )
        if (sample != null) {
            return txn.copy(
                latitude = sample.latitude,
                longitude = sample.longitude,
                placeName = sample.placeName,
                locationAccuracy = sample.accuracy,
                locationMatchedAt = sample.capturedAt,
            )
        }
        if (!locationOn) return txn.copyLocationFrom(db.locationSampleDao().latest())
        val live = try {
            locationRepository.captureCurrent()
        } catch (_: Exception) {
            null
        } ?: return txn.copyLocationFrom(db.locationSampleDao().latest())
        return txn.copy(
            latitude = live.latitude,
            longitude = live.longitude,
            placeName = live.placeName,
            locationAccuracy = live.accuracy,
            locationMatchedAt = System.currentTimeMillis(),
        )
    }

    private fun Transaction.copyLocationFrom(sample: com.krtky.financetracker.data.local.db.LocationSampleEntity?): Transaction {
        if (sample == null) return this
        return copy(
            latitude = sample.latitude,
            longitude = sample.longitude,
            placeName = sample.placeName,
            locationAccuracy = sample.accuracy,
            locationMatchedAt = sample.capturedAt,
        )
    }

    data class IngestResult(val created: Int, val skipped: Int, val error: String?)
}
