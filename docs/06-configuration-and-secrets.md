# 6. Configuration & secrets

[← Back to guide](../DEVELOPER_GUIDE.md)

## Configuration files

| File | Committed? | Purpose |
|------|-----------|---------|
| `gradle/libs.versions.toml` | ✅ | Single source of truth for library, plugin and SDK versions (`compile-sdk`, `min-sdk`, `target-sdk`, AGP, Kotlin, Room…) |
| `app/build.gradle.kts` | ✅ | App ID, `versionCode` / `versionName`, signing (env vars), build types, ABI splits, R8 |
| `metaphony/build.gradle.kts` | ✅ | Native library config, CMake version |
| `settings.gradle.kts` | ✅ | Module list (`:app`, `:metaphony`) and repositories |
| `gradle.properties` | ✅ | Gradle JVM memory (`-Xmx2048m`), `android.useAndroidX`, `nonTransitiveRClass` |
| `gradle/wrapper/gradle-wrapper.properties` | ✅ | Pins Gradle 8.13 |
| `app/proguard-rules.pro` | ✅ | R8 keep-rules for release-type builds |
| `app/room-schemas/**` | ✅ | Room schema history (needed for migrations) |
| `local.properties` | ❌ | `sdk.dir` = your Android SDK path. Created by Android Studio; machine-specific. Also holds the Global Search server URL and app token (see below) |
| `secrets/` | ❌ **never** | Release keystore (`harmoniq-release.jks`) and its credentials (`harmoniq_keystore_info.txt`) |
| `.idea/` | ❌ (except code style) | Android Studio workspace state |
| `.vscode/` | ❌ | Local editor settings |

## Environment variables

These are read at build time by `app/build.gradle.kts`; nothing is read from a `.env` file.

| Variable | Required for | Effect |
|----------|-------------|--------|
| `SIGNING_KEYSTORE_FILE` | Signed `release` / `nightly` / `canary` | Keystore path (relative to project root, or absolute) |
| `SIGNING_KEYSTORE_PASSWORD` | Same | Keystore password |
| `SIGNING_KEY_ALIAS` | Same | Key alias |
| `SIGNING_KEY_PASSWORD` | Same | Key password |
| `APP_VERSION_NAME` | Optional, any variant | Overrides `versionName` (e.g. for nightlies) |
| `JAVA_HOME` | Terminal builds | JDK used by `gradlew` (must be 17+) |
| `ANDROID_HOME` | Optional | SDK location if `local.properties` is missing (e.g. on CI) |

Debug builds need **none** of the signing variables.

### Global Search (harmoniq-server)

Which server the app uses is decided **at runtime** (the public config file [`config/global-search.json`](../config/global-search.json), or the user's own server in Settings); see [Global Search server](09-global-search-server.md#how-the-app-connects). The keys below are optional build-time values: a **built-in fallback** for the default server and the config file's location. They're read by `app/build.gradle.kts` from an environment variable or `local.properties` (the variable wins) and compiled into `BuildConfig`. A key with a build-type suffix (`_DEBUG`, `_RELEASE`, `_NIGHTLY`, `_CANARY`) overrides the plain key for that variant only.

| Key | Example | Effect |
|-----|---------|--------|
| `HARMONIQ_SERVER_URL` (or `HARMONIQ_SERVER_URL_<BUILDTYPE>`) | `https://harmoniq.example.com` | Base URL of harmoniq-server. Release-type builds must use HTTPS (only `debug` allows plain http) |
| `HARMONIQ_APP_TOKEN` (or `HARMONIQ_APP_TOKEN_<BUILDTYPE>`) | the secret part of one entry in the server's `APP_TOKENS` | Sent as `X-App-Token` |
| `HARMONIQ_REMOTE_CONFIG_URL` (or `…_<BUILDTYPE>`) | *(default: raw GitHub URL of `config/global-search.json` on `main`)* | Where the default server's config file is read; `none` disables it |

With no built-in values and an empty/unreachable config file, the Library/Global switch is hidden until a server is set up in Settings.

For local development with a phone on USB:

```properties
# local.properties (never committed)
HARMONIQ_SERVER_URL_DEBUG=http://127.0.0.1:8000
HARMONIQ_APP_TOKEN_DEBUG=<secret from harmoniq-server/.env>
```

```bash
adb reverse tcp:8000 tcp:8000
```
Forwards the phone's port 8000 to the server running on this PC (`Harmoniq-server\scripts\run.ps1`). Use `http://10.0.2.2:8000` on an emulator instead.

The default server's token is public (it's in a public file and can be extracted from an APK), so treat it as "makes casual abuse harder", not as real authentication. A token typed in for **My own server** is stored encrypted with an Android Keystore key.

`config/global-search.json` is committed on purpose: it holds no private data, only the public server URL and app key. Never put signing passwords or other secrets in it. Resolution order, the HTTPS rule for non-debug builds, all local-testing options, and troubleshooting: [Global Search server](09-global-search-server.md). Deploying the server: [deployment guide](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/docs/deployment-guide.md).

## What `.gitignore` already protects

The root `.gitignore` (plus `app/.gitignore` and `metaphony/.gitignore`) excludes, among others:

- `secrets`, `*.jks`, `*.keystore` (signing material)
- `local.properties`, `google-services.json`
- `build`, `.gradle`, `.cxx`, `.kotlin` (generated output and caches)
- `*.apk`, `*.aab`, `*.ap_`, `*.dex` (build artifacts, including the `Harmoniq v1.x.x.apk` files kept at the repo root)
- `app/build` and `app/release/` (the "Generate Signed APK" output), via `app/.gitignore`
- `.idea/*` (except `codeStyles`), `.vscode/`, `.run/`, `.github/*`

> `.github/*` is ignored, so if you add GitHub Actions workflows later you'll need to remove that line (or add a `!.github/workflows` exception) for them to be committed.

## Rules for Git

**Commit:**
- Source code, resources, Gradle files, `libs.versions.toml`, the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`)
- New Room schema JSON files in `app/room-schemas/`
- Documentation and `media/` screenshots

**Never commit:**
- Anything in `secrets/`, any `.jks`/`.keystore`, or any file containing signing passwords
- `local.properties`
- APKs/AABs. Attach them to a GitHub Release instead
- `build/`, `.cxx/`, `.gradle/`, `.kotlin/`

Check before committing:

```bash
git status --short
```
Lists changed and untracked files. Look for anything under `secrets/`, `*.jks`, `*.apk` or `local.properties`. Those should never appear (if they do, `.gitignore` was changed).

```bash
git check-ignore -v secrets/harmoniq-release.jks local.properties
```
Confirms these files are ignored and prints which `.gitignore` rule matches them.

If a secret was ever committed, deleting the file in a new commit is **not** enough, because it stays in history. Treat the credential as leaked: rotate the passwords, and remember that a leaked release key lets someone else sign "updates" as you.

## Protecting the keystore credentials

`secrets/harmoniq_keystore_info.txt` stores the keystore credentials in plain text. It's git-ignored, but it's still readable by anything on this machine. Recommended:

- Keep a copy of the `.jks` and the passwords in a password manager or another secure backup **outside this machine**.
- Prefer setting the `SIGNING_*` variables per session (as in [Release & distribution](05-release-and-distribution.md#building-a-signed-release-from-the-terminal-recommended)) over saving them permanently in your user environment.
- If you add CI later, store these values as CI secrets and base64-encode the keystore into a secret, never into the repository.
