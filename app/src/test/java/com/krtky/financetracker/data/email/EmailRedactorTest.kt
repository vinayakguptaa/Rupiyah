package com.krtky.financetracker.data.email

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmailRedactorTest {

    @Test
    fun `redact replaces card numbers`() {
        val input = "Your card 4111 1111 1111 1111 was used"
        val result = EmailRedactor.redact(input)
        assertThat(result).contains("****CARD****")
        assertThat(result).doesNotContain("4111")
    }

    @Test
    fun `redact replaces account numbers 10-18 digits`() {
        val input = "A/c 1234567890 was debited" // 10 digits (9-18 range, below card 13-19 range)
        val result = EmailRedactor.redact(input)
        assertThat(result).contains("****ACCT****")
    }

    @Test
    fun `redact replaces card numbers before account numbers`() {
        val input = "A/c 123456789012345 was debited"
        val result = EmailRedactor.redact(input)
        assertThat(result).contains("****CARD****")
    }

    @Test
    fun `redact does not replace short numbers`() {
        val input = "Ref 12345"
        val result = EmailRedactor.redact(input)
        assertThat(result).contains("12345")
    }

    @Test
    fun `redact redacts VPA usernames`() {
        val input = "UPI ref: user@okaxis"
        val result = EmailRedactor.redact(input)
        assertThat(result).contains("us***@okaxis")
    }

    @Test
    fun `redact does not redact normal emails`() {
        val input = "Write to support@example.com"
        val result = EmailRedactor.redact(input)
        assertThat(result).doesNotContain("***")
    }

    @Test
    fun `redact truncates at 8000 chars`() {
        val input = "a".repeat(10_000)
        val result = EmailRedactor.redact(input)
        assertThat(result.length).isAtMost(8000)
    }

    @Test
    fun `stripHtml removes tags`() {
        val html = "<html><body><p>Hello</p><br/><script>alert('xss')</script></body></html>"
        val result = EmailRedactor.stripHtml(html)
        assertThat(result).doesNotContain("<html>")
        assertThat(result).doesNotContain("<script>")
        assertThat(result).doesNotContain("alert")
        assertThat(result).contains("Hello")
    }

    @Test
    fun `stripHtml decodes entities`() {
        val html = "Food &amp; Drinks &lt;3"
        val result = EmailRedactor.stripHtml(html)
        assertThat(result).contains("Food & Drinks")
        assertThat(result).contains("<3")
    }

    @Test
    fun `stripHtml normalizes whitespace`() {
        val html = "<p>Line1</p><p>Line2</p>"
        val result = EmailRedactor.stripHtml(html)
        assertThat(result).contains("Line1")
        assertThat(result).contains("Line2")
    }
}
