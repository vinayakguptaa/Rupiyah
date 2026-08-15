# Rupiyah — Architecture & Remaining Issues

**Scope:** Current-state architecture of `app/` (Kotlin + Jetpack Compose + Room + Hilt + WorkManager) and the **remaining** debt to close.
**Date:** 2026-08-15 · **DB version:** 11 · minSdk 26 / targetSdk 35 · versionCode 6 / 1.4.1

---

## 1. Executive summary

Rupiyah is a single‑module, offline‑first personal finance tracker (package `com.krtky.financetracker`). It records money in ₹ via **manual entry**, **SMS auto‑import (LLM‑assisted)**, or **CSV bank‑statement import**, then organises it under **accounts**, **categories**, **tabs (open IOUs, "funds")**, and **splits**.

- **Layering is clean and conventional** (UI → ViewModel → Repository → Room/DAO), single Hilt-injected `AppDatabase`.
- **Core cashflow model is sound**: a transaction is the single source of truth; self/tab transfers and split children are ordinary rows linked by `transferGroupId` / `splitGroupId`, and are excluded from lifestyle metrics.
- **The data model is single‑field and current**: one party (`counterparty`), one account (`accountId`), one ingest id (`smsMessageId`); type strings are `DEBIT`/`CREDIT`. SMS, CSV, and manual are the only ingest paths.
- **Remaining risk is concentrated in** file/ViewModel sizing (god files), one redundant Home metric query resolved, naming echoes in the LLM/security layer, and a couple of database‑clarity gaps.

The architecture shape is right; the open debt is **simplification and a few import/security edges**, not rework of the model.

---

## 2. System architecture

### 2.1 Layers (packages under `app/src/main/java/com/krtky/financetracker/`)

| Layer | Packages | Responsibility |
| --- | --- | --- |
| **UI** | `ui/` (screens, components, navigation, viewmodel, theme, util) | Compose screens, ViewModels (`StateFlow`), navigation graph, charts, formatters |
| **Domain** | `domain/model/` | Pure data classes + enums (`Transaction`, `Account`, `Fund`, `Money`, `SplitRules`) |
| **Data** | `data/` (local/db, repository, prefs, sms, llm, sheets, importcsv, receipt) | Room entities/DAOs, repositories, encrypted prefs (`SecureStore`), parsers, network clients |
| **Services / receivers** | `sms/`, `notification/`, `location/`, `widget/`, `workers/`, `system/` | Background ingest, classification notifications, widgets, periodic work, boot rescheduling |
| **DI** | `di/AppModule.kt` | Hilt module providing `AppDatabase` + migrations |

### 2.2 Data flow

```
SMS (SmsReceiver) ─┐
CSV import wizard  ─┼─► TransactionParser / CsvStatementParser ─► TransactionRepository
Manual (+ FAB)     ─┘                                    (dedupe, classify, fund ledger)
                                                          │
                                                          ▼
                                              Room (AppDatabase) ──► encrypted prefs / widgets / Sheets
                                                          │
                                              ViewModels ──► Compose screens (StateFlow)
```

- **Ingest** is validated/deduped in `TransactionRepository`, one dedicated path per source: `insertFromSms`, `insertFromImport`, `insertManual` / `insertManualWithSplits`.
- **Classify** is scheduled via `PendingClassificationEntity` + `ClassificationWorker` (every 15 min) → notification/overlay.
- **Export** is CSV / JSON backup (`BackupRepository`) or one‑way Google Sheets sync (`SheetsSyncService` + `SheetsSyncWorker`, every 30 min). Every write enqueues a `sync_outbox` UPSERT.
- **Widgets** are refreshed by `WidgetRefreshWorker` (every 15 min) and on cold start (`FinanceApp.onCreate`).

### 2.3 Component inventory (from `AndroidManifest.xml`)

