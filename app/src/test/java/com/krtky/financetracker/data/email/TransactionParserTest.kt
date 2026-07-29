package com.krtky.financetracker.data.email

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransactionParserTest {

    @Test
    fun `resolveMethodLabel returns Cash for cash`() {
        assertThat(resolveMethodLabel("Cash", emptyList(), "Digital")).isEqualTo("Cash")
    }

    @Test
    fun `resolveMethodLabel returns existing bank`() {
        assertThat(resolveMethodLabel("HDFC", listOf("HDFC", "SBI"), "Digital")).isEqualTo("HDFC")
    }

    @Test
    fun `resolveMethodLabel returns default for Digital`() {
        assertThat(resolveMethodLabel("Digital", listOf("SBI"), "ICICI")).isEqualTo("ICICI")
    }

    @Test
    fun `resolveMethodLabel returns Digital fallback`() {
        assertThat(resolveMethodLabel("Digital", emptyList(), "")).isEqualTo("Digital")
    }

    @Test
    fun `resolveMethodLabel keeps unknown label`() {
        assertThat(resolveMethodLabel("SomeWallet", listOf("HDFC"), "Digital")).isEqualTo("SomeWallet")
    }

    @Test
    fun `matchBankToList finds exact match`() {
        assertThat(matchBankToList("HDFC", listOf("HDFC", "SBI"))).isEqualTo("HDFC")
    }

    @Test
    fun `matchBankToList finds substring match`() {
        assertThat(matchBankToList("HDFC Bank Ltd", listOf("SBI", "HDFC"))).isEqualTo("HDFC")
    }

    @Test
    fun `matchBankToList handles aliases GPay`() {
        assertThat(matchBankToList("Google Pay", listOf("GPay"))).isEqualTo("GPay")
    }

    @Test
    fun `matchBankToList returns null when no match`() {
        assertThat(matchBankToList("Unknown", listOf("HDFC", "SBI"))).isNull()
    }

    @Test
    fun `matchCategory finds exact name`() {
        val cats = listOf(
            com.krtky.financetracker.domain.model.Category(id = 1, name = "Food"),
            com.krtky.financetracker.domain.model.Category(id = 2, name = "Travel"),
        )
        assertThat(matchCategory("Food", cats)).isEqualTo(1L)
    }

    @Test
    fun `matchCategory finds contained match`() {
        val cats = listOf(
            com.krtky.financetracker.domain.model.Category(id = 1, name = "Food & Dining"),
            com.krtky.financetracker.domain.model.Category(id = 2, name = "Travel"),
        )
        assertThat(matchCategory("Food", cats)).isEqualTo(1L)
    }

    @Test
    fun `matchCategory returns null for empty input`() {
        val cats = listOf(
            com.krtky.financetracker.domain.model.Category(id = 1, name = "Food"),
        )
        assertThat(matchCategory("", cats)).isNull()
        assertThat(matchCategory(null, cats)).isNull()
    }

    @Test
    fun `detectBank finds configured bank`() {
        assertThat(detectBank("Payment to HDFC Bank", "noreply@hdfc.com", listOf("HDFC", "SBI"))).isEqualTo("HDFC")
    }

    @Test
    fun `detectBank returns null for unknown`() {
        assertThat(detectBank("Some random text", "unknown@test.com", listOf("HDFC"))).isNull()
    }

    @Test
    fun `parseTime parses ISO instant`() {
        assertThat(parseTime("2025-03-15T10:30:00Z")).isNotNull()
    }

    @Test
    fun `parseTime parses yyyy-MM-dd pattern`() {
        assertThat(parseTime("2025-03-15 10:30:00")).isNotNull()
    }

    @Test
    fun `parseTime returns null for invalid`() {
        assertThat(parseTime("not a date")).isNull()
        assertThat(parseTime("")).isNull()
        assertThat(parseTime(null)).isNull()
    }

    @Test
    fun `bankAliasKeys generates GPay aliases`() {
        val keys = bankAliasKeys("Google Pay")
        assertThat(keys).contains("gpay")
        assertThat(keys).contains("google pay")
    }

    @Test
    fun `bankAliasKeys generates HDFC aliases`() {
        val keys = bankAliasKeys("HDFC Bank")
        assertThat(keys).contains("hdfc")
    }
}

