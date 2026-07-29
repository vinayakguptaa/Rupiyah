package com.krtky.financetracker.data.email

object EmailRedactor {
    private val account = Regex("""\b\d{9,18}\b""")
    private val card = Regex("""\b(?:\d[ -]*?){13,19}\b""")

    fun redact(body: String): String {
        var t = body
        t = card.replace(t) { "****CARD****" }
        t = account.replace(t) { m ->
            if (m.value.length >= 10) "****ACCT****" else m.value
        }
        t = Regex("""([a-zA-Z0-9._\-]{2,})@([a-zA-Z]{2,})""").replace(t) { m ->
            val user = m.groupValues[1]
            val host = m.groupValues[2]
            if (host in listOf("okaxis", "ybl", "paytm", "ibl", "axl", "oksbi", "okhdfc")) {
                "${user.take(2)}***@$host"
            } else m.value
        }
        return t.take(8000)
    }

    fun stripHtml(html: String): String {
        return html
            .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
            .replace(Regex("(?is)<br\\s*/?>"), "\n")
            .replace(Regex("(?is)</p>"), "\n")
            .replace(Regex("(?is)<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
