package com.krtky.financetracker.data.llm

import android.util.Log
import com.krtky.financetracker.data.prefs.SecureStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class ExtractedTransaction(
    val type: String? = null,
    val amount: Double? = null,
    val currency: String? = "INR",
    val occurredAt: String? = null,
    val merchant: String? = null,
    val counterparty: String? = null,
    val category: String? = null,
    val bank: String? = null,
    val toBank: String? = null,
    val isSelfTransfer: Boolean? = null,
    val referenceId: String? = null,
    val paymentMethod: String? = null,
    val note: String? = null,
    val confidence: Double? = null,
)

/**
 * Thin OpenAI-compatible chat-completions transport. Every call returns an [LlmResult];
 * failures are logged (status + provider message, never the prompt) and surfaced to callers.
 */
@Singleton
class LlmClient @Inject constructor(
    private val secureStore: SecureStore,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    /** True only when AI is on and an API key is saved. */
    fun isConfigured(): Boolean = secureStore.isLlmReady()

    suspend fun extractTransaction(
        messageBody: String,
        subject: String?,
        sender: String,
        categories: List<String> = emptyList(),
        banks: List<String> = emptyList(),
    ): LlmResult<ExtractedTransaction> {
        val system = secureStore.llmSystemPrompt.ifBlank { SecureStore.DEFAULT_LLM_SYSTEM }

        val user = buildString {
            appendLine("From: $sender")
            if (!subject.isNullOrBlank()) appendLine("Subject: $subject")
            appendLine()
            // Explicit closed lists so the model maps onto the user's labels, not free-form names
            if (categories.isNotEmpty()) {
                appendLine("ALLOWED CATEGORIES (pick exactly one of these strings for \"category\", or null):")
                appendLine(categories.joinToString(" | "))
            } else {
                appendLine("ALLOWED CATEGORIES: (none configured — set category to null)")
            }
            if (banks.isNotEmpty()) {
                appendLine("ALLOWED DIGITAL ACCOUNTS (pick exactly one of these strings for \"bank\" / paymentMethod when digital, or null):")
                appendLine(banks.joinToString(" | "))
                appendLine("Match the closest account from this list only. Prefer the list label over synonyms.")
                appendLine("If money moved between two of those accounts (not a spend), set isSelfTransfer=true, bank=source, toBank=destination, type=DEBIT for the source leg semantics.")
            } else {
                appendLine("ALLOWED DIGITAL ACCOUNTS: (none configured — use paymentMethod Digital if not cash)")
            }
            appendLine("Use type \"DEBIT\" or \"CREDIT\". Use \"none\" for bills/dues/reminders/non-completed — do not invent a txn.")
            appendLine("Put the Name in \"counterparty\". For occurredAt prefer ISO-8601 with +05:30 when a date/time is in the message; else null.")
            appendLine("Masked values like ****ACCT**** are redacted: never copy them into referenceId (use null).")
            appendLine("Extract only one completed movement from this message. Respond with a single JSON object.")
            appendLine()
            appendLine("Message body:")
            append(messageBody.take(6000))
        }

        return when (val r = completeJson(system = system, user = user)) {
            is LlmResult.Failed -> r
            is LlmResult.Ok -> runCatching {
                LlmResult.Ok(json.decodeFromString(ExtractedTransaction.serializer(), r.value))
            }.getOrElse { LlmResult.Failed(LlmError.BadResponse(it.message?.take(120) ?: "invalid JSON")) }
        }
    }

    /** Minimal round trip to validate base URL, model and key. */
    suspend fun testConnection(): LlmResult<Unit> =
        completeJson(
            system = "You are a health check. Reply with the JSON object {\"ok\": true}.",
            user = "Respond with JSON.",
        ).map { }

    /**
     * One JSON-object chat completion. Returns the assistant content trimmed to the
     * outermost JSON object.
     *
     * Keep [user] small — never send whole files.
     */
    suspend fun completeJson(system: String, user: String): LlmResult<String> {
        if (!secureStore.isLlmReady()) return LlmResult.Failed(LlmError.NotConfigured)
        val apiKey = secureStore.llmApiKey ?: return LlmResult.Failed(LlmError.NotConfigured)
        val first = request(apiKey, system, user, jsonMode = true)
        // Some OpenAI-compatible servers don't support response_format; retry once without it.
        val err = first.errorOrNull()
        val result = if (err is LlmError.Http && err.code == 400 &&
            err.body.contains("response_format", ignoreCase = true)
        ) {
            request(apiKey, system, user, jsonMode = false)
        } else first
        result.errorOrNull()?.let { Log.w(TAG, "LLM call failed (model=${secureStore.llmModel}): $it") }
        return result
    }

    private suspend fun request(apiKey: String, system: String, user: String, jsonMode: Boolean): LlmResult<String> {
        val payload = ChatRequest(
            model = secureStore.llmModel,
            temperature = 0.0,
            responseFormat = if (jsonMode) ResponseFormat("json_object") else null,
            messages = listOf(
                ChatMessage("system", system),
                ChatMessage("user", user),
            ),
        )
        val body = json.encodeToString(ChatRequest.serializer(), payload)
            .toRequestBody("application/json".toMediaType())
        val request = runCatching {
            Request.Builder()
                .url(chatCompletionsUrl(secureStore.llmBaseUrl))
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
        }.getOrElse { return LlmResult.Failed(LlmError.Network("invalid base URL")) }

        val response = try {
            client.newCall(request).await()
        } catch (e: InterruptedIOException) {
            return LlmResult.Failed(LlmError.Timeout)
        } catch (e: IOException) {
            return LlmResult.Failed(LlmError.Network(e.message ?: e.javaClass.simpleName))
        }
        return response.use { resp ->
            val text = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
            if (!resp.isSuccessful) {
                return@use LlmResult.Failed(LlmError.Http(resp.code, providerMessage(text)))
            }
            val content = runCatching {
                json.decodeFromString(ChatResponse.serializer(), text).choices.firstOrNull()?.message?.content
            }.getOrNull()
                ?: return@use LlmResult.Failed(LlmError.BadResponse("no message content"))
            val obj = extractJsonObject(content)
                ?: return@use LlmResult.Failed(LlmError.BadResponse("no JSON object in reply"))
            LlmResult.Ok(obj)
        }
    }

    /** Pull `error.message` out of an OpenAI-style error body, else a short raw excerpt. */
    private fun providerMessage(body: String): String {
        val msg = runCatching {
            val err = json.parseToJsonElement(body).jsonObject["error"]
            runCatching { err?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
                ?: err?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return (msg ?: body).replace(Regex("\\s+"), " ").trim().take(200)
    }

    /** OkHttp call that cancels the HTTP request when the coroutine is cancelled. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }

            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        })
    }

    @Serializable
    private data class ChatRequest(
        val model: String,
        val temperature: Double,
        @SerialName("response_format") val responseFormat: ResponseFormat? = null,
        val messages: List<ChatMessage>,
    )

    @Serializable
    private data class ResponseFormat(val type: String)

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(val message: Msg? = null)

    @Serializable
    private data class Msg(val content: String? = null)

    companion object {
        private const val TAG = "LlmClient"

        /** Accepts `…/v1`, `…/v1/` or a full `…/chat/completions` URL. */
        fun chatCompletionsUrl(base: String): String {
            val b = base.trim().trimEnd('/')
            return if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
        }

        /** Strip markdown fences / prose around the reply: outermost `{ … }`, or null. */
        fun extractJsonObject(content: String): String? {
            val start = content.indexOf('{')
            val end = content.lastIndexOf('}')
            return if (start in 0 until end) content.substring(start, end + 1) else null
        }
    }
}
