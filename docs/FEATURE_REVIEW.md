# Rupiyah — Feature review (take 3)

**Date:** 2026-08-20 · **App version:** 1.4.1 (DB **v12**)

Replaces take 2 (2026-08-18). Product intent is unchanged. This pass is against the current tree after tab list/settle work and schema v12.

**Correct intent** (unchanged)

- Rupiyah **replaces** the Streamlit ledger. Streamlit is the prototype whose *mistakes* this app exists to fix.
- **People as categories was the bug.** Tabs are the fix (Khushboo / trip / loan as an IOU, category stays Food).
- **Keep rich facts when you can** — name, account, tab, place, receipt, note. Simple *home*, not a thin row.
- **Home is simple cashflow.** Lifestyle vs people, investment-by-name, and the 6-month trend are leftover. Delete them; do not finish them.
- **SMS classify notification is the primary review path.** A “needs review” list is the backup for missed notifications.
- **Auto-assign merchant → category (and tab when it repeats) is wanted.**
- **PDF import is wanted** if it is worth the pain. Assessment below is unchanged: not trivial on-device, not impossible for OneCard specifically.

---

## 0. What changed since take 2 (code, not intent)

| Area | Take 2 said | Now |
| --- | --- | --- |
| Tab schema | `budgetPaise` / “opening as envelope limit” | **Gone.** DB v12 dropped the column. Any stored opening became a `TAB_TRANSFER` row. Open = **debits − credits**. |
| Tab list UI | Progress bar, gray footer, 3–4 stats, `BudgetStyleTabCard` | **Simple:** Net owed hero; **open non-zero** cards (name + amount); **settled** and **archived** as quiet lists below. Manage sheet **renames**. Empty strings still named `empty_funds_*`. |
| Settle | Yes/no sheet → `settleTab()` inserts a silent txn | Checkmark opens **Add** with category **Settlement**, tab, amount, type (credit if they owe you / debit if you owe them), note `Settled · {name}`. User still picks account. Repo `settleTab()` is unused by UI. |
| Archive | No list / restore | **Archived list + restore** on the tabs screen; detail shows Unarchive when archived. `observeTab(id)` so archived detail still loads. |
| Transfer leftover UI | `AddTransferContent` unused | **Removed.** Self-transfer sheet remains. |
| Version | DB v11 | **v12** (same app versionName 1.4.1) |

Merchant memory, Home residual metrics, SMS gated on LLM, README/USER_GUIDE drift, two account doors: **not done**.

---

## 1. What this product is

A phone-native Indian ledger: record every debit/credit, know **what / who / which account / where**, and glance at this month.

It is not a budget app, not a portfolio, not a Splitwise clone that hides spend, and not a Google Sheets satellite.

**Jobs**

| Job | How |
| --- | --- |
| Catch a spend as it happens | Bank SMS → draft + heads-up classify notification |
| Catch a spend you typed or forwarded | Manual add, paste/share text |
| Catch a month you missed | CSV (and maybe PDF) into one account, then review leftovers |
| Keep people-money honest | Assign a **Tab**. Category stays the real spend type. Settle via a real transaction. |
| Remember next time | Merchant memory (**not built**; only last-used category/tab on Add, plus “most-used tab for this category”) |
| Glance | Home: balance, this-month expenses & income, recent, open tabs |

Streamlit did job 3 for one card, with the wrong taxonomy. Rupiyah already does jobs 1, 2, 4, and 6. Job 5 and a better 3 are still the remaining product work.

---

## 2. What to take from Streamlit — and what to leave

Unchanged from take 2. The 697-row `master.csv` is evidence of *habits*, not a schema to clone.

| Streamlit did | Verdict in Rupiyah |
| --- | --- |
| Debit / Credit, not Expense / Income | **Keep** (forms already do this; Home still says Expenses / Income) |
| Name / remarks as the merchant | **Keep** (`counterparty`) |
| Monthly statement ingest + dedupe | **Keep and extend** (CSV exists; PDF optional) |
| Recency-weighted merchant → category | **Bring over** — this is still the missing piece |
| Surface only rows that need a human | **Keep, but inverted:** notification first; list for what you missed |
| People as categories (KHUSHBOO, KG, …) | **Do not bring back.** That is why Tabs exist |
| One file = one card, no accounts | **Fixed** — accounts + self-transfer |
| No place | **Fixed** — stamp place when location is on |
| Desktop-only, monthly batch | **Fixed** — live SMS + phone add |

If a future import of `master.csv` lands, person-categories should become **tabs**, not categories. FOOD/Zomato stays Food. KHUSHBOO/Blinkit becomes Food + tab Khushboo.

---

## 3. Category comparison (short)

