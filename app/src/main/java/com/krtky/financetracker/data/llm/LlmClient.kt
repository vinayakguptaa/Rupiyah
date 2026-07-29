package com.krtky.financetracker.data.llm

import com.krtky.financetracker.data.prefs.SecureStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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
    val referenceId: String? = null,
    val paymentMethod: String? = null,
    val note: String? = null,
    val confidence: Double? = null,
)

@Singleton
class LlmClient @Inject constructor(
    private val secureStore: SecureStore,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = !secureStore.llmApiKey.isNullOrBlank()

    suspend fun extractTransaction(
        redactedEmailBody: String,
        subject: String?,
        sender: String,
        categories: List<String> = emptyList(),
        banks: List<String> = emptyList(),
    ): ExtractedTransaction? {
        val apiKey = secureStore.llmApiKey ?: return null
        val base = secureStore.llmBaseUrl.trimEnd('/')
        val model = secureStore.llmModel
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
            } else {
                appendLine("ALLOWED DIGITAL ACCOUNTS: (none configured — use paymentMethod Digital if not cash)")
            }
            appendLine()
            appendLine("Message body:")
            append(redactedEmailBody.take(6000))
        }

        val payload = ChatRequest(
            model = model,
            temperature = 0.0,
            responseFormat = ResponseFormat("json_object"),
            messages = listOf(
                ChatMessage("system", system),
                ChatMessage("user", user),
            ),
        )

        val body = json.encodeToString(ChatRequest.serializer(), payload)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$base/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val text = resp.body?.string() ?: return@use null
                val chat = json.decodeFromString(ChatResponse.serializer(), text)
                val content = chat.choices.firstOrNull()?.message?.content ?: return@use null
                val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                json.decodeFromString(ExtractedTransaction.serializer(), cleaned)
            }
        }
    }

    @Serializable
    private data class ChatRequest(
        val model: String,
        val temperature: Double,
        @SerialName("response_format") val responseFormat: ResponseFormat,
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
}
