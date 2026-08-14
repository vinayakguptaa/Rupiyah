# Rupiyah — Architecture & Remaining Issues

**Scope:** Current-state architecture of `app/` (Kotlin + Jetpack Compose + Room + Hilt + WorkManager) and the **remaining** debt to close. Historical "what was already fixed" is deliberately omitted.
**Date:** 2026-08-15 · **DB version:** 11 · minSdk 26 / targetSdk 35 · versionCode 6 / 1.4.1

---

## 1. Executive summary

Rupiyah is a single‑module, offline‑first personal finance tracker (package `com.krtky.financetracker`). It records money in ₹ via **manual entry**, **SMS auto‑import (LLM‑assisted)**, or **CSV bank‑statement import**, then organises it under **accounts**, **categories**, **tabs (open IOUs, "funds")**, and **splits**.

- **Layering is clean and conventional** (UI → ViewModel → Repository → Room/DAO), single Hilt-injected `AppDatabase`.
- **Core cashflow model is sound**: a transaction is the single source of truth; self/tab transfers and split children are ordinary rows linked by `transferGroupId` / `splitGroupId`, and are excluded from lifestyle metrics.
- **The data model is single‑field and current**: one party (`counterparty`), one account (`accountId`), one ingest id (`smsMessageId`); type strings are `DEBIT`/`CREDIT`. SMS, CSV, and manual are the only ingest paths.
- **Remaining risk is concentrated in** import reconciliation edge cases (S1, S2), file/ViewModel sizing (god files), redundant Home metric queries, and backup secret handling.

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

Nine migrations: `2→3`, `3→4` (no‑op), `4→5` (drops experimental recurring‑payments), `5→6` (receipt), `6→7` (accounts + Debit/Credit), `7→8` (transaction_splits), `8→9` (splits as rows + `splitGroupId`), `9→10` (drop `trusted_senders`/`email_ingest_log`), `10→11` (field collapse to single `counterparty`/`accountId`/`smsMessageId`). `fallbackToDestructiveMigrationOnDowngrade()` is set.

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
| `TransactionRepository` (**1228**) | CRUD, splits, merges, self/tab transfer, fund ledger, cashflow metrics, dedupe, classification | **God object** — §6.2 |
| `AccountRepository` (233) | Account CRUD, balances, `syncFromBankList`, legacy `resolveId` | One balance path |
| `CategoryRepository` (89) | Seeding + CRUD | Healthy |
| `StatementImportRepository` (230) | CSV preview/dedupe/commit | |
| `BackupRepository` (433) | JSON export/import (format v5) | Secrets in plaintext — §6.4 |
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
- `ClassificationNotifier` / `OverlayClassificationService` / `ClassificationActionReceiver` — classify prompts.
- `LocationTrackingService` (foreground) — optional location stamping.

---

## 6. Remaining issues

Severity: **high** · **med** · **low**. Only open items; IDs carried over from the original plan for traceability.

### 6.1 Import reconciliation (med)

| ID | Issue | File |
| --- | --- | --- |
| **S1** | The "attach statement ref to a near twin with a blank ref" HIGH branch never fires alone — it still requires `descScore >= 0.72f`. New‑ref‑on‑blank‑existing matches fall through to a weak `sameDay` check. | `ImportDedupe.kt:69-77` |
| **S2** | MEDIUM‑confidence rows default to `SKIP_MERGE`; one‑tap import silently drops uncertain rows. Easy under‑import against sparse SMS. Needs a product decision (ask vs default‑import). | `StatementImportRepository.kt:106` |
| **N1** | Dead filter `.filter { it.isNotEmpty() \|\| true }` always keeps lines; should be a single blank‑line filter. | `CsvStatementParser.kt:426` |
| **N2** | `AccountsViewModel` mutators (`addAccount`/`archiveAccount`/`restoreAccount`) don't mirror the Settings bank‑prefs list; cold start can re‑sync from stale prefs and re‑archive. | `AccountsViewModel.kt:37-55` |

### 6.2 God objects / files (high — main maintenance cost)

| Type | ~Lines | Smell |
| --- | --- | --- |
| `TransactionRepository` | 1228 | CRUD + splits + merges + transfers + fund ledger + **4 metric computations** + classify + dedupe |
| `TransactionDetailScreen` | 1347 | View + edit + amount pad + date + account + tab + location + receipt + OSM map + splits |
| `SettingsDetailScreen` | 1204 | Every settings domain |
| `AddCashScreen` | 1000 | Entry + self‑transfer + draft splits in one screen |
| `HomeDashboardSections` | 761 | Whole dashboard in one file |
| `SheetsSyncService` | 998 | Service + table assembly + worker logic |
| `OnboardingScreen` | 858 | |
| `Theme.kt` / `ThemeColorPicker` | 951 / 1031 | Theme system heavier than the core money model |
| `SettingsViewModel` | 511 | Profile, SMS, theme, LLM, Sheets, location, banks, backup, dev |

These block safe change and explain the "too many jobs per screen" UX complaints.

### 6.3 Redundant metric computations (med)

