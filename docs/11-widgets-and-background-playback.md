# Widgets and background playback

How Harmoniq plays music and takes commands with no activity open, and how the home screen widgets, launcher shortcuts and Quick Settings tile are built on that.

## The rules everything here follows

1. **`Symphony` is created only when it is needed.** It is a plain class owned by `HarmoniqApplication`, created lazily on the main thread. Drawing a widget never creates it. Only a playback command does.
2. **Everything outside the app acts through one door.** Buttons send a `PlaybackCommand` to `RadioNotificationService`, which runs it. There is no second way to start playback.
3. **Widgets draw from a saved snapshot, not from the player.** A widget has to draw after a reboot or a process kill, when nothing is loaded.
4. **No work without a widget.** The publishers check for widgets first, and update on events rather than on a timer.

## Commands

| Piece | File (under `services/playback/`) | Job |
|-------|------|-----|
| `PlaybackCommand` | `PlaybackCommand.kt` | Every action as a sealed type, encoded in an intent's `harmoniq-command://…` URI. Immutable `PendingIntent`s |
| `PlaybackCommandDispatcher` | `PlaybackCommandDispatcher.kt` | Runs one command, returns an outcome (with a message if it failed) |
| `RadioNotificationService` | `services/radio/` | Enters the foreground first, runs commands one at a time, then settles: stays if playing, stops if not |

Try any command from a shell on a debug build, the only build that exports this service:

```bash
adb shell am start-foreground-service -n dev.anujyadav.harmoniq.debug/dev.anujyadav.harmoniq.services.radio.RadioNotificationService -a dev.anujyadav.harmoniq.playback.RUN -d harmoniq-command://next
```

Commands with no arguments: `resume`, `play_pause`, `next`, `previous`, `toggle_shuffle`, `cycle_repeat`, `toggle_favorite`, `shuffle_all`, `clear_sleep_timer`, `refresh_suggestions`. The rest (`play_playlist`, `play_song`, `jump_to_queue_item`, `set_sleep_timer`) take arguments, see `PlaybackCommand`.

To add a command: add the type and its encoding in `PlaybackCommand`, handle it in `PlaybackCommandDispatcher`, and add it to the round-trip test.

## What widgets draw

- `PlaybackPublisher` hears player events, throttles them to once a second, and writes a `PlaybackSnapshot` (SharedPreferences `playback_snapshot`, JSON) through `PlaybackSnapshotStore`. Then it updates the widgets through `PlaybackWidgetHost`.
- `PlaybackArtwork` shrinks covers to fit what Android lets a widget send. A widget keeps one layout per size, each with the image, so the limit is divided between them.
- `PlaybackWidgetState` combines the snapshot, artwork, list covers and `WidgetAppearance`.
- `CoverLoader` loads the covers of a list, shown no larger than they need to be. It keeps what's still on show, so a queue moving on by one song loads one image. A playlist's own cover keeps its file name when replaced, so `WidgetCover` also has a version.
- `WidgetAppearance` reads the theme from the `settings` preferences through `AppearanceSettings`, without `Symphony`. `forWidgets()` then corrects accent colors too pale to see on a widget. The app's own theme is not changed.
- **Progress doesn't tick.** It shows where the song was at the last event. Moving it would mean constant updates.

The lyrics widget is the exception: `LyricsWidgetPublisher` keeps lines in memory only (`LyricsWidgetStore`) and works only when the setting is on, a lyrics widget exists, music is playing and the screen is on. **It is off by default** (**Settings → Player → Synced lyrics widget**). Its battery cost has not been measured. To measure it, compare `adb shell dumpsys batterystats dev.anujyadav.harmoniq.debug` after `adb shell dumpsys batterystats --reset` and a session with and without it.

## The widgets

Built with Glance. All receivers are `android:exported="false"` and live in `widgets/`.

