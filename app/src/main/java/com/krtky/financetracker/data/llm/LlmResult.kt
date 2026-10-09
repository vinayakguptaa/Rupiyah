package com.krtky.financetracker.data.llm

/** Outcome of one LLM call. Failures carry a reason the UI can show instead of a silent null. */
sealed interface LlmResult<out T> {
    data class Ok<T>(val value: T) : LlmResult<T>
    data class Failed(val error: LlmError) : LlmResult<Nothing>

    fun getOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): LlmError? = (this as? Failed)?.error
}

inline fun <T, R> LlmResult<T>.map(f: (T) -> R): LlmResult<R> = when (this) {
    is LlmResult.Ok -> LlmResult.Ok(f(value))
    is LlmResult.Failed -> this
}

sealed interface LlmError {
    /** AI is off or no API key saved. */
    data object NotConfigured : LlmError
    /** Non-2xx from the provider. [body] is a short excerpt of the provider's error message. */
    data class Http(val code: Int, val body: String) : LlmError
    data object Timeout : LlmError
    data class Network(val message: String) : LlmError
    /** 2xx but the content was not the JSON we asked for. */
    data class BadResponse(val message: String) : LlmError

    /** Worth retrying later (rate limit, server hiccup, connectivity). */
    val isTransient: Boolean
        get() = when (this) {
            is Http -> code == 408 || code == 429 || code >= 500
            Timeout, is Network -> true
            else -> false
        }

    /** Short, user-facing explanation. */
    fun describe(): String = when (this) {
        NotConfigured -> "AI helper is not set up (Settings → AI helper)"
        is Http -> when (code) {
            401, 403 -> "AI provider rejected the API key ($code)"
            404 -> "AI endpoint or model not found (404) — check base URL and model name"
            429 -> "AI provider rate limit hit (429) — try again in a minute"
            400 -> "AI provider rejected the request (400)${body.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
            in 500..599 -> "AI provider is having problems ($code)"
            else -> "AI request failed ($code)${body.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
        }
        Timeout -> "AI request timed out"
        is Network -> "Could not reach AI provider: $message"
        is BadResponse -> "AI returned an unexpected response: $message"
    }
}