Unchanged: Walnut capture + Money Manager ledger + Tabs. Do not grow a lifestyle/investment dashboard.

---

## 4. Current feature map (honest)

**Working and aligned**

- Manual debit/credit add, account, category, name, tab, note, receipt
- Self-transfer (excluded from cashflow)
- SMS → parse → draft → classify notification (place stamped when location is on)
- Paste / share text → review form or transfer sheet
- CSV import with New / Duplicate / maybe-duplicate
- Accounts with archive and opening balance
- **Tabs as open IOUs:** list is Net owed + open non-zero; settled/archived below; rename; archive/restore; settle through Add (Settlement)
- Splits after save (FD maturity, mixed bank lines) — still **not** on the add form
- Home: balance, expense/income by category or source, recent, open tabs
- Activity search + filters + CSV export
- JSON backup, optional Sheets, widgets, theme
- Place on the transaction + optional tracking so SMS can match a recent point

**Built but residual — remove from product**

| Thing | Where | Why it is residual |
| --- | --- | --- |
| Lifestyle vs Investment metrics | `CashflowMetrics`, `homeCashflowSnapshot` | Home does not show them; you do not want that cut |
| Investment-by-name | USER_GUIDE only | Never a Home section; do not add it |
| 6-month trend | Computed every Home/widget load | Home dropped the chart; Overview widget still uses it for “vs last month” |
| `fundBalance` on Home data | `HomeDashboardData` | Still calculated and passed in; **never read** by dashboard sections (Home tiles sum tabs themselves) |
| User guide Home bullets | Lifestyle / credits / investment by name | Describes the old dashboard |
| `settleTab()` | `TransactionRepository` | UI no longer calls it; keep only if you want a silent settle later |

**Half-renamed / leftover DNA**

- Room `funds` / `fund_ledger` / `fundId`, backup key `"funds"`, DataStore `last_used_fund_id`, home id `funds_summary`, widget receiver `FundsWidgetReceiver`, Sheets tab **Funds**, strings `empty_funds_*` / `home_funds_remaining`
- Seed category **Family** (pets icon) — people-as-category leftover next to Tabs
- Seed category **Transfer** next to `SELF_TRANSFER` kind
- Seed **Settlement** is now **used** (tab settle default). Do not drop it.
- README / ARCHITECTURE / GMAIL / SECURITY still describe email ingest and envelope Funds

`budgetPaise` / `limitPaise()` / `BudgetStyleTabCard` are **gone**. Do not revive them.

---

## 5. Issues in existing features

### 5.1 Home and metrics — simplify, don’t complete

Home UI is already the simple product: available balance, this-month expenses, this-month income, recent, open tabs.

The *code under it* still thinks it is the old dashboard:

- Every snapshot special-cases a category named `"Investment"`.
- It builds lifestyle/invested/redeemed lists nobody renders.
- It walks six months for a trend Home does not show (the Overview widget is the only consumer).
- `HomeDashboardData.fundBalance` is still a dead field.

**Fix:** `homeCashflowSnapshot` should return month income, month expense, by category, by source. Delete `CashflowMetrics` (or stop calling it). Drop `computeMonthlyTrend` from the Home path; if the Overview widget needs “vs last month”, compute two months there, or drop the comparison. Update USER_GUIDE. Stop talking about lifestyle in repository comments.

Do **not** exclude Tab spends from the expense pie. A Zomato on Khushboo’s tab is still Food spent this month. The tab balance answers who owes. Mixing those two questions is how Streamlit went wrong.

### 5.2 Tabs — model is right; costume is thinner

User-facing story now matches the code:

- Name only (optional opening was a txn; no envelope).
- Open = debits − credits.
- List: Net owed; non-zero active; settled + archived below.
- Settle = a real classified transaction (Settlement), not a silent ledger hack.
- Manage = rename + archive.

What still fights the story:

- Persistence, Sheets, widget receiver, empty-state **resource names** still say Fund
- Home open-tabs tile string `home_funds_remaining`
- Tests / comments that still talk like grocery envelopes (less than take 2, but DNA remains)

None of this requires a schema migration. Keep SQL `funds` if you do not want a rename migration. Finish **language**: strings, Sheets header, widget class name, comments.

Classify sheet already asks for category **and** tab. Merchant memory should learn both.

**Settle note:** the Add form is the right surface. Do not bring back the yes/no sheet. `settleTab()` can be deleted once you are sure nothing else calls it.

**Archived vs settled:** settled is zero-balance, still active. Archived is hidden from the main list. Both can coexist; archive is not useless if you want old people/pots off the settled pile.

### 5.3 Capture is good; memory is missing

SMS + paste + CSV + manual is the right set. The hole is **you re-teach Zomato every time**.