| Kind | Name | Notes |
| --- | --- | --- |
| Activity | `ui.MainActivity` | Single host activity; one `NavHost`. |
| Receiver | `sms.SmsReceiver` | `SMS_RECEIVED`; gates on `smsEnabled` + LLM ready. |
| Receiver | `system.BootReceiver` | `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `USER_PRESENT`; re‑schedules WorkManager. |
| Receiver | `notification.ClassificationActionReceiver` | Notification action handler. |
| Receivers | `widget.*WidgetReceiver` (×5) | Overview, Transactions, AddButton, **Funds**, Spending widgets (Glance). |
| Service | `location.LocationTrackingService` | Foreground `location` service; started by `SettingsViewModel` when background location is enabled. |
| Provider | `FileProvider`, `InitializationProvider` | Receipts / WorkManager init. |

> Exactly **one Activity**; all "screens" are Compose destinations.

---

## 3. Database (`AppDatabase`, version 11)

`data/local/db/` — entities, DAOs, `Mappers`, `AppDatabase`. **`exportSchema = true`** (schema JSON committed for v11 onward only).

### 3.1 Tables

| Table | Key columns | Purpose |
| --- | --- | --- |
| `categories` | id, name, icon, color, sortOrder, isSystem, isQuickAction | Category taxonomy (19 seeded defaults). |
| `accounts` | id, name, kind (BANK/CARD/CASH/WALLET), openingBalancePaise, archived | Owned ledgers. |
| `funds` | id, name, archived, **budgetPaise** | "Tabs" (open IOUs); `budgetPaise` doubles as opening balance. |
| `transactions` | id (String PK), type, amountPaise, occurredAt, **counterparty**, categoryId, fundId, **accountId**, source, kind, transferGroupId, splitGroupId, **smsMessageId**, externalRefId, contentHash, deletedAt, version, receiptUri | Core ledger; single party/account fields. |
| `fund_ledger` | id, fundId, transactionId, entryType, amountPaise, balanceAfterPaise | Materialised running balance per tab; rebuilt by `recalculateFundLedger`. |
| `location_samples` | lat/lng/accuracy, placeName | Optional location history. |
| `pending_classification` | transactionId, scheduledAt, attempts | Drives `ClassificationWorker`. |
| `sync_outbox` | entityType, entityId, operation, attempts | Outbox for Sheets sync. |
| `sync_state` | key/value | Sync cursors. |

Relationships are logical (FKs not enforced by Room): `categoryId`, `accountId`, `fundId` → parent tables; `splitGroupId` groups split children (parent soft‑deleted); `transferGroupId` links transfer legs; `fund_ledger.transactionId → transactions.id`.

### 3.2 Indexing

`transactions` has 10 indexes: unique on `smsMessageId`, `contentHash`; non‑unique on `externalRefId`, `occurredAt`, `categoryId`, `fundId`, `accountId`, `transferGroupId`, `splitGroupId`, `deletedAt`. Appropriate for the query patterns.

### 3.3 Migrations (`AppDatabase.kt`)

Nine migrations: `2→3`, `3→4` (no‑op), `4→5` (drops experimental recurring‑payments), `5→6` (receipt), `6→7` (accounts + Debit/Credit), `7→8` (transaction_splits), `8→9` (splits as rows + `splitGroupId`), `9→10` (drop `trusted_senders`/`email_ingest_log`), `10→11` (field collapse to single `counterparty`/`accountId`/`smsMessageId`). `fallbackToDestructiveMigrationOnDowngrade()` is set. Only `2→3` … `10→11` are registered in `AppModule` (10 `Migration` objects spanning versions 2–11); there is **no `1→2`** migration.

---

## 4. Screens (Compose destinations)

Type‑safe navigation (`ui/navigation/AppRoutes.kt`, kotlinx‑serialization). Bottom nav: **Home / Activity / Funds / Settings**; the rest are pushed.

| Screen file | ~Lines | Responsibility |
| --- | --- | --- |
| `MainActivity.kt` | 574 | Host + `NavHost` + floating nav + intent handling, theme state, onboarding gate |
| `HomeScreen.kt` + `HomeDashboardSections.kt` | 436 + 761 | Dashboard: net, tiles, ring, trend, open tabs, recent |
| `TransactionsScreen.kt` | 491 | Full list, search, filters, export |
| `AddCashScreen.kt` | 1000 | Debit/Credit entry + self‑transfer + draft splits |
| `TransactionDetailScreen.kt` | **1347** | View/edit, amount pad, account, tab, location, receipt, OSM map, splits |
| `SplitTransactionScreen.kt` | 510 | Split editor (own destination) |
| `FundsScreen.kt` | 479 | Tabs list — open balances ("they owe you / you owe them") |
| `FundDetailScreen.kt` | 262 | Per‑tab activity |
| `AccountsScreen.kt` | 297 | Owned ledgers + archive + CSV import entry |
| `CategoriesScreen.kt` / `CategoryDetailScreen.kt` | 193 / 237 | Category management + activity |
| `CsvImportScreen.kt` | 500 | Account → file → preview → done wizard |
| `SettingsScreen.kt` / `SettingsDetailScreen.kt` | 400 / 1204 | All settings domains |
| `OnboardingScreen.kt` | 858 | First‑run setup |
| `AppearanceSettingsContent.kt` | 369 | Theme studio |

Supporting: `components/` (~30 files), `theme/` (`Theme.kt` 951, `ThemeColorPicker.kt` 1031), `util/`. Widgets: `OverviewWidget`, `TransactionsWidget`, `SpendingWidget`, `FundsWidget`, `AddButtonWidget` (Glance).

---

## 5. Repositories, workers & background

### 5.1 Repositories (the "functions" layer)

| Repository | Role | Notes |
| --- | --- | --- |
| `TransactionRepository` (1064) | CRUD, splits, merges, self/tab transfer, fund ledger, dedupe, classify | **God object** — §6.1 |
| `CashflowRepository` (293) | Read-only cashflow metrics: `homeCashflowSnapshot`, `observeAccountBalances` | Extracted from `TransactionRepository` |
| `AccountRepository` (233) | Account CRUD, balances, `syncFromBankList`, legacy `resolveId` | One balance path |
| `CategoryRepository` (89) | Seeding + CRUD | Healthy |
| `StatementImportRepository` (230) | CSV preview/dedupe/commit | |
| `BackupRepository` (433) | JSON export/import (format v5) | Secrets gated by opt-in dialog |
| `LocationRepository` (74) | Capture + place match | Optional |

### 5.2 Workers (`workers/AppWorkers.kt`)

- `ClassificationWorker` (15 min) — notifies due pending classifications.
- `SheetsSyncWorker` (30 min, network‑constrained) — drains `sync_outbox`.
- `WidgetRefreshWorker` (15 min) — refreshes Glance widgets.
- `WorkScheduler.scheduleAll` registers the three unique periodic works.

### 5.3 Background & ingest

- `SmsReceiver` → `TransactionParser.parseSms` → `insertFromSms`.
- `CsvStatementParser` + `StatementImportRepository` → `insertFromImport`.
- `LlmClient` (OkHttp) — OpenAI‑compatible `/chat/completions`; required for SMS auto‑import.
- `ClassificationNotifier` / `ClassificationActionReceiver` — classify prompts (overlay-service path removed).
- `LocationTrackingService` (foreground) — optional location stamping.

---

## 6. Remaining issues

Severity: **high** · **med** · **low**. Only open items.

### 6.1 Import reconciliation (med)

| ID | Issue | File |
| --- | --- | --- |
| **N2** | `AccountsViewModel` mutators (`addAccount`/`archiveAccount`/`restoreAccount`) now call `mirrorBankPrefs()` to sync with Settings `bank_accounts` pref — previously missing (now fixed in code). Cold start sync (`FinanceApp.onCreate`) still provides bidirectional sync. | `AccountsViewModel.kt:37-55` ; `FinanceApp.kt:36-45` ; `SettingsViewModel.kt:440-445` |

### 6.2 God objects / files (high — main maintenance cost)

| Type | ~Lines | Smell |
| --- | --- | --- |
| `TransactionRepository` | 1064 | CRUD + splits + merges + transfers + fund ledger + dedupe + classify (cashflow reads extracted to §5.1 `CashflowRepository`) |
| `TransactionDetailScreen` | 1347 | View + edit + amount pad + date + account + tab + location + receipt + OSM map + splits |
| `SettingsDetailScreen` | 1204 | Every settings domain |
| `AddCashScreen` | 1000 | Entry + self‑transfer + draft splits in one screen |
| `HomeDashboardSections` | 761 | Whole dashboard in one file |
| `SheetsSyncService` | 998 | Service + table assembly + worker logic |
| `OnboardingScreen` | 858 | |
| `Theme.kt` / `ThemeColorPicker` | 951 / 1031 | Theme system heavier than the core money model |
| `SettingsViewModel` | 511 | Profile, SMS, theme, LLM, Sheets, location, banks, backup, dev |

These block safe change and explain the "too many jobs per screen" UX complaints.

### 6.3 Redundant metric computations (med) — **resolved**

`CashflowRepository.homeCashflowSnapshot()` now consolidates `monthlySummary`, `cashflowMetrics`, `categorySpend`, and `monthlyTrend` into a single DAO scan. The old redundant public methods (`monthlySummary()`, `cashflowMetrics()`, `categorySpend()`, `monthlyTrend()`) have been removed. WidgetData, HomeViewModel, and CategoriesViewModel now derive all cashflow data from the consolidated snapshot.

### 6.4 Security / privacy (med)

- LLM system prompt and `LlmClient.extractTransaction` parameter are SMS‑only (email references removed in `SecureStore.kt:97-98` and `LlmClient.kt:45`).

### 6.5 Database fragility (low)

- **No `1→2` migration** — latent `IllegalStateException` if a v1 install ever upgrades.
- **`MIGRATION_4_5` rebuilds `transactions` and recreates the unique `externalRefId` + `paymentMethod` index** — that index is later made obsolete by the v10→11 collapse to a single `accountId` (the rationale is documented in `MIGRATION_10_11`, `AppDatabase.kt:471-473`). *(Clarity — resolved.)*
- **Schema JSON history only from v11** — older migrations can't be authored against exported schema.

### 6.6 Dependencies & permissions (low)

- `org.osmdroid` full OSM map for a single receipt/location view inside `TransactionDetailScreen` — heavy for an optional feature.
- `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE_LOCATION`, `CAMERA`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for optional features — reasonable but need runtime justification/UI.

### 6.7 Performance (low, at personal scale)

- `recalculateFundLedger` reloads the **entire** `transactions` table on every fund mutation (`txnDao.getAllNonDeleted()`) — O(n) per edit.
- In‑memory `combine` flows re‑emit the whole transaction list on any change (several screens subscribe to `observeAll()`).

### 6.8 UI storytelling (med)

- **Home is overloaded**: greeting + layout‑edit mode (reorder, half‑width spans, drag), pending chip, setup checklist, hero net, tiles, ring, trend, open tabs, recent, investment metrics. Layout editor is power‑user chrome on the primary screen. Target fixed order: Net · Pending · Accounts strip · Spend by category · Recent · Open tabs.
- **Product vs class naming**: open IOUs are "Tabs" in UI copy (e.g. `FundsScreen.kt:118` title = "Tabs", "New tab", "Archive tab") but code still says `Fund`, `FundBalance`, `FundsViewModel`, `FundsWidget`, `FundDao`, `fundDao`, `fund_ledger`. A "renamed Funds to Tabs" commit only touched UI strings, not model/viewmodel/widget types. Low value to fully chase; a targeted rename of `Fund`→`Tab` across model + DAO + ViewModel + widget is moderate churn (≈15 files) with zero behavior change.
- **Add / Detail / Self‑transfer / Splits** are mixed into a few kitchen‑sink screens; splits are also reachable pre‑create in Add. Simpler target: Add = plain entry; self‑transfer separate; split only post‑create; Detail view‑first.

### 6.9 Naming echoes (low, cosmetic)

- `TransactionParser` reads `e.merchant`/`e.paymentMethod` from the LLM's `ExtractedTransaction` model (`LlmClient.kt:21,26`) — these are LLM response fields, not persistence.
- `SmsRedactor.kt:4-6` comment says "Renamed from the email-era `EmailRedactor`" — self-documenting, acceptable to keep.
- `SettingsUiState.kt:13` comment says "Email/SMS auto-import also needs [llmApiKeySet]" — drop "Email/".

All of the above are parse-scaffolding/parameter/comment echoes, not persistence-layer fields.

---

## 7. Prioritized recommendations

**P1 — Structural simplify:**
1. Split remaining god files: `TransactionDetailScreen` (1347), `SettingsDetailScreen` (1204), `AddCashScreen` (1000) (§6.2); simplify Home to a fixed section order, move self‑transfer to its own mode and splits to post‑create only, make Detail view‑first (§6.8).

**P2 — Data consistency:**
2. (Completed) Redundant cashflow methods consolidated into `homeCashflowSnapshot()`; N2 AccountsViewModel bank‑prefs sync also completed (§6.1, §6.3).

**P3 — Schema hygiene:**
3. Export schema JSON for v10 (generate via Room schema export or note limitation in §6.5).

---

## 8. Bottom line

| Area | Verdict |
| --- | --- |
| Layering / DI / navigation | Solid, conventional, maintainable |
| Core cashflow model | Sound; transfers/splits correctly excluded from metrics |
| Database (v11) | Good; migrations are sound; schema export on; no `1→2` gap, minor clarity gaps remain |
| Correctness | N2 AccountsViewModel bank‑prefs sync fixed; N1 dead‑filter resolved |
| Structural / KISS | God files remain (`TransactionRepository` 1064, `TransactionDetailScreen` 1347, `SettingsDetailScreen` 1204, `AddCashScreen` 1000, `HomeDashboardSections` 761). `CashflowRepository` extracted (§5.1); Home metric consolidation completed (§6.3). |
| UI storytelling | Coherent core; Home/Add/Detail still do too much; Tabs label applied but `Fund` types linger |
| Security | Secrets gated (opt-in dialog); cleartext HTTP blocked. LLM system prompt and parameter are SMS‑only (email references removed) |

**Highest‑leverage next step:** close remaining god files (split `TransactionDetailScreen`/`SettingsDetailScreen`/`AddCashScreen`), then optional `Fund`→`Tab` rename. No new features until that debt is closed.
