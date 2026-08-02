# Rupiyah — Bug, UI, and Simplify Plan

**Status:** Open action plan  
**Sources:** Local code review (2026-08-03), UI review, KISS review  
**Scope:** Cashflow remodel (Debit/Credit, accounts, tabs/splits, CSV import, email removal) plus UX/architecture debt  

Related: [CASHFLOW.md](./CASHFLOW.md), [USER_GUIDE.md](./USER_GUIDE.md), [ARCHITECTURE.md](./ARCHITECTURE.md)

---

## 0. Executive summary

The **cashflow model is largely sound**: parent stays bank truth, splits sum-check, reports use effective allocations, self/tab transfers are excluded from lifestyle metrics, Room migrations 6→7→8 line up with tests.

**Ship-blocking risks** are mostly **CSV import + account ledger integrity**.

**Product/UX debt** is larger than the domain design: the app still carries an **envelope-funds + email AI** product layered under the new **Debit/Credit + accounts + tabs + SMS/CSV** model. Users feel overcomplication because of **unfinished cutover**, not because the new model is inherently complex.

| Track | Focus |
| --- | --- |
| **A. Correctness bugs** | CSV direction, dedupe collision, account balances, tab-transfer split gate |
| **B. Import & polish** | Medium confidence UX, ref HIGH branch, archived account names, deps |
| **C. UI / language** | Tab vs Fund/envelope, Debit vs Income, dead Gmail surfaces |
| **D. KISS simplify** | Shrink Home/Add/Detail, one account truth, kill dual dialects |

---

## 1. Code review — bugs and issues

Severity: **bug** · **suggestion** · **nit**. All open unless marked done.

### 1.1 Bugs (fix first)

#### B1 — Amount-only CSV rows default to CREDIT

| | |
| --- | --- |
| **Severity** | bug |
| **File** | `app/src/main/java/com/krtky/financetracker/data/importcsv/CsvStatementParser.kt` (~265) |
| **Problem** | `resolveDirectionAndAmount` prefers `parseSignedMoneyPaise` before the absolute path. A normal positive `Amount` with no type/debit/credit columns succeeds and defaults to `TransactionType.CREDIT`. Safer “unknown direction → skip” path is dead for typical positive amounts. Spends can import as credits and inflate income/balances. |
| **Fix** | Treat amount as signed only with explicit sign or parentheses; otherwise require type/debit/credit (or description keyword); skip if ambiguous. Unit test: “Amount-only, no type → null / skip”. |
| **Status** | open |

#### B2 — Multiple statement lines can HIGH-match one existing txn

| | |
| --- | --- |
| **Severity** | bug |
| **File** | `app/src/main/java/com/krtky/financetracker/data/repository/StatementImportRepository.kt` (~89) |
| **Problem** | Preview matches each row independently against a fixed `existing` list. Two same-day same-amount Zomato debits can both `HIGH` / `SKIP_MERGE` against one SMS. Commit enriches that id twice; second bank movement never inserts — silent ledger loss. No within-file collision tracking. |
| **Fix** | After HIGH match to an existing id, mark that candidate consumed. Extra same-key lines → LOW/IMPORT (or MEDIUM). Optionally flag near-duplicate pairs inside the file in preview. |
| **Status** | open |

#### B3 — Account balances ignore paymentMethod-only rows

| | |
| --- | --- |
| **Severity** | bug |
| **File** | `app/src/main/java/com/krtky/financetracker/data/repository/AccountRepository.kt` (~50) |
| **Problem** | Balances only sum `accountId == account.id`. Import/history still match `accountId IS NULL AND paymentMethod = :accountName`. Legacy/SMS rows affect CSV dedupe and home name-grouped balances but **not** Accounts-screen totals → understated balances. |
| **Fix** | Align `balancesFlow` with `getForAccount` (include null-`accountId` + matching paymentMethod), and/or one-shot repair to backfill `accountId` for active account names after migration. |
| **Status** | open |

