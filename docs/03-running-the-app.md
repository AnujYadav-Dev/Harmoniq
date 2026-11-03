# 3. Running the app

[← Back to guide](../DEVELOPER_GUIDE.md)

During development you'll almost always run the **debug** variant. It has application ID `dev.anujyadav.harmoniq.debug` and is labelled **"Harmoniq (Debug)"**, so it installs **next to** a release install instead of replacing it.

## Option A: Physical device (recommended for a music player)

1. On the phone: **Settings → About phone → tap "Build number" 7 times** to unlock Developer options.
2. **Settings → System → Developer options → enable "USB debugging"** (on Android 11+, "Wireless debugging" also works).
3. Connect the phone over USB and accept the "Allow USB debugging?" prompt on the phone.
4. Check the connection:

```bash
adb devices
```
Lists connected devices. You want a line ending in `device`. `unauthorized` means the prompt on the phone hasn't been accepted yet.

**Wireless (Android 11+):** in *Wireless debugging*, tap *Pair device with pairing code*, then:

```bash
adb pair <ip>:<pairing-port>
```
Pairs once using the 6-digit code shown on the phone.

```bash
adb connect <ip>:<port>
```
Connects using the IP and port shown on the Wireless debugging screen (this is a different port from the pairing one).

## Option B: Emulator

1. **Tools → Device Manager → Create Virtual Device**.
2. Pick a phone (e.g. Pixel 8) and a system image with **API 28 or newer** (minSdk is 28). An **API 33+** image lets you test the Android 13+ features: notification permission prompt and theme-matched splash screen.
3. Prefer an **x86_64** image on a PC. The build includes x86/x86_64 native libraries, so TagLib works on the emulator.

From the terminal you can start an existing emulator without opening Android Studio:

```bash
"<sdk>/emulator/emulator" -list-avds
```
Lists the emulators you've created.

```bash
"<sdk>/emulator/emulator" -avd <avd_name>
```
Boots that emulator.

### Putting music on the emulator/device

The library stays empty until a media folder is added, and there has to be audio in it:

```bash
adb push "path/to/song.mp3" /sdcard/Music/
```
Copies a file into the device's `Music` folder. Push a whole folder by giving a directory path. The sample files in `app/src/test/resources/` (mp3, flac, ogg, m4a) are handy for quick tests.

Then in the app, tap the **folder button in the top bar** (or go to **Settings → Groove → Media folders**), choose **Add folder**, pick `Music`, and confirm. The app uses the Storage Access Framework, so there's no storage permission to grant; the folder pick *is* the permission.

## Run from Android Studio

1. Pick the device or emulator in the device dropdown on the toolbar.
2. Make sure the **`app`** run configuration is selected.
3. Check **Build Variants** (View → Tool Windows → Build Variants) shows **`debug`** for both `:app` and `:metaphony`.
4. Press **Run ▶** (Shift+F10) to build, install and launch, or **Debug 🐞** (Shift+F9) to do the same with the debugger attached for breakpoints.

Android Studio installs only the APK matching the device's ABI, even though five are built (see [ABI splits](04-build-variants-and-outputs.md#abi-splits)).

## Run from the terminal

```bash
./gradlew :app:installDebug
```
Builds the debug variant and installs it on **every** connected device/emulator. It doesn't launch the app.

```bash
adb shell am start -n dev.anujyadav.harmoniq.debug/dev.anujyadav.harmoniq.MainActivity
```
Launches the debug app. The format is `<applicationId>/<activity class>`; the activity class keeps the base package even in the `.debug` variant.

If more than one device is connected, target one with `-s`:

```bash
adb -s <serial-from-adb-devices> install -r app/build/outputs/apk/debug/app-universal-debug.apk
```
Installs a specific APK on one device. `-r` replaces an existing install and keeps its data.

## Viewing logs

All app logging goes through `utils/Logger.kt` under the tag **`HarmoniqLogger`**:

```bash
adb logcat -s HarmoniqLogger
```
Shows only the app's own log lines (warnings/errors from `Radio`, `MediaExposer`, etc.).

```bash
adb logcat --pid=$(adb shell pidof -s dev.anujyadav.harmoniq.debug)
```
Shows everything the running debug app logs, including system/framework messages. Use this in Git Bash; in Android Studio, the **Logcat** window does the same with `package:mine`.

Uncaught exceptions are caught by a global handler in `MainActivity`, which opens `ErrorActivity` (an in-app crash screen) instead of the system "app stopped" dialog. The stack trace is also logged under `HarmoniqLogger`.

## Useful adb commands for this app

```bash
adb shell pm clear dev.anujyadav.harmoniq.debug
```
Wipes the debug app's data: settings, Room caches, media folder grants. You get a first-launch experience.

```bash
adb uninstall dev.anujyadav.harmoniq.debug
```
Removes the debug app completely.

```bash
adb shell am force-stop dev.anujyadav.harmoniq.debug
```
Kills the app process. Launching again after this is a true **cold start**, which is what you need to check splash-screen behaviour.

```bash
adb shell pm list packages | grep harmoniq
```
Shows which Harmoniq variants are installed (`dev.anujyadav.harmoniq`, `.debug`, `.canary`).

## Running against a local Global Search server

Global Search only appears in builds that have a server URL and token. For the debug build, run harmoniq-server on this PC and point the debug build at it:

```powershell
Harmoniq-server\scripts\run.ps1
```
Starts the server on port 8000 (first time: run `scripts\setup.ps1`; see the [server README](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/README.md)). Check it with `curl http://localhost:8000/readyz`.

```properties
# local.properties (never committed)
HARMONIQ_SERVER_URL_DEBUG=http://127.0.0.1:8000
HARMONIQ_APP_TOKEN_DEBUG=hq_xxx…
```
The token is the part after the colon in `APP_TOKENS` in the server's `.env`.

```bash
adb reverse tcp:8000 tcp:8000
```
Makes `127.0.0.1:8000` on the phone reach this PC. Repeat after reconnecting the phone. On an emulator use `http://10.0.2.2:8000` instead and skip this.

```bash
./gradlew :app:installDebug
```
Rebuilds with the new values and installs. In the app, the Search tab now has a **Library | Global** switch, and **Settings → Global search → Server status** shows the server version.

Plain `http://` works only in debug builds. All options (LAN IP, emulator), the HTTPS rules and troubleshooting: [Global Search server](09-global-search-server.md).

## Previewing composables

`ui-tooling` and `ui-tooling-preview` are included, so a `@Preview` composable renders in Android Studio's **Split/Design** editor view without running the app.
