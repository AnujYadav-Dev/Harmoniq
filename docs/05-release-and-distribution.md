# 5. Release & distribution

[← Back to guide](../DEVELOPER_GUIDE.md)

## How release signing is configured

Every APK must be signed. Debug builds use an auto-generated debug key. Release-type builds (`release`, `nightly`, `canary`) use the `release` signing config in `app/build.gradle.kts`, which reads **only environment variables**:

```kotlin
signingConfigs {
    register("release") {
        storeFile     = System.getenv("SIGNING_KEYSTORE_FILE")?.let { rootProject.file(it) }
        storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
        keyAlias      = System.getenv("SIGNING_KEY_ALIAS")
        keyPassword   = System.getenv("SIGNING_KEY_PASSWORD")
    }
}
```

| Variable | Meaning |
|----------|---------|
| `SIGNING_KEYSTORE_FILE` | Keystore path, **relative to the project root** (e.g. `secrets/harmoniq-release.jks`) or absolute |
| `SIGNING_KEYSTORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Alias of the key inside the keystore |
| `SIGNING_KEY_PASSWORD` | Password of that key |
| `APP_VERSION_NAME` | *(optional)* Overrides `versionName` for this build |

The local keystore is `secrets/harmoniq-release.jks`. Its alias and passwords are recorded in `secrets/harmoniq_keystore_info.txt`. Both files are git-ignored and must stay that way (see [Configuration & secrets](06-configuration-and-secrets.md)).

### What happens if the variables are missing

The signing config isn't "ready", so:

- `./gradlew :app:assembleRelease` **fails** while packaging, after the slow R8 step, with `SigningConfig "release" is missing required property "storeFile"`. It also removes the APKs of the last release build from the output folder.
- The `installRelease` / `installNightly` / `installCanary` tasks **don't exist** ("task 'installRelease' not found").

If you see either symptom, the environment variables aren't set in the shell running Gradle.

## Building a signed release from the terminal (recommended)

**PowerShell:**

```powershell
$env:SIGNING_KEYSTORE_FILE = "secrets/harmoniq-release.jks"
$env:SIGNING_KEYSTORE_PASSWORD = "<keystore password>"
$env:SIGNING_KEY_ALIAS = "<key alias>"
$env:SIGNING_KEY_PASSWORD = "<key password>"
```
Sets the signing variables for the current PowerShell window only. They disappear when you close it, which is what you want for passwords.

```powershell
.\gradlew.bat clean :app:assembleRelease :app:bundleRelease
```
Builds from a clean tree: five signed release APKs plus a signed AAB.

**Git Bash:**

```bash
export SIGNING_KEYSTORE_FILE=secrets/harmoniq-release.jks
export SIGNING_KEYSTORE_PASSWORD='<keystore password>'
export SIGNING_KEY_ALIAS='<key alias>'
export SIGNING_KEY_PASSWORD='<key password>'
./gradlew clean :app:assembleRelease :app:bundleRelease
```
The same thing in Git Bash. Use single quotes so special characters in passwords aren't interpreted by the shell.

Outputs:

- APKs: `app/build/outputs/apk/release/app-{universal,arm64-v8a,armeabi-v7a,x86,x86_64}-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- Mapping: `app/build/outputs/mapping/release/mapping.txt`

Install the signed release on a connected device (with the variables still set):

```bash
./gradlew :app:installRelease
```
Builds if needed and installs the right ABI split. Or install a specific file with `adb install -r app/build/outputs/apk/release/app-universal-release.apk`.

## Building a signed release from Android Studio

**Build → Generate Signed App Bundle / APK…**

1. Choose **APK** (for sideloading/GitHub) or **Android App Bundle** (for Google Play).
2. **Key store path:** `<project>/secrets/harmoniq-release.jks`; enter the passwords and alias. Do **not** tick "Remember passwords" on a shared machine.
3. Select the **`release`** build variant and finish.

Output goes to `app/release/` by default. That's where the existing `app-*-release.apk` files came from. The folder is git-ignored. The wizard injects the signing info itself, so the `SIGNING_*` variables aren't needed on this path.

## Versioning

Version info lives in `app/build.gradle.kts`:

```kotlin
versionCode = 7
versionName = "1.6.0"
versionName = System.getenv("APP_VERSION_NAME") ?: versionName
```

- **`versionCode`** is an integer Android uses to decide whether an install is an upgrade. **It must increase with every release you distribute.** Installing a lower `versionCode` over a higher one fails with `INSTALL_FAILED_VERSION_DOWNGRADE`.
- **`versionName`** is the human-readable version shown in **Settings → About** as `v<versionName>`.
- The in-app update check (`AppMeta.fetchLatestStableVersion`) reads the GitHub **latest release `tag_name`** from `AnujYadav-Dev/Harmoniq`. Tag releases as **`v<versionName>`** (e.g. `v1.2.0`) so the tag matches the version the app reports.
- For a nightly, build with `APP_VERSION_NAME` containing `-nightly` (e.g. `1.6.0-nightly.1`) and publish it as a GitHub **pre-release**. `isNightlyBuild()` then checks pre-releases instead of the latest stable release.

