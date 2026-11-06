# 7. Gradle command reference

[← Back to guide](../DEVELOPER_GUIDE.md)

Run everything from the project root. `./gradlew` = Git Bash; use `.\gradlew.bat` in PowerShell.
Tasks are module-qualified (`:app:…`, `:metaphony:…`). An unqualified task like `assembleDebug` runs in every module that has it, which is usually what you want too.

Task names follow `<action><Variant>`, where the variant is `Debug`, `Release`, `Nightly` or `Canary`.

## Build

| Command | What it does |
|---------|--------------|
| `./gradlew :app:assembleDebug` | Builds the 5 debug APKs → `app/build/outputs/apk/debug/` |
| `./gradlew :app:assembleRelease` | Builds the 5 release APKs (signed only if `SIGNING_*` are set) |
| `./gradlew :app:assembleNightly` / `:app:assembleCanary` | Same for the nightly / canary build types |
| `./gradlew :app:bundleRelease` | Builds the release AAB → `app/build/outputs/bundle/release/` |
| `./gradlew :app:compileDebugKotlin` | Compiles Kotlin only, no packaging. The fastest "does it compile?" check |
| `./gradlew build` | Assembles **all** variants and runs lint + tests. Slow; mainly for CI-style checks |

## Install / uninstall

| Command | What it does |
|---------|--------------|
| `./gradlew :app:installDebug` | Builds and installs the debug app on all connected devices |
| `./gradlew :app:installRelease` | Same for release. **Only exists when `SIGNING_*` are set** |
| `./gradlew :app:installCanary` / `:app:installNightly` | Same for canary / nightly (also needs signing vars) |
| `./gradlew :app:uninstallDebug` | Uninstalls `dev.anujyadav.harmoniq.debug` |
| `./gradlew :app:uninstallAll` | Uninstalls every installed variant of the app |

Equivalent `adb` commands, for installing a file you already have:

```bash
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```
Installs or updates the debug APK and keeps app data (`-r` = replace).

```bash
adb install -r -d path/to/app.apk
```
Also allows a version downgrade (`-d`). Only works for debuggable builds or with matching signatures.

## Test & lint

| Command | What it does |
|---------|--------------|
| `./gradlew :app:testDebugUnitTest` | Runs the app's JVM unit tests on the JUnit 5 platform (JUnit 4 Robolectric tests run through the vintage engine): Global Search API client (OkHttp `MockWebServer`), DTO parsing against server fixtures, download pipeline and worker, lyrics timing, search. Report: `app/build/reports/tests/testDebugUnitTest/index.html` |
| `./gradlew :app:testDebugUnitTest --tests "dev.anujyadav.harmoniq.services.globalsearch.*"` | Runs only the Global Search tests |
| `./gradlew :metaphony:testDebugUnitTest` | JVM unit tests of the native-parser module's Kotlin code (date parsing) |
| `./gradlew :metaphony:connectedDebugAndroidTest` | Runs `AudioMetadataParserTest` **on a connected device/emulator** (instrumented test for the native parser) |
| `./gradlew :app:lintDebug` | Android lint → `app/build/reports/lint-results-debug.html`. The `NullSafeMutableLiveData` check is disabled in `app/build.gradle.kts` because it crashes AGP 8.7.3's lint (remove the exception after an AGP upgrade) |
| `./gradlew :app:lintRelease` | Lint against the release variant (run before releasing) |

## Clean / rebuild

| Command | What it does |
|---------|--------------|
| `./gradlew clean` | Deletes `app/build` and `metaphony/build` |
| `./gradlew clean :app:assembleDebug` | A full rebuild from scratch |
| `./gradlew --stop` | Stops all Gradle daemons. Fixes "file is locked" errors on Windows and stale-daemon weirdness |
| `./gradlew :app:assembleDebug --refresh-dependencies` | Re-checks remote repositories, ignoring cached dependency metadata |

`clean` does **not** remove the native CMake intermediates in `metaphony/.cxx/`. If a native build is stuck or behaves oddly, delete them by hand:

```bash
rm -rf metaphony/.cxx
```
Removes CMake/Ninja caches; the next build reconfigures and recompiles TagLib. (PowerShell: `Remove-Item -Recurse -Force metaphony\.cxx`.)

Android Studio equivalents: **Build → Clean Project**, **Build → Rebuild Project**, and **File → Invalidate Caches… → Invalidate and Restart** (clears the IDE's own indexes, not Gradle's).

## Diagnostics

| Command | What it does |
|---------|--------------|
| `./gradlew :app:tasks --all` | Lists every task available in `:app` (use it to check whether `installRelease` exists) |
| `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` | Prints the resolved dependency tree for release |
| `./gradlew :app:assembleDebug --stacktrace` | Shows the full stack trace when a build fails |
| `./gradlew :app:assembleDebug --info` | Verbose logging. Useful for signing, CMake and dependency issues |
| `./gradlew :app:assembleDebug --scan` | Uploads a build scan to scans.gradle.com (public link). **Don't use with signing variables set** |
| `./gradlew --version` | Shows the Gradle, Kotlin and JVM versions the wrapper is using |

## Useful flags

| Flag | Meaning |
|------|---------|
| `--offline` | Build without network (uses cached dependencies) |
| `--continue` | Keep running other tasks after one fails (e.g. see all lint/test failures) |
| `-q` / `--console=plain` | Quieter / CI-friendly output |
| `-x <task>` | Exclude a task, e.g. `build -x lint` |

## Typical workflows

```bash
./gradlew :app:installDebug && adb shell am start -n dev.anujyadav.harmoniq.debug/dev.anujyadav.harmoniq.HarmoniqActivity
```
The everyday loop: install the latest code and launch it.

```bash
./gradlew :app:compileDebugKotlin
```
A quick compile check after editing Kotlin, before committing.

```bash
./gradlew clean :app:lintRelease :app:testReleaseUnitTest :app:assembleRelease :app:bundleRelease
```
The release pipeline, with the `SIGNING_*` variables set.
