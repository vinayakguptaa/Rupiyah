# Gmail setup (IMAP or Google Sign-In)

Rupiyah can pull bank/wallet mail in two ways. Choose one under **Settings → Email**.

| Method | Best for | What you enter |
| --- | --- | --- |
| **Google Sign-In** | No App Password, simpler UX | One-tap Google account consent (`gmail.readonly`) |
| **IMAP** | Near-instant live monitor (IMAP IDLE) | Gmail address + 16-char App Password |

Both paths only ingest mail from **Trusted senders**. The app never sends mail.

---

## Option A — Google Sign-In (Gmail API)

1. In Rupiyah: **Settings → Email**.
2. Select **Google Sign-In**.
3. Tap **Connect with Google** and approve **View your email messages and settings** (read-only).
4. Tap **Test connection**, then **Poll now** (after trusted senders are set).
5. Optionally enable **Live email monitor**.

### How live monitor works (Gmail vs IMAP)

| Mode | Live behavior |
| --- | --- |
| **Gmail** | Mailbox **history watch**: seed cursor → `history.list` for *messageAdded* → fetch only those messages → keep **Trusted senders**. Does **not** re-scan the whole inbox every cycle. Registers Google `users.watch` when a Pub/Sub topic is configured (optional). |
| **IMAP** | Continuous **IDLE + poll** of the inbox. |

Manual **Poll now** still does a one-shot search of today’s trusted mail for either mode.

### App credentials

The APK does **not** embed a client secret or API key for Gmail. It uses the same Android Google Sign-In flow as Sheets (`play-services-auth` + package name + SHA-1/SHA-256 on the OAuth Android client).

You (or the project maintainer) must still configure Google Cloud once:

1. [Google Cloud Console](https://console.cloud.google.com/) → same project as Sheets.
2. Enable **Gmail API**.
3. OAuth consent screen → add scope  
   `https://www.googleapis.com/auth/gmail.readonly`  
   (sensitive scope: for personal/testing, add your Google account under **Test users**).
4. Ensure the **Android** OAuth client has your app’s package name and signing certificate fingerprint (debug and/or release). See [CONTRIBUTING.md](../CONTRIBUTING.md).

If sign-in fails with access denied, re-check Gmail API + scope + test users + SHA fingerprint.

---

## Option B — IMAP App Password

Google does **not** allow your normal Gmail password for IMAP.

1. Open [Google Account](https://myaccount.google.com/).
2. Go to **Security**.
3. Turn on **2-Step Verification** (required).
4. Open [App passwords](https://myaccount.google.com/apppasswords).
5. Sign in again if asked.
6. Under **Select app**, choose **Mail**.
7. Under **Select device**, choose **Other** and type `Rupiyah`.
8. Tap **Generate**.
9. Copy the **16-character** password (spaces are optional; the app strips them).
10. In Rupiyah: **Settings → Email → IMAP**
    - Email: your full Gmail address
    - App password: paste the 16 characters
    - Save → **Test connection**
    - Optionally enable **Live email monitor** (IMAP IDLE)

### Common IMAP errors

| Message | Fix |
| --- | --- |
| Login failed / invalid credentials | Use an App Password, not your Gmail password |
| Web login required | Complete any Google security challenge in a browser, then retry |
| IMAP error: imaps | Reinstall the latest release build (provider packing) |

---

## Notes

- Prefer **Google Sign-In** if you do not want to create or store an App Password.
- Prefer **IMAP** if you want the fastest live monitor (IDLE push).
- You can switch methods anytime; only the active method is used for poll / live monitor.
- Revoke access anytime: Google Account → Security → Third-party access, or disconnect in app (OAuth), or delete the App Password (IMAP).
