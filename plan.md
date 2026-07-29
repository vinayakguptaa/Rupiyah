# Rupiyah — UI cleanup, UX, and feature plan

**Product:** Personal finance tracker (INR) — email/SMS ingest, categories, envelope funds, accounts.  
**Audience:** Single-user Android app (minSdk 26), Material 3 Expressive.  
**Date:** 2026-07-26  
**Related work already done:** ViewModel split, shared filter/form helpers, BackupRepository, form components (`TransactionFormComponents`).

This plan is the next product/design roadmap. It prioritizes **clarity and speed for daily logging**, then polish, then deeper features.

---

## 1. Goals and principles

| Goal | Meaning |
|------|---------|
| **Faster daily path** | Log expense, classify pending, check balance — under 10 seconds when possible |
| **One visual system** | Same spacing, type, cards, empty states, back headers, bottom inset |
| **Honest money** | Clear labels for net vs cash vs funds vs accounts (users confuse these today) |
| **Progressive setup** | Onboarding + Settings don’t dump IMAP/LLM/SMS at once |
| **Ship in slices** | Each phase is shippable without blocking the next |

**Non-goals (for now):** Multi-user, multi-currency, full PFM investment tracking, web companion.

---

## 2. Current UX map (as-built)

### 2.1 Navigation

| Surface | Routes / role |
|---------|----------------|
| **Main tabs** | Home · Activity (transactions) · Funds · Settings |
| **Floating dock** | `FloatingBottomNav` + side FAB (Add txn / Add fund / Search settings) |
| **Stacks** | `add_cash`, `txn/{id}`, `accounts`, `categories`, `category/…`, `fund/{id}`, `settings/{section}` |
| **Overlays** | Classify sheet, confirm sheets, snackbars |

**Friction today** *(partially addressed)*

- ~~Bottom dock overlaps content → ad-hoc `bottom = 104.dp`~~ → **`Dimens.NavBarContentInset` / `NavContentInsets`** on tab roots.
- FAB meaning changes by tab (good) but **Home and Activity both “Add”** — Activity search is now expand-in-place (header icon); Settings keeps search FAB.
- ~~Deep-link filters from Home → Activity rely on many tick/boolean flags in `MainActivity`~~ → **`ActivityFilterArgs` on `SavedStateHandle`**.

### 2.2 Screen-by-screen snapshot

| Screen | Strengths | Weak spots |
|--------|-----------|------------|
| **Home** | Greeting, hero (“Net this month”), MoM %, overview tiles, pie + trend, recent list | Density improved (12.dp section gap); hide balance still session-only |
| **Activity** | Filters, multi-select delete bar, CSV, month groups, **filtered summary strip**, expand-in-place search | Saved filters / sticky month headers still open |
| **Funds** | Strong hero ring (`SpendRatioRing`), list cards | Create/adjust sheets still verbose |
| **Fund / Category detail** | Filters + CSV | Near-duplicate chrome; **fund delete uses `DeleteConfirmSheet`** |
| **Add cash / Txn detail** | Rich form (account, category, fund, date/time) | Long scroll; “Digital” vs bank naming still conceptual; edit vs view modes on detail feel heavy |
| **Categories** | Spend list | No quick add from list (must use Settings); no budget bar |
| **Settings** | Searchable groups, GroupedCard | Many advanced sections; stringly section keys; Email hub packs Gmail + senders + poll |
| **Onboarding** | Import backup shortcut | Still long if not importing; LLM not clearly optional vs required |

### 2.3 Design-system gaps

- **Spacing:** Mix of 12 / 14 / 16 dp section gaps; headers use both `headlineMedium` and `headlineLarge`.
- **Cards:** Mostly `surfaceContainerHigh` + `extraLarge` — good baseline; some screens use raw `RoundedCornerShape(24.dp)` instead of theme shapes.
- **Empty states:** Shared `EmptyState` exists but CTAs are inconsistent (“Log cash” vs “Add transaction” vs none).
- **Loading:** Home has shimmer; other lists often jump from empty → data.
- **Typography for amounts:** Full `₹x,xxx.xx` everywhere; compact (`inrCompact`) underused on tiles.
- **Dead / heavy surfaces:** Theme color picker is very large for a secondary preference; settings detail is a mega-`when(section)`.

---

## 3. UI cleanup plan (visual + structural, no new product features)

Work that makes the app look and feel consistent **without** new domain logic.

### 3.1 Design tokens (do first)

