# 10. Global Search improvements & Watch List: analysis and implementation plan

[← Back to guide](../DEVELOPER_GUIDE.md) · Related: [9. Global Search server](09-global-search-server.md) ·
server contract [Harmoniq-server `docs/api.md`](https://github.com/AnujYadav-Dev/Harmoniq-server/blob/main/docs/api.md)

> Status: **implemented** (2026-11-03): harmoniq-server v0.3.0 and the app changes below, not committed yet.
> Written from the `dev` branch and harmoniq-server v0.2.0; see §13 for what changed from this plan and what is
> still unverified.

Scope:

1. 3-dot menu + swipe gestures + a cleaner "downloaded" indicator on Global Search songs and videos
2. Playlist search, with the playlist's songs shown in a drawer
3. An **All** tab (Songs / Videos / Playlists, 10 each)
4. Preview: autoplay once ready, plus a 3-dot menu inside the preview drawer
5. A setting to enable or disable Global Search
6. A **Watch List** in Library

---

## 1. Current architecture (what exists today)

| Area | Where | How it works | Reuse for |
|------|-------|--------------|-----------|
| **Global Search facade** | `services/globalsearch/GlobalSearch.kt` | Owned by `Symphony` (`symphony.globalSearch`). One `searchRequests` `MutableStateFlow(query, type)` → debounce 400 ms → `flatMapLatest` → `searchState: StateFlow<SearchState>` (`Idle/Loading/Results/Empty/Failed`). Also `meta`, `downloads`, `downloadedVideoIds`, `enqueueDownload()`, `cancelDownload()`, `track()`, `preview()` | Extended, not replaced |
| **Server API** | `services/globalsearch/api/HarmoniqServerApi.kt`, `api/Dto.kt` | OkHttp + kotlinx-serialization, `ServerJson { ignoreUnknownKeys }`, `SearchType { Song, Video }`, `SearchResultDto` (video id, title, artists, album, duration, thumbnails, channel, `watchUrl`) | New endpoints and DTOs added alongside |
| **Config** | `services/globalsearch/ServerConfig.kt` | Every timeout and limit lives here (`SEARCH_RESULT_LIMIT = 20`, debounce, queue sizes…) | New constants go here |
| **Availability** | `GlobalSearchServices.isAvailable()` = BuildConfig URL+token present and not revoked (401). Read once into `GlobalSearch.isAvailable` (a plain `val`) | Gating at `SearchTabContent.kt:107,274`, `Settings.kt:101`, `Base.kt:188` | Becomes a `StateFlow` that also includes the new user setting |
| **Search screen** | `ui/view/SearchTabContent.kt` | Library / Global `SegmentedButton` (`SearchModeSwitch`), then in Global mode one `FilterChip` per `SearchType` → `GlobalSearchResults` → tap opens `GlobalPreviewSheet` | Chips iterate the extended `SearchType` |
| **Results list** | `ui/view/globalsearch/GlobalSearchResults.kt` | `LazyColumn` of `GlobalSearchResultRow` (thumbnail, title, subtitle, explicit badge, trailing `DownloadDone` icon). No menu, no swipe | Row becomes the shared online-track row |
| **Preview** | `ui/view/globalsearch/GlobalPreviewSheet.kt` + `YouTubePreviewPlayer.kt` | `ModalBottomSheet`; `/track` and `/preview` loaded with `produceState` into a private `Loadable`; player shown only when embeddable; `onReady` → **`cueVideo`** (never autoplays); `onPlaying` pauses `Radio`; released on dispose | Autoplay, 3-dot menu; `TrackDetails` reused for "Details" |
| **Downloads** | `GlobalDownloadControls.kt` (button, format picker, API 28 permission, progress), `download/*` (worker, records, saver) | `DownloadRecords` = SharedPreferences JSON keyed `videoId:format`; `downloads` merges records with WorkManager | Download action reuses `enqueueDownload` + the permission logic (extracted) |
| **Song actions & menu** | `ui/components/SongActions.kt`, `SongCard.kt` | `enum SongAction` + `SongAction.menuActions` order; `class SongActions(isAvailable/label/icon/perform)`; `SongActionsHost` hosts dialogs; `SongDropdownMenu`; `SongActions.toSwipeAction()` | **The pattern to copy** for online items (local `Song` can't be reused: it needs a file `Uri`) |
| **Swipe system** | `SwipeActionsBox.kt` (generic, takes any two `SwipeAction`s, M3 `SwipeToDismissBox`, snaps back), `SwipeScopes.kt` (`SongSwipeScope`, `PlaylistSwipeScope`, `ProvideSwipeScope`), `Settings.swipeActionEntry(kind, scope, direction)` (`OptionalEnumEntry`, per kind/scope/direction), `settings/SwipeActionTiles.kt` (`SettingsSwipeActionsSections` renders every scope + reset) | A third swipe **kind** for online items: customizable for free |
| **Settings** | `services/Settings.kt` (`BooleanEntry`, `EnumEntry` stores the enum **name**, `OptionalEnumEntry`, flows); `ui/view/settings/GlobalSearchSettingsView.kt` (format, .lrc, folder, server status) | `globalSearchType` is an `EnumEntry<SearchType>` | New entries: enabled, autoplay |
| **Library tabs** | `ui/view/navigation/LibraryPill.kt` (enum: icon, label, count, `songSwipeScope`), `LibraryTabContent.kt` (`HorizontalPager` + `when (pill)`), `Settings.libraryPillOrder` (saved order; **unknown/new pills are appended at the end**) | New `WatchList` pill |
| **Persistence** | `services/database/PersistentDatabase.kt` (Room, `version = 1`, entities `[Playlist]`, schemas in `app/room-schemas/`), `CacheDatabase` (v2, uses `AutoMigration`), `PlaylistStore` DAO, `PlaylistRepository` (`MutableStateFlow` lists) | Watch List table via `AutoMigration(1, 2)` |
| **Dialogs** | `InformationDialog`, `ScaffoldDialog`, `SongInformationDialog` | "Details" for online items |
| **Misc** | `SeeAllButton`, `IconTextBody` (empty/error states), `ActivityUtils.startBrowserActivity`, `AppStrings` (all UI text), `UndoHost` | All tab, Open in YouTube, undo on remove |

**Server (v0.2.0):** `GET /v1/search?type=song|video` only (`app/api/v1/search.py:28`, `domain/models.py:44,59`).
No playlist search or playlist contents. **Server changes are required** for features 2 and 3.

Verified against real data (ytmusicapi 1.12.3, 2026-11-03):

- `search(q, filter="playlists")` takes about 1.5 s and returns `title`, `browseId` (`VL…`), `author`, `thumbnails`.
  `itemCount` is often `null`, and it returns 20 results even with `limit=10`, so the server must cut the list.
- `get_playlist(id, limit)` takes about 0.4 s. Each track has `videoId`, `title`, `artists`, `album`,
  `duration_seconds`, `videoType`, `isAvailable`, `isExplicit`, `thumbnails`.
- The unfiltered `search(q)` doesn't label result types reliably, so "All" needs three filtered searches.

**Player library (android-youtube-player 13.1.0, checked in the AAR):** `WebViewYouTubePlayer` disables
`mediaPlaybackRequiresUserGesture`; it has `YouTubePlayer.loadVideo`, `IFramePlayerOptions.Builder.autoplay`
and `YouTubePlayerUtils.loadOrCueVideo(lifecycle, id, start)`. That last one loads (plays) only while the lifecycle
is resumed and cues otherwise. So autoplay is supported without workarounds.

## 2. Design principles

- **One model for every online track.** Playlist tracks and Watch List items are returned and stored as
  `SearchResultDto`, so rows, preview, download and actions work identically everywhere.
- **One actions class, many entry points.** The 3-dot menu, swipe gestures, preview drawer menu and Watch List all go
  through `OnlineTrackActions`, as local songs go through `SongActions`.
- **Configuration over hard-coding.** Limits in `ServerConfig`, user choices in `Settings`, text in `AppStrings`,
  menu order in a single list, swipe defaults in a scope enum.
- **Feature-detect the server.** The app reads `features.search_types` from `/v1/meta`, so a new app keeps working
  against an older server (Playlists / All hidden).

## 3. Data / model changes

### App

| Change | Detail |
|--------|--------|
| `SearchType` | Add `All("all")` and `Playlist("playlist")`. `EnumEntry` stores the name, so saved `Song`/`Video` values stay valid. Display order `All, Songs, Videos, Playlists`; the default for new installs is `All` |
| `PlaylistResultDto` (new) | `playlistId`, `title`, `author`, `itemCount?`, `thumbnails`, `watchUrl` |
| `PlaylistDto` (new) | Playlist fields + `description?`, `trackCount?`, `duration?`, `tracks: List<SearchResultDto>`, `truncated` |
| `SearchResponseDto` | Add optional `playlists: List<PlaylistResultDto>` and `sections: SearchSectionsDto?` (`songs`, `videos`, `playlists`, `errors`) |
| `MetaDto` | Add `features.searchTypes: List<String>` (missing = `["song","video"]`) |
| `SearchResultDto` | Add `fun shareUrl()` / `fun youtubeUrl()` helpers (moves the private `youtubeWatchUrl()` out of `GlobalPreviewSheet.kt`). The URL template goes into `ServerConfig` |
| `GlobalSearch.SearchState.Results` | Becomes `Results(query, type, page: SearchPage)`, where `SearchPage(songs, videos, playlists, sectionErrors)`. Single-type searches fill one list |
| Download state per video | New derived `StateFlow<Map<String, OnlineDownloadState>>` keyed by `videoId` (`None/Queued/Running(fraction)/Done/Failed`), merged over formats, so a row doesn't care which format was used |
| `WatchListItem` (Room entity, new) | See §7 |

### Server (harmoniq-server → v0.3.0)

| Change | Detail |
|--------|--------|
| `GET /v1/search?type=playlist` | Results in a new `playlists` field (`PlaylistResult`: `result_type="playlist"`, `playlist_id` without `VL`, `title`, `author`, `item_count?`, `thumbnails`, `watch_url`). `results` stays `[]`, so old clients never see a new shape |
| `GET /v1/search?type=all&limit=10` | `sections: {songs, videos, playlists}`, fetched **concurrently** on the server, cached per section. A failing section comes back empty with `sections.errors.{name}=code`; the call fails only if all three fail. Counts once against the per-device search limit and **3×** against the global upstream budget |
| `GET /v1/playlists/{playlist_id}?limit=` (new) | Playlist info + `tracks: [SearchResult]` (same shape as search results; `result_type` = `song` for `MUSIC_VIDEO_TYPE_ATV`, else `video`). Unavailable / id-less tracks dropped; `truncated: true` above `MAX_PLAYLIST_TRACKS` (config, default 200). Cached `METADATA_CACHE_TTL` |
| Validation | Playlist id `^(VL)?[A-Za-z0-9_-]{10,64}$` (normalise by stripping `VL`). New error codes `invalid_playlist_id` (422) and `playlist_not_found` (404) |
| `/v1/meta` | `features.search_types: ["song","video","playlist","all"]` |
| Config / docs / tests | `MAX_PLAYLIST_TRACKS`, `SEARCH_ALL_SECTION_LIMIT`; update `api.md`; pytest for playlist search, all-sections partial failure, playlist detail, id validation, caching, meta |

## 4. UI / UX changes

```text
Search ▸ [ Library | Global ]                       (Global hidden when the setting is off)
          (All) (Songs) (Videos) (Playlists)        chips; Playlists/All only if the server supports them
 All:   Songs ───────────── See all ›   ← SeeAllButton switches the chip
          ▢ Title                     ⋮
            Artist · Album · 3:45  ✓    ← downloaded badge on the thumbnail + tinted row
        Videos ──────────── See all ›
        Playlists ───────── See all ›
          ▤ Lofi Beats · Author · 29 songs       tap → playlist drawer
```

- **Online track row** (`OnlineTrackRow`, replaces `GlobalSearchResultRow`): thumbnail with a **status badge**
  overlaid on the bottom corner (✓ downloaded on `primary`, small circular progress while downloading, ! failed).
  It reuses the badge style of `SongCard`'s `thumbnailLabel`. Then title, explicit badge + subtitle, a ⋮ button
  (`LabeledIconButton(MoreVert, AppStrings.MoreOptions)`), and the whole row wrapped in the swipe box.
- **Playlist row** (`OnlinePlaylistRow`): stacked-cover thumbnail, title, "author · N songs", chevron.
- **Playlist drawer:** a `ModalBottomSheet`.
  - Header: cover, title, author, track count, and "showing first N" when truncated.
  - List: the same `OnlineTrackRow`s.
  - Tapping a track opens its preview in the **same sheet** (a page stack of Playlist → Preview with a back arrow
    and system back), so two bottom sheets never stack.
- **Preview drawer:** the ⋮ menu sits at the end of the title row (top-aligned with the first title line; the title
  takes `weight(1f)`). It shows every action except Preview. The download controls stay as they are.
- **Watch List page** (Library pill "Watch List", placed right after Songs):
  - toolbar with count + sort (date added / title / artist, using `MediaSortBar`);
  - `OnlineTrackRow` list;
  - empty state ("Save songs from Global Search to listen later");
  - remove with undo (`UndoHost`).
- **Settings → Global Search:**
  - "Enable Global Search" switch at the top; the other tiles are greyed out when it's off;
  - "Autoplay previews" switch;
  - Swipe settings get "Online songs – Search / Playlist / Watch List" sections automatically.

## 5. State management

- `GlobalSearch` keeps being the single owner of state; the UI only collects flows.
- `searchRequests` carries the type. `type=All` calls the new all-sections endpoint, and the Playlists tab calls
  `type=playlist`. Changing the chip re-runs the current query (existing behaviour).
- New flows on `GlobalSearch`: `isEnabled: StateFlow<Boolean>` = `settings.globalSearchEnabled.flow` ∧ available,
  `supportedSearchTypes` (from meta), `downloadStateByVideoId`.
- Playlist contents: `suspend fun playlist(id): Result<PlaylistDto>`, with a small in-memory LRU like the server
  cache. The drawer state uses a `Loadable` (promote the private `Loadable` in `GlobalPreviewSheet.kt` to a shared
  file instead of creating a second one).
- Sheet navigation (`GlobalSheetState`: stack of `Playlist(id)` / `Preview(result, autoplay)`) is held in
  `SearchTabContent` and in the Watch List page with `rememberSaveable` and a JSON `Saver`
  (`SearchResultDto` is `@Serializable`), so rotation keeps the open drawer.
- Watch List: `WatchListRepository` exposes `items: StateFlow<List<WatchListItem>>` and
  `ids: StateFlow<Set<String>>`, loaded from Room at `onSymphonyReady`.

## 6. Actions, menu and gestures

```kotlin
enum class OnlineTrackAction { Preview, Download, AddToWatchList, Share, OpenInYouTube, Details, Remove;
    companion object { val menuActions = listOf(Download, AddToWatchList, Share, OpenInYouTube, Details) } }

class OnlineTrackActions(context, result, downloadState, isInWatchList, removeAction?, onPreview, onShowDetails, ...) {
    fun isAvailable(a)   // Download hidden while Done/Running (menu shows "Downloaded"/progress instead); Remove only where offered
    fun label(a)         // AddToWatchList ⇄ "Remove from Watch List", like Favorite/Unfavorite
    fun icon(a); fun perform(a)
}
@Composable fun OnlineTrackActionsHost(...)   // hosts the Details dialog + the download launcher (API 28 permission)
fun OnlineTrackActions.toSwipeAction(a)       // same as SongActions.toSwipeAction
```

| Action | Implementation (reused piece) |
|--------|-------------------------------|
| Preview | Opens the preview drawer (autoplay) |
| Download | `globalSearch.enqueueDownload(result, settings.globalSearchDownloadFormat.value)`. The API 28 permission + enqueue + messages are extracted from `GlobalDownloadControls` into `rememberDownloadLauncher()`, which both the button and the action use |
| Add to / Remove from Watch List | `watchList.toggle(result)`; removal from the Watch List page shows Undo |
| Share | `Intent.ACTION_SEND`, `text/plain`, `EXTRA_TEXT` = `result.watchUrl` (server-provided; `music.youtube.com` for songs) or the YouTube fallback, `EXTRA_SUBJECT` = title, wrapped in `Intent.createChooser` (opens the system share sheet) |
| Open in YouTube | `ActivityUtils.startBrowserActivity(activity, uri)`. `ACTION_VIEW` on a YouTube URL opens the YouTube app when installed, else the browser |
| Details | `InformationDialog` showing `/track` data. **Reuses** `TrackDetails` from `GlobalPreviewSheet.kt` (made internal and shared), with a loading/error state |
| Remove | Contextual (Watch List page) |

**Gestures:** a new swipe **kind** reuses everything.

- `OnlineSwipeScope` enum (`Search`, `Playlist`, `WatchList`) with defaults:
  - Search / Playlist: right = Download, left = Add to Watch List
  - WatchList: right = Download, left = Remove
- `Settings.onlineSwipeAction(scope, direction)` = `swipeActionEntry("online", …)`.
- `OnlineSwipeActionsBox(context, actions)` = `SwipeActionsBox(actions.toSwipeAction(right), …)`.
- `LocalOnlineSwipeScope` + `ProvideSwipeScope(onlineScope = …)`.
- `SettingsSwipeActionsSections` loops over `OnlineSwipeScope.entries` as well, and the reset covers it. Users can
  then customize the online swipes in the existing Swipe actions screen.

Rows are identical for songs and videos; only the data differs.

## 7. Watch List design

```kotlin
@Entity(tableName = "watch_list")
data class WatchListItem(
    @PrimaryKey val videoId: String,
    val resultType: String,            // "song" | "video"
    val title: String,
    val artists: List<String>,         // stored via the existing RoomConvertors.serializeStringList
    val album: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val watchUrl: String?,
    val isExplicit: Boolean?,
    val addedAt: Long,
    val sourcePlaylistId: String?,     // where it was saved from (optional)
)
```

- **Storage:** Room `PersistentDatabase` **version 2** with `AutoMigration(from = 1, to = 2)` (adding a table needs
  no spec). Commit the generated `room-schemas/.../2.json`. Existing playlists are untouched. This matches how the
  app stores user data (playlists), unlike the SharedPreferences JSON used for download records.
- **DAO** `WatchListStore`: `upsert`, `delete(videoId)`, `entries()` (and a `Flow` query if preferred).
- **Repository** `services/globalsearch/watchlist/WatchList.kt`, owned by `GlobalSearch`: `items`, `ids`, `add`,
  `remove`, `toggle`, `contains`.
- **Mapping:** `WatchListItem.toSearchResult()` / `SearchResultDto.toWatchListItem()`, so every UI piece takes a
  `SearchResultDto`.
- **Downloaded state:** from `downloadStateByVideoId` (the same badge as search). If the user deletes the file, the
  record still says Done. Same limitation as today; see §12.
- **Offline:** the list always shows (it's local). Actions needing the network show the existing
  "Server unreachable" message.

## 8. Player / preview approach

- `YouTubePreviewPlayer(videoId, autoPlay: Boolean, …)`: `onReady` →
  `if (autoPlay) youTubePlayer.loadOrCueVideo(lifecycle, videoId, 0f) else cueVideo(videoId, 0f)`.
  - `onReady` fires only after the IFrame API has loaded. `loadVideo` then buffers and starts by itself.
  - The player is only created after `/preview` says it's embeddable (existing logic). So playback never starts
    before the content and the player are ready, and never while the app is in the background.
- `autoPlay` = `settings.globalSearchAutoplayPreviews` (new, default `true`). It applies to songs, videos and
  playlist tracks (they all open the same sheet).
- Unchanged: `onPlaying` pauses `Radio`; `onError` falls back to cover + "Open in YouTube"; released on dispose.
- Opening another track in the playlist drawer recreates the player for the new id (the existing `remember(videoId)`).

## 9. Settings changes

| Entry | Type / default | Used by |
|-------|----------------|---------|
| `globalSearchEnabled` | `BooleanEntry("global_search_enabled", true)` | `GlobalSearch.isEnabled`; hides the Library/Global switch (forces Library mode), the Watch List pill and the online actions; the settings page itself stays reachable so it can be turned back on |
| `globalSearchAutoplayPreviews` | `BooleanEntry("global_search_autoplay_previews", true)` | Preview player |
| `globalSearchType` | existing; default → `All` | Chip selection |
| `onlineSwipeAction(scope, dir)` | `OptionalEnumEntry` via `swipeActionEntry("online", …)` | Swipe gestures |
| `libraryPillOrder` | existing | Insert new pills **after their default predecessor** instead of at the end, so "Watch List" appears beside "Songs" for users with a saved order |

When disabled: downloads already running keep running (WorkManager) and finish normally. Watch List data is
kept and comes back when re-enabled.

## 10. Step-by-step implementation plan

Each step: build (`./gradlew :app:assembleDebug`), unit tests, no unrelated changes. Server first, because the app
depends on its contract.

1. **Baseline:** confirm the current build and tests pass; note `git status` (the app branch `dev` may have
   uncommitted work).
2. **Server v0.3.0:** `type=playlist`, `type=all` (concurrent sections, partial errors), `GET /v1/playlists/{id}`,
   validation + error codes, `features.search_types`, config, `api.md`, pytest (unit + one live test). Run the
   full server suite.
3. **App data layer:** DTOs, API methods (`searchAll`, `searchPlaylists`, `playlist`), feature detection, `SearchType`
   additions, `SearchPage` state, `downloadStateByVideoId`; DTO tests with JSON captured from the real server.
4. **Shared actions:** `OnlineTrackAction(s)` + host, `rememberDownloadLauncher` extraction, share / open / details,
   `OnlineSwipeScope` + settings entries + settings sections; unit tests for availability / labels.
5. **Rows & indicator:** `OnlineTrackRow` (badge, ⋮, swipe), `OnlinePlaylistRow`; switch `GlobalSearchResults` to
   them; Songs/Videos tabs at parity.
6. **All tab:** sections + `SeeAllButton` + per-section empty/error states.
7. **Playlists:** Playlists tab, drawer with page stack (Playlist → Preview), truncated / empty / unavailable states.
8. **Preview:** autoplay setting + `loadOrCueVideo`, ⋮ menu in the drawer, shared `TrackDetails`.
9. **Watch List:** entity, DAO, migration 1→2 (+ schema JSON), repository, `LibraryPill.WatchList` + page, pill
   order insertion, actions wired.
10. **Enable/disable setting:** `isEnabled` flow; gate `SearchTabContent`, `Settings.kt:101` tile behaviour,
    `Base.kt:188` (the `LibraryLinkPrompt` host), the Watch List pill and online actions.
11. **Docs:** update `09-global-search-server.md`, `01-project-structure.md`, server `api.md`, and this plan's status.
12. **Verification:** the full checklist below on an emulator + the USB phone against the local server.

## 11. Testing checklist

**Server (pytest):** playlist search shape and limit cut; `all` with one section failing → others returned +
`errors`; all three failing → error; playlist detail (ATV → song, UGC → video, unavailable dropped, truncation);
id validation (`VL` prefix, bad chars, too short); caching; `/v1/meta` features; old `song`/`video` unchanged.

**App unit tests:**

- DTO parsing (new shapes; missing `features`)
- `SearchPage` building
- `OnlineTrackActions.isAvailable` / labels for each download state and Watch List state
- swipe defaults per scope
- `libraryPillOrder` insertion of new pills
- `WatchListItem` ↔ `SearchResultDto` mapping
- Room migration test 1→2 (`MigrationTestHelper`), with existing playlists still present

**Manual / device (debug build + local server over USB):**

- [ ] Songs, Videos, Playlists, All: results, ⋮ menu, both swipes, row tap → preview autoplays
- [ ] Share opens the system share sheet with the right link; Open in YouTube opens the app or browser
- [ ] Details dialog: loading, data, error
- [ ] Download from the menu, from a swipe and from the preview; badge goes progress → ✓; already downloaded = no
      duplicate
- [ ] All: 10 per section, See all switches tab, one section failing shows the others
- [ ] Playlist drawer: open, scroll, preview a track (autoplay), back to the list, download, add to Watch List;
      empty/private playlist message
- [ ] Preview never plays before ready; Radio pauses; closing the sheet stops audio; app in background → no playback
- [ ] Autoplay setting off → cue only
- [ ] Watch List: add/remove (undo), persists after app restart, sort, swipes, offline open, downloaded badge, preview
- [ ] Customize online swipes in Settings → takes effect immediately; reset works
- [ ] Global Search off → switch, Watch List pill and online actions gone; back on → everything returns, data intact
- [ ] Existing users: saved tab order keeps working and Watch List appears beside Songs; old `Song`/`Video` saved
      chip still selected; downloaded songs still in the library
- [ ] Older server (v0.2.0): Playlists/All hidden, Songs/Videos still work
- [ ] Rotation with a drawer open; process death; release build (R8) smoke test

## 12. Edge cases and risks

| Case / risk | Handling |
|-------------|----------|
| Playlist is private, deleted, or a mix (`RD…`) the API can't open | Server `playlist_not_found`; drawer error state with "Open in YouTube" |
| Huge playlists | Server truncates to `MAX_PLAYLIST_TRACKS`; header says "showing first N" |
| Playlist entries without `videoId` / unavailable | Dropped server-side; count shown is what's playable |
| "All" costs 3 upstream searches | One request from the app; the server counts it 3× against its global budget; sections cached separately |
| Older server | `features.search_types` missing → only Songs/Videos chips |
| Saved chip value no longer offered (older server) | Fall back to the first supported type |
| Swipe conflicts with the playlist drawer's vertical drag / pager | `SwipeToDismissBox` is horizontal-only; the sheet drag is vertical. Test on the drawer list specifically |
| Autoplay policies | Library disables the user-gesture requirement (verified); `loadOrCueVideo` respects the lifecycle |
| Two sheets stacking | Single sheet with an internal page stack |
| Downloaded file deleted by the user | Record still says Done. Optional improvement: verify `savedUri` with `ContentResolver` when the row becomes visible and downgrade the badge |
| Same song downloaded in two formats | Indicator is per `videoId` (any format Done = ✓) |
| Room migration | Auto-migration + migration test; schema JSON committed |
| Global Search disabled mid-download | Download completes; only the UI hides |
| Share with no target apps | `createChooser` shows the system "no apps" sheet; wrap in `try/catch` + toast like `SongActions.share` |
| Rate limits (30 searches/min) | Debounce unchanged; All counts as one search request per device |
| RTL layouts | Swipe direction follows the existing `SwipeActionsBox` semantics (start/end) |

## 13. Implementation status and deviations

Implemented in the §10 order. Verified: server pytest (unit + live), app `assembleDebug`, all JVM unit tests
(incl. the Room 1→2 `MigrationTestHelper` test), release `minifyReleaseWithR8`, install over an existing debug
install on a phone (launches, no crash; the real 1→2 migration ran).

| Plan | What was done instead, and why |
|------|-------------------------------|
| API methods `searchAll` / `searchPlaylists` | The single `search(query, type, limit)` already covers every type (same endpoint); only `playlist(id)` was added |
| `GlobalSheetState` with `Playlist(id)` / `Preview(result, autoplay)` | `GlobalSheetPage.Playlist(PlaylistResultDto)` (the header shows while loading) and `Preview(result, sourcePlaylistId)`; autoplay is read from Settings in the preview, not stored per page |
| Separate remove-action type for online rows | Reuses `SongRemoveAction` (label, onRemove, onUndo), which is generic |
| Server `limit` for playlists sent by the app | The app doesn't send one; the server's `MAX_PLAYLIST_TRACKS` decides, so a lower server limit can't cause `invalid_query` |
| Saved chip not offered | Not overwritten: the chip falls back to the first supported type for display/search only, so the saved choice returns when the server supports it again |
| Until `/meta` answers | All four chips show; a search for All/Playlists waits up to `ServerConfig.metaWaitTimeout` for `/meta` and falls back to Songs on an old server |
| Room migration test | Robolectric only sees the tested variant's merged assets, so `room-schemas/` is added to the **debug** assets (not release) |
| Settings tile (`Settings.kt:101`) | Unchanged: still shown when the build has a server, so Global Search can be turned back on |

**Fixed after release to testers: reorder crash.** Opening *Reorder tabs* (and any other reorderable list: Queue,
Settings → Home playlists) killed the app with `NoSuchMethodError: LazyItemScope.animateItemPlacement`.
`sh.calvin.reorderable` 2.1.1 was built for Compose 1.6, but android-youtube-player 13.1.0 (added with Global
Search) pulls lifecycle 2.9.4, which raises Compose UI to 1.9.0, where that method no longer exists. Debug ran
foundation 1.9.0 (crash) while release kept foundation 1.7.2 next to UI 1.9.0 (an unsupported mix). Fix: reorderable
2.4.3 (built on Compose 1.7, uses `animateItem`), and `compose = "1.9.0"` declared in the version catalog and applied
to foundation, so both variants run the same Compose. No dex in the APK references `animateItemPlacement` anymore.

Not verified yet (needs hands on the device): the §11 manual checklist (menus, swipes, share sheet, autoplay and
background behaviour, playlist drawer back navigation inside the sheet, Watch List undo/sort, rotation, older-server
chips, settings toggle) and a signed release build smoke test.
