package com.krtky.financetracker.data.importcsv

/**
 * Thrown when a PDF or Excel statement requires a password to open.
 *
 * @param isRetry true if a password was already attempted and was rejected.
 */
class StatementEncryptedException(
    message: String = "This statement is password-protected. Please enter the password to unlock it.",
    val isRetry: Boolean = false,
) : Exception(message)
