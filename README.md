<p align="center">
  <img src="docs/images/app-icon.png" width="96" alt="Rupiyah app icon" />
</p>

<h1 align="center">Rupiyah</h1>

<p align="center">
  <strong>Your money, on your phone — open source, INR-first, no bank login required</strong><br/>
  Auto-import UPI &amp; bank alerts · envelope budgets · smart classify · Material&nbsp;3
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3ddc84" alt="Android 8+" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-blueviolet" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Compose-Material%203-7F52FF" alt="Compose" />
  <img src="https://img.shields.io/badge/version-1.4.1-informational" alt="Version" />
  <img src="https://img.shields.io/badge/offline--first-yes-success" alt="Offline first" />
</p>

<p align="center">
  <img src="docs/images/hero-banner.jpg" alt="Rupiyah — personal finance tracker" width="100%" />
</p>

---

## Why Rupiyah?

Bank apps show *their* ledger. Spreadsheets rot. Most “money apps” want your net banking password or a cloud subscription.

**Rupiyah is different:**

- **No bank login** — reads the **emails and SMS you already get** (UPI, cards, wallets)
- **Data stays on device** — Room database + encrypted prefs; you export when *you* want
- **Built for India** — ₹ formatting, UPI/cash split, wallet labels, trusted bank senders
- **Open source** — Apache 2.0; audit the parser, the LLM calls, the backup format

---

## Features

### Capture every rupee — automatically

| Feature | What you get |
| --- | --- |
| **Gmail import** | Google Sign-In (`gmail.readonly`) *or* IMAP + App Password |
| **Live mail watch** | Foreground monitor + IMAP IDLE / Gmail history so new alerts show up without constant manual sync |
| **Trusted senders** | Only mail from banks/wallets you allow becomes a transaction |
| **SMS ingest** | Optional bank SMS (sender IDs + keywords) |
| **Rules + LLM** | Deterministic amount/merchant parse first; optional **OpenAI-compatible** LLM (Groq, OpenAI, local) for messy texts |
| **Manual log** | Fast cash/UPI entry with numpad, categories, funds, notes, receipts |

### Understand spending — at a glance

| Feature | What you get |
| --- | --- |
| **Home dashboard** | Balance snapshot, time ranges, recent activity, pending classify |
| **Activity feed** | Search, filters, sort, month grouping |
| **Envelope funds** | Allocate into Needs / Wants / goals; ring chart + ledger; transfer between funds |
| **Categories** | Full icon set, colors, edit/delete; category drill-down |
| **Accounts** | Cash vs digital, named banks/wallets, default payment method |
| **Home widgets** | Overview, spending, funds, recent txns, quick-add |

### Stay in control — privacy by design

| Feature | What you get |
| --- | --- |
| **On-device storage** | Transactions live in Room; secrets in **EncryptedSharedPreferences** |
| **Classify notifications** | Tap a category or fund from the notification / overlay |
| **Optional location** | Match a spend to where you were (off by default) |
| **Google Sheets** | One-way export workbook (Dashboard, categories, merchants…) |
| **CSV + JSON backup** | Export/restore including setup (treat backups as secrets) |
| **Theme** | Material You, presets, custom primary/secondary/tertiary, dark mode |
| **Onboarding** | Guided setup: permissions, email, SMS, LLM — skippable |

### Developer-friendly extras

- **Dev menu** — tap version 7× for system prompt, classify delay, status helpers  
- **Paste-test inbox** — debug parsers without waiting for real mail  
- **R8 release builds** — minify + shrink  

---

## Screenshots

<p align="center">
  <img src="docs/images/screenshot-home.jpg" alt="Home dashboard" width="260" />
  &nbsp;
  <img src="docs/images/screenshot-funds.jpg" alt="Envelope funds" width="260" />
  &nbsp;
  <img src="docs/images/screenshot-transactions.jpg" alt="Transactions" width="260" />
</p>

<p align="center"><em>Marketing previews — UI styling may differ slightly from your build. Replace with real device shots when you ship.</em></p>

---

## Feature map (where things live)

```
Home          → balances, charts, pending, shortcuts
Activity      → all transactions, search & filters
Funds         → envelopes, transfers, fund detail
+ (FAB)       → log cash / digital spend or income
Settings      → email, SMS, LLM, sheets, theme, backup, accounts, categories
Widgets       → home-screen glance + quick add
```

---

## Quick start (users)

