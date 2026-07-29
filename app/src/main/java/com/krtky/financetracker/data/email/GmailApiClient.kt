package com.krtky.financetracker.data.email

import android.accounts.Account
import android.content.Context
import android.util.Base64
import com.google.android.gms.auth.GoogleAuthUtil
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.repository.TrustedSenderRepository
import com.krtky.financetracker.domain.model.TrustedSender
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gmail REST API client using OAuth2 [GMAIL_READONLY_SCOPE].
 *
 * Live mode uses mailbox **history** change detection:
 * 1. Seed / renew [users.watch] when a Pub/Sub topic is configured
 * 2. Poll [users.history.list] for messageAdded (cheap — not a full inbox scan)
 * 3. Fetch only those messages and keep trusted senders
 *
 * No client secret is embedded — Android Google Sign-In + GoogleAuthUtil obtain tokens.
 */
@Singleton
class GmailApiClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStore: SecureStore,
    private val trustedSenderRepository: TrustedSenderRepository,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean =
        !secureStore.gmailOAuthEmail.isNullOrBlank()

    fun connectedEmail(): String? = secureStore.gmailOAuthEmail?.trim()?.lowercase()

    fun clearSession() {
        val old = secureStore.gmailAccessToken
        if (!old.isNullOrBlank()) {
            runCatching { GoogleAuthUtil.clearToken(context, old) }
        }
        secureStore.gmailAccessToken = null
        secureStore.gmailOAuthEmail = null
        secureStore.gmailHistoryId = null
        secureStore.gmailWatchExpirationMs = null
    }

    /**
     * Persist OAuth account after Google Sign-In and cache the access token.
     */
    fun saveSession(email: String, accessToken: String) {
        secureStore.gmailOAuthEmail = email.trim().lowercase()
        secureStore.gmailAccessToken = accessToken.trim()
        if (secureStore.gmailAddress.isNullOrBlank()) {
            secureStore.gmailAddress = email.trim().lowercase()
        }
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("Sign in with Google first (Gmail.readonly)"),
            )
        }
        try {
            val body = authorizedGet("$BASE/users/me/profile")
            val root = json.parseToJsonElement(body).jsonObject
            val email = root["emailAddress"]?.jsonPrimitive?.contentOrNull
                ?: connectedEmail().orEmpty()
            val total = root["messagesTotal"]?.jsonPrimitive?.contentOrNull
            val historyId = root["historyId"]?.jsonPrimitive?.contentOrNull
            if (!historyId.isNullOrBlank() && secureStore.gmailHistoryId.isNullOrBlank()) {
                secureStore.gmailHistoryId = historyId
            }
            Result.success(
                "Connected as $email via Gmail API" +
                    (total?.let { " · ~$it messages" } ?: "") +
                    " · history watch ready",
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException(friendlyError(e), e))
        }
    }

    /**
     * Bootstrap + renew Gmail watch state.
     * - Always seeds [SecureStore.gmailHistoryId] from profile if missing
     * - Calls [users.watch] when a Pub/Sub topic is stored (true GCP push registration)
     * - Without a topic, history.list still detects changes (app-side watch loop)
     */
    suspend fun ensureWatch(): WatchSetup = withContext(Dispatchers.IO) {
        if (!isConfigured()) throw IllegalStateException("Gmail OAuth not connected")

        var historyId = secureStore.gmailHistoryId
        if (historyId.isNullOrBlank()) {
            historyId = fetchProfileHistoryId()
            secureStore.gmailHistoryId = historyId
        }

        val topic = secureStore.gmailPubSubTopic?.trim().orEmpty()
        val exp = secureStore.gmailWatchExpirationMs
        val needRenew = topic.isNotBlank() &&
            (exp == null || exp < System.currentTimeMillis() + WATCH_RENEW_AHEAD_MS)

        var watchRegistered = false
        var watchNote: String? = null
        if (topic.isNotBlank() && needRenew) {
            try {
                val setup = registerUsersWatch(topic)
                secureStore.gmailHistoryId = setup.historyId
                secureStore.gmailWatchExpirationMs = setup.expirationMs
                historyId = setup.historyId
                watchRegistered = true
                watchNote = "users.watch active"
            } catch (e: Exception) {
                watchNote = "users.watch failed: ${e.message?.take(80)}"
                // Continue with history.list using existing historyId
            }
        } else if (topic.isBlank()) {
            watchNote = "history.list watch (no Pub/Sub topic)"
        } else {
            watchRegistered = true
            watchNote = "users.watch still valid"
        }

        WatchSetup(
            historyId = historyId!!,
            watchRegistered = watchRegistered,
            note = watchNote,
        )
    }

    /**
     * Check mailbox history since the stored cursor. Returns newly arrived trusted emails only.
     * Updates the stored historyId on success.
     */
    suspend fun fetchChangesForTrusted(): HistoryFetchResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext HistoryFetchResult(emptyList(), null, "not configured")
        }
        val trusted = trustedSenderRepository.getEnabled()
        if (trusted.isEmpty()) {
            return@withContext HistoryFetchResult(emptyList(), null, "no trusted senders")
        }

        var startId = secureStore.gmailHistoryId
        if (startId.isNullOrBlank()) {
            val setup = ensureWatch()
            startId = setup.historyId
            // First run: no prior cursor — do a one-shot recent pull, then track history
            val bootstrap = fetchRecent(startOfTodayMillis(), maxMessages = 50)
            return@withContext HistoryFetchResult(bootstrap, secureStore.gmailHistoryId, "bootstrap")
        }

        try {
            val delta = listHistoryMessageIds(startId)
            secureStore.gmailHistoryId = delta.newHistoryId
            if (delta.messageIds.isEmpty()) {
                return@withContext HistoryFetchResult(emptyList(), delta.newHistoryId, "no changes")
            }
            val emails = delta.messageIds
                .distinct()
                .take(40)
                .mapNotNull { id ->
                    try {
                        fetchMessage(id, trusted)
                    } catch (_: Exception) {
                        null
                    }
                }
            HistoryFetchResult(emails, delta.newHistoryId, "history")
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            // historyId too old / invalid → reset cursor and fall back to recent search
            if (msg.contains("404") || msg.contains("historyId") || msg.contains("notFound")) {
                val fresh = fetchProfileHistoryId()
                secureStore.gmailHistoryId = fresh
                val fallback = fetchRecent(startOfTodayMillis(), maxMessages = 50)
                HistoryFetchResult(fallback, fresh, "history reset")
            } else {
                throw e
            }
        }
    }

    suspend fun fetchRecent(sinceMillis: Long, maxMessages: Int = 50): List<RawEmail> {
        if (!isConfigured()) return emptyList()
        val trusted = trustedSenderRepository.getEnabled()
        if (trusted.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            val q = buildSearchQuery(sinceMillis, trusted)
            val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
            val listUrl = "$BASE/users/me/messages?maxResults=$maxMessages&q=$encoded"
            val listBody = authorizedGet(listUrl)
            // Keep history cursor fresh when listing
            json.parseToJsonElement(listBody).jsonObject["resultSizeEstimate"]
            runCatching {
                val profileId = fetchProfileHistoryId()
                if (secureStore.gmailHistoryId.isNullOrBlank()) {
                    secureStore.gmailHistoryId = profileId
                }
            }
            val ids = parseMessageIds(listBody)
            ids.mapNotNull { id ->
                try {
                    fetchMessage(id, trusted)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    fun resolveWallet(sender: String, trusted: List<TrustedSender>): String =
        trustedSenderRepository.matches(sender, trusted)?.walletLabel ?: "Email"

    private fun registerUsersWatch(topicName: String): WatchSetup {
        val body = buildJsonObject {
            put("topicName", topicName)
            putJsonArray("labelIds") { add(JsonPrimitive("INBOX")) }
            put("labelFilterBehavior", "INCLUDE")
        }.toString()
        val text = authorizedPost("$BASE/users/me/watch", body)
        val root = json.parseToJsonElement(text).jsonObject
        val historyId = root["historyId"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("watch response missing historyId")
        val expiration = root["expiration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        return WatchSetup(
            historyId = historyId,
            watchRegistered = true,
            note = "registered",
            expirationMs = expiration,
        )
    }

    private fun fetchProfileHistoryId(): String {
        val body = authorizedGet("$BASE/users/me/profile")
        return json.parseToJsonElement(body).jsonObject["historyId"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Gmail profile missing historyId")
    }

    private fun listHistoryMessageIds(startHistoryId: String): HistoryDelta {
        val encoded = URLEncoder.encode(startHistoryId, StandardCharsets.UTF_8.name())
        // Only care about new messages in INBOX
        val url = "$BASE/users/me/history?startHistoryId=$encoded" +
            "&historyTypes=messageAdded&labelId=INBOX&maxResults=100"
        val text = authorizedGet(url)
        val root = json.parseToJsonElement(text).jsonObject
        val newHistoryId = root["historyId"]?.jsonPrimitive?.contentOrNull ?: startHistoryId
        val ids = linkedSetOf<String>()
        val history = root["history"] as? JsonArray
        history?.forEach { entry ->
            val added = entry.jsonObject["messagesAdded"] as? JsonArray ?: return@forEach
            added.forEach { item ->
                val mid = item.jsonObject["message"]?.jsonObject?.get("id")
                    ?.jsonPrimitive?.contentOrNull
                if (!mid.isNullOrBlank()) ids += mid
            }
        }
        // Paginate if needed
        var pageToken = root["nextPageToken"]?.jsonPrimitive?.contentOrNull
        var guard = 0
        while (!pageToken.isNullOrBlank() && guard < 5) {
            guard++
            val pageUrl = "$BASE/users/me/history?startHistoryId=$encoded" +
                "&historyTypes=messageAdded&labelId=INBOX&maxResults=100" +
                "&pageToken=${URLEncoder.encode(pageToken, StandardCharsets.UTF_8.name())}"
            val pageText = authorizedGet(pageUrl)
            val pageRoot = json.parseToJsonElement(pageText).jsonObject
            val pageHist = pageRoot["history"] as? JsonArray
            pageHist?.forEach { entry ->
                val added = entry.jsonObject["messagesAdded"] as? JsonArray ?: return@forEach
                added.forEach { item ->
                    val mid = item.jsonObject["message"]?.jsonObject?.get("id")
                        ?.jsonPrimitive?.contentOrNull
                    if (!mid.isNullOrBlank()) ids += mid
                }
            }
            pageToken = pageRoot["nextPageToken"]?.jsonPrimitive?.contentOrNull
        }
        return HistoryDelta(newHistoryId = newHistoryId, messageIds = ids.toList())
    }

    private fun buildSearchQuery(sinceMillis: Long, trusted: List<TrustedSender>): String {
        val cal = Calendar.getInstance().apply { timeInMillis = sinceMillis }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val after = "after:$y/$m/$d"
        val fromClause = trusted
            .map { it.emailPattern.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(20)
            .joinToString(" OR ") { pattern ->
                if (pattern.contains("@")) "from:$pattern" else pattern
            }
        return if (fromClause.isBlank()) after else "$after ($fromClause)"
    }

    private fun parseMessageIds(listBody: String): List<String> {
        val root = json.parseToJsonElement(listBody).jsonObject
        val arr = root["messages"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            el.jsonObject["id"]?.jsonPrimitive?.contentOrNull
        }
    }

    private fun fetchMessage(id: String, trusted: List<TrustedSender>): RawEmail? {
        val body = authorizedGet("$BASE/users/me/messages/$id?format=full")
        val root = json.parseToJsonElement(body).jsonObject
        val payload = root["payload"]?.jsonObject ?: return null
        val headers = payload["headers"] as? JsonArray
        val from = header(headers, "From")?.let { extractEmail(it) } ?: return null
        if (trustedSenderRepository.matches(from, trusted) == null) return null
        val subject = header(headers, "Subject")
        val messageId = header(headers, "Message-ID")?.trim()
            ?: root["id"]?.jsonPrimitive?.contentOrNull
            ?: id
        val internalDate = root["internalDate"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: System.currentTimeMillis()
        val text = extractBody(payload)
        return RawEmail(
            messageId = messageId,
            sender = from,
            subject = subject,
            body = text,
            receivedAt = internalDate,
        )
    }

    private fun header(headers: JsonArray?, name: String): String? {
        if (headers == null) return null
        for (el in headers) {
            val obj = el.jsonObject
            val n = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
            if (n.equals(name, ignoreCase = true)) {
                return obj["value"]?.jsonPrimitive?.contentOrNull
            }
        }
        return null
    }

    private fun extractEmail(fromHeader: String): String? {
        val angle = Regex("""<([^>]+)>""").find(fromHeader)?.groupValues?.getOrNull(1)
        val raw = angle ?: fromHeader
        return Regex("""[\w.+-]+@[\w.-]+""").find(raw)?.value?.lowercase()?.trim()
    }

    private fun extractBody(payload: JsonObject): String {
        val plain = StringBuilder()
        val html = StringBuilder()
        fun walk(part: JsonObject) {
            val mime = part["mimeType"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            val data = part["body"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull
            if (!data.isNullOrBlank()) {
                val decoded = decodeBase64Url(data)
                when {
                    mime.contains("text/plain") -> plain.appendLine(decoded)
                    mime.contains("text/html") -> html.appendLine(decoded)
                    mime.startsWith("text/") && plain.isEmpty() -> plain.appendLine(decoded)
                }
            }
            val parts = part["parts"] as? JsonArray
            parts?.forEach { walk(it.jsonObject) }
        }
        walk(payload)
        return when {
            plain.isNotBlank() -> plain.toString()
            html.isNotBlank() -> html.toString()
            else -> payload["snippet"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
    }

    private fun decodeBase64Url(data: String): String {
        return try {
            val bytes = Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            try {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                String(bytes, StandardCharsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun startOfTodayMillis(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun authorizedGet(url: String): String =
        authorizedRequest(url, method = "GET", body = null)

    private fun authorizedPost(url: String, jsonBody: String): String =
        authorizedRequest(url, method = "POST", body = jsonBody)

    private fun authorizedRequest(url: String, method: String, body: String?): String {
        var token = resolveToken(forceRefresh = false)
        var response = execute(url, method, body, token)
        if (response.code == 401) {
            invalidateToken(token)
            token = resolveToken(forceRefresh = true)
            response = execute(url, method, body, token)
        }
        val text = response.body
        if (response.code !in 200..299) {
            throw IllegalStateException(
                "Gmail API ${response.code}: ${text.take(240).ifBlank { "request failed" }}",
            )
        }
        return text
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun execute(url: String, method: String, body: String?, token: String): HttpResult {
        val builder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
        when (method) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonMedia))
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { resp ->
            return HttpResult(resp.code, resp.body?.string().orEmpty())
        }
    }

    private fun resolveToken(forceRefresh: Boolean): String {
        val email = secureStore.gmailOAuthEmail?.trim()
            ?: throw IllegalStateException("Gmail OAuth not connected")
        val account = Account(email, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE)
        if (forceRefresh) {
            secureStore.gmailAccessToken?.let { old ->
                runCatching { GoogleAuthUtil.clearToken(context, old) }
            }
            secureStore.gmailAccessToken = null
        }
        val cached = secureStore.gmailAccessToken
        if (!cached.isNullOrBlank() && !forceRefresh) return cached
        val token = GoogleAuthUtil.getToken(context, account, "oauth2:$GMAIL_READONLY_SCOPE")
        secureStore.gmailAccessToken = token
        return token
    }

    private fun invalidateToken(token: String) {
        runCatching { GoogleAuthUtil.clearToken(context, token) }
        if (secureStore.gmailAccessToken == token) {
            secureStore.gmailAccessToken = null
        }
    }

    private fun friendlyError(e: Exception): String {
        val msg = (e.message ?: e.toString()).lowercase()
        return when {
            msg.contains("needspermission") || msg.contains("userexception") ||
                msg.contains("userrecoverable") ->
                "Google needs re-authorization. Tap Connect with Google again."
            msg.contains("network") || msg.contains("unable to resolve") || msg.contains("timeout") ->
                "Network error talking to Gmail API. Check internet."
            msg.contains("403") || msg.contains("access_denied") || msg.contains("insufficient") ->
                "Gmail access denied. Enable Gmail API in Google Cloud and grant gmail.readonly."
            msg.contains("401") ->
                "Session expired. Tap Connect with Google again."
            else -> "Gmail API error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    data class WatchSetup(
        val historyId: String,
        val watchRegistered: Boolean,
        val note: String? = null,
        val expirationMs: Long? = null,
    )

    data class HistoryFetchResult(
        val emails: List<RawEmail>,
        val historyId: String?,
        val mode: String,
    )

    private data class HistoryDelta(
        val newHistoryId: String,
        val messageIds: List<String>,
    )

    companion object {
        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        private const val BASE = "https://gmail.googleapis.com/gmail/v1"
        /** Renew users.watch a day before expiry. */
        private const val WATCH_RENEW_AHEAD_MS = 24L * 60L * 60L * 1000L
    }
}