What exists today is *not* merchant memory:

- Last **global** category / tab / payment on Add (`last_used_*`)
- “Most-used tab for this **category**” (`getRecommendedTabForCategory`)

That is last-form-state, not “Blinkit → Groceries (+ Khushboo)”.

Suggested rule order (same as a good Walnut):

1. Exact / normalised Name last used → category (and last tab if any)
2. Small hard list for unambiguous names (Zomato, Uber, Netflix)
3. SMS/LLM/CSV hint
4. Else leave uncategorised → notification (live) or needs-review (batch)

SMS should not require an AI key for clean bank templates. LLM stays the fallback for messy text. **Today the SMS toggle is still gated on `llmReady`** (`SmsSettingsContent`). That is still the main live-path fragility.

### 5.4 Classify: notifications first, list second

Plumbing is still there: pending queue, heads-up, tap to classify, Home pending banner.

Unchanged issues:

- CSV import still `scheduleClassification` for uncategorised rows (`insertFromImport`). A 80-row statement can still fan out notifications. Batch should **skip** the notifier and land in needs-review only.
- Live SMS: one notification per payment is correct.
- Needs-review (Home banner + Activity) is the inbox for missed taps. Make that filter obvious; do not add a fourth classify surface.
- Auto-assigned rows (once merchant memory exists) should not notify at all, or notify as “saved” without demanding a tap.

### 5.5 Place is a feature, not chrome

Unchanged. Keep SMS attach + 15-minute buffer. Do not grow a map product. `placeName` is not surfaced on Activity list cards.

### 5.6 Accounts still have two front doors

Settings → Bank accounts and the Accounts screen both manage the same ledgers. Prefs `bank_accounts` is mirrored with Room. `isCash` duplicates `AccountKind.CASH`. “Digital (no bank)” is a display bucket for unassigned rows.

Fine as migration; confusing as a permanent third account. New SMS/CSV rows should always land on a real account when the name matches.

### 5.7 Docs and naming still describe the previous app

**Worse than the app.** README still: Gmail/IMAP, envelope Funds, spending ring, expense/income, “fit my budget.”  
USER_GUIDE: “investments by name”; Home metrics still Lifestyle / Investment by Name; AI required for SMS.  
GMAIL_IMAP.md / SECURITY Gmail paragraph / ARCHITECTURE email path: dead.  
Sheets workbook: Subcategory (does not exist), Funds, lat/long as first-class columns.

This will make the next agent rebuild email and lifestyle.

### 5.8 Other concrete bugs / leftovers

- `PASTE` is still not an Activity source filter (enum exists; filter UI is type / payment / category / time)
- LLM `toBank` is parsed and ignored (self-transfer from paste uses a different infer path)
- Categories *route* is still a thin alias of MonthFlow; Settings has the real editor
- Investment special-case is a string match on category name
- Backup JSON can include API keys (say so in the export UI, default off)
- `AddTransferContent` **fixed** (file gone)

---

## 6. Inconsistencies

| Topic | README | USER_GUIDE | App you want | App that exists |
| --- | --- | --- | --- | --- |
| Email | Primary ingest | Removed | Gone | Gone (docs lie) |
| People | Funds / budgets | Tabs | Tabs | Tabs UI + Fund storage names |
| Home | Ring + funds | Lifestyle + investment by name | Simple month cashflow | Simple UI + residual metrics code |
| Form words | Expense, merchant, fund | Debit, Name, Tab | Debit, Name, Tab | Forms good; Home says Expenses |
| Review | Classify overlay | Prompt / Activity | SMS notif, then needs-review | Notif + banner + sheet; **CSV still notifies** |
| Place | Mentioned | Not in the short guide | Keep | Works if location on; quiet on Activity |
| Tabs settle | — | Open balance only | Real txn via Add | **Done** (Add prefill) |
| Tab opening | Envelope | Optional opening | Txn stream only | **Done** (v12) |

---

## 7. Duplications

| Concept | Copies | Keep |
| --- | --- | --- |
| Account list | Room + prefs string + Settings banks + Accounts screen | Room + one UI |
| Cash | `AccountKind` + `isCash` + Sheets column | Derive from account |
| People | Tabs (correct) + category Family | Tabs only; drop Family from seed or treat as a normal category, not a person |
| Transfer | kinds + category Transfer | kinds |
| Add transfer UI | was sheet + leftover file | **sheet only** (leftover gone) |
| Month-by-category | Home pie, MonthFlow, Categories alias | Home → MonthFlow |
| Classify | worker, notif, Home banner, sheet | notif for live; banner/Activity for missed |
| Export | Activity CSV, JSON backup, Sheets | backup for restore; CSV for archive; Sheets optional and slimmer |
| Tab settle | `settleTab()` + Add prefill | **Add prefill**; delete unused helper |

