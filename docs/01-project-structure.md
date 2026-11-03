# 1. Project structure

[← Back to guide](../DEVELOPER_GUIDE.md)

Harmoniq is a Gradle multi-module Android project with two modules:

- **`:app`**: the music player itself (Kotlin + Jetpack Compose).
- **`:metaphony`**: an Android library with a C++ layer (JNI + TagLib) that reads audio tags. `:app` depends on it through `implementation(project(":metaphony"))`.

## Top-level layout

```text
Harmoniq/
├── app/                         # The application module
│   ├── build.gradle.kts         # App build config: versions, signing, build types, ABI splits
│   ├── proguard-rules.pro       # R8 keep-rules used by release-type builds
│   ├── room-schemas/            # Exported Room DB schemas (commit these)
│   ├── src/
│   │   ├── main/                # All app code and resources
│   │   ├── debug/               # Debug-only: app name "Harmoniq (Debug)", and a manifest + network security
│   │   │                        #   config that allow plain http (local Global Search server)
│   │   ├── canary/res/          # Canary-only resources (app name "Harmoniq (Canary)")
│   │   ├── test/java/           # JVM unit tests (Global Search client/pipeline/worker, lyrics, search…)
│   │   └── test/resources/      # Sample audio files (mp3/flac/ogg/m4a) + Global Search server JSON fixtures
│   ├── build/                   # Generated output (git-ignored)
│   └── release/                 # Output of Android Studio's "Generate Signed APK" wizard (git-ignored)
├── metaphony/                   # Native metadata library module
│   ├── build.gradle.kts         # Library config + CMake 3.22.1 external native build
│   ├── src/main/cpp/            # JNI sources + CMakeLists.txt
│   │   └── taglib/              # Vendored TagLib source (committed as plain files)
│   ├── src/main/java/           # Kotlin wrapper around the native parser
│   ├── src/test/                # JVM unit test: AudioMetadataDatesTest.kt (year / year-month dates)
│   ├── src/androidTest/         # Instrumented test: AudioMetadataParserTest.kt
│   └── .cxx/                    # CMake/Ninja intermediate build files (git-ignored)
├── gradle/
│   ├── libs.versions.toml       # Version catalog: ALL dependency, plugin and SDK versions
│   └── wrapper/                 # Gradle wrapper (pins Gradle 8.10.2)
├── build.gradle.kts             # Root build file (declares plugins, applies none)
├── settings.gradle.kts          # Module list + repositories (google, mavenCentral)
├── gradle.properties            # JVM memory for Gradle, AndroidX flags
├── gradlew / gradlew.bat        # Gradle wrapper scripts (always use these, not a global gradle)
├── local.properties             # Local SDK path (git-ignored, machine-specific)
├── secrets/                     # Release keystore + its notes (git-ignored, NEVER commit)
├── media/                       # README screenshots and the app icon SVG
├── README.md                    # Public project overview
├── DEVELOPER_GUIDE.md           # This guide's entry point
└── docs/                        # This guide
```

> `.gitmodules` still lists `metaphony/src/main/cpp/taglib` as a submodule, but TagLib is actually committed as regular files (no gitlink). A plain `git clone` gets everything; `git submodule update` does nothing.

## App source layout

Root package: `app/src/main/java/dev/anujyadav/harmoniq/`

```text
harmoniq/
├── MainActivity.kt          # Single activity: splash screen, theme session, sets Compose content
├── ErrorActivity.kt         # Shown by the global uncaught-exception handler (crash screen)
├── ActivityIgnition.kt      # ViewModel that keeps the splash screen up until the first frame
├── Symphony.kt              # App-wide ViewModel; owns settings, database, groove, radio; lifecycle hooks
├── services/
│   ├── Settings.kt          # All user preferences (SharedPreferences-backed typed entries)
│   ├── AppMeta.kt           # App name, version (from BuildConfig), GitHub release check
│   ├── Permissions.kt       # Runtime permission request (POST_NOTIFICATIONS on Android 13+)
│   ├── database/            # Room databases (CacheDatabase v2, PersistentDatabase v1) + stores
│   ├── groove/              # Media library: scanning (MediaExposer), repositories, models
│   ├── radio/               # Playback engine: player, queue, focus, media session, notification service
│   └── globalsearch/        # Global Search: harmoniq-server client, config, download worker, watchlist/ (Room)
│       ├── api/             # HarmoniqServerApi (OkHttp), DTOs, ServerError + error codes
│       └── download/        # ServerDownloadWorker (WorkManager), pipeline, saver, records, notifications
├── ui/
│   ├── view/                # Screens (MainShell, NowPlaying, Settings, Library/Home/Search tabs…)
│   │   ├── globalsearch/    # Global results, online rows + actions, playlist/preview sheet, Watch List, player, downloads
│   │   └── settings/        # Settings sub-screens, incl. GlobalSearchSettingsView
│   ├── components/          # Reusable composables (dialogs, lists, tiles, settings tiles)
│   ├── theme/               # Colors, color schemes, typography, splash theme registration
│   └── helpers/             # ViewContext, transitions, asset helpers
└── utils/                   # Strings (AppStrings), logging, file system helpers, misc utilities
```