| Token | Proposal |
|-------|----------|
| **Screen padding** | Horizontal `16.dp`; top content under status bar `8.dp` |
| **Section gap** | `12.dp` between blocks; `8.dp` within a card |
| **List bottom inset** | Shared `NavContentInsets.bottom` (~`96–104.dp`) composable — **one constant** for all tab roots |
| **Page title** | `headlineMedium` + Bold for tab roots; `headlineSmall` for stack screens |
| **Page subtitle** | `bodyMedium` / `onSurfaceVariant` |
| **Amount emphasis** | `titleLarge` SemiBold for heroes; `titleMedium` for list rows |
| **Shapes** | Prefer `MaterialTheme.shapes.*` over one-off `RoundedCornerShape` |
| **Destructive** | Always error-tinted confirm sheet (not mixed AlertDialog / sheet) |

**Deliverable:** `ui/theme/Dimens.kt` (or `Spacing.kt`) + short comment in Theme.kt; migrate tab screens to `NavContentInsets`.

### 3.2 Shared chrome components

Extract and reuse:

| Component | Use on |
|-----------|--------|
| `ScreenHeader(title, subtitle?, actions)` | Activity, Funds, Settings, Categories, Accounts |
| `StackTopBar(onBack, title, actions)` | Category/Fund detail, Settings detail, Accounts |
| `MoneyText(paise, hidden, compact)` | Home tiles, lists, widgets |
| `FilteredTxnListScaffold` | Category detail, Fund detail (filters + list + empty) |
| `ConfirmDeleteSheet` only | Fund delete, bulk delete, txn delete |

**Also:** Split `SharedUiComponents.kt` into packages for maintainability (already partially cleaned of dead composables):

```
ui/components/
  chrome/     # FloatingBottomNav, GroupedCard, EmptyState, headers
  home/       # BalanceHeroCard, OverviewTile, FundsWaveSummary, shimmer
  charts/     # pie, monthly trend
  transaction/ # TransactionCard, form, filters (existing pieces)
```

### 3.3 Screen-specific UI cleanup

#### Home
- Clarify hero title: **“Net this month”** (or “Month net”) with helper “Income − expenses”, not “Total balance”.
- Optional second line: **Accounts total** (Cash + Digital) as secondary metric already on Accounts tile — avoid implying hero = bank balance.
- Collapse vertical density: reduce spacer stack; put Overview title + 2×2 tiles tighter.
- Pie: when empty, single empty illustration (not two competing empty messages).
- Persist `isNetHidden` in `UserPreferences`.
- “See all activity” → primary text button style consistent with Funds CTAs.

#### Activity
- Sticky header: title + filter + sort; search as expand-in-place (match Settings search pattern).
- Show **filtered summary strip**: count + net of visible results (`N txns · −₹x · +₹y`).
- Multi-select: show bottom bar “Delete (n)” when selection non-empty (discoverability).
- Month headers: sticky or clearer `surface` contrast.

#### Funds
- Reuse `FundRingChart` (or home chart tokens) instead of inline Canvas in `FundsScreen`.
- List card: one progress language (“₹left of ₹limit”) everywhere — same as detail.
- Create sheet: name + opening amount only; advanced later.

#### Add / Edit transaction
- Already share form components — next cleanup:
  - Default collapse “More” (location, fund toggle) behind one expand.
  - Sticky bottom **Save** bar (already partly true) — ensure always above IME + nav.
  - Amount field: large centered type; quick chips `+100 / +500 / +1000` (optional, light feature).
- Unify view/edit chrome on detail: less mode switching jank; soft “Edit” FAB stays.

#### Settings
- Typed `SettingsSection` enum (replace string routes) for safety.
- Email hub: split list rows into Gmail / Trusted senders / Live poll as clear children (even if same detail file).
- Appearance: collapse scheme styles behind “Advanced palette” (reduce intimidation).
- Status text under title: prefer snackbar for “Saved” (less layout jump).

#### Onboarding
- Progress: step title + short why (“Why Gmail?” one line).
- Mark LLM/email as **optional skip** with “You can set this later in Settings”.
- After import backup: skip to permissions only (already partial) — make success screen explicit.

#### Dialogs / sheets
- Fund delete → `DeleteConfirmSheet` like transactions.
- Date/time pickers already shared — ensure all call sites use them (no regressions).

### 3.4 Accessibility & system UI