---

## 8. Over-complexity (revised)

Still too much relative to “simple home, rich row”:

- Theme studio larger than the write path (park, don’t productise)
- Five widgets (Add + this-month spend is enough) — Add, Overview, Spending, Tabs, Transactions
- Sheets as a 20-column second schema
- Onboarding that configures sensors before the first account
- 21 system categories. **Settlement now earns its slot.** Family / Transfer / Dividend / Tax / Fees / Professional are the ones to question against how you actually tag.

**Not over-complexity:**

- Place / optional location service
- Tabs as a first-class object (including settle-as-txn, archive, rename)
- Receipts, notes, splits
- Classify notifications for live SMS

---

## 9. Missing features that still add value

### Do next

1. **Merchant memory**  
   Persist last category (and last tab) per normalised Name. Use it on SMS, paste, CSV, and manual add.

2. **Needs-review as the missed-notification inbox**  
   Activity (or Home banner only) filtered to uncategorised. CSV/PDF import goes here, not to 80 notifications.

3. **SMS without an API key** for ordinary bank templates; LLM optional. Ungate `SmsSettingsContent`.

4. **One-account UI** and kill the prefs mirror.

5. **Delete residual metrics** and fix docs so lifestyle / investment-by-name / 6-month Home cannot come back by accident.

6. **Finish Tab naming** in strings, Sheets header, empty states, widget receiver. Keep SQL `funds` if you do not want a migration.

### Worth it if cheap

7. **CSV preset for old `master.csv`** as a *migration* into Tabs (person categories → tab).

8. **Show place on Activity / detail** more clearly.

9. **Credit-card cycle day** on an account.

### PDF import — difficulty

Unchanged from take 2. Do **not** block the app on PDF. CSV is the generic path. OneCard PDF is a few-day issuer-specific port, not an hour. No generic Indian-bank PDF engine.

### Do not add

- Lifestyle vs people on Home
- Investment-by-name / XIRR / stocks
- Envelope budgets / progress bars on tabs
- Silent settle without the Add form
- Email ingest
- Bank Account Aggregator login
- More classify UIs
- A map explorer

---

## 10. Keep / fix / shrink / add

| Feature | Verdict |
| --- | --- |
| Manual debit/credit + Name + tab | **Keep** |
| Place stamp + optional tracking | **Keep** |
| SMS + classify notification | **Keep**; don’t gate on LLM |
| Needs-review inbox | **Keep / make obvious**; use for batch + missed notifs |
| Tabs as IOUs + settle via Add + archive | **Keep**; finish Fund→Tab language |
| Accounts | **Keep**; one UI |
| CSV + dedupe | **Keep**; mute classify notifs on batch |
| Paste/share | **Keep** |
| Splits after save | **Keep**, not on the add form |
| Home pies + drill-down | **Keep** as simple month totals (include everything except transfers) |
| Residual lifestyle / investment / 6-month Home metrics | **Remove** |
| Merchant memory | **Add** |
| OneCard PDF | **Add only** as an issuer-specific port, after memory + docs |
| Sheets | **Shrink** to live fields; rename Funds → Tab |
| Widgets | **Shrink** |
| Location as a map product | **Don’t grow** |
| Family / Transfer seed categories | **Fix** |
| Settlement seed | **Keep** |
| Email / Funds / lifestyle docs | **Fix or delete** |
| Unused `settleTab()` | **Delete** once unused is confirmed |

---

## 11. Product sentence

> **A phone ledger: catch the rupee (SMS, paste, statement, hand), remember Name → category/tab, stamp place when you can, put people on Tabs, and show a simple month.**

Streamlit’s file is the migration source. It is not the destination.

---

## 12. Order of attack

1. **Delete residual Home metrics** (lifestyle, investment cuts, unused `fundBalance`, trend off the Home path) and fix USER_GUIDE / README so they cannot be “finished” later.
2. **Merchant memory** + stop notifying when the memory is confident.
3. **Needs-review filter** as the missed-notification / CSV inbox; mute classify notifications on batch import.
4. **SMS works without an LLM key** for clean templates.
5. **One accounts surface**; Tab strings/Sheets/empty states; drop Family-as-person from seed; drop unused `settleTab()`.
6. **Docs hygiene** (email, Funds, lifestyle).
7. **OneCard PDF** only if you still hate the laptop step after the above.

Bar for “Streamlit is retired”: you can live a month on SMS + classify notifs, recover misses in needs-review, and import a statement without opening Streamlit — with people on Tabs, not as categories.

Tab *product* (IOU, settle as a real txn, archive) is now close enough. The bar is still capture + memory + honest docs, not more tab chrome.
