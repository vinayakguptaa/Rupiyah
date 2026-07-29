# Google Sheets workbook (app-managed)

The app creates and maintains this workbook when you connect Google Sheets
and sync. You do **not** need to hand-build the template.

## Tabs

| Tab | Purpose |
|-----|---------|
| **Transactions** | Raw rows A–T (frozen header, auto-filter, ₹ amount format) |
| **Dashboard** | This-month + all-time KPIs, cash vs digital, embedded charts |
| **Monthly** | QUERY: expense & income by `yyyy-MM` |
| **Categories** | QUERY: expense by category |
| **Accounts** | QUERY: expense by payment method / bank |
| **Funds** | QUERY: amounts by fund (pivoted by type) |
| **Merchants** | QUERY: top 30 merchants by expense |

## Transaction columns (A–T)

Transaction ID, Date, Time, Type, Amount, Merchant, Category, Subcategory,
Fund, Payment Method, Cash vs Digital, Note, Latitude, Longitude, Place,
Source, Email Ref, Deleted, Updated At, **Month** (`yyyy-MM`).

## Charts (Dashboard)

Created once when the spreadsheet is first set up (or if no charts exist):

1. Pie — spending by category  
2. Column — monthly expenses  
3. Bar — spending by account  

Analytics tabs use `QUERY` formulas, so they update as new rows sync.

## Setup in the app

1. Settings → Google Sheets → sign in / paste access token  
2. Create spreadsheet (or paste an existing spreadsheet ID)  
3. Enable sync  

Scope required: `https://www.googleapis.com/auth/spreadsheets`

Sync is one-way: **app → sheet**. Soft-deleted local rows are removed from
the Transactions tab.
