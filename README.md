<h1 align="center">
  <img src="./media/icon.svg" width="72" alt="Harmoniq Icon"><br>
  Harmoniq
</h1>

## 📖 What is Harmoniq?

Harmoniq is a custom fork and major evolution of the original [Symphony](https://github.com/zyrouge/symphony) architecture. It has been extensively refactored to provide a premium, dynamic, and intuitive user experience that rivals popular streaming apps, while remaining fully offline-first. Designed with modern Android development paradigms, Harmoniq focuses heavily on a robust UI, performance, and seamless media playback synchronization.

Harmoniq plays the music on your phone. Optionally, its **Global Search** finds new songs through your own self-hosted companion server, [**Harmoniq-server**](https://github.com/AnujYadav-Dev/Harmoniq-server), and saves them as fully tagged audio files straight into your library.

<p align="center">
  A modernized, elegant, offline-first music player for Android, inspired by premium streaming experiences.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Min%20SDK-Android%209%20(API%2028)-AD2A5A?style=for-the-badge&logo=android">
  <img src="https://img.shields.io/badge/Target%20SDK-Android%2014%20(API%2034)-4CAF50?style=for-the-badge&logo=android">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=for-the-badge"></a>
  <a href="https://github.com/AnujYadav-Dev/Harmoniq-server"><img src="https://img.shields.io/badge/Companion-Harmoniq--server-6E40C9?style=for-the-badge&logo=github"></a>
</p>

<br>

<p align="center">
  <img src="./media/Home.jpeg" width="30%">
  <img src="./media/Search.jpeg" width="30%">
  <img src="./media/Library.jpeg" width="30%">
</p>

<p align="center">
  <img src="./media/Now Playing.jpeg" width="30%">
  <img src="./media/Queue.jpeg" width="30%">
  <img src="./media/Settings.jpeg" width="30%">
</p>

## ✨ Key Features

### Your library

- **Modern 3-Tab Architecture**: Quick, streamlined bottom navigation splitting the app into **Home**, **Search**, and **Library** tabs.
- **Premium Library UI**: Reorderable library tabs (For you, Songs, Watch List, Albums, Artists, Genres, Playlists, Folders…), each with Grid or List view.
- **Home**: Customisable playlist shortcuts and a Suggested songs section (For you, Rediscover, genre picks).
- **Playlists**: Redesigned playlist screen with in-playlist search and custom covers (Favorites included).
- **Swipe Actions**: Swipe left or right on song, playlist and online rows. Every screen has its own configurable actions, with Undo for removals.
- **Synchronized Lyrics**: LRC lyrics, from a `.lrc` file next to the song or embedded in its tags, scroll in sync with playback.
- **Dynamic Theming & Aesthetics**: Uses Compose and active Palette extraction to match UI colors to the currently playing album artwork seamlessly.
- **Custom Playback Engine**: Custom `Radio` subsystem managing continuous background playback state via native Android foreground services (`RadioNotificationService`).
- **Performance Optimized**: English-only app with removed heavy legacy translations, leading to an incredibly lightweight, fast experience.

### Global Search (optional, needs [Harmoniq-server](https://github.com/AnujYadav-Dev/Harmoniq-server))

- **Library | Global switch** in the Search tab, with **All**, **Songs**, **Videos** and **Playlists** results.
- **Preview before you download**: The official YouTube player plays the result once it's ready, and pauses your music while it does.
- **Playlists**: Open a playlist and preview, download or save any of its songs.
- **One-tap download**: The server returns a high-quality file (Opus, M4A or MP3) with title, artists, album, track and disc number, release date, genres, cover art and synced lyrics embedded. It's saved to `Download/Harmoniq` and appears in your library automatically.
- **Actions everywhere**: The ⋮ menu and swipe gestures offer Download, Add to Watch List, Share, Open in YouTube and Details. A badge shows downloaded, in-progress and failed items.
- **Watch List**: A Library tab for songs and videos you want to keep without downloading them yet. It's stored on the device and works offline.
- **You stay in control**: Turn Global Search off in **Settings → Global search**. Without a configured server the app stays 100% offline.

> Global Search is meant for personal use with your own server. Only download content you have the right to keep.

## 🛠 Tech Stack

- **Language**: 100% Kotlin
- **UI Toolkit**: Jetpack Compose (`androidx.compose`), Material 3
- **Architecture**: MVVM / Clean Architecture principles using Compose state management
- **Local Database**: Room Database (`androidx.room`) for offline metadata caching, playlists and the Watch List
- **Tag Reading**: `:metaphony`, a TagLib-based (C++/JNI) metadata parser
- **Image Loading**: Coil (`io.coil-kt`) optimized for Compose
- **Media Playback**: Custom Radio Session implementation natively integrated with Android Media components
- **Navigation**: Jetpack Compose Navigation (`androidx.navigation:navigation-compose`)
- **Concurrency**: Kotlin Coroutines & Flow
- **Global Search**: OkHttp + kotlinx.serialization (server API), WorkManager (background downloads), [android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player) (previews)

## 🏗 Architecture Overview

Harmoniq delegates logic across cleanly separated layers. The UI state is entirely driven by Compose flows reacting to the backend Custom playback service (`RadioSession`).
- **UI Layer**: Comprised of full-screen routing components (`MainShell`), dedicated Tab contents, and granular rendering blocks (`view`/`components`).
- **Service Layer**: An isolated `services/radio` setup handles Android's Foreground Services, Notifications, Audio Focus, and continuous state streaming independently from UI fragments. `services/globalsearch` talks to Harmoniq-server, runs downloads in WorkManager and owns the Watch List.
- **Data Layer**: Backed by `CacheDatabase` and `PersistentDatabase` managed via Room, handling queries asynchronously and feeding offline song data into the UI.

```text
Harmoniq (this app) ──HTTPS + app token──▶ Harmoniq-server ──▶ YouTube Music / YouTube, MusicBrainz, LRCLIB
        ▲                                         │
        └────── tagged audio file (Opus/M4A/MP3) ◀─┘   → saved to Download/Harmoniq → library
```

## 📂 Project Structure

```text
Harmoniq/
├── app/src/main/java/dev/anujyadav/harmoniq/
│   ├── services/
│   │   ├── radio/          # Playback engine, queue, focus, media session, notification service
│   │   ├── groove/         # Media library: scanning, songs, albums, artists, playlists
│   │   ├── database/       # Room databases (cache + persistent: playlists, Watch List)
│   │   └── globalsearch/   # Harmoniq-server client, downloads (WorkManager), Watch List
│   ├── ui/                 # Jetpack Compose UI trees, split by `view`, `components`, and `theme`
│   └── utils/              # Formatting, Fuzzy search algorithms, custom filesystem managers
├── app/room-schemas/       # Exported Room schemas (database migrations)
├── metaphony/              # Native (TagLib) module for media metadata extraction
└── build.gradle.kts        # Project-level configurations and Kotlin build scripts
```

## 🚀 Build Instructions

### Prerequisites
- Android Studio (latest stable release)
- JDK 17
- Android SDK (Compile API 35), NDK and CMake 3.22.1 (for `:metaphony`)

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/AnujYadav-Dev/Harmoniq.git
   cd Harmoniq
   ```
2. Build the debug APK via Gradle Wrapper:
   ```bash
   ./gradlew :app:assembleDebug
   ```
3. To install directly to an attached device:
   ```bash
   ./gradlew :app:installDebug
   ```

### Connecting Global Search to your server (optional)

The server is chosen **at runtime**, so changing it never needs a rebuild:

- **Harmoniq server (default):** the app reads its address and app key from [`config/global-search.json`](./config/global-search.json) in this repository. Edit that file on GitHub to move the server, rotate the key, pause Global Search or show a notice, and every installed app picks it up within minutes. The app keeps the last copy for offline use.
- **My own server:** in **Settings → Global search → Server**, choose *My own server* and enter your harmoniq-server's URL and token (stored encrypted on the phone), then tap *Test connection*.

For development, a debug build can use a server on your PC as its built-in fallback (`local.properties`, git-ignored):

```properties
# run `adb reverse tcp:8000 tcp:8000` for a USB-connected phone
HARMONIQ_SERVER_URL_DEBUG=http://127.0.0.1:8000
HARMONIQ_APP_TOKEN_DEBUG=<token from the server's APP_TOKENS>
```

Details: [docs/09-global-search-server.md](./docs/09-global-search-server.md). Setting up the server itself: the [VPS guide](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/docs/deployment-vps.md) or the [home-machine guide](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/docs/deployment-guide.md).

### Developer documentation

Building, running, build variants, releasing, secrets, troubleshooting and the Global Search integration are covered in the [Developer Guide](./DEVELOPER_GUIDE.md) and [`docs/`](./docs).

## 📦 Installation Instructions (APK)

If you don't wish to compile the project yourself, simply:
1. Go to the [Releases](https://github.com/AnujYadav-Dev/Harmoniq/releases) page of this repository.
2. Download the APK attached to the latest release (the universal APK works on every device).
3. Transfer it to your Android device.
4. Open the APK file and select "Install" (ensure "Install from Unknown Sources" is enabled in your device settings).

The app checks GitHub for new releases from **Settings → About**.

## 🔗 Related Repositories

| Repository | What it is |
|------------|------------|
| [**Harmoniq**](https://github.com/AnujYadav-Dev/Harmoniq) (this repo) | The Android music player |
| [**Harmoniq-server**](https://github.com/AnujYadav-Dev/Harmoniq-server) | The self-hosted FastAPI backend behind Global Search: YouTube Music / YouTube search, playlists, preview info, and tagged high-quality audio downloads |

## 🤝 Contributing

We welcome pull requests! For major changes, please open an issue first to discuss what you would like to change. Ensure to update any relevant Compose preview functions, test cases, and adhere to the existing Kotlin style formatting throughout the `app` module. Unit tests run with `./gradlew :app:testDebugUnitTest`.

## 🙏 Acknowledgements

Harmoniq was originally born as a heavily customized fork of **Symphony** by [zyrouge](https://github.com/zyrouge). Massive thanks to the original author for creating the structural bedrock of this offline music player experience.

## 📄 License

This project is licensed under the terms of the [AGPL-3.0 License](./LICENSE).
