package com.krtky.financetracker.ui.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity / account / tab CSV export format.
 *
 * Readable in Excel and round-trippable via Settings → Backup restore.
 * UTF-8 with BOM so Excel on Windows does not mojibake notes (e.g. `·`).
 */
object ActivityCsvFormat {
    val HEADERS: List<String> = listOf(
        "Date",
        "Time",
        "Type",
        "Amount (INR)",
        "Name",
        "Category",
        "Tab",
        "Account",
        "Cash vs Digital",
        "Note",
        "Place",
        "Source",
        "Kind",
        "Split Group ID",
        "Transfer Group ID",
        "Transaction ID",
    )
}

/**
 * Build a CSV of the **currently filtered** [transactions] and save it to the
 * system Downloads folder (not a share sheet).
 *
 * @return absolute/display path message on success
 */
fun downloadTransactionsCsv(
    context: Context,
    transactions: List<Transaction>,
    fileNamePrefix: String = "transactions",
): Result<String> {
    if (transactions.isEmpty()) {
        Toast.makeText(context, "Nothing to export with current filters", Toast.LENGTH_SHORT).show()
        return Result.failure(IllegalStateException("No transactions"))
    }

    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val safePrefix = fileNamePrefix.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
    val fileName = "${safePrefix}_$stamp.csv"
    val csv = buildTransactionsCsv(transactions)
    // BOM helps Excel pick UTF-8 instead of a Windows ANSI code page.
    val bytes = ("\uFEFF$csv").toByteArray(Charsets.UTF_8)

    return runCatching {
        val savedAs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToMediaStoreDownloads(context, fileName, bytes)
        } else {
            writeToLegacyDownloads(fileName, bytes)
        }
        val msg = "Saved ${transactions.size} rows → Downloads/$savedAs"
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        savedAs
    }.onFailure {
        Toast.makeText(
            context,
            "Download failed: ${it.message ?: "unknown error"}",
            Toast.LENGTH_LONG,
        ).show()
    }
}

/** Pure CSV body (no BOM). Prefer [downloadTransactionsCsv] for saving. */
fun buildTransactionsCsv(transactions: List<Transaction>): String {
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val tf = SimpleDateFormat("HH:mm:ss", Locale.US)
    val rows = transactions.map { t ->
        listOf(
            df.format(Date(t.occurredAt)),
            tf.format(Date(t.occurredAt)),
            t.type.name,
            String.format(Locale.US, "%.2f", t.amountPaise / 100.0),
            t.counterparty.orEmpty().trim(),
            t.categoryName.orEmpty(),
            t.tabName.orEmpty(),
            t.accountName.orEmpty(),
            if (t.isCash) "Cash" else "Digital",
            t.note.orEmpty(),
            t.placeName.orEmpty(),
            t.source.name,
            t.kind.name,
            t.splitGroupId.orEmpty(),
            t.transferGroupId.orEmpty(),
            t.id,
        )
    }
    return buildString {
        appendLine(ActivityCsvFormat.HEADERS.joinToString(",") { csvEscape(it) })
        rows.forEach { row ->
            appendLine(row.joinToString(",") { csvEscape(it) })
        }
    }
}

/** @deprecated Use [downloadTransactionsCsv] */
fun shareTransactionsCsv(
    context: Context,
    transactions: List<Transaction>,
    fileNamePrefix: String = "transactions",
) {
    downloadTransactionsCsv(context, transactions, fileNamePrefix)
}

private fun writeToMediaStoreDownloads(
    context: Context,
    fileName: String,
    bytes: ByteArray,
): String {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, values)
        ?: error("Could not create Downloads entry")
    resolver.openOutputStream(uri)?.use { it.write(bytes) }
        ?: error("Could not write CSV")
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return fileName
}

@Suppress("DEPRECATION")
private fun writeToLegacyDownloads(fileName: String, bytes: ByteArray): String {
    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, fileName)
    FileOutputStream(file).use { it.write(bytes) }
    return fileName
}

internal fun csvEscape(value: String): String {
    val needsQuotes = value.contains(',') || value.contains('"') ||
        value.contains('\n') || value.contains('\r')
    val escaped = value.replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}

fun csvSummaryLine(transactions: List<Transaction>): String {
    val income = transactions.filter { it.type == TransactionType.CREDIT }.sumOf { it.amountPaise }
    val expense = transactions.filter { it.type == TransactionType.DEBIT }.sumOf { it.amountPaise }
    return "${transactions.size} txns · income ${income.inr()} · expense ${expense.inr()}"
}