#### B4 — Split UI allows tab transfers (repo rejects)

| | |
| --- | --- |
| **Severity** | bug |
| **File** | `TransactionDetailScreen.kt` (~389), `SplitTransactionScreen.kt` |
| **Problem** | `canSplit = !t.isSelfTransfer()` only. Tab-transfer legs can open the split editor; `setSplits` rejects both self and tab transfers. User gets a save-only failure. |
| **Fix** | Gate with `!isSelfTransfer() && !isTabTransfer()` on detail + split screen (match repo + spec). |
| **Status** | open |

### 1.2 Suggestions

#### S1 — Incomplete HIGH branch for statement ref on blank existing ref

| | |
| --- | --- |
| **Severity** | suggestion |
| **File** | `ImportDedupe.kt` (~69) |
| **Problem** | Comment intends HIGH when statement has a new ref and existing ref is blank; code still requires `descScore >= 0.72f`. Ref-attach arm never matches alone. |
| **Fix** | Amount/type/window + new ref on blank existing → HIGH (“attach statement ref”), optional modest desc threshold. |
| **Status** | open |

#### S2 — Medium confidence defaults to silent skip

| | |
| --- | --- |
| **Severity** | suggestion |
| **File** | `StatementImportRepository.kt` (~91) |
| **Problem** | Spec: Medium should ask the user. Code defaults MEDIUM → `SKIP_MERGE`. Preview has overrides / “Import uncertain rows too”, but one-tap Import skips uncertain rows — easy under-import vs sparse SMS. |
| **Fix** | MEDIUM = needs decision before commit, **or** default MEDIUM to import and only auto-skip HIGH. Align USER_GUIDE + CASHFLOW. |
| **Status** | open |

#### S3 — Dead Gmail / bank-email UX after email removal

| | |
| --- | --- |
| **Severity** | suggestion |
| **File** | `SettingsScreen.kt` (~124), `SettingsViewModel`, setup checklist, onboarding |
| **Problem** | Ingest services removed; Settings still talks about connecting Gmail / checking mail. Poll/sign-in no-op. Conflicts with SMS + CSV + manual product. |
| **Fix** | Remove or replace bank-email section; strip trusted-sender/poll chrome that does nothing. Setup = SMS + bank account + first txn/CSV. |
| **Status** | open |

#### S4 — Partial splits leave uncategorized spend without classify prompt

| | |
| --- | --- |
| **Severity** | suggestion |
| **File** | `TransactionRepository.kt` `setSplits` (~421) |
| **Problem** | Parent stamped CLASSIFIED if **any** split has a category; remaining lines hide under Uncategorized with no pending prompt. Spec allows this; UX weak. |
| **Fix** | Keep PENDING until every split categorized (or explicit leave-uncategorized), or surface “incomplete split categories” in pending/detail. |
| **Status** | open |

#### S5 — Unused JavaMail dependencies

| | |
| --- | --- |
| **Severity** | suggestion |
| **File** | `app/build.gradle.kts` (~176) |
| **Problem** | `android-mail` / `android-activation` remain after email client deletion. |
| **Fix** | Remove if unused; verify with dependency insight / release build. |
| **Status** | open |

#### S6 — Archived accounts can show blank account name

| | |
| --- | --- |
| **Severity** | suggestion |
| **File** | `TransactionRepository.observeTransactions` (~90) |
| **Problem** | Joins only active accounts; pure `accountId` rows without paymentMethod can show blank name after archive. |
| **Fix** | Hydrate names from all accounts (or id→name including archived). |
| **Status** | open |

### 1.3 Nits

#### N1 — Dead filter in CSV line split

| | |
| --- | --- |
| **Severity** | nit |
| **File** | `CsvStatementParser.kt` (~409) |
| **Problem** | `.filter { it.isNotEmpty() \|\| true }` always keeps lines. |
| **Fix** | Single blank-line filter. |
| **Status** | open |

