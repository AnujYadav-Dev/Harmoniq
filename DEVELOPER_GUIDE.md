# Harmoniq Developer Guide

This is the entry point for working on Harmoniq: building, running, releasing and troubleshooting.
It assumes you know general development concepts but not necessarily Android Studio or the Android build/release process.

Detailed topics live in [`docs/`](docs/):

| # | Document | What it covers |
|---|----------|----------------|
| 1 | [Project structure](docs/01-project-structure.md) | Modules, directories, important files, how the code is organised |
| 2 | [Environment setup](docs/02-environment-setup.md) | Android Studio, JDK, SDK, NDK, CMake, `adb`, opening the project |
| 3 | [Running the app](docs/03-running-the-app.md) | Emulator / physical device, debug builds, installing, logs |
| 4 | [Build variants & outputs](docs/04-build-variants-and-outputs.md) | `debug` / `release` / `nightly` / `canary`, ABI splits, where files land |
| 5 | [Release & distribution](docs/05-release-and-distribution.md) | Signing, release APK/AAB, versioning, pre-release checklist |
| 6 | [Configuration & secrets](docs/06-configuration-and-secrets.md) | Env vars, `local.properties`, what to commit and what never to commit |
| 7 | [Gradle command reference](docs/07-gradle-commands.md) | Every command used in this project, explained |
| 8 | [Troubleshooting](docs/08-troubleshooting.md) | Build, install and runtime problems and their fixes |
| 9 | [Global Search server](docs/09-global-search-server.md) | How the app reaches harmoniq-server, URL/token keys per build type, changing the server, local testing, troubleshooting |

---

## At a glance

| Item | Value | Defined in |
|------|-------|------------|
| Application ID | `dev.anujyadav.harmoniq` (`.debug` / `.canary` suffixes on those variants) | `app/build.gradle.kts` |
| Version | `versionName = "1.4.0"`, `versionCode = 5` | `app/build.gradle.kts` |
| Min / target / compile SDK | 28 (Android 9) / 34 (Android 14) / 35 | `gradle/libs.versions.toml` |
| Gradle / AGP / Kotlin | 8.10.2 / 8.7.3 / 2.1.0 | `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml` |
| JDK | 17 | `app/build.gradle.kts` (`JavaVersion.VERSION_17`) |
| Native toolchain | NDK `27.0.12077973` (AGP default), CMake `3.22.1` | `metaphony/build.gradle.kts` |
| Modules | `:app` (the app), `:metaphony` (C++/TagLib metadata parser) | `settings.gradle.kts` |
| Release signing | Environment variables `SIGNING_*` | `app/build.gradle.kts` |
| Global Search server | Chosen at runtime: the default server from [`config/global-search.json`](config/global-search.json) on GitHub, or the user's own server in Settings. `HARMONIQ_SERVER_URL` / `HARMONIQ_APP_TOKEN` are only a built-in fallback | `config/global-search.json`; fallback keys in `local.properties` or env vars |

## Quick start

All commands run from the project root (the folder containing `gradlew`).
On Windows, use Git Bash for `./gradlew`, or PowerShell with `.\gradlew.bat`. See [Environment setup](docs/02-environment-setup.md#running-gradle-on-windows).

```bash
./gradlew :app:assembleDebug
```
Builds the debug APKs into `app/build/outputs/apk/debug/`.

```bash
./gradlew :app:installDebug
```
Builds and installs the debug app ("Harmoniq (Debug)") on the connected device or emulator.

```bash
adb shell am start -n dev.anujyadav.harmoniq.debug/dev.anujyadav.harmoniq.MainActivity
```
Launches the installed debug app.

On first launch, the library is empty until you add a media folder (top-bar folder button or **Settings → Groove → Media folders**). The app reads music through the Storage Access Framework, not a storage permission.

## The five things most likely to trip you up

1. **Release builds are unsigned unless the `SIGNING_*` environment variables are set.** In that case Gradle still "succeeds", but produces `*-release-unsigned.apk` files that cannot be installed, and `installRelease` doesn't exist. See [Release & distribution](docs/05-release-and-distribution.md).
2. **The native module needs NDK + CMake 3.22.1.** A missing NDK or CMake is the most common first-build failure. See [Environment setup](docs/02-environment-setup.md#3-install-sdk-components).
3. **ABI splits are on.** Every APK build produces five APKs (`universal`, `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`). Share the universal one unless you know the device's ABI. See [Build variants & outputs](docs/04-build-variants-and-outputs.md#abi-splits).
4. **Never commit `secrets/`, `*.jks` or `local.properties`.** They are git-ignored; keep it that way. Losing the release keystore means you can never ship an update that installs over existing installs. See [Configuration & secrets](docs/06-configuration-and-secrets.md).
5. **Always test a release build before shipping.** R8 minification is enabled only for release-type builds, so a bug can appear in release that never shows up in debug. See [Troubleshooting](docs/08-troubleshooting.md#release-build-crashes-but-debug-works).

Also: **the Global Search server URL and token are no longer baked into the APK.** The default server is described by [`config/global-search.json`](config/global-search.json); editing that file on GitHub moves the server or rotates the token for every installed app, without a rebuild. Users can also enter their own server in **Settings → Global search → Server**. Only `debug` builds may use plain `http://`. See [Global Search server](docs/09-global-search-server.md).
