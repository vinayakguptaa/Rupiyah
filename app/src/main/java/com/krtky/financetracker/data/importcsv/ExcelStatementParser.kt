package com.krtky.financetracker.data.importcsv

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ExcelStatementParser {

    private val OLE_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
    )

    fun isExcel(name: String?, mime: String?): Boolean {
        val n = name?.lowercase().orEmpty()
        val m = mime?.lowercase().orEmpty()
        return n.endsWith(".xlsx") || n.endsWith(".xls") ||
                m.contains("spreadsheetml") || m.contains("excel")
    }

    fun parse(inputStream: InputStream, password: String? = null): CsvParseResult {
        val bytes = inputStream.readBytes()
        if (bytes.size < 8) {
            return CsvParseResult(emptyList(), emptyList(), "Excel", listOf("File is empty or corrupted"))
        }

        val isOle = bytes.take(8).toByteArray().contentEquals(OLE_MAGIC)
        val zipStream = if (isOle) {
            if (password.isNullOrBlank()) {
                throw StatementEncryptedException(
                    message = "This Excel statement is password-protected by your bank. Please enter password.",
                    isRetry = false
                )
            }
            try {
                OfficeAgileDecryptor.decrypt(bytes, password)
            } catch (e: StatementEncryptedException) {
                throw e
            } catch (e: Exception) {
                throw StatementEncryptedException(
                    message = "Incorrect password for this Excel statement.",
                    isRetry = true
                )
            }
        } else {
            bytes.inputStream()
        }

        return parseXlsxZip(zipStream)
    }

    private fun parseXlsxZip(inputStream: InputStream): CsvParseResult {
        val sharedStrings = mutableListOf<String>()
        var sheetBytes: ByteArray? = null

        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    sharedStrings.addAll(parseSharedStrings(zis.readBytes()))
                } else if (name == "xl/worksheets/sheet1.xml" || (sheetBytes == null && name.startsWith("xl/worksheets/sheet"))) {
                    sheetBytes = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (sheetBytes == null) {
            return CsvParseResult(emptyList(), emptyList(), "Excel", listOf("No worksheet found in Excel file"))
        }

        val rows = parseSheetRows(sheetBytes!!, sharedStrings)
        return CsvStatementParser.parseRows(rows, presetNameOverride = "Excel statement")
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xmlBytes.inputStream(), "UTF-8")

        var eventType = parser.eventType
        var currentText = StringBuilder()
        var insideT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") {
                        insideT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideT) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") {
                        insideT = false
                    } else if (parser.name == "si") {
                        strings.add(currentText.toString())
                        currentText = StringBuilder()
                    }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    private fun parseSheetRows(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val tableRows = mutableListOf<MutableList<String>>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xmlBytes.inputStream(), "UTF-8")

        var eventType = parser.eventType
        var currentRow = mutableListOf<String>()
        var cellType: String? = null
        var cellValue = StringBuilder()
        var insideV = false
        var insideT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRow = mutableListOf()
                        }
                        "c" -> {
                            cellType = parser.getAttributeValue(null, "t")
                            val r = parser.getAttributeValue(null, "r")
                            val targetCol = colRefToIndex(r)
                            while (currentRow.size < targetCol) {
                                currentRow.add("")
                            }
                            cellValue = StringBuilder()
                        }
                        "v" -> insideV = true
                        "t" -> insideT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideV || insideT) {
                        cellValue.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> insideV = false
                        "t" -> insideT = false
                        "c" -> {
                            val raw = cellValue.toString().trim()
                            val finalVal = if (cellType == "s") {
                                val idx = raw.toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                            } else {
                                raw
                            }
                            currentRow.add(finalVal)
                        }
                        "row" -> {
                            if (currentRow.any { it.isNotBlank() }) {
                                tableRows.add(currentRow)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return tableRows
    }

    private fun colRefToIndex(cellRef: String?): Int {
        if (cellRef.isNullOrBlank()) return 0
        var col = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else {
                break
            }
        }
        return if (col > 0) col - 1 else 0
    }
}