#### N2 — AccountsViewModel mutators skip bank prefs sync

| | |
| --- | --- |
| **Severity** | nit |
| **File** | `AccountsViewModel.kt` (~37) |
| **Problem** | `addAccount` / archive / restore don’t mirror Settings `syncBankPrefsFromAccounts()`. Latent if those methods gain callers; cold start can re-sync from prefs. |
| **Fix** | Share prefs-mirror helper or remove unused mutators. |
| **Status** | open |

---

## 2. UI review

### 2.1 Conflicting product language (highest UX risk)

| Spec / new model | What the UI still says |
| --- | --- |
| **Tabs** (open IOUs) | Nav: “Tabs”; `FundsScreen` subtitle still **“Envelope budgets you can credit and spend from”** |
| Open balance: they owe you / you owe them | Cards still use **remaining / spent / overspent** envelope chrome (`remainingRatio`, budget bars) |
| **Debit / Credit** on forms | Home still **income / spent / net**, “Expense activity”, `MonthlyExpenseChart` |
| Email **removed** | Settings Gmail copy, setup checklist Gmail/AI row, onboarding Gmail steps |
| **Accounts** = ledger truth | Home also shows **Cash vs Digital** from name-grouped balances — different path than Accounts screen |

Same concept as Tab / Fund / envelope depending on screen → unstable mental model.

### 2.2 Home is overloaded

Currently stacks: greeting + **layout edit mode** (reorder + half-width spans + drag), pending chip, setup checklist (legacy Gmail framing), hero net, overview tiles, category pie, monthly trend, open tabs, recent (category filter), investment metrics.

Layout editor alone (`HomeSection` parse/serialize + half-width pairing + drag) is power-user chrome on the primary screen.

**Target Home (fixed order):** Net · Pending · Accounts strip · Spend by category · Recent · Open tabs. Drop reorder/span for v1.

### 2.3 God screens

| Screen | ~Lines | Packed concerns |
| --- | ---: | --- |
| `TransactionDetailScreen` | ~1300 | View + edit, dirty leave, amount pad, date/time, legacy account resolve, tab/fund, location, receipt, OSM map, splits |
| `AddCashScreen` | ~980 | Debit/credit + **self-transfer** + **draft splits** + full-screen split editor |
| `SettingsDetailScreen` | ~1300 | Many settings domains |
| `ThemeColorPicker` | ~996 | Theme system larger than core money flows |

**Target patterns:**

- **Add** = amount + Debit/Credit + account + name + category (+ optional tab).
- **Self transfer** = separate mode/screen (not mixed fields on Add).
- **Split** only after parent exists (`SplitRoute`); don’t compose splits on Add.
- **Detail** = read-only first; light edit for safe fields; “More” for receipt/location.

### 2.4 Tabs UI still sells the wrong product

`FundsScreen` / `FundBalance` still speak **budget left, spent ratio, overspent**. Spec: open balance only (`opening + debits − credits`). Math may be right while chrome lies (progress bars vs “Ravi owes ₹2,400”).

### 2.5 Classification has too many front doors

Home chip, classify sheet/overlay, detail, post-import. Fine if one primary path is obvious; setup still points at AI/Gmail while product is SMS + CSV + manual.

### 2.6 What’s already clean

- **CSV import wizard** (account → file → preview → done) is appropriately linear.
- **Bottom nav** (Home / Activity / Tabs / Settings) is a sane skeleton.
- **Split editor as its own destination** is good — keep it there, don’t also embed deeply in Add.

---

## 3. KISS review

### 3.1 What the domain got right

- Parent = bank truth; split sum validation; reports use **effective allocations** (never parent + splits together).
- Self/tab transfers excluded from lifestyle metrics.
- Small pure helpers (`SplitRules`, `needsClassification`, `amountLocked`).
- Spec principles in `CASHFLOW.md` are simple; implementation has not finished deleting the old world.

