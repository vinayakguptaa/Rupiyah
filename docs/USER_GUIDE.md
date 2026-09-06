# Rupiyah — User guide

Personal **cashflow** tracker for India: where money moved, what you spent, what came in, investments by name, and open tabs with people.

**Capture:** SMS · CSV import · manual entry.  
**Not included:** Email import · portfolio / XIRR.

---

## Quick start

1. **Accounts** (also Settings → Bank accounts) — add your banks / UPI apps (e.g. Kotak, Credit Card). Cash is always available.
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
- **Archive** on the Accounts screen hides them from Add but **keeps all history**. Restore anytime.
- Past transactions on archived banks stay linked; Activity filters can still find them.

---

## Tabs (shared money / loans)

Open balance only: positive → they owe you; negative → you owe them.  
Use for trips, loans, shared pots — not for ordinary Food/Family support.

---

## CSV — three different files

| Job | Where | File |
| --- | --- | --- |
| Import a **bank statement** | Settings → Capture → Import bank statement (or Accounts ⋮) | Bank/wallet CSV (Date + Debit/Credit) |
| Export a **spreadsheet of Activity** | Activity → ⋮ → Export activity CSV | Saved to Downloads (`activity_….csv`) |
| Merge that Activity export | Settings → Backup → Merge Activity CSV | Same Activity CSV (not a bank file) |

**Bank statement import**

1. Choose the account the file belongs to.  
2. Pick a CSV (Date + Debit/Credit or Amount + Type columns work for most banks).  
3. Preview: **New** rows import; **Duplicate** skips/merges; **Maybe duplicate** defaults to skip (you can force import).  
4. Uncategorized rows join the classify queue.

---

## SMS

With AI helper on, matching bank SMS become draft transactions (account auto-matched to your bank list when possible). Classify later from the prompt or Activity.

---

## Home metrics (month)

- **Available balance** — cash + banks (self-transfers do not change the total)  
- **Debits** — this month’s debits by category or account  
- **Credits** — this month’s credits by category or account  
- **Open tabs** · **Accounts** (recent activity lives on the Activity tab)

---

## Backup & restore

**Settings → Copies → Backup & restore**

- **Save JSON backup** — full copy (categories, accounts including archived, tabs, transactions, splits, prefs).  
- **Restore JSON backup** — replaces local data. Same full restore from onboarding’s **Import backup file**.  
- **Merge Activity CSV** — adds/updates rows by Transaction ID; does **not** wipe settings. Not a bank statement.

Email credentials are not restored (email import removed).

---

## Tips

| Prefer | Avoid on forms |
| --- | --- |
| Debit / Credit | Expense / Income |
| Account | Payment method |
| Name | Merchant only |
| Tab | Fund |
| Settlement | Rewriting old spends |

Ordinary entry: amount · Debit/Credit · account · category · Name · note · date · optional tab/receipt.
