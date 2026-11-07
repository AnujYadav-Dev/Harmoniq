# 2. Environment setup

[← Back to guide](../DEVELOPER_GUIDE.md)

## Requirements

| Tool | Version required by this project | Notes |
|------|-------------------------------|-------|
| Android Studio | Narwhal 3 Feature Drop (2025.1.3) or newer | Needed for Android Gradle Plugin 8.13 |
| JDK | 17+ | Android Studio's bundled JBR works; the code compiles to Java 17 bytecode |
| Android SDK Platform | API 35 | `compileSdk = 35` |
| Android SDK Build-Tools | Installed automatically by Gradle | |
| Android SDK Platform-Tools | Latest | Provides `adb` |
| **NDK (Side by side)** | **27.0.12077973** | Default NDK for AGP 8.13; used to build `:metaphony` |
| **CMake** | **3.22.1** | Pinned in `metaphony/build.gradle.kts` |
| Gradle | 8.13 | Downloaded automatically by `gradlew`, no install needed |
| Git | any | |

You do **not** need to install Gradle or Kotlin separately. The wrapper (`gradlew`) and the Gradle plugins handle that.

## 1. Install Android Studio

Install the latest stable Android Studio. On first launch, let the setup wizard install the default Android SDK. Note the SDK location it shows; you'll need it for `adb` and the command line.

## 2. Open the project

1. **File → Open…** and select the `Harmoniq/` folder (the one containing `settings.gradle.kts`). Do not select the `app/` folder.
2. Android Studio creates `local.properties` with `sdk.dir=…` and starts a **Gradle sync** (bottom status bar). The first sync downloads Gradle 8.13 and all dependencies, so it takes a while.
3. If prompted to "upgrade the Android Gradle Plugin", **decline**. The project pins AGP 8.13.2 in `gradle/libs.versions.toml`, and upgrades should be deliberate commits.

## 3. Install SDK components

**Tools → SDK Manager**:

- **SDK Platforms** tab: check **Android 15.0 (API 35)**.
- **SDK Tools** tab: tick **Show Package Details**, then check:
  - **NDK (Side by side) → 27.0.12077973**
  - **CMake → 3.22.1**
  - **Android SDK Platform-Tools**
  - **Android Emulator** (if you'll use an emulator)

Or from a terminal (Git Bash; replace `<sdk>` with your SDK path, the one from `local.properties`):

```bash
"<sdk>/cmdline-tools/latest/bin/sdkmanager" "platforms;android-35" "ndk;27.0.12077973" "cmake;3.22.1" "platform-tools"
```
Installs exactly the SDK packages this project needs. This needs the "Android SDK Command-line Tools" package, which you can install from the same SDK Tools tab.

## 4. Check the Gradle JDK

**File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK.** Pick a JDK 17+ (the default **jbr-17** / **jbr-21** bundled with Android Studio is fine).

When running Gradle from a terminal, Gradle uses `JAVA_HOME`. If `java -version` shows something older than 17, point `JAVA_HOME` at Android Studio's bundled JBR:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```
PowerShell, current session only. Set it permanently through Windows "Environment Variables" if you build from the terminal a lot.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```
The Git Bash equivalent.

## 5. Put `adb` on your PATH

`adb` (Android Debug Bridge) lives in `<sdk>/platform-tools/`. Add that folder to your `PATH` so the `adb …` commands in this guide work from any terminal. Check it with:

```bash
adb version
```
Prints the adb version; if this fails, `platform-tools` isn't on your PATH.

## Running Gradle on Windows

This repo is developed on Windows. Both shells work:

| Shell | Command form |
|-------|--------------|
| Git Bash | `./gradlew :app:assembleDebug` |
| PowerShell / cmd | `.\gradlew.bat :app:assembleDebug` |

This guide writes commands in the `./gradlew` form; in PowerShell, swap in `.\gradlew.bat`. Environment-variable syntax differs too (`export X=…` vs `$env:X = "…"`); both are shown where it matters.

Android Studio also has a built-in **Terminal** tab (bottom toolbar) that opens in the project root, plus a **Gradle** tool window (right side) where every task can be run by double-clicking it (`Harmoniq → app → Tasks → build / install / …`).

## Verify the setup

```bash
./gradlew :app:assembleDebug
```
A full debug build. If this succeeds, your JDK, SDK, NDK and CMake are all set up correctly. The first run compiles TagLib (C++) for four ABIs and takes several minutes; later runs are incremental.
