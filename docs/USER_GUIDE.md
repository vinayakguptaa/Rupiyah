# Rupiyah — User guide

Personal **cashflow** tracker for India: where money moved, what you spent, what came in, investments by name, and open tabs with people.

**Capture:** SMS · CSV import · manual entry.  
**Not included:** Email import · portfolio / XIRR.

---

## Quick start

1. **Settings → Bank accounts** — add your banks / UPI apps (e.g. Kotak, Credit Card). Cash is always available.
2. **Settings → AI helper** — required only for **SMS** auto-parse (API key + enable).
3. **Settings → Bank text messages** — turn on SMS reading and grant SMS permission.
4. Or skip SMS: use **+** for manual entry, or **Accounts → Import bank statement (CSV)**.

---

## Add a transaction (+)

1. Enter amount on the numpad → choose **Debit**, **Credit**, or **Transfer** (self-transfer between your accounts).
2. Pick **Account** (active banks + Cash only).
3. Optional: **Category**, **Name** (merchant/person), **Tab**, note, receipt.
4. Optional: **Splits** — full-screen editor to break one bank amount across categories/names/tabs (e.g. FD maturity: Investment + Interest). Sum must match the parent amount.

Self transfers never need category or splits; they are excluded from lifestyle spend.

---

## Accounts

- **Active** accounts appear on Add and Self Transfer.
- **Archive** (Settings → Bank accounts) hides them from Add but **keeps all history**. Restore anytime.
- Past transactions on archived banks stay linked; Activity filters can still find them.

---

## Tabs (shared money / loans)

Open balance only: positive → they owe you; negative → you owe them.  
Use for trips, loans, shared pots — not for ordinary Food/Family support.

---

## CSV import

**Accounts → Import bank statement (CSV)**

1. Choose the account the file belongs to.  
2. Pick a CSV (Date + Debit/Credit or Amount + Type columns work for most banks).  
3. Preview: **New** rows import; **Duplicate** skips/merges; **Maybe duplicate** defaults to skip (you can force import).  
4. Uncategorized rows join the classify queue.

---

## SMS

With AI helper on, matching bank SMS become draft transactions (account auto-matched to your bank list when possible). Classify later from the prompt or Activity.

---

## Home metrics (month)

- **Lifestyle spend** — debits excluding Investment and self-transfers  
- **Credits** — by category  
- **Investment** — net by Name (Zerodha, FD, …)  
- **Open tabs** · **Accounts**

---

## Backup & restore

**Settings → Backup & restore** exports categories, accounts (incl. archived), tabs, transactions, **splits**, and prefs.  
Restore replaces local data. Email credentials are not restored (email import removed).

---

## Tips

| Prefer | Avoid on forms |
| --- | --- |
| Debit / Credit | Expense / Income |
| Account | Payment method |
| Name | Merchant only |
| Tab | Fund |
| Settlement | Rewriting old spends |

Ordinary entry stays short: amount · direction · account · category · Name.
