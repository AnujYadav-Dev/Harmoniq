package dev.anujyadav.harmoniq.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshot
import dev.anujyadav.harmoniq.ui.theme.ThemeMode
import dev.anujyadav.harmoniq.utils.AppStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryWidgetContentTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()

    private fun state(snapshot: PlaybackSnapshot) = PlaybackWidgetState(
        snapshot = snapshot,
        isPlaying = false,
        artwork = null,
        appearance = WidgetAppearance(ThemeMode.SYSTEM, true, null),
    )

    private fun widget(content: @Composable () -> Unit, test: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(DpSize(250.dp, 180.dp))
            setContext(application)
            provideComposable(content)
            test()
        }

    private fun GlanceAppWidgetUnitTest.hasText(text: String) = onNode(hasTextEqualTo(text)).assertExists()

    @Test
    fun playlistsPlayWhenTapped() {
        val playlists = PlaybackSnapshot(
            playlists = listOf(
                PlaybackSnapshot.PlaylistItem("id-1", "Road trip", 12),
                PlaybackSnapshot.PlaylistItem("id-2", "Focus", 3),
            ),
        )
        widget({ PlaylistShortcutsContent(state(playlists)) }) {
            hasText(AppStrings.Playlists)
            hasText("Road trip")
            hasText(AppStrings.XSongs("12"))
            hasText("Focus")
            onNode(sends(PlaybackCommand.PlayPlaylist("id-1"))).assertExists()
            onNode(sends(PlaybackCommand.PlayPlaylist("id-2"))).assertExists()
        }
    }

    @Test
    fun beforeTheLibraryLoadedPlaylistsAskToOpenTheApp() {
        widget({ PlaylistShortcutsContent(state(PlaybackSnapshot())) }) {
            hasText(AppStrings.WidgetOpenAppToLoadPlaylists)
        }
    }

    @Test
    fun noPlaylistsAtAllIsSaidAsSuch() {
        widget({ PlaylistShortcutsContent(state(PlaybackSnapshot(isLibraryLoaded = true))) }) {
            hasText(AppStrings.WidgetNoPlaylists)
        }
    }

    @Test
    fun upNextJumpsToTheSongTapped() {
        val queue = PlaybackSnapshot(
            queue = listOf(
                PlaybackSnapshot.Item("path-a", "Song a", "Artist a", queueIndex = 4),
                PlaybackSnapshot.Item("path-b", "Song b", "Artist b", queueIndex = 5),
                // the same song, twice
                PlaybackSnapshot.Item("path-a", "Song a", "Artist a", queueIndex = 6),
            ),
        )
        widget({ UpNextContent(state(queue)) }) {
            hasText(AppStrings.UpNext)
            hasText("Song b")
            hasText("Artist b")
            onNode(sends(PlaybackCommand.JumpToQueueItem(4, "path-a"))).assertExists()
            onNode(sends(PlaybackCommand.JumpToQueueItem(5, "path-b"))).assertExists()
            onNode(sends(PlaybackCommand.JumpToQueueItem(6, "path-a"))).assertExists()
        }
    }

    @Test
    fun nothingUpNextIsSaidAsSuch() {
        widget({ UpNextContent(state(PlaybackSnapshot())) }) {
            hasText(AppStrings.WidgetNothingUpNext)
        }
    }

    @Test
    fun suggestionsPlayWhenTappedAndCanBeRefreshed() {
        val suggestions = PlaybackSnapshot(
            suggestions = listOf(PlaybackSnapshot.Item("path-a", "Song a", "Artist a")),
        )
        widget({ SuggestedContent(state(suggestions)) }) {
            hasText(AppStrings.SuggestedForYou)
            hasText("Song a")
            onNode(sends(PlaybackCommand.PlaySong("path-a"))).assertExists()
            onNode(hasContentDescriptionEqualTo(AppStrings.Refresh)).assert(sends(PlaybackCommand.RefreshSuggestions))
        }
    }

    @Test
    fun noSuggestionsYetOfferRefresh() {
        widget({ SuggestedContent(state(PlaybackSnapshot())) }) {
            hasText(AppStrings.WidgetTapRefreshForSuggestions)
            onNode(sends(PlaybackCommand.RefreshSuggestions)).assertExists()
        }
    }

    @Test
    fun aLoadedLibraryWithNoSuggestionsIsEmpty() {
        widget({ SuggestedContent(state(PlaybackSnapshot(isLibraryLoaded = true))) }) {
            hasText(AppStrings.CommandEmptyLibrary)
        }
    }
}