- Content descriptions for icon-only actions (CSV, sort, hide balance, FAB).
- Min touch target 48.dp on chips where possible (AccountChip already dense — pad slightly).
- Respect font scale: avoid fixed heights that clip `titleLarge`.
- Predictive back: keep NavHost-owned; don’t reintroduce intercepting BackHandlers except dirty edit.
- Edge-to-edge: verify nav bar contrast on light/dark with opaque dock.

### 3.5 UI cleanup checklist (implementation order)

1. `Dimens` + `NavContentInsets`  
2. `ScreenHeader` / `StackTopBar` + migrate tab roots  
3. Rename Home hero copy + persist hide balance  
4. Activity summary strip + multi-select bar  
5. Funds ring reuse + delete sheet  
6. Settings section enum + quieter save feedback  
7. Split SharedUiComponents packages  
8. Empty-state copy pass (one verb set: “Add transaction”, “Create fund”, “Set up Gmail”)

---

## 4. UX improvements (behavior, not just pixels)

### 4.1 Mental model: four money concepts

Users must instantly distinguish:

| Concept | Definition in product language |
|---------|--------------------------------|
| **Month net** | Income − expense in selected period (Home hero) |
| **Accounts** | Running net per payment method (Cash, HDFC, …) |
| **Funds** | Envelope pots (budget limit + remaining) |
| **Categories** | Spend tags (not pots) |

**UX changes**

- Tooltips / first-run coach marks on Home tiles (once, dismissible).
- Accounts screen subtitle: “Where money sits by payment method”.
- Funds subtitle stays envelope-focused (already good).

### 4.2 Daily logging loop

**Ideal path:** FAB → amount → category (or skip) → Save → done.

**Improvements**

| Idea | Detail |
|------|--------|
| **Smart defaults** | Last-used category, fund, payment method (beyond current defaults) |
| **Recent categories first** | Already usage-sorted — surface top 6 as “Recent”, rest “All” |
| **Quick log from Home** | Long-press FAB or secondary action “Expense ₹…” mini sheet |
| **Duplicate transaction** | From detail menu — clones amount/category/account with new time |
| **Templates** | Optional “Coffee ₹80 · Cash · Food” saved presets (settings or long-press) |

### 4.3 Classification (pending inbox)

| Idea | Detail |
|------|--------|
| **Home badge** | “N to classify” chip → opens queue or first sheet |
| **Batch classify** | Same category for selected Activity items |
| **Snooze** | Delay notification without losing pending state |
| **Undo snackbar** | After classify, “Undo” 5s |

### 4.4 Activity discovery

| Idea | Detail |
|------|--------|
| **Saved filters** | “This week expenses”, “Cash only” as chips |
| **Search operators** later | `cat:food`, `>500` — optional advanced |
| **Relative dates** | Keep TimeRange chips; ensure custom range UX shows selected dates on pill |
| **Jump to month** | Tap month header → scroll or filter that month |

### 4.5 Funds UX

| Idea | Detail |
|------|--------|
| **Move between funds** | Transfer UI (expense fund A + income fund B or ledger transfer) |
| **Overspend warning** | Color ring red when remaining &lt; 0 or ratio &gt; 100% |
| **Link from category** | “Spend from fund X” suggestion when logging |

### 4.6 Onboarding & empty product

| Idea | Detail |
|------|--------|
| **Demo data toggle** | Optional sample month (dev or first-run) so charts aren’t empty |
| **Setup checklist** on Home | Gmail ✓ · Senders ✓ · First txn ✓ — dismissible card |
| **Skip everywhere** | Never hard-block on LLM key |

### 4.7 Feedback & trust

| Idea | Detail |
|------|--------|
| **Pull-to-refresh results** | Snackbar: “3 new from Gmail” (Home already ingests) |
| **Error specificity** | “App password rejected” vs “No network” |
| **Last sync time** | On Home subtitle or Settings email row |
| **Biometric lock** | Optional app open gate (see features) |
| **Balance hide** | Global preference, applies lists + widgets |

### 4.8 Navigation UX

| Idea | Detail |
|------|--------|
| **Type-safe routes** | Kotlin serialization navigation (long-term) |
| **Single deep-link model** | Replace MainActivity filter ticks with `ActivityArgs` savedStateHandle |
| **Tab reselect** | Reselect Activity scrolls to top / clears search |
| **Settings search FAB** | Keep; add “?” help only in advanced sections |

---

## 5. Feature plan

Prioritized by user value vs complexity. Aligns with README capabilities (ingest, funds, classify, sheets).

