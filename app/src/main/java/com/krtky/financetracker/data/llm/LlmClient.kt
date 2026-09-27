package com.krtky.financetracker.data.llm

import com.krtky.financetracker.data.prefs.SecureStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    val toBank: String? = null,
    val isSelfTransfer: Boolean? = null,
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

    /** True only when AI is on and an API key is saved (required for SMS auto-import). */
    fun isConfigured(): Boolean = secureStore.isLlmReady()

    suspend fun extractTransaction(
        messageBody: String,
        subject: String?,
        sender: String,
        categories: List<String> = emptyList(),
        banks: List<String> = emptyList(),
    ): ExtractedTransaction? {
        if (!secureStore.isLlmReady()) return null
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
            appendLine("Extract only one completed movement from this message.")
            appendLine()
            appendLine("Message body:")
            append(messageBody.take(6000))
        }

        val cleaned = completeJson(system = system, user = user) ?: return null
        return runCatching {
            json.decodeFromString(ExtractedTransaction.serializer(), cleaned)
        }.getOrNull()
    }

    /**
     * Batch-classify a list of transaction descriptions or bank narrations into allowed categories using the configured LLM.
     * Returns a map of description -> canonical category name.
     */
    suspend fun batchClassifyDescriptions(
        descriptions: List<String>,
        categoryNames: List<String>,
    ): Map<String, String> {
        if (!isConfigured() || descriptions.isEmpty() || categoryNames.isEmpty()) return emptyMap()

        val system = """You are an expert personal finance transaction classifier.
You will receive a list of bank statement transaction descriptions / narrations (such as UPI, POS swipes, card transactions, NEFT, IMPS, salary, etc.) and a list of allowed categories.
For each transaction description:
1. Identify the merchant, counterparty, or purpose of spend (for example: from 'UPI/DR/625203830280/Swiggy/ICICI' identify 'Swiggy' -> 'Food & Dining').
2. Choose EXACTLY ONE matching category name from ALLOWED CATEGORIES.
3. If you cannot confidently determine the category, or if it is an unknown/ambiguous transfer, assign null for category.
Return JSON with format:
{"classifications": [{"description": "...exact input string...", "category": "...exact allowed category name or null..."}]}"""

        val user = buildString {
            appendLine("ALLOWED CATEGORIES:")
            appendLine(categoryNames.joinToString(", "))
            appendLine()
            appendLine("TRANSACTION DESCRIPTIONS TO CLASSIFY:")
            descriptions.forEachIndexed { i, d -> appendLine("${i + 1}. $d") }
        }

        val jsonStr = completeJson(system = system, user = user) ?: return emptyMap()
        return runCatching {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val array = root["classifications"]?.jsonArray
                ?: root["categories"]?.jsonArray
                ?: root["results"]?.jsonArray
            val result = mutableMapOf<String, String>()
            array?.forEach { elem ->
                val obj = elem.jsonObject
                val d = obj["description"]?.jsonPrimitive?.contentOrNull
                    ?: obj["merchant"]?.jsonPrimitive?.contentOrNull
                    ?: obj["narration"]?.jsonPrimitive?.contentOrNull
                val c = obj["category"]?.jsonPrimitive?.contentOrNull
                if (!d.isNullOrBlank() && !c.isNullOrBlank()) {
                    val canonical = categoryNames.firstOrNull { it.equals(c, ignoreCase = true) }
                    if (canonical != null) {
                        result[d] = canonical
                    }
                }
            }
            result
        }.getOrDefault(emptyMap())
    }

    suspend fun batchClassifyMerchants(
        merchants: List<String>,
        categoryNames: List<String>,
    ): Map<String, String> = batchClassifyDescriptions(merchants, categoryNames)

    /**
     * One JSON-object chat completion. Returns the assistant content with
     * markdown fences stripped, or null if AI is off / the call fails.
     *
     * Used for small structured tasks (CSV header mapping). Do not send
     * whole files — keep [user] to headers + a few sample rows.
     */
    suspend fun completeJson(system: String, user: String): String? {
        if (!secureStore.isLlmReady()) return null
        val apiKey = secureStore.llmApiKey ?: return null
        val base = secureStore.llmBaseUrl.trimEnd('/')
        val model = secureStore.llmModel
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
            runCatching {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val text = resp.body?.string() ?: return@use null
                    val chat = json.decodeFromString(ChatResponse.serializer(), text)
                    val content = chat.choices.firstOrNull()?.message?.content ?: return@use null
                    content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                }
            }.getOrNull()
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
