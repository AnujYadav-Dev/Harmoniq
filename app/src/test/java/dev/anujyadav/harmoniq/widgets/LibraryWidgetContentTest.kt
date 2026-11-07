package dev.anujyadav.harmoniq.widgets

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.playback.PlaybackArtwork
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshot
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshotStore
import dev.anujyadav.harmoniq.services.playback.WidgetCover
import dev.anujyadav.harmoniq.ui.theme.ThemeMode
import dev.anujyadav.harmoniq.utils.AppStrings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
    fun rowsWorkWithAndWithoutTheirCovers() {
        val cover = WidgetCover("/covers/a.jpg")
        val snapshot = PlaybackSnapshot(
            queue = listOf(
                PlaybackSnapshot.Item("path-a", "Song a", "Artist a", artworkPath = cover.path, queueIndex = 1),
                PlaybackSnapshot.Item("path-b", "Song b", "Artist b", queueIndex = 2),
                // has a path, but it couldn't be loaded
                PlaybackSnapshot.Item("path-c", "Song c", "Artist c", artworkPath = "/gone.jpg", queueIndex = 3),
            ),
        )
        val covers = mapOf(cover to Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        widget({ UpNextContent(state(snapshot).copy(covers = covers)) }) {
            hasText("Song a")
            hasText("Song b")
            hasText("Song c")
        }
    }

    @Test
    fun aPlaylistsCoverChangesWhenItsFileDoes() {
        val before = PlaybackSnapshot.PlaylistItem("id", "Mix", 1, artworkPath = "/c.jpg", artworkVersion = 1L)
        assertNotEquals(before.cover, before.copy(artworkVersion = 2L).cover)
        assertNull(before.copy(artworkPath = null).cover)
    }

    @Test
    fun aSnapshotFromBeforeCoversIsStillRead() {
        val store = PlaybackSnapshotStore(application)
        application.getSharedPreferences("playback_snapshot", Context.MODE_PRIVATE).edit()
            .putString("snapshot", """{"playlists":[{"id":"a","title":"Old","songCount":2}]}""")
            .commit()
        assertEquals("Old", store.read().playlists.single().title)
        assertNull(store.read().playlists.single().cover)
    }

    @Test
    fun aQueueThatMovedOnOnlyLoadsWhatIsNew() {
        val first = coverFile("first.png")
        val second = coverFile("second.png")
        val loader = CoverLoader(application, sizePx = 100, layouts = 1)
        val loaded = loader.load(listOf(WidgetCover(first), WidgetCover(second)))
        assertEquals(2, loaded.size)
        val moved = loader.load(listOf(WidgetCover(second)))
        assertSame(loaded[WidgetCover(second)], moved[WidgetCover(second)])
        assertEquals(1, moved.size)
    }

    @Test
    fun aReplacedPlaylistCoverIsLoadedAgain() {
        val path = coverFile("cover.png")
        val loader = CoverLoader(application, sizePx = 100, layouts = 1)
        val before = loader.load(listOf(WidgetCover(path, version = 1L))).values.single()
        val after = loader.load(listOf(WidgetCover(path, version = 2L))).values.single()
        assertNotSame(before, after)
    }

    @Test
    fun aCoverThatCanNotBeLoadedIsLeftOut() {
        val loader = CoverLoader(application, sizePx = 100, layouts = 1)
        assertTrue(loader.load(listOf(WidgetCover("/no/such/file.jpg"))).isEmpty())
    }

    @Test
    fun aFullListOfCoversStaysWithinWhatAndroidAllows() {
        val metrics = application.resources.displayMetrics
        val images = PlaybackSnapshot.PLAYLISTS_PREVIEW_LIMIT * LibraryWidgetDimens.PlaylistSizes.size
        val side = PlaybackArtwork.maxSide(
            (LibraryWidgetDimens.CoverSize.value * metrics.density).toInt(),
            metrics.widthPixels,
            metrics.heightPixels,
            images,
        )
        val limit = 1.5 * metrics.widthPixels * metrics.heightPixels * BYTES_PER_PIXEL
        assertTrue(side.toLong() * side * BYTES_PER_PIXEL * images <= limit)
    }

    private fun coverFile(name: String): String {
        val file = File(application.cacheDir, name)
        file.outputStream().use {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return file.path
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
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