### 5.1 P0 — High value, fits current architecture

| Feature | Why | Notes |
|---------|-----|-------|
| **Pending classification hub** | Core loop for email users | Home chip + dedicated list of `PENDING` |
| **Filtered list totals** | Trust filters | Activity + category/fund detail |
| **Persist privacy hide** | Already half-built | Prefs + widgets respect mask |
| **Last used defaults on Add** | Speed | DataStore keys |
| **Setup checklist card** | Activation | Dismiss + deep links to Settings |
| **Overspend fund styling** | Envelope clarity | Pure UI + existing `FundBalance` math |

### 5.2 P1 — Strong product depth

| Feature | Why | Notes |
|---------|-----|-------|
| **Category monthly budgets** | Caps + warnings | New field on category or separate table; progress on Categories screen |
| **Recurring / subscription hints** | “Same merchant ~same amount monthly” | Heuristic, not full recurrence engine at first |
| **Merchant / counterparty insights** | “Where does money go?” | Aggregate by merchant on Home or Insights tab |
| **Period compare** | MoM already on hero — expand to categories | “vs last month” on category detail |
| **Biometric app lock** | Privacy | BiometricPrompt; optional |
| **Export CSV from Home period** | Power users | Reuse `CsvExport` |
| **Duplicate transaction** | Speed | Detail overflow menu |
| **Search in Activity improvements** | Find notes/merchants | Debounce + highlight |

### 5.3 P2 — Growth / delight

| Feature | Why | Notes |
|---------|-----|-------|
| **Dashboard customization** | Reorder/hide Home sections | Prefs list of section ids |
| **Quick amount chips / calculator** | Logging | Add screen |
| **Split transaction** | One UPI → two categories | Heavier domain model |
| **Budgets multi-currency** | Out of scope unless requested | — |
| **Widgets v2** | Glance already present | Theme sync, hide amounts, classify action |
| **Local notifications digest** | Daily spend summary | WorkManager |
| **Receipt photo attach** | Optional | Storage + detail |
| **Maps spend view** | Location already optional | Cluster pins if location set |
| **Google Sheets polish** | Analytics already heavy | Prefer reliability over new charts |
| **SMS sender picker** | Less manual IDs | Read recent SMS senders with permission |

### 5.4 Explicit YAGNI (defer)

- Social / shared household budgets  
- Bank Open APIs (Account Aggregator) until legal/product ready  
- Full double-entry accounting  
- Cryptocurrency  
- iOS  

---

## 6. Widgets & system surfaces

Current: Financial summary, Funds, Categories, Quick action (Glance).

| Improvement | Detail |
|-------------|--------|
| **Theme parity** | Dynamic color + hide amounts |
| **Refresh reliability** | Already from HomeViewModel — add WorkManager periodic refresh when app cold |
| **Quick action** | Deep link to Add with type=EXPENSE preselected |
| **Classify widget action** | Open pending list |
| **Size variants** | Compact 2×1 vs 4×2 layouts |

---

## 7. Content & microcopy

| Location | Prefer |
|----------|--------|
| Home hero | “Net this month” not “Total balance” |
| Empty Activity | “No transactions yet” + “Add transaction” |
| Empty Funds | “Create an envelope for rent, food, or savings” |
| Classify | “Assign category” not internal status names |
| Errors | Human first: “Couldn’t reach Gmail. Check App Password.” |
| Settings status | Avoid stacking under title; use snackbar |

Language: **simple Indian English**, rupee symbol, 12h time where already used.

---

## 8. Technical enablers (support UI/UX)

Not user-facing alone, but unblock clean UI:

| Item | Purpose |
|------|---------|
| `SettingsSection` enum + Nav routes | Safer Settings |
| `ActivityFilterArgs` in SavedStateHandle | Kill tick flags in MainActivity |
| Shared `FilteredTransactionList` composable | Category/Fund detail DRY |
| `UserPreferences.hideBalances` | Global privacy |
| Optional `InsightsRepository` later | Merchant aggregates without bloating VMs |
| Screenshot / Compose preview set | Home, Activity, Funds light+dark |

---

## 9. Phased roadmap

### Phase A — UI consistency (1–2 weeks)

**Outcome:** App feels like one product.