### 3.2 Accidental complexity (not essential)

**Two eras of data model in parallel**

- `merchant` + `counterparty`
- `paymentMethod` + `accountId`
- `budgetPaise` as “opening” + envelope helpers on `FundBalance`
- `TransactionSource.EMAIL`, `TrustedSender`, `EmailProcessStatus`, Gmail settings APIs that no-op
- `Fund` / `FundsScreen` / `FundRoute` while product language is Tab

Every feature must speak both dialects.

**Two account balance truths**

- Home: `observeAccountBalances()` → Cash / Digital / bank **names**
- Accounts: `AccountRepository` → `opening + credits − debits` by `accountId`
- Import also matches null-`accountId` + `paymentMethod`

One ledger path is KISS. Two paths is how balances “feel wrong.”

**Dual metrics on Home**

`monthlySummary` **and** `cashflowMetrics` **and** `categorySpend` with fallback (`lifestyleByCategory.ifEmpty { categorySpend }`). Prefer one cashflow snapshot for the month.

**God objects**

| Type | ~Lines | Smell |
| --- | ---: | --- |
| `TransactionRepository` | ~1262 | CRUD + splits + funds + cashflow + trends + classify |
| `SettingsViewModel` | ~538 | Profile, SMS, theme, LLM, Gmail (dead), Sheets, location, banks, backup, dev |
| Home layout system | non-trivial | Reorder + span grid for a 4-tab personal app |

**Feature surface vs “personal cashflow”**

| Essential (v1) | Heavy / defer |
| --- | --- |
| Accounts, Debit/Credit, categories | Full theme studio |
| SMS, CSV, classify | Location + OSM map |
| Optional tabs & splits | Sheets (keep backup first) |
| Backup | Email UI ghosts, trusted senders |
| | Home layout editor |
| | Envelope fund chrome |
| | AI setup as primary checklist |

### 3.3 Spec vs code drift

Spec: “Tabs and Splits are optional power tools.”  
UI: Funds tab is primary nav; envelope language; split embedded in Add; detail is a kitchen sink.

That’s product complexity, not just code. Debt is mostly **unfinished migration**, not over-designed greenfield.

---

## 4. Ordered execution plan

Do in order; stop feature work that widens dual-dialect surface until A–C land.

### Phase A — Ship-blocking correctness (bugs B1–B4)

1. **B1** CSV direction: no CREDIT default on unsigned amount-only rows + tests.  
2. **B2** Import dedupe: consume HIGH match candidates; within-file collision.  
3. **B3** Account balances = same set of txns as import/history (or backfill `accountId`).  
4. **B4** Block splits on tab transfers in UI.

### Phase B — Import UX + small polish

5. **S2** Medium confidence needs decision (or safer default).  
6. **S1** HIGH attach statement ref when existing ref blank.  
7. **S6** Account names for archived accounts in lists.  
8. **S5** Remove JavaMail deps.  
9. **N1**, **N2** quick cleanups.

### Phase C — Language & dead product surfaces (UI)

10. **One vocabulary:** UI = **Tab** only; copy = open balance / they owe you / you owe them. Kill envelope subtitle, overspent-as-budget primary chrome, widget “Funds” if product is Tabs.  
11. **Delete dead email surfaces:** Settings Gmail, setup Gmail checklist, onboarding Gmail, poll/sign-in no-ops, unused trusted-sender chrome (**S3**). Setup → SMS on → add bank → first txn / CSV.  
12. **Debit/Credit language on Home:** Credits / lifestyle debits (or In / Out), not Income/Expense if forms already use Debit/Credit.  
13. **S4** Partial-split classification UX (pending or incomplete indicator).

### Phase D — Structural simplify (KISS)

