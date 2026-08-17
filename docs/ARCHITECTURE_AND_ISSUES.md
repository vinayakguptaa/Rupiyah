# Rupiyah — Architecture & Remaining Issues

**Scope:** Current architecture of `app/` (Kotlin + Jetpack Compose + Room + Hilt + WorkManager) and **open** debt.
**Date:** 2026-08-18 · **DB version:** 11 · minSdk 26 / targetSdk 35 · versionCode 6 / 1.4.1

---

## 1. Executive summary

Rupiyah is a single-module, offline-first personal finance tracker (`com.krtky.financetracker`). Money is recorded in ₹ via **manual spend**, **account-to-account transfer**, **SMS auto-import (LLM-assisted)**, **paste / share-to-AI** (same parser), or **CSV bank-statement import**, then organised under **accounts**, **categories**, **tabs** (open IOUs), and **splits**.

- Layering is conventional: UI → ViewModel → Repository → Room/DAO. One Hilt-injected `AppDatabase`.
- A transaction is the source of truth. Self/tab transfers and split children are ordinary rows linked by `transferGroupId` / `splitGroupId` and are excluded from lifestyle cashflow metrics.
- Persistence is single-field: one `counterparty`, one `accountId`, one `smsMessageId`; types are `DEBIT` / `CREDIT`. Sources are `SMS` / `MANUAL` / `IMPORT` / `PASTE`. Email ingest is gone.
- Kotlin types, routes, ViewModels, and the Glance widget now say **Tab**. Room tables stay `funds` / `fund_ledger` and the column stays `fundId` (mapped with `@ColumnInfo`) so v11 installs do not migrate.
- **Add UX, Home, drill-down, and `Fund`→`Tab` naming are done.** Remaining work is host/write-path size (only if it still hurts) and leftover email-removal hygiene.

---

## 2. System architecture

### 2.1 Layers (`app/src/main/java/com/krtky/financetracker/`)

| Layer | Packages | Responsibility |
| --- | --- | --- |
| **UI** | `ui/` | Compose screens, ViewModels (`StateFlow`), navigation, charts, formatters, theme |
| **Settings UI** | `ui/screens/settings/` | Per-domain settings (profile, backup, LLM, SMS, location, Sheets, Google auth, categories, banks, dev) |
| **Domain** | `domain/model/` | Pure types (`Transaction`, `Account`, `Tab`, `Money`, `SplitRules`, `SourceSpend`, …) |
| **Data** | `data/` | Room, repositories, `SecureStore` / DataStore, SMS/paste parser, LLM client, CSV import, Sheets, receipts |
| **Services** | `sms/`, `notification/`, `location/`, `widget/`, `workers/`, `system/` | SMS ingest, classify notifications, optional location, Glance widgets, WorkManager, boot reschedule |
| **DI** | `di/AppModule.kt` | Provides `AppDatabase` + migrations 2→11 |

Empty leftover: `data/email/` (no sources).

### 2.2 Data flow

```
SMS (SmsReceiver)          ─┐
Paste / share (FAB sheet)  ─┼─► TransactionParser ──┐
CSV import wizard          ─┤                       ├─► TransactionRepository
Manual spend (FAB → Add)   ─┘ CsvStatementParser ───┘     (dedupe, classify, tab ledger)
Transfer (FAB sheet) ──► createSelfTransfer ────────┘
                                                    │
                                                    ▼
                                        Room (AppDatabase)
                                                    │
                    CashflowRepository ──► Home / MonthFlow / widgets
                    AccountRepository  ──► Accounts / AccountDetail
                              ViewModels ──► Compose screens
                              Backup / Sheets / widgets read the same store
```

- **Ingest** is dedicated: `insertFromSms`, `insertFromImport`, `insertManual` / `insertManualWithSplits` (paste saves through `insertManual` with `source = PASTE` after form review). Transfers use `createSelfTransfer` (two legs, `kind = SELF_TRANSFER`).
- **Parse:** `TransactionParser.parseSms` and `parsePastedText` share `parseSource` (deterministic + LLM merge via `LlmClient`). After paste, `inferSelfTransfer` looks for two owned account names (and transfer language). Hits open the transfer sheet; otherwise the spend form hydrates for review.
- **Home metrics:** `CashflowRepository.homeCashflowSnapshot` is one month scan: income/expense totals, lifestyle vs investment, spend/income by **category** and by **source** (account; unassigned → “Digital”), plus a 6-month trend. Self/tab transfers stay out.
- **Classify** uses `pending_classification` + `ClassificationWorker` (15 min) → notification.
- **Export:** JSON backup (`BackupRepository`) or one-way Google Sheets (`SheetsSyncService` + `SheetsSyncWorker`, 30 min, network required). Writes enqueue `sync_outbox`.
- **Widgets** refresh via `WidgetRefreshWorker` (15 min) and `FinanceApp.onCreate`.

