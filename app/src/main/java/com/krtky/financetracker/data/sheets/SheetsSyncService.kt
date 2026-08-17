package com.krtky.financetracker.data.sheets

import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.domain.model.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-way Room → Google Sheets sync via Sheets API v4.
 *
 * Workbook tabs:
 * - Transactions (raw rows A–T)
 * - Dashboard (KPIs + charts)
 * - Monthly / Categories / Accounts / Funds / Merchants (QUERY analytics)
 */
@Singleton
class SheetsSyncService @Inject constructor(
    private val db: AppDatabase,
    private val secureStore: SecureStore,
    private val userPreferences: UserPreferences,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val tf = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val monthFmt = SimpleDateFormat("yyyy-MM", Locale.US)

    private val txnHeaders = listOf(
        "Transaction ID", "Date", "Time", "Type", "Amount", "Merchant", "Category",
        "Subcategory", "Tab", "Payment Method", "Cash vs Digital", "Note",
        "Latitude", "Longitude", "Place", "Source", "SMS Ref", "Deleted",
        "Updated At", "Month",
    )

    private val analyticsTabs = listOf(
        "Dashboard", "Monthly", "Categories", "Accounts", "Funds", "Merchants",
    )

    fun isConfigured(): Boolean =
        !secureStore.sheetsSpreadsheetId.isNullOrBlank() &&
            !secureStore.sheetsAccessToken.isNullOrBlank()

    suspend fun createSpreadsheet(title: String): Result<String> = withContext(Dispatchers.IO) {
        val token = secureStore.sheetsAccessToken
            ?: return@withContext Result.failure(IllegalStateException("Google OAuth access token not saved"))
        val safeTitle = title.ifBlank { "Rupiyah" }
        val body = buildJsonObject {
            put("properties", buildJsonObject {
                put("title", safeTitle)
                put("locale", "en_IN")
                put("timeZone", "Asia/Kolkata")
            })
            put("sheets", buildJsonArray {
                add(sheetProps("Transactions", 0))
                analyticsTabs.forEachIndexed { i, name ->
                    add(sheetProps(name, i + 1))
                }
            })
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException(text.ifBlank { "Create spreadsheet failed" }),
                )
            }
            val spreadsheetId = runCatching {
                json.parseToJsonElement(text).jsonObject["spreadsheetId"]?.jsonPrimitive?.content
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(
                    IllegalStateException("Google did not return a spreadsheet id"),
                )
            secureStore.sheetsSpreadsheetId = spreadsheetId
            setupWorkbook(spreadsheetId, token, forceCharts = true)
            Result.success(spreadsheetId)
        }
    }

    suspend fun sync(): Result<Int> = withContext(Dispatchers.IO) {
        if (!userPreferences.sheetsSyncEnabled.first()) return@withContext Result.success(0)
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Sheets not configured"))
        }
        val sheetId = secureStore.sheetsSpreadsheetId!!
        val token = secureStore.sheetsAccessToken!!
        val dirty = db.transactionDao().getDirtyIncludingDeleted()
        if (dirty.isEmpty()) {
            // Still refresh analytics formulas / structure occasionally
            setupWorkbook(sheetId, token, forceCharts = false)
            return@withContext Result.success(0)
        }

        setupWorkbook(sheetId, token, forceCharts = false)
        val existingRows = transactionRows(sheetId, token)
        val accountNames = db.accountDao().observeAll().first().associate { it.id to it.name }

        var count = 0
        for (txn in dirty) {
            val rowNumber = existingRows[txn.id]
            if (txn.deletedAt != null) {
                if (rowNumber != null && deleteRow(sheetId, token, rowNumber)) {
                    db.transactionDao().markSynced(txn.id)
                    count++
                }
                continue
            }
            val cats = db.categoryDao().getById(txn.categoryId ?: -1)
            val tab = db.tabDao().getById(txn.tabId ?: -1)
            val row = listOf(
                txn.id,
                df.format(Date(txn.occurredAt)),
                tf.format(Date(txn.occurredAt)),
                txn.type,
                Money(txn.amountPaise).toRupees().toString(),
                txn.counterparty.orEmpty(),
                cats?.name.orEmpty(),
                "",
                tab?.name.orEmpty(),
                txn.accountId?.let { accountNames[it] }.orEmpty(),
                if (txn.isCash) "Cash" else "Digital",
                txn.note.orEmpty(),
                txn.latitude?.toString().orEmpty(),
                txn.longitude?.toString().orEmpty(),
                txn.placeName.orEmpty(),
                txn.source,
                txn.smsMessageId.orEmpty(),
                "FALSE",
                txn.updatedAt.toString(),
                monthFmt.format(Date(txn.occurredAt)),
            )
            val ok = upsertRow(sheetId, token, rowNumber, row)
            if (ok) {
                db.transactionDao().markSynced(txn.id)
                count++
            }
        }
        Result.success(count)
    }

    private fun sheetProps(title: String, index: Int) = buildJsonObject {
        put("properties", buildJsonObject {
            put("title", title)
            put("index", index)
        })
    }

    private fun setupWorkbook(spreadsheetId: String, token: String, forceCharts: Boolean) {
        ensureTabs(spreadsheetId, token)
        ensureHeader(spreadsheetId, token)
        writeAnalytics(spreadsheetId, token)
        // Always refresh dashboard/analytics styling so formula layout stays current
        formatAnalytics(spreadsheetId, token)
        if (forceCharts || !hasAnyChart(spreadsheetId, token)) {
            formatTransactions(spreadsheetId, token)
            addCharts(spreadsheetId, token)
        }
    }

    private fun ensureTabs(spreadsheetId: String, token: String) {
        val existing = sheetIds(spreadsheetId, token)
        val missing = (listOf("Transactions") + analyticsTabs).filter { it !in existing }
        if (missing.isEmpty()) return
        val body = buildJsonObject {
            put("requests", buildJsonArray {
                missing.forEach { title ->
                    add(buildJsonObject {
                        put("addSheet", buildJsonObject {
                            put("properties", buildJsonObject { put("title", title) })
                        })
                    })
                }
            })
        }.toString().toRequestBody("application/json".toMediaType())
        batchUpdate(spreadsheetId, token, body)
    }

    private fun ensureHeader(spreadsheetId: String, token: String) {
        val range = "Transactions!A1:T1"
        val body = buildJsonObject {
            put("range", range)
            put("majorDimension", "ROWS")
            put("values", JsonArray(listOf(JsonArray(txnHeaders.map { JsonPrimitive(it) }))))
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(
                "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/" +
                    "$range?valueInputOption=RAW",
            )
            .addHeader("Authorization", "Bearer $token")
            .put(body)
            .build()
        client.newCall(req).execute().close()
    }

    private fun formatTransactions(spreadsheetId: String, token: String) {
        val sid = sheetIds(spreadsheetId, token)["Transactions"] ?: return
        val navy = rgb(0.09, 0.16, 0.32)
        val white = rgb(1.0, 1.0, 1.0)
        val soft = rgb(0.93, 0.95, 0.98)
        val body = buildJsonObject {
            put("requests", buildJsonArray {
                // Freeze header
                add(buildJsonObject {
                    put("updateSheetProperties", buildJsonObject {
                        put("properties", buildJsonObject {
                            put("sheetId", sid)
                            put("gridProperties", buildJsonObject {
                                put("frozenRowCount", 1)
                                put("frozenColumnCount", 1)
                            })
                        })
                        put("fields", "gridProperties.frozenRowCount,gridProperties.frozenColumnCount")
                    })
                })
                // Header style
                add(buildJsonObject {
                    put("repeatCell", buildJsonObject {
                        put("range", buildJsonObject {
                            put("sheetId", sid)
                            put("startRowIndex", 0)
                            put("endRowIndex", 1)
                            put("startColumnIndex", 0)
                            put("endColumnIndex", 20)
                        })
                        put("cell", buildJsonObject {
                            put("userEnteredFormat", buildJsonObject {
                                put("backgroundColor", navy)
                                put("horizontalAlignment", "CENTER")
                                put("verticalAlignment", "MIDDLE")
                                put("textFormat", buildJsonObject {
                                    put("foregroundColor", white)
                                    put("fontSize", 11)
                                    put("bold", true)
                                    put("fontFamily", "Google Sans")
                                })
                            })
                        })
                        put("fields", "userEnteredFormat(backgroundColor,textFormat,horizontalAlignment,verticalAlignment)")
                    })
                })
                // Amount column currency format
                add(buildJsonObject {
                    put("repeatCell", buildJsonObject {
                        put("range", buildJsonObject {
                            put("sheetId", sid)
                            put("startRowIndex", 1)
                            put("startColumnIndex", 4)
                            put("endColumnIndex", 5)
                        })
                        put("cell", buildJsonObject {
                            put("userEnteredFormat", buildJsonObject {
                                put("numberFormat", buildJsonObject {
                                    put("type", "CURRENCY")
                                    put("pattern", "₹#,##0.00")
                                })
                            })
                        })
                        put("fields", "userEnteredFormat.numberFormat")
                    })
                })
                // Date format
                add(buildJsonObject {
                    put("repeatCell", buildJsonObject {
                        put("range", buildJsonObject {
                            put("sheetId", sid)
                            put("startRowIndex", 1)
                            put("startColumnIndex", 1)
                            put("endColumnIndex", 2)
                        })
                        put("cell", buildJsonObject {
                            put("userEnteredFormat", buildJsonObject {
                                put("numberFormat", buildJsonObject {
                                    put("type", "DATE")
                                    put("pattern", "yyyy-mm-dd")
                                })
                            })
                        })
                        put("fields", "userEnteredFormat.numberFormat")
                    })
                })
                // Column widths (key columns)
                val widths = listOf(
                    0 to 140, // ID
                    1 to 100, // Date
                    2 to 80,  // Time
                    3 to 90,  // Type
                    4 to 100, // Amount
                    5 to 160, // Merchant
                    6 to 120, // Category
                    8 to 120, // Tab
                    9 to 120, // Payment
                    10 to 110, // Cash/Digital
                    11 to 180, // Note
                    19 to 90, // Month
                )
                widths.forEach { (col, px) ->
                    add(buildJsonObject {
                        put("updateDimensionProperties", buildJsonObject {
                            put("range", buildJsonObject {
                                put("sheetId", sid)
                                put("dimension", "COLUMNS")
                                put("startIndex", col)
                                put("endIndex", col + 1)
                            })
                            put("properties", buildJsonObject { put("pixelSize", px) })
                            put("fields", "pixelSize")
                        })
                    })
                }
                // Auto filter
                add(buildJsonObject {
                    put("setBasicFilter", buildJsonObject {
                        put("filter", buildJsonObject {
                            put("range", buildJsonObject {
                                put("sheetId", sid)
                                put("startRowIndex", 0)
                                put("startColumnIndex", 0)
                                put("endColumnIndex", 20)
                            })
                        })
                    })
                })
                // Soft banded look for header row height
                add(buildJsonObject {
                    put("updateDimensionProperties", buildJsonObject {
                        put("range", buildJsonObject {
                            put("sheetId", sid)
                            put("dimension", "ROWS")
                            put("startIndex", 0)
                            put("endIndex", 1)
                        })
                        put("properties", buildJsonObject { put("pixelSize", 36) })
                        put("fields", "pixelSize")
                    })
                })
                // Light fill on type column area hint (unused soft color kept for future)
                add(buildJsonObject {
                    put("repeatCell", buildJsonObject {
                        put("range", buildJsonObject {
                            put("sheetId", sid)
                            put("startRowIndex", 1)
                            put("startColumnIndex", 3)
                            put("endColumnIndex", 4)
                        })
                        put("cell", buildJsonObject {
                            put("userEnteredFormat", buildJsonObject {
                                put("backgroundColor", soft)
                            })
                        })
                        put("fields", "userEnteredFormat.backgroundColor")
                    })
                })
            })
        }.toString().toRequestBody("application/json".toMediaType())
        batchUpdate(spreadsheetId, token, body)
    }

    private fun writeAnalytics(spreadsheetId: String, token: String) {
        // Named ranges for readability in formulas
        val mStart = "TEXT(EOMONTH(TODAY(),-1)+1,\"yyyy-mm-dd\")"
        val mEnd = "TEXT(EOMONTH(TODAY(),0)+1,\"yyyy-mm-dd\")"
        // Types are DEBIT/CREDIT after cashflow migration (legacy INCOME/EXPENSE also matched).
        val incM = "=SUMIFS(Transactions!E:E,Transactions!D:D,\"CREDIT\",Transactions!B:B,\">=\"&$mStart,Transactions!B:B,\"<\"&$mEnd)+SUMIFS(Transactions!E:E,Transactions!D:D,\"INCOME\",Transactions!B:B,\">=\"&$mStart,Transactions!B:B,\"<\"&$mEnd)"
        val expM = "=SUMIFS(Transactions!E:E,Transactions!D:D,\"DEBIT\",Transactions!B:B,\">=\"&$mStart,Transactions!B:B,\"<\"&$mEnd)+SUMIFS(Transactions!E:E,Transactions!D:D,\"EXPENSE\",Transactions!B:B,\">=\"&$mStart,Transactions!B:B,\"<\"&$mEnd)"
        val cntM = "=COUNTIFS(Transactions!A:A,\"<>\",Transactions!B:B,\">=\"&$mStart,Transactions!B:B,\"<\"&$mEnd)"
        val cashM = "=SUMIFS(Transactions!E:E,Transactions!D:D,\"DEBIT\",Transactions!K:K,\"Cash\",Transactions!B:B,\">=\"&$mStart,Transactions!B:B,\"<\"&$mEnd)+SUMIFS(Transactions!E:E,Transactions!D:D,\"EXPENSE\",Transactions!K:K,\"Cash\",Transactions!B:B,\">=\"&$mStart,Transactions!B:B,\"<\"&$mEnd)"
        val digM = "=SUMIFS(Transactions!E:E,Transactions!D:D,\"DEBIT\",Transactions!K:K,\"Digital\",Transactions!B:B,\">=\"&$mStart,Transactions!B:B,\"<\"&$mEnd)+SUMIFS(Transactions!E:E,Transactions!D:D,\"EXPENSE\",Transactions!K:K,\"Digital\",Transactions!B:B,\">=\"&$mStart,Transactions!B:B,\"<\"&$mEnd)"

        val dash = listOf(
            listOf("Rupiyah", "Personal finance dashboard", "", "", "", "", "", ""),
            listOf("=TEXT(TODAY(),\"dddd, dd mmmm yyyy\")", "Synced from the app · formulas auto-update", "", "", "", "", "", ""),
            listOf("", "", "", "", "", "", "", ""),
            listOf("THIS MONTH", "=\"· \"&TEXT(EOMONTH(TODAY(),-1)+1,\"mmm yyyy\")", "", "ALL TIME", "", "", "QUICK RATIOS", ""),
            listOf("Income", incM, "", "Income", "=SUMIF(Transactions!D:D,\"CREDIT\",Transactions!E:E)+SUMIF(Transactions!D:D,\"INCOME\",Transactions!E:E)", "", "Savings rate (month)", "=IF(B5=0,\"—\",B7/B5)"),
            listOf("Expense", expM, "", "Expense", "=SUMIF(Transactions!D:D,\"DEBIT\",Transactions!E:E)+SUMIF(Transactions!D:D,\"EXPENSE\",Transactions!E:E)", "", "Expense / income", "=IF(B5=0,\"—\",B6/B5)"),
            listOf("Net", "=B5-B6", "", "Net", "=E5-E6", "", "Daily avg spend", "=IF(DAY(TODAY())=0,0,B6/DAY(TODAY()))"),
            listOf("Transactions", cntM, "", "Transactions", "=COUNTA(Transactions!A2:A)", "", "Avg txn (expense)", "=IFERROR(B6/MAX(1,COUNTIFS(Transactions!D:D,\"DEBIT\",Transactions!B:B,\">=\"&$mStart,Transactions!B:B,\"<\"&$mEnd)+COUNTIFS(Transactions!D:D,\"EXPENSE\",Transactions!B:B,\">=\"&$mStart,Transactions!B:B,\"<\"&$mEnd)),0)"),
            listOf("", "", "", "", "", "", "", ""),
            listOf("PAYMENT MIX (this month)", "", "", "HIGHLIGHTS", "", "", "", ""),
            listOf("Cash spend", cashM, "", "Top category", "=IFERROR(INDEX(Categories!A:A,2),\"—\")", "", "Top category ₹", "=IFERROR(INDEX(Categories!B:B,2),0)"),
            listOf("Digital spend", digM, "", "Top account", "=IFERROR(INDEX(Accounts!A:A,2),\"—\")", "", "Top account ₹", "=IFERROR(INDEX(Accounts!B:B,2),0)"),
            listOf("Cash share", "=IF((B11+B12)=0,\"—\",B11/(B11+B12))", "", "Top merchant", "=IFERROR(INDEX(Merchants!A:A,2),\"—\")", "", "Top merchant ₹", "=IFERROR(INDEX(Merchants!B:B,2),0)"),
            listOf("", "", "", "", "", "", "", ""),
            listOf("LAST 6 MONTHS (expense)", "Amount", "", "TOP CATEGORIES", "Amount", "", "BY ACCOUNT", "Amount"),
            listOf(
                "=IFERROR(QUERY(Transactions!A:T,\"select T, sum(E) where D='DEBIT' and T is not null and T<>'' group by T order by T desc limit 6 label T 'Month', sum(E) 'Amount'\",1),\"\")",
                "",
                "",
                "=IFERROR(QUERY(Transactions!A:T,\"select G, sum(E) where D='DEBIT' and G is not null and G<>'' group by G order by sum(E) desc limit 8 label G 'Category', sum(E) 'Amount'\",1),\"\")",
                "",
                "",
                "=IFERROR(QUERY(Transactions!A:T,\"select J, sum(E) where D='DEBIT' and J is not null and J<>'' group by J order by sum(E) desc limit 8 label J 'Account', sum(E) 'Amount'\",1),\"\")",
                "",
            ),
            listOf("", "", "", "", "", "", "", ""),
            listOf("Charts are placed below · data also lives on Monthly / Categories / Accounts tabs.", "", "", "", "", "", "", ""),
            listOf("Tip: use filter arrows on the Transactions sheet header; Dashboard updates automatically.", "", "", "", "", "", "", ""),
        )
        putValues(spreadsheetId, token, "Dashboard!A1:H19", dash)

        // Monthly: expense series (A–B) for column chart + income series (D–E)
        putValues(
            spreadsheetId, token, "Monthly!A1:D2",
            listOf(
                listOf("Month", "Expense", "Month", "Income"),
                listOf(
                    "=IFERROR(QUERY(Transactions!A:T,\"select T, sum(E) where D='DEBIT' and T is not null and T<>'' group by T order by T label T 'Month', sum(E) 'Expense'\",1),\"\")",
                    "",
                    "=IFERROR(QUERY(Transactions!A:T,\"select T, sum(E) where D='CREDIT' and T is not null and T<>'' group by T order by T label T 'Month', sum(E) 'Income'\",1),\"\")",
                    "",
                ),
            ),
        )

        putValues(
            spreadsheetId, token, "Categories!A1:B2",
            listOf(
                listOf("Category", "Expense total"),
                listOf(
                    "=IFERROR(QUERY(Transactions!A:T,\"select G, sum(E) where D='DEBIT' and G is not null and G<>'' group by G order by sum(E) desc label G 'Category', sum(E) 'Expense total'\",1),\"No data\")",
                    "",
                ),
            ),
        )

        putValues(
            spreadsheetId, token, "Accounts!A1:B2",
            listOf(
                listOf("Payment method", "Expense total"),
                listOf(
                    "=IFERROR(QUERY(Transactions!A:T,\"select J, sum(E) where D='DEBIT' and J is not null and J<>'' group by J order by sum(E) desc label J 'Payment method', sum(E) 'Expense total'\",1),\"No data\")",
                    "",
                ),
            ),
        )

        putValues(
            spreadsheetId, token, "Funds!A1:B2",
            listOf(
                listOf("Tab", "Net (income − expense)"),
                listOf(
                    "=IFERROR(QUERY(Transactions!A:T,\"select I, sum(E) where I is not null and I<>'' group by I pivot D order by I\",1),\"No data\")",
                    "",
                ),
            ),
        )

        putValues(
            spreadsheetId, token, "Merchants!A1:B2",
            listOf(
                listOf("Merchant", "Expense total"),
                listOf(
                    "=IFERROR(QUERY(Transactions!A:T,\"select F, sum(E) where D='DEBIT' and F is not null and F<>'' group by F order by sum(E) desc limit 30 label F 'Merchant', sum(E) 'Expense total'\",1),\"No data\")",
                    "",
                ),
            ),
        )
    }

    private fun formatAnalytics(spreadsheetId: String, token: String) {
        val ids = sheetIds(spreadsheetId, token)
        val navy = rgb(0.09, 0.16, 0.32)
        val white = rgb(1.0, 1.0, 1.0)
        val teal = rgb(0.10, 0.45, 0.42)
        val lightTeal = rgb(0.88, 0.95, 0.94)
        val body = buildJsonObject {
            put("requests", buildJsonArray {
                ids["Dashboard"]?.let { sid ->
                    // Title banner
                    add(headerBand(sid, 0, 1, 0, 8, navy, white, 20))
                    // Section headers
                    add(headerBand(sid, 3, 4, 0, 2, teal, white, 11))
                    add(headerBand(sid, 3, 4, 3, 5, teal, white, 11))
                    add(headerBand(sid, 3, 4, 6, 8, teal, white, 11))
                    add(headerBand(sid, 9, 10, 0, 2, teal, white, 11))
                    add(headerBand(sid, 9, 10, 3, 8, teal, white, 11))
                    add(headerBand(sid, 14, 15, 0, 2, navy, white, 11))
                    add(headerBand(sid, 14, 15, 3, 5, navy, white, 11))
                    add(headerBand(sid, 14, 15, 6, 8, navy, white, 11))
                    // Currency KPI values
                    add(currencyRange(sid, 4, 8, 1, 2))
                    add(currencyRange(sid, 4, 8, 4, 5))
                    add(currencyRange(sid, 6, 8, 7, 8))
                    add(currencyRange(sid, 10, 12, 1, 2))
                    add(currencyRange(sid, 10, 13, 7, 8))
                    add(currencyRange(sid, 15, 30, 1, 2))
                    add(currencyRange(sid, 15, 30, 4, 5))
                    add(currencyRange(sid, 15, 30, 7, 8))
                    // Percent formats for ratios
                    add(buildJsonObject {
                        put("repeatCell", buildJsonObject {
                            put("range", buildJsonObject {
                                put("sheetId", sid)
                                put("startRowIndex", 4)
                                put("endRowIndex", 6)
                                put("startColumnIndex", 7)
                                put("endColumnIndex", 8)
                            })
                            put("cell", buildJsonObject {
                                put("userEnteredFormat", buildJsonObject {
                                    put("numberFormat", buildJsonObject {
                                        put("type", "PERCENT")
                                        put("pattern", "0.0%")
                                    })
                                })
                            })
                            put("fields", "userEnteredFormat.numberFormat")
                        })
                    })
                    add(buildJsonObject {
                        put("repeatCell", buildJsonObject {
                            put("range", buildJsonObject {
                                put("sheetId", sid)
                                put("startRowIndex", 12)
                                put("endRowIndex", 13)
                                put("startColumnIndex", 1)
                                put("endColumnIndex", 2)
                            })
                            put("cell", buildJsonObject {
                                put("userEnteredFormat", buildJsonObject {
                                    put("numberFormat", buildJsonObject {
                                        put("type", "PERCENT")
                                        put("pattern", "0.0%")
                                    })
                                })
                            })
                            put("fields", "userEnteredFormat.numberFormat")
                        })
                    })
                    // Column widths
                    listOf(0 to 160, 1 to 130, 2 to 24, 3 to 130, 4 to 130, 5 to 24, 6 to 150, 7 to 130)
                        .forEach { (col, px) ->
                            add(buildJsonObject {
                                put("updateDimensionProperties", buildJsonObject {
                                    put("range", buildJsonObject {
                                        put("sheetId", sid)
                                        put("dimension", "COLUMNS")
                                        put("startIndex", col)
                                        put("endIndex", col + 1)
                                    })
                                    put("properties", buildJsonObject { put("pixelSize", px) })
                                    put("fields", "pixelSize")
                                })
                            })
                        }
                    add(buildJsonObject {
                        put("updateDimensionProperties", buildJsonObject {
                            put("range", buildJsonObject {
                                put("sheetId", sid)
                                put("dimension", "ROWS")
                                put("startIndex", 0)
                                put("endIndex", 1)
                            })
                            put("properties", buildJsonObject { put("pixelSize", 40) })
                            put("fields", "pixelSize")
                        })
                    })
                }
                listOf("Monthly", "Categories", "Accounts", "Funds", "Merchants").forEach { name ->
                    val sid = ids[name] ?: return@forEach
                    add(headerBand(sid, 0, 1, 0, 4, navy, white, 11))
                    add(currencyRange(sid, 1, 200, 1, 2))
                    if (name == "Monthly") add(currencyRange(sid, 1, 200, 3, 4))
                    add(buildJsonObject {
                        put("updateSheetProperties", buildJsonObject {
                            put("properties", buildJsonObject {
                                put("sheetId", sid)
                                put("gridProperties", buildJsonObject { put("frozenRowCount", 1) })
                            })
                            put("fields", "gridProperties.frozenRowCount")
                        })
                    })
                    add(buildJsonObject {
                        put("updateDimensionProperties", buildJsonObject {
                            put("range", buildJsonObject {
                                put("sheetId", sid)
                                put("dimension", "COLUMNS")
                                put("startIndex", 0)
                                put("endIndex", 1)
                            })
                            put("properties", buildJsonObject { put("pixelSize", 180) })
                            put("fields", "pixelSize")
                        })
                    })
                    // Soft header accent row under title
                    add(buildJsonObject {
                        put("repeatCell", buildJsonObject {
                            put("range", buildJsonObject {
                                put("sheetId", sid)
                                put("startRowIndex", 1)
                                put("endRowIndex", 2)
                                put("startColumnIndex", 0)
                                put("endColumnIndex", 4)
                            })
                            put("cell", buildJsonObject {
                                put("userEnteredFormat", buildJsonObject {
                                    put("backgroundColor", lightTeal)
                                })
                            })
                            put("fields", "userEnteredFormat.backgroundColor")
                        })
                    })
                }
            })
        }.toString().toRequestBody("application/json".toMediaType())
        batchUpdate(spreadsheetId, token, body)
    }

    private fun headerBand(
        sheetId: Int,
        startRow: Int,
        endRow: Int,
        startCol: Int,
        endCol: Int,
        bg: JsonObject,
        fg: JsonObject,
        fontSize: Int,
    ) = buildJsonObject {
        put("repeatCell", buildJsonObject {
            put("range", buildJsonObject {
                put("sheetId", sheetId)
                put("startRowIndex", startRow)
                put("endRowIndex", endRow)
                put("startColumnIndex", startCol)
                put("endColumnIndex", endCol)
            })
            put("cell", buildJsonObject {
                put("userEnteredFormat", buildJsonObject {
                    put("backgroundColor", bg)
                    put("textFormat", buildJsonObject {
                        put("foregroundColor", fg)
                        put("fontSize", fontSize)
                        put("bold", true)
                        put("fontFamily", "Google Sans")
                    })
                })
            })
            put("fields", "userEnteredFormat(backgroundColor,textFormat)")
        })
    }

    private fun currencyRange(
        sheetId: Int,
        startRow: Int,
        endRow: Int,
        startCol: Int,
        endCol: Int,
    ) = buildJsonObject {
        put("repeatCell", buildJsonObject {
            put("range", buildJsonObject {
                put("sheetId", sheetId)
                put("startRowIndex", startRow)
                put("endRowIndex", endRow)
                put("startColumnIndex", startCol)
                put("endColumnIndex", endCol)
            })
            put("cell", buildJsonObject {
                put("userEnteredFormat", buildJsonObject {
                    put("numberFormat", buildJsonObject {
                        put("type", "CURRENCY")
                        put("pattern", "₹#,##0.00")
                    })
                })
            })
            put("fields", "userEnteredFormat.numberFormat")
        })
    }

    private fun hasAnyChart(spreadsheetId: String, token: String): Boolean {
        val request = Request.Builder()
            .url(
                "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId" +
                    "?fields=sheets.charts",
            )
            .addHeader("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val sheets = runCatching {
                json.parseToJsonElement(response.body?.string().orEmpty())
                    .jsonObject["sheets"]?.jsonArray
            }.getOrNull() ?: return false
            sheets.any { sheet ->
                (sheet.jsonObject["charts"]?.jsonArray?.size ?: 0) > 0
            }
        }
    }

    private fun addCharts(spreadsheetId: String, token: String) {
        val ids = sheetIds(spreadsheetId, token)
        val dashId = ids["Dashboard"] ?: return
        val catId = ids["Categories"] ?: return
        val monthlyId = ids["Monthly"] ?: return
        val acctId = ids["Accounts"] ?: return

        fun sourceRange(sheetId: Int, r0: Int, r1: Int, c0: Int, c1: Int) = buildJsonObject {
            put("sources", buildJsonArray {
                add(buildJsonObject {
                    put("sheetId", sheetId)
                    put("startRowIndex", r0)
                    put("endRowIndex", r1)
                    put("startColumnIndex", c0)
                    put("endColumnIndex", c1)
                })
            })
        }

        val body = buildJsonObject {
            put("requests", buildJsonArray {
                // Category pie
                add(buildJsonObject {
                    put("addChart", buildJsonObject {
                        put("chart", buildJsonObject {
                            put("spec", buildJsonObject {
                                put("title", "Spending by category")
                                put("pieChart", buildJsonObject {
                                    put("legendPosition", "RIGHT_LEGEND")
                                    put("domain", buildJsonObject {
                                        put("sourceRange", sourceRange(catId, 0, 16, 0, 1))
                                    })
                                    put("series", buildJsonObject {
                                        put("sourceRange", sourceRange(catId, 0, 16, 1, 2))
                                    })
                                })
                            })
                            put("position", buildJsonObject {
                                put("overlayPosition", buildJsonObject {
                                    put("anchorCell", buildJsonObject {
                                        put("sheetId", dashId)
                                        put("rowIndex", 24)
                                        put("columnIndex", 0)
                                    })
                                    put("widthPixels", 460)
                                    put("heightPixels", 300)
                                })
                            })
                        })
                    })
                })
                // Monthly expense column
                add(buildJsonObject {
                    put("addChart", buildJsonObject {
                        put("chart", buildJsonObject {
                            put("spec", buildJsonObject {
                                put("title", "Monthly expenses")
                                put("basicChart", buildJsonObject {
                                    put("chartType", "COLUMN")
                                    put("legendPosition", "NO_LEGEND")
                                    put("axis", buildJsonArray {
                                        add(buildJsonObject {
                                            put("position", "BOTTOM_AXIS")
                                            put("title", "Month")
                                        })
                                        add(buildJsonObject {
                                            put("position", "LEFT_AXIS")
                                            put("title", "Amount (₹)")
                                        })
                                    })
                                    put("domains", buildJsonArray {
                                        add(buildJsonObject {
                                            put("domain", buildJsonObject {
                                                put("sourceRange", sourceRange(monthlyId, 0, 24, 0, 1))
                                            })
                                        })
                                    })
                                    put("series", buildJsonArray {
                                        add(buildJsonObject {
                                            put("series", buildJsonObject {
                                                put("sourceRange", sourceRange(monthlyId, 0, 24, 1, 2))
                                            })
                                            put("targetAxis", "LEFT_AXIS")
                                        })
                                    })
                                    put("headerCount", 1)
                                })
                            })
                            put("position", buildJsonObject {
                                put("overlayPosition", buildJsonObject {
                                    put("anchorCell", buildJsonObject {
                                        put("sheetId", dashId)
                                        put("rowIndex", 24)
                                        put("columnIndex", 5)
                                    })
                                    put("widthPixels", 460)
                                    put("heightPixels", 300)
                                })
                            })
                        })
                    })
                })
                // Accounts bar
                add(buildJsonObject {
                    put("addChart", buildJsonObject {
                        put("chart", buildJsonObject {
                            put("spec", buildJsonObject {
                                put("title", "Spending by account")
                                put("basicChart", buildJsonObject {
                                    put("chartType", "BAR")
                                    put("legendPosition", "NO_LEGEND")
                                    put("domains", buildJsonArray {
                                        add(buildJsonObject {
                                            put("domain", buildJsonObject {
                                                put("sourceRange", sourceRange(acctId, 0, 16, 0, 1))
                                            })
                                        })
                                    })
                                    put("series", buildJsonArray {
                                        add(buildJsonObject {
                                            put("series", buildJsonObject {
                                                put("sourceRange", sourceRange(acctId, 0, 16, 1, 2))
                                            })
                                        })
                                    })
                                    put("headerCount", 1)
                                })
                            })
                            put("position", buildJsonObject {
                                put("overlayPosition", buildJsonObject {
                                    put("anchorCell", buildJsonObject {
                                        put("sheetId", dashId)
                                        put("rowIndex", 40)
                                        put("columnIndex", 0)
                                    })
                                    put("widthPixels", 560)
                                    put("heightPixels", 300)
                                })
                            })
                        })
                    })
                })
            })
        }.toString().toRequestBody("application/json".toMediaType())
        batchUpdate(spreadsheetId, token, body)
    }

    private fun rgb(r: Double, g: Double, b: Double) = buildJsonObject {
        put("red", r)
        put("green", g)
        put("blue", b)
    }

    private fun putValues(
        spreadsheetId: String,
        token: String,
        range: String,
        values: List<List<String>>,
    ) {
        val body = buildJsonObject {
            put("range", range)
            put("majorDimension", "ROWS")
            put(
                "values",
                JsonArray(values.map { row -> JsonArray(row.map { JsonPrimitive(it) }) }),
            )
        }.toString().toRequestBody("application/json".toMediaType())
        val encoded = range.replace(" ", "%20")
        val req = Request.Builder()
            .url(
                "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/" +
                    "$encoded?valueInputOption=USER_ENTERED",
            )
            .addHeader("Authorization", "Bearer $token")
            .put(body)
            .build()
        client.newCall(req).execute().close()
    }

    private fun batchUpdate(spreadsheetId: String, token: String, body: okhttp3.RequestBody) {
        val request = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        client.newCall(request).execute().close()
    }

    private fun transactionRows(spreadsheetId: String, token: String): Map<String, Int> {
        val request = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Transactions!A2:A")
            .addHeader("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyMap()
            val values = runCatching {
                json.parseToJsonElement(response.body?.string().orEmpty())
                    .jsonObject["values"]?.jsonArray
            }.getOrNull() ?: return emptyMap()
            values.mapIndexedNotNull { index, row ->
                row.jsonArray.firstOrNull()?.jsonPrimitive?.content
                    ?.takeIf { it.isNotBlank() }
                    ?.let { it to index + 2 }
            }.toMap()
        }
    }

    private fun upsertRow(
        spreadsheetId: String,
        token: String,
        rowNumber: Int?,
        row: List<String>,
    ): Boolean {
        val url = if (rowNumber == null) {
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/" +
                "Transactions!A:T:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"
        } else {
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/" +
                "Transactions!A$rowNumber:T$rowNumber?valueInputOption=USER_ENTERED"
        }
        val body = buildJsonObject {
            put("values", JsonArray(listOf(JsonArray(row.map { JsonPrimitive(it) }))))
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .method(if (rowNumber == null) "POST" else "PUT", body)
            .build()
        return client.newCall(req).execute().use { it.isSuccessful }
    }

    private fun deleteRow(spreadsheetId: String, token: String, rowNumber: Int): Boolean {
        val transactionsSheetId = sheetIds(spreadsheetId, token)["Transactions"] ?: return false
        val body = buildJsonObject {
            put("requests", buildJsonArray {
                add(buildJsonObject {
                    put("deleteDimension", buildJsonObject {
                        put("range", buildJsonObject {
                            put("sheetId", transactionsSheetId)
                            put("dimension", "ROWS")
                            put("startIndex", rowNumber - 1)
                            put("endIndex", rowNumber)
                        })
                    })
                })
            })
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        return client.newCall(request).execute().use { it.isSuccessful }
    }

    private fun sheetIds(spreadsheetId: String, token: String): Map<String, Int> {
        val request = Request.Builder()
            .url(
                "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId" +
                    "?fields=sheets.properties",
            )
            .addHeader("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyMap()
            val sheets = runCatching {
                json.parseToJsonElement(response.body?.string().orEmpty())
                    .jsonObject["sheets"]?.jsonArray
            }.getOrNull() ?: return emptyMap()
            sheets.mapNotNull { sheet ->
                val properties = sheet.jsonObject["properties"]?.jsonObject ?: return@mapNotNull null
                val title = properties["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val id = properties["sheetId"]?.jsonPrimitive?.int ?: return@mapNotNull null
                title to id
            }.toMap()
        }
    }
}