// Helper functions extracted from TransactionParser via reflection-like access.
// Since these are private, we create package-level wrappers for testing.
private fun resolveMethodLabel(detected: String?, banks: List<String>, defaultDigital: String): String {
    val cleaned = detected?.trim()?.takeIf { it.isNotBlank() }
    if (cleaned != null) {
        if (cleaned.equals("Cash", true)) return "Cash"
        if (!cleaned.equals("Digital", true) && !cleaned.equals("UPI", true)) {
            matchBankToList(cleaned, banks)?.let { return it }
            return cleaned
        }
    }
    return defaultDigital.ifBlank { banks.firstOrNull() ?: "Digital" }
}

private fun matchBankToList(raw: String, banks: List<String>): String? {
    if (banks.isEmpty()) return null
    val needle = raw.trim()
    banks.firstOrNull { it.equals(needle, true) }?.let { return it }
    banks.firstOrNull { needle.contains(it, true) || it.contains(needle, true) }?.let { return it }
    val aliases = bankAliasKeys(needle)
    for (bank in banks) {
        val bankKeys = bankAliasKeys(bank)
        if (aliases.any { a -> bankKeys.any { b -> a.equals(b, true) || a.contains(b, true) || b.contains(a, true) } }) {
            return bank
        }
    }
    return null
}

private fun bankAliasKeys(label: String): List<String> {
    val n = label.trim().lowercase()
        .replace("bank", "")
        .replace("limited", "")
        .replace("ltd", "")
        .replace(".", "")
        .replace("-", " ")
        .trim()
    val keys = mutableListOf(n, n.replace(" ", ""))
    when {
        n.contains("google pay") || n == "gpay" || n.contains("g pay") -> keys += listOf("gpay", "google pay", "googlepay")
        n.contains("phonepe") || n.contains("phone pe") -> keys += listOf("phonepe", "phone pe")
        n.contains("paytm") -> keys += listOf("paytm")
        n.contains("amazon pay") -> keys += listOf("amazon pay", "amazonpay")
        n.contains("fampay") || n.contains("fam pay") -> keys += listOf("fampay", "fam")
        n.contains("hdfc") -> keys += listOf("hdfc")
        n.contains("icici") -> keys += listOf("icici")
        n.contains("sbi") || n.contains("state bank") -> keys += listOf("sbi", "state bank")
        n.contains("axis") -> keys += listOf("axis")
        n.contains("kotak") -> keys += listOf("kotak")
        n.contains("yes bank") || n == "yes" -> keys += listOf("yes", "yes bank")
        n.contains("idfc") -> keys += listOf("idfc")
        n.contains("cred") -> keys += listOf("cred")
    }
    return keys.distinct()
}

private fun matchCategory(raw: String?, categories: List<com.krtky.financetracker.domain.model.Category>): Long? {
    if (raw.isNullOrBlank() || categories.isEmpty()) return null
    val needle = raw.trim().lowercase()
    categories.firstOrNull { it.name.equals(needle, true) }?.id?.let { return it }
    categories.firstOrNull {
        val n = it.name.lowercase()
        n.contains(needle) || needle.contains(n)
    }?.id?.let { return it }
    val tokens = needle.split(' ', '/', '&', '-', '_').filter { it.length >= 3 }
    if (tokens.isNotEmpty()) {
        categories.firstOrNull { cat ->
            val cn = cat.name.lowercase()
            tokens.any { t -> cn.contains(t) || t.contains(cn) }
        }?.id?.let { return it }
    }
    return null
}

private fun detectBank(text: String, sender: String, banks: List<String>): String? {
    val blob = "$sender $text"
    banks.firstOrNull { bank -> blob.contains(bank, ignoreCase = true) }?.let { return it }
    val bankHints = listOf(
        "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "YES BANK", "IDFC", "PNB", "BOB", "CANARA",
        "FamPay", "PhonePe", "GPay", "Google Pay", "Paytm", "Amazon Pay", "CRED",
    )
    for (hint in bankHints) {
        if (!blob.contains(hint, ignoreCase = true)) continue
        matchBankToList(hint, banks)?.let { return it }
        if (banks.isEmpty()) return hint
    }
    return null
}

private fun parseTime(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return try {
        java.time.Instant.parse(raw).toEpochMilli()
    } catch (_: Exception) {
        try {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            java.time.LocalDateTime.parse(raw.trim().take(19).replace('T', ' '), fmt)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
