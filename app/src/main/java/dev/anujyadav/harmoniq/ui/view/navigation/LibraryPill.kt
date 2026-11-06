package dev.anujyadav.harmoniq.ui.view.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.ui.graphics.vector.ImageVector
import dev.anujyadav.harmoniq.services.groove.Groove
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.ui.components.SongSwipeScope
import kotlinx.coroutines.flow.StateFlow

enum class LibraryPill(
    val icon: ImageVector,
    val label: String,
    // what the pill counts: a flow, as the library keeps changing while the pill is on screen
    val countedItems: ((Groove) -> StateFlow<List<String>>)?,
) {
    ForYou(
        icon = Icons.Filled.Star,
        label = AppStrings.ForYou,
        countedItems = null,
    ),
    Songs(
        icon = Icons.Filled.MusicNote,
        label = AppStrings.Songs,
        countedItems = { it.song.all },
    ),

    // Global Search: only hidden while the user has turned it off in Settings
    WatchList(
        icon = Icons.Filled.WatchLater,
        label = AppStrings.WatchList,
        countedItems = null,
    ),
    Downloads(
        icon = Icons.Filled.Download,
        label = AppStrings.Downloads,
        countedItems = null,
    ),
    Albums(
        icon = Icons.Filled.Album,
        label = AppStrings.Albums,
        countedItems = { it.album.all },
    ),
    Artists(
        icon = Icons.Filled.Group,
        label = AppStrings.Artists,
        countedItems = { it.artist.all },
    ),
    AlbumArtists(
        icon = Icons.Filled.Face,
        label = AppStrings.AlbumArtists,
        countedItems = { it.albumArtist.all },
    ),
    Genres(
        icon = Icons.Filled.Tune,
        label = AppStrings.Genres,
        countedItems = { it.genre.all },
    ),
    Playlists(
        icon = Icons.AutoMirrored.Filled.QueueMusic,
        label = AppStrings.Playlists,
        countedItems = { it.playlist.all },
    ),
    Browser(
        icon = Icons.Filled.Folder,
        label = AppStrings.Browser,
        countedItems = null,
    ),
    Folders(
        icon = Icons.Filled.FolderOpen,
        label = AppStrings.Folders,
        countedItems = null,
    ),
    Tree(
        icon = Icons.Filled.AccountTree,
        label = AppStrings.Tree,
        countedItems = null,
    );

    // swipe configuration used by the song rows this pill shows
    val songSwipeScope
        get() = when (this) {
            Albums, Artists, AlbumArtists, Genres -> SongSwipeScope.Collections
            Browser, Folders, Tree -> SongSwipeScope.Folders
            ForYou, Songs, Playlists, WatchList, Downloads -> SongSwipeScope.Songs
        }

    val isGlobalSearch get() = this == WatchList || this == Downloads

    companion object {
        // order used until the user reorders the tabs
        val defaultOrder = listOf(ForYou, Playlists).let { pinned ->
            pinned + entries.filterNot { it in pinned }
        }

        /**
         * A saved order plus the pills added since it was saved, each placed right after the
         * pill it follows in [defaultOrder] (e.g. Watch List after Songs), or first if none is.
         */
        fun withNewPills(saved: List<LibraryPill>): List<LibraryPill> {
            val result = saved.distinct().toMutableList()
            defaultOrder.forEachIndexed { index, pill ->
                if (pill in result) return@forEachIndexed
                val predecessor = defaultOrder.take(index).lastOrNull { it in result }
                result.add(predecessor?.let { result.indexOf(it) + 1 } ?: 0, pill)
            }
            return result
        }
    }
}