| Picker name | Receiver | Default | Smallest | What changes with size |
|-------------|----------|---------|----------|------------------------|
| Now playing bar | `NowPlayingBarReceiver` | 4×1 | 180×56dp | From 280dp wide: adds artwork and previous |
| Full player | `FullPlayerReceiver` | 4×2 | 180×56dp | Below 280×110dp it is the bar. From 130dp tall: larger artwork |
| Album art | `AlbumArtReceiver` | 2×2 | 110×110dp | Fills whatever it's given |
| Quick actions | `QuickActionsReceiver` | 2×1 | 56×56dp | Below 110dp wide: one button |
| Play button | `QuickActionsSmallReceiver` | 1×1 | 56×56dp | Above 110dp wide: both buttons |
| Playlist shortcuts | `PlaylistShortcutsReceiver` | 4×2 | 180×110dp | From 300dp wide: two columns |
| Up next | `UpNextReceiver` | 4×3 | 180×110dp | The list scrolls |
| Suggested for you | `SuggestedReceiver` | 4×2 | 180×110dp | The list scrolls |
| Synced lyrics | `LyricsReceiver` | 4×2 | 180×110dp | From 250dp wide: cover. From 320×130dp: larger cover and text, song name. From 320×200dp: larger again, artist |

The bar and Full player are one `PlayerWidget`, as are Quick actions and Play button. List rows show a 40dp cover, or a music note if there's none. Images count against the limit on what a widget can send and go out with every update, so `PlaybackArtwork` shares the limit between them.

### Things that are easy to get wrong

- **Android picks the closest size, not the largest that fits.** With `SizeMode.Responsive`, every bucket is compared by distance. A bar bucket wider than the full player's would win on a big widget. `WidgetSizingTest` checks this.
- **`minResizeWidth` / `minResizeHeight` must not go below the smallest bucket**, or the content gets clipped. Also tested.
- **Touch targets are 48dp.** Use `WidgetControl` and `WidgetPlayButton`, which already are.
- **Launchers can scale widgets.** MIUI draws them smaller and gives the layout more room than the size it reports, so a widget can have space left over that no size bucket knows of. Decide where it goes: the full player is centered as a whole so it's shared evenly above and below, as a `defaultWeight()` on one part would put it all there.
- **Don't publish from `onUpdate`.** A publish updates the widgets, which calls `onUpdate`, which never ends. Receivers only publish from `onEnabled`.
- **Don't `runBlocking` on the main thread in tests of code that uses `Dispatchers.Main`.** It deadlocks.
- **The whole widget has no content description, on purpose.** TalkBack reads its text, which a description on the root would hide. Every button and image has its own.

### Adding a widget

1. Write a `PlaybackWidget` and a `PlaybackWidgetReceiver` in `widgets/`.
2. Add `xml/widget_<name>_info.xml` with `initialLayout`, sizes, `previewImage` and `previewLayout`, and `updatePeriodMillis="0"`.
3. Add `layout/widget_preview_<name>.xml` and `drawable/widget_preview_<name>.xml`, using the `widget_preview_*` colors so the preview follows light and dark.
4. Add the label and description to `strings.xml`, and everything shown on the widget itself to `AppStrings`.
5. Declare the receiver in the manifest, and add it to `AppWidgetPlaybackHost.PLAYER_AND_LIBRARY_PROVIDERS`.
6. Add it to `WidgetSizingTest`, and to `WidgetRenderTest`, which runs on Android 9, 11 and 14.

## Shortcuts and the tile

- `shortcuts/AppLink.kt`: the links that open a screen in the app (`AppDestination`), handled in `HarmoniqActivity`.
- `shortcuts/AppShortcuts.kt`: launcher shortcuts and pinned playlists. Pinned ones are updated or disabled when a playlist is renamed or deleted.
- `shortcuts/SleepTimerTileService.kt`: the Quick Settings tile. A tap clears a running timer, or starts one for the last length used. Long-pressing it opens the app.

## Testing

```bash
./gradlew :app:testDebugUnitTest --tests "dev.anujyadav.harmoniq.widgets.*"
```

The tests cover what is drawn, how commands travel, sizes and contrast. **They can't show** how a real launcher draws or resizes a widget, what the picker looks like, whether TalkBack reads well, or how much battery is used. Check those on a phone.

`:app:lintDebug` currently crashes inside Compose's own lint rule (AGP 8.7 with Compose 1.9), even on files nothing here has touched.
