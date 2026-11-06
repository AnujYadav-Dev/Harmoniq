package dev.anujyadav.harmoniq.ui.components

import dev.anujyadav.harmoniq.services.globalsearch.OnlineDownloadState
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.ui.view.navigation.LibraryPill
import dev.anujyadav.harmoniq.utils.AppStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnlineTrackActionsTest {
    private val result = SearchResultDto(videoId = "FGBhQbmPwH8", resultType = "song", title = "One More Time")

    private fun actions(
        state: OnlineDownloadState = OnlineDownloadState.None,
        inWatchList: Boolean = false,
        removeAction: SongRemoveAction? = null,
        onPreview: (() -> Unit)? = {},
        onDownload: () -> Unit = {},
    ) = OnlineTrackActions(result, state, inWatchList, removeAction, onPreview, onDownload = onDownload)

    @Test
    fun downloadIsOfferedOnlyWhenNotDownloadedOrRunning() {
        assertTrue(actions(OnlineDownloadState.None).isAvailable(OnlineTrackAction.Download))
        assertTrue(actions(OnlineDownloadState.Failed).isAvailable(OnlineTrackAction.Download))
        assertFalse(actions(OnlineDownloadState.Queued).isAvailable(OnlineTrackAction.Download))
        assertFalse(actions(OnlineDownloadState.Running(0.2f)).isAvailable(OnlineTrackAction.Download))
        assertFalse(actions(OnlineDownloadState.Done).isAvailable(OnlineTrackAction.Download))
    }

    @Test
    fun downloadLabelsAndStatus() {
        assertEquals(AppStrings.Download, actions().label(OnlineTrackAction.Download))
        assertEquals(AppStrings.DownloadAgain, actions(OnlineDownloadState.Failed).label(OnlineTrackAction.Download))
        assertEquals(AppStrings.Downloaded, actions(OnlineDownloadState.Done).downloadStatusLabel())
        assertEquals(AppStrings.DownloadingXPercent("40"), actions(OnlineDownloadState.Running(0.4f)).downloadStatusLabel())
        assertEquals(AppStrings.Downloading, actions(OnlineDownloadState.Running(null)).downloadStatusLabel())
        assertEquals(AppStrings.DownloadWaiting, actions(OnlineDownloadState.Queued).downloadStatusLabel())
        assertNull(actions().downloadStatusLabel())
    }

    @Test
    fun watchListToggleLabel() {
        assertEquals(AppStrings.AddToWatchList, actions(inWatchList = false).label(OnlineTrackAction.AddToWatchList))
        assertEquals(AppStrings.RemoveFromWatchList, actions(inWatchList = true).label(OnlineTrackAction.AddToWatchList))
    }

    @Test
    fun removeAndPreviewDependOnTheScreen() {
        assertFalse(actions().isAvailable(OnlineTrackAction.Remove))
        val remove = SongRemoveAction(AppStrings.RemoveFromWatchList, onRemove = {})
        assertTrue(actions(removeAction = remove).isAvailable(OnlineTrackAction.Remove))
        assertEquals(AppStrings.RemoveFromWatchList, actions(removeAction = remove).label(OnlineTrackAction.Remove))
        assertFalse(actions(onPreview = null).isAvailable(OnlineTrackAction.Preview))
    }

    @Test
    fun performSkipsUnavailableActions() {
        var downloads = 0
        actions(OnlineDownloadState.Done, onDownload = { downloads++ }).perform(OnlineTrackAction.Download)
        assertEquals(0, downloads)
        actions(OnlineDownloadState.None, onDownload = { downloads++ }).perform(OnlineTrackAction.Download)
        assertEquals(1, downloads)
    }

    @Test
    fun menuLeavesOutPreviewAndRemove() {
        assertFalse(OnlineTrackAction.Preview in OnlineTrackAction.menuActions)
        assertFalse(OnlineTrackAction.Remove in OnlineTrackAction.menuActions)
    }

    @Test
    fun swipeDefaultsPerScope() {
        OnlineSwipeScope.entries.forEach {
            assertEquals(OnlineTrackAction.Download, it.defaultAction(SwipeDirection.Right))
        }
        assertEquals(OnlineTrackAction.AddToWatchList, OnlineSwipeScope.Search.defaultAction(SwipeDirection.Left))
        assertEquals(OnlineTrackAction.AddToWatchList, OnlineSwipeScope.Playlist.defaultAction(SwipeDirection.Left))
        assertEquals(OnlineTrackAction.Remove, OnlineSwipeScope.WatchList.defaultAction(SwipeDirection.Left))
        // keys are part of the stored setting names
        assertEquals(OnlineSwipeScope.entries.size, OnlineSwipeScope.entries.map { it.key }.toSet().size)
    }

    @Test
    fun newPillsAreInsertedAfterTheirDefaultPredecessor() {
        val saved = listOf(
            LibraryPill.Songs, LibraryPill.ForYou, LibraryPill.Albums, LibraryPill.Artists,
            LibraryPill.AlbumArtists, LibraryPill.Genres, LibraryPill.Playlists, LibraryPill.Browser,
            LibraryPill.Folders, LibraryPill.Tree,
        )
        val order = LibraryPill.withNewPills(saved)
        // the user's order is kept, with the Watch List and Downloads right after Songs
        assertEquals(
            listOf(LibraryPill.Songs, LibraryPill.WatchList, LibraryPill.Downloads, LibraryPill.ForYou),
            order.take(4),
        )
        assertEquals(LibraryPill.entries.size, order.size)
        assertEquals(LibraryPill.defaultOrder, LibraryPill.withNewPills(LibraryPill.defaultOrder))
        // pills missing their predecessor chain land in default order, duplicates collapse
        assertEquals(LibraryPill.defaultOrder, LibraryPill.withNewPills(listOf(LibraryPill.Tree, LibraryPill.Tree)))
    }

    @Test
    fun watchListComesAfterSongsByDefault() {
        val order = LibraryPill.defaultOrder
        assertEquals(order.indexOf(LibraryPill.Songs) + 1, order.indexOf(LibraryPill.WatchList))
    }
}