Home consumes `monthlySummary`, `cashflowMetrics`, `categorySpend`, **and** `monthlyTrend` (`HomeViewModel.kt:53-89`) — several queries computing overlapping monthly numbers (with `cashflow.lifestyleByCategory` overlapping `categorySpend`). Consolidate into one cashflow snapshot.

### 6.4 Security / privacy (high)

- **Backups contain secrets in plaintext.** `BackupRepository.exportData` writes `llm_api_key`, `llm_base_url`, `sheets_access_token`, `sheets_spreadsheet_id` into an unencrypted JSON file (`BackupRepository.kt:50-58`). Should be optional/warning‑gated.
- LLM base URL is user‑supplied and called over plain OkHttp; a `http://` endpoint may be allowed unless `network_security_config` blocks cleartext (not verified).

### 6.5 Database fragility (med/low)

- **No `1→2` migration** — latent `IllegalStateException` if a v1 install ever upgrades. *(Low.)*
- **`MIGRATION_4_5` hardcodes the `transactions` CREATE** — omits later columns, relying on subsequent `ALTER`s; brittle on the v4→ path. *(Med, legacy.)*
- **Schema JSON history only from v11** — older migrations can't be authored against exported schema. *(Low.)*

### 6.6 Dependencies & permissions (low)

- `org.osmdroid` full OSM map for a single receipt/location view inside `TransactionDetailScreen` — heavy for an optional feature.
- `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE_LOCATION`, `CAMERA`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for optional features — reasonable but need runtime justification/UI.

### 6.7 Performance (low, at personal scale)

- `recalculateFundLedger` reloads the **entire** `transactions` table on every fund mutation (`txnDao.getAllNonDeleted()`) — O(n) per edit.
- In‑memory `combine` flows re‑emit the whole transaction list on any change (several screens subscribe to `observeAll()`).

### 6.8 UI storytelling (med)

- **Home is overloaded**: greeting + layout‑edit mode (reorder, half‑width spans, drag), pending chip, setup checklist, hero net, tiles, category pie, trend, open tabs, recent, investment metrics. Layout editor is power‑user chrome on the primary screen. Target fixed order: Net · Pending · Accounts strip · Spend by category · Recent · Open tabs.
- **Product vs class naming**: open IOUs are "Tabs" in UI copy but `Fund`, `FundBalance`, `FundsWidget` in code — cosmetic, pervasive rename is churn.
- **Home language**: Home still reads income/spent/net where forms use Debit/Credit; and Home's "Cash/Digital" collapsed view (rows with no owning account → display‑only "Digital" bucket) differs from the Accounts screen's real‑account list. They agree per named account, but the "Digital" pseudo‑bucket deserves a clarifying comment (`TransactionRepository.kt:924-934`).
- **Add / Detail / Self‑transfer / Splits** are mixed into a few kitchen‑sink screens; splits are also reachable pre‑create in Add. Simpler target: Add = plain entry; self‑transfer separate; split only post‑create; Detail view‑first.

### 6.9 Naming echoes (low, cosmetic)

- `LlmClient` still uses a `redactedEmailBody` parameter and its system prompt says "emails and SMS"; `TransactionParser` reads `e.merchant`/`e.paymentMethod` while parsing raw SMS; `AccountRepository.resolveId(paymentMethod, isCash)` keeps the `paymentMethod` param name. All parse‑scaffolding/params, not persistence.

---

## 7. Prioritized recommendations

**P1 — ship‑level (correctness/security):**
1. Fix **S1** (let a new ref on a blank‑ref near twin match HIGH) and decide **S2** (MEDIUM must ask, or default to import).
2. Gate **backup secrets** behind an explicit warning/opt‑in (§6.4).

**P2 — structural simplify:**
3. Split `TransactionRepository` (cashflow queries, fund ledger, splits, transfer) and `SettingsViewModel` into focused modules.
4. Consolidate the redundant Home metric queries into one cashflow snapshot (§6.3).
5. Simplify Home to a fixed section order; move self‑transfer to its own mode and splits to post‑create only; make Detail view‑first (§6.8).
6. Remove OSM / camera / background‑location if those features stay optional.

**P3 — hygiene:**
7. N1, N2 cleanups (§6.1).
8. Add a `1→2` migration or document that v1 never shipped; comment the "Digital" fold (§6.5/6.8).
9. Consider collapsing the `fund_ledger` materialised table or bounding its recompute (§6.7).
10. Optional cosmetic renames (`Fund*` → `Tab*`, `redactedEmailBody`) only when cheap.

---

## 8. Bottom line

| Area | Verdict |
| --- | --- |
| Layering / DI / navigation | Solid, conventional, maintainable |
| Core cashflow model | Sound; transfers/splits correctly excluded from metrics |
| Database (v11) | Good; migrations are the fragile part; schema export on |
| Correctness | Import‑reconciliation edges (S1, S2) remain |
| Structural / KISS | God files + redundant metrics are the main cost |
| UI storytelling | Coherent core; Home/Add/Detail still do too much |
| Security | Plaintext backup secrets is the main concern |

**Highest‑leverage next step:** fix S1/S2 and gate backup secrets (P1), then split the god files and consolidate Home metrics (P2). No new features until that debt is closed.