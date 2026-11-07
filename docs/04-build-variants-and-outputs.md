# 4. Build variants & outputs

[← Back to guide](../DEVELOPER_GUIDE.md)

## Build types

Defined in `app/build.gradle.kts` (`:metaphony` mirrors the same names so the variants match up):

| Build type | Application ID | App label | Minified (R8) | Signed with | Purpose |
|------------|----------------|-----------|---------------|-------------|---------|
| `debug` | `dev.anujyadav.harmoniq.debug` | Harmoniq (Debug) | No | Android debug key (automatic) | Day-to-day development |
| `release` | `dev.anujyadav.harmoniq` | Harmoniq | Yes | Release key from `SIGNING_*` env vars | Public releases |
| `nightly` | `dev.anujyadav.harmoniq` | Harmoniq | Yes | Same as release (`initWith(release)`) | Pre-release builds |
| `canary` | `dev.anujyadav.harmoniq.canary` | Harmoniq (Canary) | Yes | Same as release | Experimental builds that install alongside the stable app |

Notes:

- `debug` also appends `-debug` to the version name (e.g. `1.2.0-debug`). The About screen shows `v1.2.0-debug`.
- `release`, `nightly` and `canary` all run R8 with `proguard-android-optimize.txt` + `app/proguard-rules.pro`, and package **full native debug symbols** (`ndk.debugSymbolLevel = "FULL"`).
- `nightly` shares the release app ID, so it **replaces** the stable app. The in-app update check (`AppMeta.isNightlyBuild()`) treats a build as nightly only if its version name contains `-nightly`, so set `APP_VERSION_NAME` accordingly (see [Release & distribution](05-release-and-distribution.md#versioning)).
- There are no product flavors, so variant name = build type name.
- Each build type can point Global Search at its own server (`HARMONIQ_SERVER_URL_<BUILDTYPE>`), and only `debug` may use plain `http://`. See [Global Search server](09-global-search-server.md).

Switch the active variant in Android Studio via **View → Tool Windows → Build Variants**. Set `:app` and `:metaphony` to the same value.

## ABI splits

`app/build.gradle.kts` enables ABI splits:

```kotlin
splits { abi { isEnable = true; isUniversalApk = true
    include("arm64-v8a", "armeabi-v7a", "x86_64", "x86") } }
```

`:metaphony` contains native code (TagLib), so each APK carries `.so` libraries. Splitting gives one smaller APK per CPU architecture plus a universal one:

| APK | Use it for |
|-----|-----------|
| `app-universal-<variant>.apk` | Anyone, any device. Largest file. **Share this one if unsure.** |
| `app-arm64-v8a-<variant>.apk` | Virtually all modern phones (64-bit ARM) |
| `app-armeabi-v7a-<variant>.apk` | Old 32-bit ARM phones |
| `app-x86_64-<variant>.apk` | PC emulators, Chromebooks |
| `app-x86-<variant>.apk` | Old 32-bit emulators |

Check a device's ABI:

```bash
adb shell getprop ro.product.cpu.abi
```
Prints the device's primary ABI (e.g. `arm64-v8a`).

ABI splits apply to APKs only. An **AAB** (`bundle*` tasks) ignores them, because Google Play generates per-device APKs from the bundle itself.

## Where build outputs go

Everything under `build/` and `.cxx/` is generated and git-ignored.

| Output | Path |
|--------|------|
| Debug APKs | `app/build/outputs/apk/debug/app-<abi>-debug.apk` |
| Release APKs (signed) | `app/build/outputs/apk/release/app-<abi>-release.apk` |
| Nightly / canary APKs | `app/build/outputs/apk/nightly/…`, `app/build/outputs/apk/canary/…` |
| Release AAB | `app/build/outputs/bundle/release/app-release.aab` |
| Output metadata (version, file list) | `output-metadata.json` next to the APKs |
| R8 mapping (deobfuscates stack traces) | `app/build/outputs/mapping/release/mapping.txt` |
| Native debug symbols | `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip` |
| Lint reports | `app/build/reports/lint-results-<variant>.html` |
| Unit-test reports | `app/build/reports/tests/test<Variant>UnitTest/index.html` |
| Android Studio "Generate Signed APK" output | `app/release/` (by default) |
| Native intermediates (CMake/Ninja) | `metaphony/.cxx/` |

> **Keep `mapping.txt` for every build you distribute.** Release builds are obfuscated, so a crash stack trace from a user is unreadable without the mapping file from that exact build. Archive it next to the APK you publish (e.g. on the GitHub release, or privately).

The build folders get big: `app/build` + `metaphony/build` + `metaphony/.cxx` can exceed 2 GB. A [clean](07-gradle-commands.md#clean--rebuild) reclaims the space.

## Inspecting an APK

In Android Studio: **Build → Analyze APK…** and pick an APK. It shows size by component, the manifest, DEX classes and native libs per ABI.

```bash
"<sdk>/build-tools/<version>/aapt2" dump badging app/build/outputs/apk/debug/app-universal-debug.apk
```
Prints the package name, versionCode/versionName, min/target SDK and native ABIs from the terminal.

```bash
"<sdk>/build-tools/<version>/apksigner" verify --print-certs app/build/outputs/apk/release/app-universal-release.apk
```
Verifies the APK signature and prints the signing certificate. Use it to confirm a release APK is signed with the release key and not the debug key.