14. **One account model in UI:** always `accountId`; one balance formula; Home accounts strip = same truth as Accounts (e.g. top 3). Drop Cash/Digital name heuristics unless derived purely from `Account.kind`.  
15. **Home:** fixed sections; remove layout editor / half-width grid for v1.  
16. **Add:** simple entry only; Self transfer separate; split only from detail.  
17. **Detail:** view-first; lighter edit; optional receipt/location under More.  
18. **When touching code:** split cashflow queries out of `TransactionRepository`; split Settings VM (Import / Appearance / Data); stop growing god files.  
19. **Longer term:** collapse legacy fields (`merchant`/`paymentMethod` after backfill), rename Fund* identifiers to Tab* when cheap, trim `FundBalance` envelope API from primary UI.

### Phase E — Docs & guide

20. Align `USER_GUIDE.md` with email-removed product and Medium import behavior.  
21. Mark `GMAIL_IMAP.md` historical or remove from “how to use” paths.  
22. Keep `CASHFLOW.md` as source of truth; update only if product decisions change (e.g. Medium default).

---

## 5. Suggested PR slicing

| PR | Contents | Unblocks |
| --- | --- | --- |
| **PR1** | B1 + B2 + tests | Safe CSV import |
| **PR2** | B3 + optional accountId backfill | Honest account balances |
| **PR3** | B4 + S5 + N1 | Small correctness + size |
| **PR4** | S3 + setup checklist + hide Gmail settings | Honest product surface |
| **PR5** | Tab copy + FundsScreen IOU chrome (not envelopes) | Mental model |
| **PR6** | S1 + S2 Medium UX | Import reconciliation |
| **PR7** | Home fixed layout; drop section editor | Cognitive load |
| **PR8** | Add/Detail/self-transfer split simplification | Primary flow KISS |
| **PR9** | Repo/VM splits (no behavior change) | Maintainability |

---

## 6. Out of scope (do not expand until A–D settle)

- New portfolio / PnL / XIRR  
- Full history multi-account CSV migration tooling beyond current wizard  
- Rebuilding email ingest  
- More Home customization  
- Theme system expansion  

---

## 7. Checklist (track completion)

### Bugs

- [ ] B1 CSV amount-only direction  
- [ ] B2 Dedupe candidate consumption  
- [ ] B3 Account balance vs paymentMethod rows  
- [ ] B4 Tab transfer cannot open split UI  

### Suggestions / nits

- [ ] S1 Ref HIGH branch  
- [ ] S2 Medium confidence UX  
- [ ] S3 Dead email UI  
- [ ] S4 Partial split classify UX  
- [ ] S5 Remove JavaMail  
- [ ] S6 Archived account names  
- [ ] N1 CSV filter  
- [ ] N2 Accounts prefs sync  

### UI / KISS

- [ ] Tab vocabulary + IOU chrome (no envelope primary UX)  
- [ ] Setup without Gmail  
- [ ] Home language Debit/Credit aligned  
- [ ] One account balance path for Home + Accounts  
- [ ] Home fixed layout (no editor)  
- [ ] Add simplified; self transfer separate; split post-create only  
- [ ] Detail view-first / lighter edit  
- [ ] USER_GUIDE + GMAIL doc status  

---

## 8. Bottom line

| Area | Verdict |
| --- | --- |
| Core cashflow idea | Simple enough; mostly right |
| Correctness | Fix CSV + balances + split gates before more features |
| UI storytelling | Overcomplicated — Fund/Tab/envelope + Income/Debit + email ghosts |
| Home / Add / Detail | Too many jobs per screen |
| CSV import UX | Appropriately structured; fix correctness first |
| Theme / settings / integrations | Heavier than the money model |
| KISS debt | Mostly unfinished migration, not greenfield over-design |

**Priority:** deletion and renaming + import/ledger bugs, **before** new abstractions or features.

---

*Document assembled from local `/review` findings and follow-up UI + KISS review. Update checkboxes as work lands.*
