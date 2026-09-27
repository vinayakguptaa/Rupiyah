package com.krtky.financetracker.data.importcsv

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

object PdfStatementParser {

    fun parse(
        context: Context,
        inputStream: InputStream,
        password: String? = null
    ): CsvParseResult {
        runCatching { PDFBoxResourceLoader.init(context) }

        val doc: PDDocument = try {
            if (password != null) {
                PDDocument.load(inputStream, password)
            } else {
                PDDocument.load(inputStream)
            }
        } catch (e: InvalidPasswordException) {
            throw StatementEncryptedException(
                message = "Incorrect PDF statement password.",
                isRetry = password != null
            )
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                throw StatementEncryptedException(
                    message = "This PDF statement is password-protected. Please enter password.",
                    isRetry = password != null
                )
            }
            throw e
        }

        return doc.use { document ->
            if (document.isEncrypted && !document.isAllSecurityToBeRemoved) {
                throw StatementEncryptedException(
                    message = "This PDF statement is password-protected. Please enter password.",
                    isRetry = password != null
                )
            }

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }
            val text = stripper.getText(document)

            if (OneCardPdfParser.isOneCard(text)) {
                OneCardPdfParser.parse(text)
            } else {
                parseGenericPdfTable(text)
            }
        }
    }

    internal fun parseGenericPdfTable(text: String): CsvParseResult {
        val lines = text.lines()
        val tableRows = lines.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            // Split columns on 2 or more whitespace characters
            val cells = trimmed.split(Regex("""\s{2,}""")).map { it.trim() }
            if (cells.isEmpty()) null else cells
        }

        return CsvStatementParser.parseRows(tableRows, presetNameOverride = "PDF Table")
    }
}
