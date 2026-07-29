package com.krtky.financetracker.data.email

import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.repository.TrustedSenderRepository
import com.krtky.financetracker.domain.model.TrustedSender
import com.sun.mail.imap.IMAPFolder
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent
import javax.mail.internet.InternetAddress
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm

@Singleton
class ImapEmailClient @Inject constructor(
    private val secureStore: SecureStore,
    private val trustedSenderRepository: TrustedSenderRepository,
) {
    fun isConfigured(): Boolean =
        !secureStore.gmailAddress.isNullOrBlank() && !secureStore.gmailAppPassword.isNullOrBlank()

    fun credentials(): Pair<String, String>? {
        val user = secureStore.gmailAddress?.trim()?.lowercase() ?: return null
        // Gmail app passwords are often copied with spaces — strip them
        val pass = secureStore.gmailAppPassword?.replace(" ", "")?.trim().orEmpty()
        if (user.isBlank() || pass.isBlank()) return null
        return user to pass
    }

    suspend fun testConnection(): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val creds = credentials() ?: return@withContext Result.failure(IllegalStateException("Email or app password missing"))
        try {
            openStore(creds.first, creds.second).use { store ->
                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_ONLY)
                val count = inbox.messageCount
                inbox.close(false)
                Result.success("Connected as ${creds.first}. Inbox has $count messages.")
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(friendlyError(e), e))
        }
    }

    suspend fun fetchRecent(sinceMillis: Long, maxMessages: Int = 50): List<RawEmail> {
        val creds = credentials() ?: return emptyList()
        val trusted = trustedSenderRepository.getEnabled()
        if (trusted.isEmpty()) return emptyList()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            openStore(creds.first, creds.second).use { store ->
                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_ONLY)
                try {
                    // Server-side date filter only — FromStringTerm is unreliable on Gmail
                    val since = java.util.Date(sinceMillis - 60_000L) // 1 min skew
                    val candidates = try {
                        inbox.search(ReceivedDateTerm(ComparisonTerm.GE, since))
                    } catch (_: Exception) {
                        // Fallback: last N messages
                        val total = inbox.messageCount
                        if (total <= 0) emptyArray()
                        else inbox.getMessages((total - maxMessages + 1).coerceAtLeast(1), total)
                    }
                    candidates
                        .sortedByDescending { it.receivedDate?.time ?: 0L }
                        .take(maxMessages * 3)
                        .mapNotNull { msg -> toRawEmail(msg, trusted) }
                        .take(maxMessages)
                } finally {
                    if (inbox.isOpen) inbox.close(false)
                }
            }
        }
    }

    /**
     * Blocks on IMAP IDLE until new mail, disconnect, or timeout.
     * Returns newly arrived trusted emails (may be empty if idle timed out).
     * Call from a background dispatcher.
     */
    fun idleOnce(
        trusted: List<TrustedSender>,
        onStatus: (String) -> Unit = {},
        idleTimeoutMs: Long = 25 * 60_000L,
    ): List<RawEmail> {
        val creds = credentials() ?: return emptyList()
        if (trusted.isEmpty()) return emptyList()

        val arrived = mutableListOf<RawEmail>()
        openStore(creds.first, creds.second).use { store ->
            val folder = store.getFolder("INBOX") as IMAPFolder
            folder.open(Folder.READ_ONLY)
            try {
                folder.addMessageCountListener(object : MessageCountAdapter() {
                    override fun messagesAdded(e: MessageCountEvent) {
                        e.messages.forEach { msg ->
                            toRawEmail(msg, trusted)?.let { arrived += it }
                        }
                    }
                })
                onStatus("IDLE listening…")
                // idle() returns when new messages arrive or server ends IDLE
                // Run with a watchdog timeout via interrupt from caller if needed
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < idleTimeoutMs && arrived.isEmpty()) {
                    try {
                        folder.idle(true)
                    } catch (e: Exception) {
                        onStatus("IDLE ended: ${e.message}")
                        break
                    }
                    // After idle returns, if no messages, loop (server keepalive)
                    if (arrived.isEmpty()) {
                        Thread.sleep(500)
                    }
                }
            } finally {
                if (folder.isOpen) folder.close(false)
            }
        }
        return arrived
    }

    fun resolveWallet(sender: String, trusted: List<TrustedSender>): String =
        trustedSenderRepository.matches(sender, trusted)?.walletLabel ?: "Email"

    private fun openStore(user: String, pass: String): Store {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", "imap.gmail.com")
            put("mail.imaps.port", "993")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.trust", "*")
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "60000")
            put("mail.imaps.writetimeout", "20000")
            put("mail.imaps.partialfetch", "false")
            put("mail.imaps.peek", "true")
        }
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        store.connect("imap.gmail.com", 993, user, pass)
        return store
    }

    private fun toRawEmail(msg: javax.mail.Message, trusted: List<TrustedSender>): RawEmail? {
        return try {
            val from = extractAddress(msg) ?: return null
            if (trustedSenderRepository.matches(from, trusted) == null) return null
            val messageId = msg.getHeader("Message-ID")?.firstOrNull()?.trim()
                ?: "uid-${msg.receivedDate?.time}-$from-${msg.subject.hashCode()}"
            RawEmail(
                messageId = messageId,
                sender = from,
                subject = msg.subject,
                body = readBody(msg),
                receivedAt = msg.receivedDate?.time ?: System.currentTimeMillis(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAddress(msg: javax.mail.Message): String? {
        val addr = msg.from?.firstOrNull() ?: return null
        return when (addr) {
            is InternetAddress -> addr.address?.lowercase()?.trim()
            else -> {
                val s = addr.toString()
                Regex("""[\w.+-]+@[\w.-]+""").find(s)?.value?.lowercase()
            }
        }
    }

    private fun readBody(msg: javax.mail.Message): String {
        return try {
            when (val content = msg.content) {
                is String -> content
                is javax.mail.Multipart -> {
                    val plain = StringBuilder()
                    val html = StringBuilder()
                    fun walk(mp: javax.mail.Multipart) {
                        for (i in 0 until mp.count) {
                            val part = mp.getBodyPart(i)
                            val ct = part.contentType?.lowercase().orEmpty()
                            when {
                                part.content is javax.mail.Multipart -> walk(part.content as javax.mail.Multipart)
                                ct.contains("text/plain") -> plain.appendLine(part.content?.toString().orEmpty())
                                ct.contains("text/html") -> html.appendLine(part.content?.toString().orEmpty())
                            }
                        }
                    }
                    walk(content)
                    if (plain.isNotBlank()) plain.toString() else html.toString()
                }
                else -> content?.toString().orEmpty()
            }
        } catch (_: Exception) {
            try {
                msg.getHeader("Subject")?.joinToString(" ").orEmpty()
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun friendlyError(e: Exception): String {
        val msg = (e.message ?: e.toString()).lowercase()
        return when {
            msg.contains("authenticationfailed") || msg.contains("invalid credentials") ||
                msg.contains("username and password not accepted") ->
                "Login failed. Use a Google App Password (not your normal password). Enable 2FA first, then create an App Password for Mail."
            msg.contains("application-specific password") ->
                "Gmail requires an App Password. Create one at myaccount.google.com/apppasswords"
            msg.contains("web login required") || msg.contains("please log in via your web browser") ->
                "Gmail blocked the login. Visit accounts.google.com and allow access, then use an App Password."
            msg.contains("timeout") || msg.contains("timed out") ->
                "Connection timed out. Check internet and try again."
            msg.contains("unknown host") || msg.contains("unable to resolve") ->
                "Cannot reach imap.gmail.com. Check internet/DNS."
            else -> "IMAP error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private inline fun <T> Store.use(block: (Store) -> T): T {
        try {
            return block(this)
        } finally {
            try {
                if (isConnected) close()
            } catch (_: Exception) {
            }
        }
    }
}
