# Release signing (APK / AAB)

## Short answers

| Question | Answer |
| --- | --- |
| Can I ship a **signed** APK without a keystore? | **No.** Android requires a keystore for release signing. |
| Do I need *your* project keystore? | **No.** Each publisher creates **their own**. |
| Can I share my keystore on GitHub? | **No.** Never commit or publish `.jks` / passwords. |

Debug builds (`assembleDebug`) are auto-signed with the machine’s **debug** keystore (`~/.android/debug.keystore`). That is fine for testing, not for Play Store / public “official” releases.

---

## Create your own release keystore (one time)

```powershell
# From project root — creates keystore/my-release.jks (gitignored)
keytool -genkeypair -v `
  -keystore keystore/my-release.jks `
  -alias rupiyah `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

You will be prompted for a **store password**, **key password**, and certificate name fields (name, org, etc.). Use a strong password and store it in a password manager.

---

## Configure the project (local only)

```powershell
copy keystore.properties.example keystore.properties
```

Edit `keystore.properties`:

```properties
storeFile=keystore/my-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=rupiyah
keyPassword=YOUR_KEY_PASSWORD
```

These files are **gitignored**. Do not push them.

---

## Build a signed release APK

```powershell
.\gradlew.bat :app:assembleRelease
```

Output:

`app/build/outputs/apk/release/app-release.apk`

Or pass secrets only on the command line (no properties file):

```powershell
.\gradlew.bat :app:assembleRelease `
  -PRELEASE_STORE_FILE="keystore/my-release.jks" `
  -PRELEASE_STORE_PASSWORD="***" `
  -PRELEASE_KEY_ALIAS="rupiyah" `
  -PRELEASE_KEY_PASSWORD="***"
```

---

## What open-source users do

| Role | Keystore |
| --- | --- |
| **You (maintainer)** | Your private release keystore — used for GitHub Releases / Play Store |
| **Someone who clones the repo** | Builds `assembleDebug`, **or** creates their **own** release keystore for their fork |
| **End users** | Install **your** signed APK from Releases; they never need a keystore |

You do **not** share the keystore so others can “use the same signature.” If they lose it or you leak it, you cannot update the same app identity on Play Store.

---

## Play Store note

Once an app is published with a given signing key, **updates must use the same key** (or Play App Signing with the upload key Google registered). Back up `my-release.jks` + passwords offline. Losing them means you cannot ship updates under the same package name without Google’s recovery processes.
