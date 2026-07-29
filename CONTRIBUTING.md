# Contributing

## Prerequisites

- JDK 17
- Android SDK 35
- Android Studio Ladybug (or newer)

## Getting started

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
.\gradlew.bat assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Signed release

Never commit passwords or `.jks` files.

**Option A — local properties file**

```bash
cp keystore.properties.example keystore.properties
# edit values; put the .jks under keystore/
.\gradlew.bat :app:assembleRelease
```

**Option B — CLI properties**

```powershell
.\gradlew.bat :app:assembleRelease `
  -PRELEASE_STORE_FILE="keystore/release.jks" `
  -PRELEASE_STORE_PASSWORD="***" `
  -PRELEASE_KEY_ALIAS="***" `
  -PRELEASE_KEY_PASSWORD="***"
```

See also [`.env.example`](.env.example) for a checklist of secret names (not loaded by the app at runtime).

## Google OAuth (Sheets + Gmail)

Sheets sync and **Gmail OAuth** (`gmail.readonly`) both use Android Google Sign-In. The app does **not** ship a client secret — tokens come from the device account after the user consents.

Register your **APK signing certificate SHA-1 / SHA-256** on the Android OAuth client in Google Cloud Console.

### APIs and scopes (Cloud project)

| Feature | API to enable | OAuth scope |
| --- | --- | --- |
| Sheets sync | Google Sheets API | `https://www.googleapis.com/auth/spreadsheets` |
| Gmail ingest (OAuth mode) | Gmail API | `https://www.googleapis.com/auth/gmail.readonly` |

`gmail.readonly` is a **sensitive** scope. For unpublished/testing apps, add accounts under **OAuth consent → Test users**.

### If you're just building from source:

Android's default debug keystore (`~/.android/debug.keystore`) is used automatically. Get its fingerprint:

```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

Then either:
- Add that SHA-256 to the existing OAuth client in the Google Cloud Console project, **or**
- Create your own OAuth 2.0 Android client (package `com.krtky.financetracker` / `.debug` for debug builds).

### For maintainers (publishing releases):

Use the production keystore whose fingerprint is already registered. Keep that keystore **outside the repository** (see `.gitignore`).

## Code style

- Follow official Kotlin style (`kotlin.code.style=official`)
- Run lint before pushing: `.\gradlew.bat lint`
- No ktlint/detekt config yet — contributions to add one are welcome

## Pull requests

1. One feature/fix per PR
2. Keep the commit history clean — squash if needed
3. Update docs if behaviour changes
4. Make sure `.\gradlew.bat lint` passes
