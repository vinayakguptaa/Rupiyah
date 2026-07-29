package com.krtky.financetracker.data.email

/**
 * How the app pulls bank/wallet mail.
 * - [IMAP]: Gmail App Password + JavaMail IMAP (supports IDLE live monitor).
 * - [GMAIL_OAUTH]: Google Sign-In + Gmail API with gmail.readonly (no App Password).
 */
enum class EmailSource {
    IMAP,
    GMAIL_OAUTH,
    ;

    companion object {
        fun fromStored(raw: String?): EmailSource =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: IMAP
    }
}