### Key concepts

- **`Symphony`** is the central `AndroidViewModel`. Everything (settings, groove, radio, database) hangs off it, and UI code reaches it through `ViewContext.symphony`.
- **Groove** is the media library. `MediaExposer` scans the folders the user picked (Storage Access Framework tree URIs) and caches song metadata in the Room `CacheDatabase`.
- **Radio** is the playback engine. It wraps `android.media.MediaPlayer` (`RadioPlayer`), manages the queue (`RadioQueue`), and runs `RadioNotificationService` as a `mediaPlayback` foreground service.
- **Global Search** (`services/globalsearch/`) talks to [harmoniq-server](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/README.md). `GlobalSearch` hangs off `Symphony` for the UI; downloads run in `ServerDownloadWorker` (WorkManager, `dataSync` foreground work), which builds its own API client from a plain `Context` because it can run without `Symphony`. Saved songs go to `Download/Harmoniq` and reach the library through a normal `groove.fetch()` rescan. The feature exists only in builds that have a server URL and token, and the user can turn it off (**Settings → Global search**). Songs saved for later live in the **Watch List** (Room table `watch_list`, `PersistentDatabase` v2). See [Global Search server](09-global-search-server.md).
- **Navigation** uses type-safe Compose Navigation routes (`@Serializable` objects/classes, e.g. `SettingsViewRoute`), declared in `ui/view/Base.kt`.
- **Strings** are plain constants in `utils/AppStrings.kt`. The app is English-only and does not use `strings.xml` for UI text (only `app_name`).

## Important files

| File | Why you care |
|------|--------------|
| `app/build.gradle.kts` | `versionCode` / `versionName`, signing config, build types, ABI splits, R8 |
| `gradle/libs.versions.toml` | Change any library/SDK/plugin version here, not in the module files |
| `app/proguard-rules.pro` | Keep-rules for kotlinx.serialization / Navigation routes in minified builds |
| `app/src/main/AndroidManifest.xml` | Permissions, `MainActivity`, `ErrorActivity`, `RadioNotificationService`, WorkManager's `SystemForegroundService` (`dataSync`) for downloads |
| `app/src/debug/res/xml/network_security_config.xml` | Lets **debug** builds use plain `http://` (local server); other builds are HTTPS-only |
| `services/globalsearch/ServerConfig.kt` | Global Search URL/token (from `BuildConfig`), timeouts and limits |
| `app/src/main/res/values/splash.xml` | Splash screen themes (one per accent color) |
| `app/room-schemas/` | Room schema history, used for auto-migrations; must be committed |
| `metaphony/src/main/cpp/CMakeLists.txt` | Native build definition (links TagLib) |
| `gradle.properties` | Gradle daemon heap (`-Xmx2048m`); raise it if builds run out of memory |
| `local.properties` | `sdk.dir=…` written by Android Studio; machine-specific |

## Room database schemas

`app/build.gradle.kts` sets `room { schemaDirectory("$projectDir/room-schemas") }`, so every compile exports the current schema JSON into `app/room-schemas/`.

When you change a Room entity:

1. Bump `version` in the `@Database` annotation (`CacheDatabase.kt` or `PersistentDatabase.kt`).
2. Add a migration. `CacheDatabase` already uses `AutoMigration(1, 2, …)` as an example.
3. Build, then **commit the new `<version>.json`**. Auto-migrations need the old schema files to exist.