```bash
APP_VERSION_NAME=1.6.0-nightly.1 ./gradlew :app:assembleNightly
```
Git Bash: builds the nightly variant with an overridden version name, without editing the build file. (PowerShell: `$env:APP_VERSION_NAME="1.6.0-nightly.1"; .\gradlew.bat :app:assembleNightly`.)

## APK vs AAB

| | APK | AAB |
|---|-----|-----|
| Task | `assembleRelease` | `bundleRelease` |
| Installable directly | Yes (`adb install`, or open the file on the phone) | No. Needs Google Play or `bundletool` |
| ABI splits | 5 APKs (universal + per-ABI) | One bundle; Play makes per-device APKs |
| Use for | GitHub releases, sideloading, testers | Google Play Console upload |

For Play, also upload `native-debug-symbols.zip` and `mapping.txt` (Play Console → App bundle explorer → Downloads/Uploads) so native and Kotlin crash reports are readable.

## Pre-release checklist

1. **Bump** `versionCode` (+1) and set `versionName` in `app/build.gradle.kts`.
2. If you changed a Room entity, check the schema version bump and that the new JSON in `app/room-schemas/` is committed.
3. **Global Search server.** Check that [`config/global-search.json`](../config/global-search.json) on `main` has the live **`https://`** server URL and a token that's in the server's current `APP_TOKENS` (that's what installed apps use). Built-in values (`HARMONIQ_SERVER_URL_<BUILDTYPE>` / `HARMONIQ_APP_TOKEN_<BUILDTYPE>`) are optional and only used until a phone has read the file once; if you set them, check them too:

```bash
grep HARMONIQ_SERVER_URL app/build/generated/source/buildConfig/release/dev/anujyadav/harmoniq/BuildConfig.java
```
Shows the built-in URL of the last release build (use `canary`/`nightly` in the path for those). An empty value is fine: the build then relies on the config file. Token changes never need a new build (see [Global Search server](09-global-search-server.md#changing-or-updating-the-server-url-or-token)).

4. Run the checks:

```bash
./gradlew :app:lintRelease :app:testDebugUnitTest :app:testReleaseUnitTest
```
Runs Android lint and the JVM unit tests against both variants. The database migration test is skipped in the release variant, as the schemas it reads are only in debug builds, so run both. Lint has to finish with no errors. Four Compose rules are off, see [Gradle commands](07-gradle-commands.md#test--lint). The report is at `app/build/reports/lint-results-release.html`.

5. Build signed artifacts from a clean tree (`clean :app:assembleRelease`, plus `:app:bundleRelease` for Play).
6. **Install the release build on a real device and smoke-test it.** Minification only happens in release-type builds. Check: add a media folder and scan, play/pause/skip, the notification controls, lyrics, playlists, settings screens, and a cold start (`adb shell am force-stop dev.anujyadav.harmoniq`, then relaunch).
   For builds with Global Search, also test it **signed** (a signed `canary` first is the safest way, since it installs next to the stable app): **Settings → Global search → Server status** shows the server version; a search returns results; the preview plays; a download finishes, lands in `Download/Harmoniq` and appears in the library with cover and lyrics. R8 can break serialization or the YouTube player only in signed, minified builds.
7. Confirm it's signed with the release key:

```bash
"<sdk>/build-tools/<version>/apksigner" verify --print-certs app/build/outputs/apk/release/app-universal-release.apk
```
The certificate must be the release one, not "CN=Android Debug".

8. **Archive** `mapping.txt` and `native-debug-symbols.zip` for this version.
9. Create a GitHub release tagged `v<versionName>`, attach `app-universal-release.apk` (optionally the per-ABI APKs), and write the changelog.

## Distributing to testers

- **Easiest:** send `app-universal-release.apk` (works on every device). Testers enable "Install unknown apps" for their browser/file manager.
- **Side-by-side testing:** ship a `canary` build (`./gradlew :app:assembleCanary`). It installs as **"Harmoniq (Canary)"** next to the stable app, so testers keep their normal setup.
- **Global Search:** testers don't need a new APK when the server moves or the token changes. Edit [`config/global-search.json`](../config/global-search.json) on GitHub instead. See [Global Search server](09-global-search-server.md#changing-or-updating-the-server-url-or-token).
- **Quick internal builds:** a debug APK (`app/build/outputs/apk/debug/app-universal-debug.apk`) needs no release key. It's slower and unminified, but fine for UI feedback.

> **Signing-key continuity:** Android only installs an update over an existing app if both are signed with the **same key**. If `secrets/harmoniq-release.jks` is lost, existing users can never update; they'd have to uninstall (losing playlists and settings) and reinstall. Keep at least one backup of the keystore and its passwords somewhere safe outside this machine.
