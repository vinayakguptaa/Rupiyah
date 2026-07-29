# Architecture

Rupiyah is a single-module Android app (`app/`) built with Kotlin, Jetpack Compose, Hilt, Room, and WorkManager.

## Layers

```
ui/          Compose screens, components, navigation, ViewModels
domain/      Shared models
data/        Room, repositories, email/LLM/Sheets clients, encrypted prefs
email/       Foreground IMAP / Gmail watch service + boot receiver
sms/         SMS broadcast receiver
notification/ Classification notifications & overlay
location/    Optional place matching
widget/      Home-screen widgets
workers/     Periodic sync / maintenance work
di/          Hilt modules
```

## Data flow (high level)

1. **Ingest** — Gmail (OAuth or IMAP) and/or SMS deliver raw messages.
2. **Parse** — Deterministic rules extract amount, merchant, type; optional LLM fills gaps.
3. **Persist** — Room (`AppDatabase`) stores transactions, categories, funds, accounts.
4. **Classify** — User classifies via notification, overlay, or in-app sheet.
5. **Present** — ViewModels expose `StateFlow` UI state to Compose screens.
6. **Export** (optional) — CSV, JSON backup, or one-way Google Sheets sync.

## Secrets

| Secret | Where it lives |
| --- | --- |
| LLM API key, Gmail app password, OAuth tokens | `SecureStore` → EncryptedSharedPreferences |
| Google Web client ID (optional) | Same (`SecureStore`) |
| Release keystore passwords | `keystore.properties` or `-PRELEASE_*` (never in source) |

No API keys or passwords are compiled into the APK from source control.

## Package

`com.krtky.financetracker` · minSdk 26 · targetSdk 35 · primary currency INR