- [x] Dimens + bottom inset helper  
- [x] Screen / stack headers (`ScreenHeader` / `StackTopBar` / `MoneyText`)  
- [x] Home copy + density pass  
- [x] Empty state copy pass  
- [x] Fund delete → confirm sheet  
- [x] Dialog/sheet consistency (destructive fund/txn via sheets; saves → snackbar)  
- [ ] Package split for large component files *(partial: `chrome/ScreenChrome.kt`; full SharedUiComponents split deferred)*  

**Exit criteria:** Tab screens share padding/titles; no AlertDialog for destructive txn/fund actions.

### Phase B — Daily UX (1–2 weeks)

**Outcome:** Faster log + clearer money.

- [x] Persist hide balances globally  
- [x] Activity filtered totals + multi-select bar  
- [x] Pending classify entry points (Home + optional badge)  
- [x] Last-used defaults on Add  
- [x] Setup checklist card  
- [x] Deep-link filters via SavedStateHandle  

**Exit criteria:** New user sees checklist; returning user logs with fewer taps; filter math visible.

### Phase C — Insights & control (2–3 weeks)

**Outcome:** “Why did I spend this?”

- [ ] Category budget caps + progress UI  
- [ ] Merchant top list (simple)  
- [x] Fund overspend emphasis *(transfer still open)*  
- [ ] Duplicate transaction  
- [ ] Widget privacy + quick expense  

**Exit criteria:** Categories show budget state; merchant insight on Home or sheet.

### Phase D — Trust & polish (ongoing)

- [ ] Biometric lock  
- [x] Better sync messaging *(Home pull: “N new from Gmail”; last poll time still open)*  
- [x] Onboarding skip clarity *(Gmail/LLM optional + “set later in Settings”)*  
- [ ] Dashboard section reorder (if still desired)  
- [x] Accessibility *(content descriptions for search/CSV/FAB; more audit later)*  
- [ ] Performance: list keys, avoid full recompose on Home  

---

## 10. Metrics (how we know it worked)

Qualitative (dogfood):

- Time to log a cash expense after open  
- Whether “net vs accounts vs funds” is explained without asking  
- Classify backlog stays near zero  

Optional analytics later (privacy-preserving, local only):

- Count of txns created per day  
- % classified within 24h  
- Checklist completion  

---

## 11. Risk notes

| Risk | Mitigation |
|------|------------|
| Renaming “Total balance” confuses existing users | One-time coach mark; keep amount the same |
| More Home widgets/cards increase clutter | Checklist dismissible; customization in Phase D |
| Category budgets need data model | Start with optional monthly limit field only |
| Navigation refactor breaks deep links | Feature-flag SavedStateHandle migration behind compile |

---

## 12. Suggested first implementation slice

If implementing immediately after this plan, ship **Phase A + half of B** as one vertical:

1. `Dimens` / `NavContentInsets`  
2. Home hero rename + `hideBalances` preference  
3. Activity summary strip  
4. Pending count chip on Home  
5. Empty-state string pass  

No new backend services required; all sit on existing repositories and UI.

---

## 13. Appendix — File touch map (guidance)

| Area | Likely files |
|------|----------------|
| Spacing / chrome | `Theme.kt`, new `Dimens.kt`, `MainActivity.kt`, tab screens |
| Home | `HomeScreen.kt`, `SharedUiComponents.kt` (or split), `HomeViewModel.kt`, `UserPreferences.kt` |
| Activity | `TransactionsScreen.kt`, `FilterBottomSheets.kt`, `TransactionsViewModel.kt` |
| Funds | `FundsScreen.kt`, `FundDetailScreen.kt`, `FundRingChart.kt` |
| Forms | `AddCashScreen.kt`, `TransactionDetailScreen.kt`, `TransactionFormComponents.kt` |
| Settings | `SettingsScreen.kt`, `SettingsDetailScreen.kt`, `SettingsViewModel.kt` |
| Classify | `ClassifyTransactionSheet.kt`, `HomeScreen.kt`, notification package |
| Widgets | `widget/*`, `WidgetUpdater.kt` |

---

## 14. Summary

Rupiyah already has a solid M3 shell (floating nav, hero, filters, envelopes). The highest leverage work is:

1. **Clarify money language** (net ≠ bank balance ≠ fund pot).  
2. **One spacing/header system** so every screen feels related.  
3. **Speed the log + classify loop** (defaults, pending hub, totals).  
4. **Then** budgets, merchants, lock, and dashboard customization.

This plan supersedes the shorter widget-centric outline that lived in this file; widgets remain in Phase C/D as enhancements to existing Glance surfaces, not the only focus.
