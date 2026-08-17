# Rupiyah — Architecture & Remaining Issues

**Scope:** Current architecture of `app/` (Kotlin + Jetpack Compose + Room + Hilt + WorkManager) and **open** debt.
**Date:** 2026-08-17 · **DB version:** 11 · minSdk 26 / targetSdk 35 · versionCode 6 / 1.4.1

---

## 1. Executive summary

Rupiyah is a single-module, offline-first personal finance tracker (`com.krtky.financetracker`). Money is recorded in ₹ via **manual entry**, **SMS auto-import (LLM-assisted)**, **paste-to-AI** (same parser, review on the Add form), or **CSV bank-statement import**, then organised under **accounts**, **categories**, **tabs** (open IOUs; code still says `Fund`), and **splits**.

- Layering is conventional: UI → ViewModel → Repository → Room/DAO. One Hilt-injected `AppDatabase`.
- A transaction is the source of truth. Self/tab transfers and split children are ordinary rows linked by `transferGroupId` / `splitGroupId` and are excluded from lifestyle cashflow metrics.
- Persistence is single-field: one `counterparty`, one `accountId`, one `smsMessageId`; types are `DEBIT` / `CREDIT`. Sources are `SMS` / `MANUAL` / `IMPORT` / `PASTE`. Email ingest is gone.
- **P1 (settings split + Add extract + paste ingest) landed.** Remaining work is Detail/repo/theme size, Home crowding, `Fund`→`Tab` naming, and schema/permission hygiene — not a model rewrite.

---

## 2. System architecture

### 2.1 Layers (`app/src/main/java/com/krtky/financetracker/`)

| Layer | Packages | Responsibility |
| --- | --- | --- |
| **UI** | `ui/` | Compose screens, ViewModels (`StateFlow`), navigation, charts, formatters, theme |
| **Settings UI** | `ui/screens/settings/` | Per-domain settings (profile, backup, LLM, SMS, location, Sheets, Google auth, categories, banks, dev) |
| **Domain** | `domain/model/` | Pure types (`Transaction`, `Account`, `Fund`, `Money`, `SplitRules`, …) |
| **Data** | `data/` | Room, repositories, `SecureStore` / DataStore, SMS/paste parser, LLM client, CSV import, Sheets, receipts |
| **Services** | `sms/`, `notification/`, `location/`, `widget/`, `workers/`, `system/` | SMS ingest, classify notifications, optional location, Glance widgets, WorkManager, boot reschedule |
| **DI** | `di/AppModule.kt` | Provides `AppDatabase` + migrations 2→11 |

Empty leftover: `data/email/` (no sources).

### 2.2 Data flow

```
SMS (SmsReceiver)     ─┐
Paste (Add sheet)     ─┼─► TransactionParser ──┐
CSV import wizard     ─┤                       ├─► TransactionRepository
Manual (+ FAB)        ─┘ CsvStatementParser ───┘     (dedupe, classify, fund ledger)
                                                    │
                                                    ▼
                                        Room (AppDatabase)
                                                    │
                              ViewModels ──► Compose screens
                              Backup / Sheets / widgets read the same store
```

- **Ingest** is dedicated: `insertFromSms`, `insertFromImport`, `insertManual` / `insertManualWithSplits` (paste saves through `insertManual` with `source = PASTE` after form review).
- **Parse:** `TransactionParser.parseSms` and `parsePastedText` share `parseSource` (deterministic + LLM merge via `LlmClient`).
- **Classify** uses `pending_classification` + `ClassificationWorker` (15 min) → notification.
- **Export:** JSON backup (`BackupRepository`) or one-way Google Sheets (`SheetsSyncService` + `SheetsSyncWorker`, 30 min, network required). Writes enqueue `sync_outbox`.
- **Widgets** refresh via `WidgetRefreshWorker` (15 min) and `FinanceApp.onCreate`.

### 2.3 Components (`AndroidManifest.xml`)