### 2.3 Components (`AndroidManifest.xml`)

| Kind | Name | Notes |
| --- | --- | --- |
| Activity | `ui.MainActivity` | Single host; `singleTop`; `NavHost`. `ACTION_SEND` `text/plain` and `ACTION_PROCESS_TEXT` open the paste sheet. |
| Receiver | `sms.SmsReceiver` | `SMS_RECEIVED`; gated by SMS enabled + LLM ready. |
| Receiver | `system.BootReceiver` | `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `USER_PRESENT`; reschedules work. |
| Receiver | `notification.ClassificationActionReceiver` | Notification actions. |
| Receivers | `widget.*WidgetReceiver` (×5) | Overview, Transactions, AddButton, Funds (class name kept; UI says Tabs), Spending (Glance). |
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
| `funds` | Tabs (open IOUs). Kotlin `TabEntity` / `TabDao`. `budgetPaise` is the envelope / opening figure. |
| `transactions` | Ledger. String PK; `type`, `amountPaise`, `occurredAt`, `counterparty`, `categoryId`, `fundId` (Kotlin `tabId`), `accountId`, `source`, `kind`, `transferGroupId`, `splitGroupId`, `smsMessageId`, `externalRefId`, `contentHash`, `deletedAt`, `receiptUri`. |
| `fund_ledger` | Materialised tab running balance; rebuilt by `recalculateTabLedger`. Kotlin `TabLedgerEntity` / `TabLedgerDao`. |
| `location_samples` | Optional location history. |
| `pending_classification` | Queue for `ClassificationWorker`. |
| `sync_outbox` / `sync_state` | Sheets outbox and cursors. |

Room does not enforce FKs. Split children share `splitGroupId` (parent soft-deleted). Transfer legs share `transferGroupId`.

Rows with `accountId == null` are a display-only **Digital** bucket (not an `accounts` row). Home totals include them; Accounts lists real ledgers and surfaces the bucket as “Digital (no bank)”. `UNASSIGNED_DIGITAL_ACCOUNT_ID = -1` is the nav sentinel into `AccountDetail`.

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

Type-safe navigation (`ui/navigation/AppRoutes.kt`). Bottom nav: **Home / Activity / Tabs / Settings** (`FloatingBottomNav` dock + side FAB). Route ids are `tabs` / `tab`; `destinationFromNavigateExtra` still accepts legacy `funds` and `fund/{id}`.

**Add is not a hub screen.** On Home and Activity the + FAB opens a menu above the dock (56dp chips, grow-from-right). Tabs FAB still creates a tab; Settings FAB searches.

| Action | Lands on |
| --- | --- |
| **Spend** | `AddCashScreen` — debit/credit form. Amount pad opens only when the amount field is tapped (not the first screen). Empty-state / widget Add also go here. |
| **From text** | `PasteAiParseSheet` over the current tab (`AddFromTextContent`). Share / select-text use the same sheet. |
| **Transfer** | `SelfTransferSheet` over the current tab. |

Paste result: if `inferSelfTransfer` finds two owned accounts → transfer sheet prefilled; else → `AddCashScreen` with a review banner and accounts always visible.

**Home** is available balance + this-month **Expenses** / **Income** (category or source cut, pie + rows) + recent + open tabs. Reorder and half-width spans stay. Tapping a flow header opens `MonthFlowScreen` (full this-month list). A category row opens `CategoryDetail`; a source row opens `AccountDetail` (Digital uses the unassigned sentinel).

The old Overview tiles, standalone category ring, and 6-month trend block are gone. `CategoriesRoute` is the same MonthFlow surface (expenses × category).

| Screen | ~Lines | Role |
| --- | --- | --- |
| `MainActivity.kt` | 755 | Host, `NavHost`, floating nav + FAB menu, share/paste overlays, theme, onboarding gate |
| `NavigationComponents.kt` | 347 | Dock + overlay FAB menu (does not reflow the bar) |
| `HomeScreen.kt` | 349 | Greeting, pending, setup, reorderable dashboard |
| `HomeDashboardSections.kt` | 430 | Section chrome, span, drag reorder |
| `HomeFlowBreakdown.kt` | 305 | Expense/income pie + category/source cut |
| `HomeRecent.kt` / `HomeDashboardModels.kt` | 97 / 27 | Recent list; dashboard data bag |
| `MonthFlowScreen.kt` | 246 | This-month flow list (direction × category/source) |
| `TransactionsScreen.kt` | 500 | List, search, filters, export |
| `AddCashScreen.kt` | 705 | Spend form + save + review-from-AI; not a mode picker |
| `AddFromTextContent.kt` | 139 | Paste field, clipboard, Read with AI |
| `PasteAiParseSheet.kt` | 41 | Thin sheet around `AddFromTextContent` |
| `AddEntryOverlays.kt` | 66 | Hosts paste + transfer sheets from `MainActivity` |
| `SelfTransferSheet.kt` | 150 | Account-to-account transfer bottom sheet |
| `AddCashSelfTransfer.kt` | 65 | From/to account chips (used by the sheet) |
| `AddTransferContent.kt` | 80 | Transfer fields (kept; primary path is the sheet) |
| `TransactionDetailScreen.kt` | 577 | Host: view-first + dirty/save; edit is `TransactionDetailEdit` |
| `TransactionDetailEdit.kt` | 508 | Dedicated edit composable |
| `SplitTransactionScreen.kt` | 510 | Split editor destination |
| `TabsScreen.kt` / `TabDetailScreen.kt` | 464 / 256 | Tabs list + per-tab activity |
| `AccountsScreen.kt` | 363 | Ledgers, archive, Digital bucket, CSV import entry |
| `AccountDetailScreen.kt` | 247 | Per-account (or Digital) activity + filters + CSV |
| `CategoriesScreen.kt` | 23 | Thin wrapper → MonthFlow (expenses × category) |
| `CategoryDetailScreen.kt` | 246 | Category activity; optional range + type from MonthFlow |
| `CsvImportScreen.kt` | 500 | Account → file → preview → commit |
| `SettingsScreen.kt` / `SettingsDetailScreen.kt` | 400 / 104 | Hub + thin section router |
| `ui/screens/settings/*` | 64–374 each | Per-domain settings (10 files) |
| `OnboardingScreen.kt` | 858 | First-run monolith |
| `AppearanceSettingsContent.kt` | 369 | Theme studio (still under `ui/screens/`) |

Shared form: `TransactionFormState` + `TransactionFormFields` (used by Add spend and Detail edit). Widgets: Overview, Transactions, Spending, Tabs (`TabsWidget`; receiver stays `FundsWidgetReceiver` so home-screen placements survive), AddButton.

---

## 5. Repositories, workers & background

### 5.1 Repositories

| Repository | ~Lines | Role |
| --- | --- | --- |
| `TransactionRepository` | 1011 | CRUD, splits, merges, self/tab transfer, tab ledger, dedupe, classify |
| `CashflowRepository` | 237 | Read-only metrics; `homeCashflowSnapshot` is the single Home/widget/category scan |
| `AccountRepository` | 252 | Account CRUD, balances, `observeUnassignedDigital`, `syncFromBankList` |
| `CategoryRepository` | 89 | Seed + CRUD |
| `StatementImportRepository` | 234 | CSV preview / dedupe / commit |
| `BackupRepository` | 441 | JSON export/import |
| `LocationRepository` | 74 | Optional stamp + place match (`location/`) |

Accounts created/archived/restored from `AccountsViewModel` call `mirrorBankPrefs()` so Settings `bank_accounts` stays aligned. Cold start in `FinanceApp` still bidirectional-syncs bank names and repairs tab ledgers (`repairAllTabLedgers`).

### 5.2 Workers (`workers/AppWorkers.kt`)

`WorkScheduler.scheduleAll` registers three unique periodic jobs:

- `ClassificationWorker` — 15 min
- `SheetsSyncWorker` — 30 min, `CONNECTED`
- `WidgetRefreshWorker` — 15 min

### 5.3 Ingest & side paths

- SMS → `TransactionParser.parseSms` → `insertFromSms` (LLM via `LlmClient`, OpenAI-compatible `/chat/completions`).
- Paste / share → FAB **From text** or `ACTION_SEND` / `PROCESS_TEXT` → `parsePastedText` → `PasteParseResult`. Spends hydrate Add for review (`source = PASTE`). Self-transfers (two account names in the text, optionally NEFT/IMPS/RTGS/`from X to Y`) open `SelfTransferSheet`. LLM optional; without a key only bank-style deterministic text works. The LLM prompt can set `isSelfTransfer` / `toBank`; routing today is name-match on the raw text.
- Transfer → `SelfTransferSheet` → `createSelfTransfer` (not a spend).
- CSV → `CsvStatementParser` + `StatementImportRepository` → `insertFromImport`.
- Classify: `ClassificationNotifier` + `ClassificationActionReceiver` (no overlay service).
- Location: optional foreground `LocationTrackingService`.

A typical merchant SMS names **one** account and stays a debit. The parser cannot invent a self-transfer from a single-account SMS.

---

## 6. Remaining issues

Closed this pass: Settings god-file, Detail view-first + edit extract, Add as FAB menu (not a second picker screen), transfer + paste as sheets, pre-create splits removed, paste/share ingest, self-transfer hint from paste, Home tile/ring/trend de-dupe, MonthFlow + AccountDetail drill-down, Digital unassigned bucket, **`Fund`→`Tab` Kotlin/route/widget rename**.

Two open items, in order. Size work stays optional.

### 6.1 `Fund` → `Tab` rename — **done**

Kotlin types, files, ViewModels, routes, form fields, and widget UI now say **Tab** (`Tab`, `TabBalance`, `TabEntity`, `TabDao`, `TabsViewModel`, `TabsScreen`, `TabsRoute` / `TabRoute`, `TabsWidget`). No schema bump.

Left on purpose so existing installs keep working:

- Room tables `funds` / `fund_ledger` and column `fundId` (`@ColumnInfo` on `tabId`).
- Backup JSON keys `"funds"` / `"fundId"`.
- DataStore key `last_used_fund_id`.
- Home-section id `funds_summary`.
- Glance receiver class `FundsWidgetReceiver` (AndroidManifest / existing widgets).
- `destinationFromNavigateExtra` still accepts `funds` and `fund/{id}`.
- Google Sheets worksheet title stays `Funds` (QUERY / existing workbooks). Header cell is now `Tab`.

### 6.2 Host and write-path size (P2)

| File | ~Lines | Why it still matters |
| --- | --- | --- |
| `TransactionRepository` | 1011 | Writes + splits + transfers + ledger + dedupe + classify |
| `ThemeColorPicker` / `Theme.kt` | 996 / 887 | Theme heavier than the ledger |
| `SheetsSyncService` | 963 | Client + table assembly + worker glue |
| `OnboardingScreen` | 819 | First-run monolith |
| `MainActivity` | 755 | Host + FAB menu + share overlays + routes |
| `AddCashScreen` | 676 | Full spend form (no longer a mode picker) |
| `SettingsViewModel` | 460 | Profile, SMS, theme, LLM, Sheets, location, banks, backup, dev |

Do not slice Home further. Next size cut is `MainActivity` overlay/share state if it grows again, then `TransactionRepository` write vs classify vs ledger. Theme/Sheets/Onboarding are optional and must not block 6.3.

### 6.3 Email-removal leftovers (P3) — **next**

One small hygiene PR:

- Drop unused `FOREGROUND_SERVICE_DATA_SYNC` (no data-sync foreground service).
- Delete empty `data/email/`.
- Fix `SecureStore` comments that still say “email/SMS auto-import”.
- Record `1→2` as unsupported (or add a no-op) if v1 installs no longer matter. Schema JSON exists only from v10.

Parked (not in the two): `recalculateTabLedger` full-table rebuild; `observeAll()` on several screens; optional `osmdroid` / location / camera permission story; unused `AddTransferContent`; LLM `toBank` not applied in `mapExtracted`; `PASTE` not shown in Activity filters.

---

## 7. Status of the last plan

| Item | Status |
| --- | --- |
| P1.1 Split `SettingsDetailScreen` → `ui/screens/settings/` | **Done.** Router ~104 lines; 10 domain files. |
| P1.2 Extract self-transfer + draft splits from Add; Detail view-first | **Done.** `TransactionDetailView` default; `TransactionDetailEdit` for edit. Transfer is `SelfTransferSheet`. Splits only via `SplitTransactionScreen` after create. |
| P1.3 Optional: thin `SheetsSyncService` / `OnboardingScreen` | **Not started** (optional; folded under 6.2). |
| Add UX: FAB menu + paste/share + transfer sheet | **Done.** Spend / From text / Transfer above the Home/Activity FAB. Dock does not reflow. |
| Paste text through AI parser | **Done.** `TransactionSource.PASTE`, `parsePastedText`, share intents, review form or transfer sheet. |
| Self-transfer from AI/paste | **Done (heuristic).** Two owned account names (+ transfer language). Single-account SMS stays a spend. |
| P2 Home de-dupe tiles / keep reorder | **Done.** Overview tiles, standalone ring, and 6-month trend block removed. Expenses + Income (category/source). Reorder/spans kept. |
| MonthFlow + AccountDetail | **Done.** `MonthFlowRoute` / `AccountRoute`. Digital unassigned is a nav sentinel, not a fake account row. |
| P2 `Fund` → `Tab` rename | **Done.** Kotlin + routes + widget UI. SQL / backup / DataStore / widget receiver unchanged. |
| P3 `1→2` / drop `FOREGROUND_SERVICE_DATA_SYNC` / empty `data/email/` | **Not started** — now the next item (6.3). |
| P3 Leave `recalculateTabLedger` | **Deferred** (intentional). |

Verify: `./gradlew assembleDebug`. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 8. Further plan of action

**P1 — Naming**

1. `Fund` → `Tab` — **done** (Kotlin / routes / widget UI; persistence names kept).

**P3 — Hygiene (next; one PR, mechanical)**

2. Drop `FOREGROUND_SERVICE_DATA_SYNC` and empty `data/email/`.
3. Fix leftover comments (`SecureStore` “email/SMS”).
4. Document `1→2` as unsupported (or add a no-op) in `AppModule` and here.
5. Leave `recalculateTabLedger` until a tab has enough rows to hurt.

**P2 — Size (only if something still hurts)**

6. Slim `MainActivity` overlay/share state if it grows past the current host role.
7. Optional later: split `TransactionRepository` (writes vs classify vs ledger). Do not block 6.3.
8. Optional later: split `SheetsSyncService` (HTTP vs table assembly) and slice `OnboardingScreen` by step. Do not block product work on theme files.

**Parked (do not schedule unless a real miss shows up)**

9. Delete unused `AddTransferContent`; apply LLM `toBank` in `mapExtracted` if paste transfer misses still happen.
10. Show `PASTE` in Activity filters / detail source line if users cannot tell pasted rows from manual.
11. No new ingest surface (email, notification listener, etc.).

---

## 9. Bottom line

| Area | Status |
| --- | --- |
| Layering / DI / navigation | Conventional, one activity, `singleTop` + share filters |
| Cashflow model | Sound; transfers/splits excluded; Home is one snapshot scan |
| Database v11 | Migrations 2→11 registered; no `1→2`; schema export v10–v11 |
| Ingest | SMS + CSV + manual spend + paste/share + self-transfer sheet |
| Add UX | FAB menu on Home/Activity; spend form; paste + transfer overlays; amount pad on tap |
| Home | Balance + expenses/income (category or source) + recent + tabs; reorder kept |
| Drill-down | MonthFlow → CategoryDetail / AccountDetail (incl. Digital unassigned) |
| Structure | Settings split done; Detail view-first; repo / Sheets / Onboarding / host still large |
| Naming | `Tab` in Kotlin / routes / widget UI; SQL tables still `funds` / `fund_ledger` |
| Security | Encrypted prefs; secrets opt-in on backup; cleartext HTTP blocked; LLM is SMS + paste parse |

**Next:** one hygiene PR (6.3): drop `FOREGROUND_SERVICE_DATA_SYNC`, delete empty `data/email/`, fix `SecureStore` comments, record `1→2` as unsupported. Size work only if the host or write path keeps growing.
