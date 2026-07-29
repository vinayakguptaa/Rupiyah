package com.krtky.financetracker.data.repository

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.domain.model.TrustedSender
import io.mockk.mockk
import org.junit.Test

class TrustedSenderRepositoryTest {

    private val repo = TrustedSenderRepository(mockk<AppDatabase>(relaxed = true))

    private val enabledPatterns = listOf(
        TrustedSender(id = 1, emailPattern = "noreply@fampay.in", walletLabel = "FamPay", enabled = true),
        TrustedSender(id = 2, emailPattern = "alerts@bank.com", walletLabel = "Bank", enabled = true),
        TrustedSender(id = 3, emailPattern = "fampay.in", walletLabel = "FamPay", enabled = true),
    )

    private val patternsWithDisabled = enabledPatterns + TrustedSender(
        id = 4, emailPattern = "disabled@old.com", walletLabel = "Old", enabled = false
    )

    @Test
    fun `matches exact email`() {
        val result = repo.matches("noreply@fampay.in", enabledPatterns)
        assertThat(result).isNotNull()
        assertThat(result?.walletLabel).isEqualTo("FamPay")
    }

    @Test
    fun `matches domain-only pattern via endsWith`() {
        val result = repo.matches("hello@fampay.in", enabledPatterns)
        assertThat(result).isNotNull()
        assertThat(result?.walletLabel).isEqualTo("FamPay")
    }

    @Test
    fun `matches via substring containment`() {
        val result = repo.matches("alerts@bank.com.something", enabledPatterns)
        assertThat(result).isNotNull()
    }

    @Test
    fun `does not match disabled sender`() {
        val result = repo.matches("disabled@old.com", patternsWithDisabled)
        assertThat(result).isNull()
    }

    @Test
    fun `returns null for unknown sender`() {
        val result = repo.matches("unknown@gmail.com", enabledPatterns)
        assertThat(result).isNull()
    }

    @Test
    fun `matches noreply-like pattern on known domain`() {
        val result = repo.matches("no-reply@fampay.in", enabledPatterns)
        assertThat(result).isNotNull()
    }

    @Test
    fun `case insensitive matching`() {
        val result = repo.matches("NOREPLY@FAMPAY.IN", enabledPatterns)
        assertThat(result).isNotNull()
    }

    @Test
    fun `does not match when empty pattern`() {
        val patterns = listOf(TrustedSender(id = 5, emailPattern = "", walletLabel = "Empty", enabled = true))
        val result = repo.matches("test@test.com", patterns)
        assertThat(result).isNull()
    }
}
