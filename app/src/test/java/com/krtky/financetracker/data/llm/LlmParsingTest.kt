package com.krtky.financetracker.data.llm

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.Category
import org.junit.Test

class LlmParsingTest {

    private val food = Category(id = 1, name = "Food & Dining")
    private val travel = Category(id = 2, name = "Travel")
    private val cats = listOf(food, travel)

    @Test
    fun `chat completions url accepts base or full url`() {
        assertThat(LlmClient.chatCompletionsUrl("https://api.groq.com/openai/v1"))
            .isEqualTo("https://api.groq.com/openai/v1/chat/completions")
        assertThat(LlmClient.chatCompletionsUrl("https://api.groq.com/openai/v1/ "))
            .isEqualTo("https://api.groq.com/openai/v1/chat/completions")
        assertThat(LlmClient.chatCompletionsUrl("https://api.openai.com/v1/chat/completions"))
            .isEqualTo("https://api.openai.com/v1/chat/completions")
    }

    @Test
    fun `extracts json object from fenced or chatty replies`() {
        assertThat(LlmClient.extractJsonObject("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}")
        assertThat(LlmClient.extractJsonObject("Sure! Here it is: {\"a\":{\"b\":2}} hope that helps"))
            .isEqualTo("{\"a\":{\"b\":2}}")
        assertThat(LlmClient.extractJsonObject("no json here")).isNull()
    }

    @Test
    fun `classification maps back by id, not by echoed text`() {
        val raw = """{"results":[{"id":2,"category":"travel"},{"id":"1","category":"Food & Dining"}]}"""
        val out = TransactionClassifier.parseResults(raw, size = 2, categories = cats)
        assertThat(out).containsExactly(0, food, 1, travel)
    }

    @Test
    fun `classification ignores unknown ids, nulls and invented categories`() {
        val raw = """{"results":[{"id":3,"category":"Travel"},{"id":1,"category":null},{"id":2,"category":"Groceries"}]}"""
        assertThat(TransactionClassifier.parseResults(raw, size = 2, categories = cats)).isEmpty()
        assertThat(TransactionClassifier.parseResults("not json", size = 2, categories = cats)).isEmpty()
    }

    @Test
    fun `describe sends counterparty plus one redacted line`() {
        val sms = "Rs 250.00 debited from A/c XX1234\non 09-10-26 to Swiggy.\nUPI Ref 123456789012"
        val d = TransactionClassifier.describe("Swiggy", sms)!!
        assertThat(d).doesNotContain("\n")
        assertThat(d).doesNotContain("123456789012")
        assertThat(d).contains("Swiggy")
        assertThat(TransactionClassifier.describe("Zomato", null)).isEqualTo("Zomato")
        assertThat(TransactionClassifier.describe(" ", "  ")).isNull()
    }

    @Test
    fun `transient errors are retryable, auth errors are not`() {
        assertThat(LlmError.Http(429, "").isTransient).isTrue()
        assertThat(LlmError.Http(503, "").isTransient).isTrue()
        assertThat(LlmError.Timeout.isTransient).isTrue()
        assertThat(LlmError.Http(401, "").isTransient).isFalse()
        assertThat(LlmError.Http(404, "").isTransient).isFalse()
        assertThat(LlmError.NotConfigured.isTransient).isFalse()
    }
}