1. Install a [release APK](https://github.com/krtky/finance-tracker/releases) or [build from source](#build-from-source).
2. Open **Settings → Profile** and set your display name.
3. **Settings → Intelligence → LLM Providers** — paste an API key  
   → [docs/OPENAI_API_KEY.md](docs/OPENAI_API_KEY.md)
4. **Settings → Email** — Google Sign-In *or* IMAP App Password  
   → [docs/GMAIL_IMAP.md](docs/GMAIL_IMAP.md)
5. Add **Trusted senders** for your banks and wallets.
6. Optionally enable **SMS transactions** and list sender IDs.
7. Add **Bank accounts** under Money for clear labels on each spend.

Longer walkthrough: [docs/Rupiyah User Guide.pdf](docs/Rupiyah%20User%20Guide.pdf)

---

## Build from source

### Requirements

| Tool | Version |
| --- | --- |
| JDK | **17** |
| Android SDK | **35** |
| OS | Windows / macOS / Linux |
| IDE (optional) | Android Studio Ladybug+ |

### Clone & debug build

```bash
git clone https://github.com/krtky/finance-tracker.git
cd finance-tracker
```

**Windows (PowerShell)**

```powershell
# Point JAVA_HOME at a JDK 17 install if needed
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
.\gradlew.bat assembleDebug
```

**macOS / Linux**

```bash
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`  
(package id suffix: `com.krtky.financetracker.debug`)

### Signed release

You **cannot** produce a release-signed APK without *a* keystore — but you use **your own**, not one from the repo (none is published). Never share your `.jks` or passwords.

Full walkthrough: **[docs/SIGNING.md](docs/SIGNING.md)**

```powershell
# 1) Create keystore once
keytool -genkeypair -v -keystore keystore/my-release.jks -alias rupiyah -keyalg RSA -keysize 2048 -validity 10000

# 2) Copy template and fill passwords (gitignored)
copy keystore.properties.example keystore.properties

# 3) Build
.\gradlew.bat :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Also see [`.env.example`](.env.example) for a checklist of local secret names (the app does **not** load `.env` at runtime).

### Google OAuth (Sheets + Gmail Sign-In)

See [CONTRIBUTING.md](CONTRIBUTING.md). You must register your signing cert **SHA-1 / SHA-256** on an Android OAuth client in Google Cloud Console. The app never ships a client secret.

---

## Project layout

```
app/src/main/java/com/krtky/financetracker/
├── ui/           # Compose screens, components, navigation, ViewModels
├── data/         # Room, repos, email/LLM/Sheets, SecureStore
├── domain/       # Models
├── email/        # Live mail monitor service
├── sms/          # SMS receiver
├── notification/ # Classification prompts
├── location/     # Optional location services
├── widget/       # Home screen widgets
├── workers/      # Background work
└── di/           # Hilt

docs/             # User & setup guides, images
sheets/           # Google Sheets template notes
keystore/         # Your local .jks only (not committed)
```

Deep dive: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

---

## Tech stack

- **UI** — Jetpack Compose, Material 3 Expressive
- **DI** — Hilt
- **DB** — Room
- **Async** — Kotlin Coroutines / Flow, WorkManager
- **Network** — OkHttp (Gmail API, Sheets API, LLM)
- **Mail** — JavaMail IMAP (optional IDLE)
- **Security** — EncryptedSharedPreferences (AES-256 GCM)
- **Build** — AGP 8.7, Kotlin 2.0, minSdk 26, targetSdk 35

---

## Configuration & secrets

| What | How |
| --- | --- |
| LLM API key, model, base URL | **In-app** Settings → Intelligence |
| Gmail IMAP password / OAuth | **In-app** Settings → Email |
| Google Web client ID | **In-app** Settings → Google |
| Release keystore | `keystore.properties` or `-PRELEASE_*` |

Runtime secrets use `SecureStore` (encrypted prefs). They are **never** hardcoded in the repository.

Developer options: on **Settings**, tap the version line **seven times** to unlock LLM system prompt, classification delay, and status helpers.

---

## Security

- Secrets: EncryptedSharedPreferences (AES-256 GCM)
- Release: R8 minify + resource shrink
- Backup JSON **includes** API keys and passwords — treat backups as secrets
- Gmail access is **read-only** (IMAP or `gmail.readonly`)
- Do not commit `local.properties`, `.env`, `keystore.properties`, or `*.jks`

Full policy: [SECURITY.md](SECURITY.md)

---

## Contributing

PRs welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md).

```powershell
.\gradlew.bat lint
.\gradlew.bat test
```

Please:

1. Keep one feature or fix per PR  
2. Prefer clear Kotlin + Compose style (`kotlin.code.style=official`)  
3. Update docs when behaviour changes  

---

## License

[Apache License 2.0](LICENSE)

---

## Disclaimer

Rupiyah is a personal tool, not a bank product. Parsing of SMS/email is best-effort and depends on your bank’s message format. Always verify balances with your bank.