| Kind | Name | Notes |
| --- | --- | --- |
| Activity | `ui.MainActivity` | Single host; one `NavHost`. |
| Receiver | `sms.SmsReceiver` | `SMS_RECEIVED`; gated by SMS enabled + LLM ready. |
| Receiver | `system.BootReceiver` | `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `USER_PRESENT`; reschedules work. |
| Receiver | `notification.ClassificationActionReceiver` | Notification actions. |
| Receivers | `widget.*WidgetReceiver` (×5) | Overview, Transactions, AddButton, Funds, Spending (Glance). |
| Service | `location.LocationTrackingService` | Foreground location; started from Settings when enabled. |
| Provider | `FileProvider`, `InitializationProvider` | Receipts; WorkManager init (default initializer removed). |

---

## 3. Database (`AppDatabase`, version 11)

`data/local/db/` — entities, DAOs, `Mappers`. `exportSchema = true` (JSON for **v10** and **v11** only).

### 3.1 Tables

| Table | Purpose |
| --- | --- |
| `categories` | Taxonomy (seeded defaults). |
| `accounts` | Owned ledgers: `kind` BANK / CARD / CASH / WALLET, `openingBalancePaise`, `archived`. |
| `funds` | Tabs (open IOUs). `budgetPaise` is the envelope / opening figure. |
| `transactions` | Ledger. String PK; `type`, `amountPaise`, `occurredAt`, `counterparty`, `categoryId`, `fundId`, `accountId`, `source`, `kind`, `transferGroupId`, `splitGroupId`, `smsMessageId`, `externalRefId`, `contentHash`, `deletedAt`, `receiptUri`. |
| `fund_ledger` | Materialised tab running balance; rebuilt by `recalculateFundLedger`. |
| `location_samples` | Optional location history. |
| `pending_classification` | Queue for `ClassificationWorker`. |
| `sync_outbox` / `sync_state` | Sheets outbox and cursors. |

Room does not enforce FKs. Split children share `splitGroupId` (parent soft-deleted). Transfer legs share `transferGroupId`.

### 3.2 Indexes (`transactions`)

Unique: `smsMessageId`, `contentHash`. Non-unique: `externalRefId`, `occurredAt`, `categoryId`, `fundId`, `accountId`, `transferGroupId`, `splitGroupId`, `deletedAt`.

### 3.3 Migrations

Registered in `AppModule`: `2→3` … `10→11`. **No `1→2`.** `fallbackToDestructiveMigrationOnDowngrade()` is set.

| Step | Effect |
| --- | --- |
| 2→3 | Fund budget freeze |
| 3→4 | No-op (recurring never landed on this path) |
| 4→5 | Drop experimental recurring; rebuild `transactions` |
| 5→6 | Receipt URI |
| 6→7 | Accounts + Debit/Credit |
| 7→8 | `transaction_splits` table |
| 8→9 | Splits as child rows + `splitGroupId` |
| 9→10 | Drop `trusted_senders`, `email_ingest_log` |
| 10→11 | Collapse to `counterparty` / `accountId` / `smsMessageId` |

`PASTE` is a `transactions.source` string only — no schema bump.

---

## 4. Screens

Type-safe navigation (`ui/navigation/AppRoutes.kt`). Bottom nav: **Home / Activity / Funds / Settings**.

| Screen | ~Lines | Role |
| --- | --- | --- |
| `MainActivity.kt` | 549 | Host, `NavHost`, floating nav, intents, theme, onboarding gate |
| `HomeScreen.kt` + dashboard files | 409 + 398 + sections | Net, tiles, ring, trend, open tabs, recent; reorder/span still on Home |
| `TransactionsScreen.kt` | 476 | List, search, filters, export |
| `AddCashScreen.kt` | 763 | Entry + type/transfer segment; paste sheet; still hosts save/numpad |
| `AddCashSelfTransfer.kt` | 63 | Self-transfer from/to chips (extracted) |
| `AddCashDraftSplits.kt` | 93 | Draft-split summary card (extracted) |
| `PasteAiParseSheet.kt` | 141 | Paste text → `parsePastedText` → hydrate form |
| `TransactionDetailScreen.kt` | 945 | View/edit; helpers: `TransactionDetailView`, `TransactionDetailSplits`, `OsmMap` |
| `SplitTransactionScreen.kt` | 498 | Split editor destination |
| `FundsScreen.kt` / `FundDetailScreen.kt` | 464 / 256 | Tabs list + per-tab activity |
| `AccountsScreen.kt` | 286 | Ledgers, archive, CSV import entry |
| `CategoriesScreen.kt` / `CategoryDetailScreen.kt` | 187 / 233 | Category CRUD + activity |
| `CsvImportScreen.kt` | 486 | Account → file → preview → commit |
| `SettingsScreen.kt` / `SettingsDetailScreen.kt` | 382 / **100** | Hub + thin section router |
| `ui/screens/settings/*` | ~60–367 each | Per-domain settings (10 files) |
| `OnboardingScreen.kt` | 819 | First-run monolith |
| `AppearanceSettingsContent.kt` | 356 | Theme studio (still under `ui/screens/`) |

Shared form: `TransactionFormState` + `TransactionFormFields` (used by Add and Detail edit). Widgets: Overview, Transactions, Spending, Funds, AddButton.

---

## 5. Repositories, workers & background

### 5.1 Repositories

| Repository | ~Lines | Role |
| --- | --- | --- |
| `TransactionRepository` | 1011 | CRUD, splits, merges, self/tab transfer, fund ledger, dedupe, classify |
| `CashflowRepository` | 190 | Read-only metrics; `homeCashflowSnapshot` is the single Home/widget/category scan |
| `AccountRepository` | 207 | Account CRUD, balances, `syncFromBankList` |
| `CategoryRepository` | 76 | Seed + CRUD |
| `StatementImportRepository` | 217 | CSV preview / dedupe / commit |
| `BackupRepository` | 429 | JSON export/import |
| `LocationRepository` | 68 | Optional stamp + place match |

Accounts created/archived/restored from `AccountsViewModel` call `mirrorBankPrefs()` so Settings `bank_accounts` stays aligned. Cold start in `FinanceApp` still bidirectional-syncs bank names and repairs fund ledgers.

### 5.2 Workers (`workers/AppWorkers.kt`)

`WorkScheduler.scheduleAll` registers three unique periodic jobs:

- `ClassificationWorker` — 15 min
- `SheetsSyncWorker` — 30 min, `CONNECTED`
- `WidgetRefreshWorker` — 15 min

### 5.3 Ingest & side paths

- SMS → `TransactionParser.parseSms` → `insertFromSms` (LLM via `LlmClient`, OpenAI-compatible `/chat/completions`).
- Paste → Add top-bar clipboard → `PasteAiParseSheet` → `parsePastedText` → form hydrate → `insertManual(..., source = PASTE)`. LLM optional; without a key only bank-style deterministic text works.
- CSV → `CsvStatementParser` + `StatementImportRepository` → `insertFromImport`.
- Classify: `ClassificationNotifier` + `ClassificationActionReceiver` (no overlay service).
- Location: optional foreground `LocationTrackingService`.

---

## 6. Remaining issues

Open items only. Closed this pass: Settings god-file, Add self-transfer/splits extract, paste ingest.

### 6.1 God files (high)

| File | ~Lines | Smell |
| --- | --- | --- |
| `TransactionRepository` | 1011 | Writes + splits + transfers + ledger + dedupe + classify |
| `TransactionDetailScreen` | 945 | View + edit + pad + account/tab/location/receipt (partially extracted) |
| `ThemeColorPicker` / `Theme.kt` | 996 / 887 | Theme heavier than the ledger |
| `SheetsSyncService` | 963 | Client + table assembly + worker glue |
| `OnboardingScreen` | 819 | First-run monolith |
| `AddCashScreen` | 763 | Still owns save CTA, numpad, last-used defaults (better; not done) |
| `SettingsViewModel` | 460 | Profile, SMS, theme, LLM, Sheets, location, banks, backup, dev |

`TransactionFormState` is shared. Add still mixes transfer *mode* and pre-create splits. Detail is not view-first.

### 6.2 Database (low)

- No `1→2` migration — a v1 DB would fail upgrade.
- Schema JSON only from v10. Older migrations cannot be replayed against exported schemas.

### 6.3 Performance (low at personal scale)

- `recalculateFundLedger` loads all non-deleted transactions (`getAllNonDeleted()`) on each fund mutation.
- Several screens `combine` / `observeAll()` and re-emit the full list on any change.

### 6.4 Dependencies & permissions (low)

- `osmdroid` for one optional map in Detail.
- `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE_LOCATION`, `CAMERA`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for optional features — need a clear runtime story.
- `FOREGROUND_SERVICE_DATA_SYNC` is declared; there is no data-sync foreground service (email watch is gone).

### 6.5 Product / naming (med)

- **Home** still mixes dashboard + layout editor (reorder, half-width spans). Keep reorder if that is a product requirement; the screen is still crowded (Lifestyle / tabs / top category appear in more than one widget).
- UI copy says **Tabs**; types stay `Fund`, `FundsViewModel`, `FundsWidget`, `fund_ledger`. Rename is mechanical (~15 files), no behaviour change.
- Target still: Add = entry; self-transfer = own sheet (chips extracted, not a sheet); splits = post-create; Detail = view-first.

### 6.6 Comment / pref leftovers (low)

- `SecureStore` comments still say “email/SMS auto-import”.
- LLM `ExtractedTransaction` fields `merchant` / `paymentMethod` are parse JSON only, not columns.

---

## 7. Status of the last plan

| Item | Status |
| --- | --- |
| P1.1 Split `SettingsDetailScreen` → `ui/screens/settings/` | **Done.** Router ~100 lines; 10 domain files. |
| P1.2 Extract self-transfer + draft splits from Add; Detail view-first | **Partial.** Extracts exist; Add still 763. Detail still 945, mixed view/edit. |
| P1.3 Optional: thin `SheetsSyncService` / `OnboardingScreen` | **Not started.** |
| Paste text through AI parser | **Done.** `TransactionSource.PASTE`, `parsePastedText`, Add clipboard sheet. |
| P2 Home de-dupe tiles / keep reorder | **Not started.** |
| P2 `Fund` → `Tab` rename | **Not started.** |
| P3 `1→2` migration or “unsupported” | **Not started.** |
| P3 Drop `FOREGROUND_SERVICE_DATA_SYNC` + empty `data/email/` | **Not started.** |
| P3 Leave `recalculateFundLedger` | **Deferred** (intentional). |

Verify: `./gradlew compileDebugKotlin` (last green 2026-08-17). Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 8. Further plan of action

Do these in order. Isolated P3 hygiene can land in the same PR as P2 if it stays mechanical.

**Next (finish P1 structure)**

1. **Detail view-first.** Keep `TransactionDetailView` as the default; move edit fields into a dedicated composable (or reuse Add form). Dirty-tracking stays on the Detail ViewModel.
2. **Add: transfer as a sheet, splits post-create.** Drop pre-create draft splits from Add (use `SplitTransactionScreen` after save). Move self-transfer chips into a sheet so Add is one entry path + paste.
3. Optional later: split `SheetsSyncService` (HTTP client vs table assembly) and slice `OnboardingScreen` by step. Do not block product work on theme files.

**Then P2 — UX**

4. Home: keep reorder if it stays a product requirement; drop duplicate tiles (Lifestyle / open tabs / top category).
5. `Fund` → `Tab` rename across model + DAO + ViewModel + widget when convenient (no behaviour change).

**Then P3 — Hygiene**

6. Add a no-op or documented `1→2` if v1 installs still matter; otherwise record “unsupported” here and in `AppModule`.
7. Drop unused `FOREGROUND_SERVICE_DATA_SYNC` and empty `data/email/`.
8. Fix leftover comments (`SecureStore` “email/SMS”).
9. Leave `recalculateFundLedger` until a tab has enough rows to hurt.

**Product (only after the above, or a dedicated ingest PR)**

10. Show `PASTE` in Activity filters / detail source line if users cannot tell pasted rows from manual.
11. No new ingest surface (email, notification listener, etc.) until Detail/Add are smaller.

---

## 9. Bottom line

| Area | Status |
| --- | --- |
| Layering / DI / navigation | Conventional, one activity |
| Cashflow model | Sound; transfers/splits excluded from lifestyle metrics |
| Database v11 | Migrations 2→11 registered; no `1→2`; schema export v10–v11 |
| Ingest | SMS + CSV + manual + paste-to-AI (review then save) |
| Structure | Settings split done; Add extracts started; Detail / `TransactionRepository` / Sheets / Onboarding still large |
| Naming | Tabs in UI, `Fund` in code |
| Security | Encrypted prefs; secrets opt-in on backup; cleartext HTTP blocked; LLM is SMS + paste parse |

**Next:** finish Detail view-first, then move Add self-transfer to a sheet and splits to post-create. Then Home de-dupe and `Fund`→`Tab`.
