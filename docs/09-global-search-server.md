# 9. Global Search server

[← Back to guide](../DEVELOPER_GUIDE.md)

**Global Search** lets Harmoniq search YouTube Music / YouTube, preview a result, and download it as a fully tagged
song into `Download/Harmoniq`. The searching and processing happen on
[**harmoniq-server**](https://github.com/AnujYadav-Dev/Harmoniq-server). This page covers the app side: how it
finds the server, how to change the server or token **without rebuilding**, local testing and troubleshooting.

Deploying the server itself: [Harmoniq-server deployment guide](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/docs/deployment-guide.md).
API contract: [api.md](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/docs/api.md).

## How the app connects

The server address and app token are decided **at runtime**, so changing them never needs a new APK.

```text
Settings → Global search → Server
 ├─ Harmoniq server (default) ── config/global-search.json on GitHub (public)  ─┐  newest first:
 │                               └─ last copy saved on the phone (offline)      │  1. config file
 │                               └─ values built into the APK (BuildConfig)     │  2. built-in values
 └─ My own server ────────────── URL + token typed in Settings (token encrypted) │
                                                                                  ▼
                       ServerConnections (one per process, used by the UI and the download worker)
                                                                                  │
            HarmoniqServerApi ── GET/POST <server URL>/v1/... ──▶ harmoniq-server
            headers: X-App-Token, X-Device-Id (random UUID per install), User-Agent "Harmoniq/<version> (Android <sdk>)"
```

- **Harmoniq server (default).** The app reads a small public file,
  [`config/global-search.json`](../config/global-search.json), from this repository on GitHub:

  ```json
  {
    "version": 1,
    "server_url": "https://harmoniq.example.com",
    "app_token": "hq_xxx…",
    "enabled": true,
    "message": null
  }
  ```

  | Field | Meaning |
  |-------|---------|
  | `server_url` | Base URL of the server, **without** `/v1`. HTTPS (plain `http://` is only accepted by debug builds) |
  | `app_token` | The secret part (after the colon) of one entry in the server's `APP_TOKENS` |
  | `enabled` | `false` pauses Global Search on the default server for everyone (the app shows `message` instead) |
  | `message` | Optional notice shown above search results, e.g. `"Server maintenance tonight"` (max 300 characters) |
  | `version` | File format; files with a newer version than the app understands are ignored |

  - The file is read when Global mode is opened (at most every **6 hours**), and again right away after a
    "wrong token" or "server unreachable" error (at most once a minute). Phones see an edit within minutes:
    GitHub's raw file service caches for about 5 minutes.
  - The last copy that worked is saved on the phone, so the default server keeps working offline or when GitHub
    is down. Until the file has been read once, or if it's empty (`server_url`/`app_token` blank), the app uses the
    values built into the APK (`HARMONIQ_SERVER_URL` / `HARMONIQ_APP_TOKEN`, below).
  - **The file is public, so its token is a public "app key", not a secret.** It's no less safe than before: a
    token built into a public APK can be extracted too. It keeps out bots and scanners; the server's per-device
    and global rate limits do the rest.
- **My own server.** Anyone can run their own harmoniq-server and enter its URL and token in
  **Settings → Global search → Server**. The token is encrypted with an Android Keystore key (AES-GCM) before
  it's stored and is never shown again. **Test connection** calls `/v1/meta` with the entered values. In this mode the
  GitHub file is never read.
- If no server is known for the selected mode, the **Library | Global** switch is hidden. **Settings → Global search**
  is always there, so a server can be set up.
- Every request goes to `<server URL>/v1/…`, e.g. `https://harmoniq.example.com/v1/search`.
- Timeouts: 10 s to connect, 30 s to read (`ServerConfig`).

## Configuration keys

Read by `app/build.gradle.kts` for each build type and written into `BuildConfig`. They're optional now: they only
provide the **built-in fallback** for the default server and the location of the config file.

| Key | Example | Meaning |
|-----|---------|---------|
| `HARMONIQ_SERVER_URL` | `https://harmoniq.example.com` | Built-in server URL, shared by all build types |
| `HARMONIQ_APP_TOKEN` | `hq_xxx…` | Built-in token (the part after the colon of one `APP_TOKENS` entry) |
| `HARMONIQ_REMOTE_CONFIG_URL` | *(default: the raw GitHub URL of `config/global-search.json` on `main`)* | Where the default server's config file is read from. `none` = don't read one |
| any of the above + `_DEBUG` · `_RELEASE` · `_NIGHTLY` · `_CANARY` | `HARMONIQ_SERVER_URL_DEBUG=http://127.0.0.1:8000` | Value for that build type only |

How a value is resolved for a build type (e.g. `canary`):

1. `HARMONIQ_SERVER_URL_CANARY`: environment variable, else `local.properties`.
2. If that is empty: `HARMONIQ_SERVER_URL`: environment variable, else `local.properties`.
3. If that is empty too: empty (for the config URL: the GitHub default).

An environment variable that exists but is empty counts as "set" and hides the `local.properties` value of the same
key. A typical `local.properties` (git-ignored, never commit it):

```properties
# debug: the server on this PC (see "Testing against a local server")
HARMONIQ_SERVER_URL_DEBUG=http://127.0.0.1:8000
HARMONIQ_APP_TOKEN_DEBUG=hq_xxx…
```

Release builds normally need **no** keys: the config file on GitHub supplies the server. See also
[Configuration & secrets](06-configuration-and-secrets.md#global-search-harmoniq-server).

## HTTPS requirement

| Build type | Plain `http://` allowed? | Why |
|------------|--------------------------|-----|
| `debug` | **Yes**, to any host | `app/src/debug/res/xml/network_security_config.xml` sets `<base-config cleartextTrafficPermitted="true" />`, wired in by `app/src/debug/AndroidManifest.xml` |
| `release`, `nightly`, `canary` | **No**, HTTPS only | Android's default since API 28 ([network security config](https://developer.android.com/privacy-and-security/security-config)) |

Also for non-debug builds: the certificate must be trusted by the system. User-installed CAs (and self-signed
certificates) are not trusted by apps targeting API 24+, so use a real certificate. Cloudflare Tunnel and Let's Encrypt
both provide one.
An `http://` URL in a canary/release build fails with **"Server unreachable"**.

## Changing or updating the server URL or token

**Default server (everyone using "Harmoniq server"):** edit [`config/global-search.json`](../config/global-search.json)
on GitHub (the file on the `main` branch; GitHub's web editor is fine) and commit. Nothing is rebuilt or
reinstalled. Phones pick it up the next time Global mode is opened after their copy is 6 hours old, or straight
away when the old server rejects them or can't be reached.

- **Move the server:** change `server_url`.
- **Rotate the token** without anyone losing access
  ([server deployment guide §11](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/docs/deployment-guide.md#11-rotating-the-app-token)):
  1. Add the new token to the server's `APP_TOKENS` next to the old one and restart the server (both work now).
  2. Put the new token in `app_token` and commit.
  3. A day later, remove the old token from `APP_TOKENS`. A phone still holding it gets one `401`, re-reads the file
     and continues with the new token. A download in progress retries instead of failing.
- **Pause Global Search** for everyone (maintenance, YouTube trouble): `"enabled": false` plus a `message`.
- **Show a notice:** set `message`; `null` removes it.

**Own server:** **Settings → Global search → Server → My own server**, then edit *Server URL* and *Access token*.

**Built-in values** (`local.properties`) only matter until a phone has read the config file once, so changing them
still needs a rebuild but is rarely necessary. To check which built-in URL a build has:

```bash
grep HARMONIQ_ app/build/generated/source/buildConfig/canary/dev/anujyadav/harmoniq/BuildConfig.java
```
Prints the built-in URL, token and config file URL of the last canary build. (Don't share its output: it contains
the built-in token.)

## Testing against a local server

Start the server on your PC (`Harmoniq-server\scripts\run.ps1`, or Docker) and confirm it with
`curl http://localhost:8000/readyz`. Then pick one option for the **debug** build:

| Option | `HARMONIQ_SERVER_URL_DEBUG` | Works with | Notes |
|--------|-----------------------------|-----------|-------|
| **USB + `adb reverse`** (recommended) | `http://127.0.0.1:8000` | Physical device on USB (or wireless adb) | Run the command below after every reconnect |
| **Emulator** | `http://10.0.2.2:8000` | Android emulator | `10.0.2.2` is the emulator's alias for the PC's `127.0.0.1` ([emulator networking](https://developer.android.com/studio/run/emulator-networking-address)) |
| **Same Wi-Fi** | `http://<pc-lan-ip>:8000` | Phone on the PC's network | Only for the **local mode** server (listens on `0.0.0.0`); Windows Firewall may need to allow Python on port 8000. The Docker setup listens on `127.0.0.1` only, so use one of the other options with it |

```bash
adb reverse tcp:8000 tcp:8000
```
Makes the phone's `127.0.0.1:8000` reach port 8000 on this PC. `adb reverse --list` shows active rules,
`adb reverse --remove-all` clears them.

Use the token from the server's `.env` (the part after the colon in `APP_TOKENS`) as `HARMONIQ_APP_TOKEN_DEBUG`,
then `./gradlew :app:installDebug`.

Cleartext only works in `debug`. To test a `canary` or `release` build, point it at an HTTPS server.

## Checking that the server is reachable

1. **In the app:** **Settings → Global search → Server status**. It shows `Server 0.3.0 · YouTube ok` when the app
   can reach the server with its token, or the error (tap to check again). `YouTube degraded`/`blocked` means the
   server is up but YouTube is refusing it.
2. **From the phone's browser:** open `https://harmoniq.example.com/healthz`. `{"status":"ok"}` means the network
   path works (this endpoint needs no token).
3. **From a PC:**

   ```bash
   curl -fsS https://harmoniq.example.com/v1/meta -H "X-App-Token: hq_xxx…"
   ```
   Returns the server version, components and YouTube status. A `401` means the token is wrong.

4. **Logs:** Global Search failures are logged under the app's tag:

   ```bash
   adb logcat -s HarmoniqLogger
   ```
   Look for lines starting with `GlobalSearch:` (e.g. `search failed: network error: …`).

## Troubleshooting Global Search

| What the app shows | Meaning | What to do |
|--------------------|---------|------------|
| No **Library \| Global** switch | No server is known for the selected mode: the config file is empty/unreachable and the build has no built-in values, or *My own server* has no URL/token | **Settings → Global search → Server**: check *Connection* (tap to refresh), or fill in your own server |
| No **Library \| Global** switch and no **Watch List** tab, but *Global search* is in Settings | The user turned Global Search off | **Settings → Global search → Enable Global Search** |
| Only *Songs* and *Videos* chips (no *All*, *Playlists*) | The server is older than v0.3.0 (`/v1/meta` has no `features.search_types`) | Update harmoniq-server |
| *This playlist is private, deleted or can't be opened* | `playlist_not_found` (also some `RD…` mixes) | Use *Open in YouTube* |
| *Server unreachable* | Network error: no connection, DNS failure, timeout, server or tunnel down, or `http://` in a non-debug build | Open `/healthz` in the phone's browser; check the server and tunnel; local testing: rerun `adb reverse` |
| *The server didn't accept the access key…* | `401 unauthorized`: the token isn't in the server's `APP_TOKENS`, and re-reading the config file didn't bring a new one | Default server: put a valid token in `config/global-search.json`. Own server: re-enter the token |
| *Global Search is paused right now* (or your notice) | `"enabled": false` in the config file | Set `"enabled": true` when the server is back |
| *No Global Search server is set up* | Download tapped while no server is known | **Settings → Global search → Server** |
| *Rate limited, try again in N s* / *Rate limited, try again later* | Per-device (30 searches/min, 60 downloads/h) or global limit | Wait. Limits are set on the server |
| *Server busy, will retry* | A retryable server error (`upstream_error`, `queue_full`, `too_many_active_jobs`, …) | Downloads retry by themselves (up to 5 runs). If it persists, check `/v1/meta` and the server logs |
| *Server temporarily blocked by YouTube* / banner *YouTube is blocking the server right now* | The server's IP is bot-checked by YouTube | Server side: update yt-dlp, then check the proxy and cookies. See the server troubleshooting [on a VPS](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/docs/deployment-vps.md#13-troubleshooting) or [at home](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/docs/deployment-guide.md#15-troubleshooting) |
| *Unexpected server response* | The response didn't match the API contract (wrong URL pointing at another web server, or app/server versions out of step) | Check the URL; `/v1/meta` must return JSON |
| *Search text is empty or too long* | Query over 200 characters | Shorten it |
| *This video is private, removed or not available* · *This video is age restricted* · *Live streams can't be downloaded* · *Longer than the server's limit* | That video can't be downloaded | Pick another result |
| *Waiting for connection* | The download is waiting for network | It continues when the phone is online |
| *Not enough storage* | Saving to `Download/Harmoniq` failed | Free space on the phone |
| *Storage permission is needed to save downloads on Android 9* | API 28 needs `WRITE_EXTERNAL_STORAGE` | Grant it when asked |
| *Too many downloads queued, wait for some to finish* | 20 downloads already queued locally | Wait |
| Saved, but the song isn't in the library | `Download/Harmoniq` isn't inside a media folder | Accept the *Add downloads to your library?* prompt, or **Settings → Global search → Not in library: add folder** |
| *Preview not available for this video* | The video can't be embedded | Use *Open in YouTube*; downloading still works |

## Features (app + server v0.3.0)

- **Tabs:** All (10 songs, videos and playlists, each with *See all*), Songs, Videos, Playlists. The app reads
  `features.search_types` from `/v1/meta`; an older server only gets Songs and Videos.
- **Rows:** every song or video has a ⋮ menu (Download, Add to Watch List, Share, Open in YouTube, Details), two swipe
  actions (customizable in **Settings → Swipe actions → Online songs**) and a badge on the cover: ✓ downloaded,
  a ring while downloading, ! after a failure.
- **Playlists:** tapping one opens its songs in a sheet; tapping a song opens its preview in the same sheet (back
  arrow / system back returns to the list).
- **Preview:** starts playing once the player is ready (**Autoplay previews** setting), never in the background; the
  ⋮ menu sits next to the title.
- **Watch List:** a Library tab (after Songs) with songs saved for later, stored on the phone (works offline),
  sortable, remove with undo.
- **Enable Global Search** (Settings) hides all of it; downloads in progress finish and the Watch List is kept.

## Where the code lives

All paths are under `app/src/main/java/dev/anujyadav/harmoniq/`.

| Path | What |
|------|------|
| `services/globalsearch/ServerConfig.kt` | Config file URL and refresh timings, all timeouts and limits |
| `services/globalsearch/connection/` | `ServerConnections` (which server + token, refresh, API client), `ConnectionRules` (pure resolution rules), `ServerConnectionStore` (saved choices, cached config), `KeystoreSecretCipher` (token encryption), `RemoteConfigLoader` |
| `services/globalsearch/GlobalSearch.kt` | Facade used by the UI (created by `Symphony`): search, server meta, download queue, library prompt, rescans |
| `services/globalsearch/GlobalSearchServices.kt` | Builds an API client for a connection; small persisted flags (library prompt) |
| `services/globalsearch/GlobalSearchMessages.kt` | Error code → message shown to the user |
| `services/globalsearch/DeviceId.kt` | Random per-install ID sent as `X-Device-Id` |
| `services/globalsearch/api/` | `HarmoniqServerApi` (OkHttp), DTOs, `ServerError` and error codes |
| `services/globalsearch/download/` | `ServerDownloadWorker` (WorkManager), `DownloadPipeline`, `DownloadSaver`, `DownloadRecords`, `LibraryLink`, notifications |
| `services/globalsearch/SearchPage.kt` | One page of results (songs / videos / playlists + failed sections) and the per-video download state |
| `services/globalsearch/watchlist/` | Watch List: Room entity, repository, sort order |
| `ui/components/OnlineTrackActions.kt` | `OnlineTrackAction` (menu order) and the shared actions class; swipe scopes are in `SwipeScopes.kt` |
| `ui/view/globalsearch/` | Results (All / Songs / Videos / Playlists), online rows + actions host, the sheet (playlist → preview), Watch List page, YouTube player, download controls, library prompt |
| `ui/view/settings/GlobalSearchSettingsView.kt`, `GlobalSearchServerSettings.kt` | **Settings → Global search** and its **Server** section |
| `ui/view/SearchTabContent.kt` | The Library / Global switch |

Build and config: `app/build.gradle.kts` (keys, `lint {}` exceptions), `app/src/debug/` (cleartext for debug),
`app/src/main/AndroidManifest.xml` (`FOREGROUND_SERVICE_DATA_SYNC`, `WRITE_EXTERNAL_STORAGE` up to API 28,
WorkManager's foreground service). Tests: `app/src/test/java/dev/anujyadav/harmoniq/services/globalsearch/`, with
server JSON fixtures in `app/src/test/resources/globalsearch/`. Design record and deviations:
[android-integration-plan.md](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/docs/android-integration-plan.md#17-implementation-status-and-deviations).

## Next steps

1. Build a **signed canary**, install it on an Android 9 (API 28) and an Android 14 (API 34) device or emulator, and
   run the [test matrix](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/docs/android-integration-plan.md#16-validating-the-complete-flow-before-testers-get-it).
2. Test a signed `release` build the same way (R8).
3. After an AGP upgrade: move WorkManager past 2.10.x (2.11+ needs Room 2.7) and drop the lint exceptions
   in `app/build.gradle.kts`.
