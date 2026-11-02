package dev.anujyadav.harmoniq.ui.view.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import dev.anujyadav.harmoniq.services.groove.Groove
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.ui.components.SongSwipeScope

enum class LibraryPill(
    val icon: ImageVector,
    val label: String,
    val countProvider: ((Groove) -> Int)?,
) {
    ForYou(
        icon = Icons.Filled.Star,
        label = AppStrings.ForYou,
        countProvider = null,
    ),
    Songs(
        icon = Icons.Filled.MusicNote,
        label = AppStrings.Songs,
        countProvider = { it.song.all.value.size },
    ),
    Albums(
        icon = Icons.Filled.Album,
        label = AppStrings.Albums,
        countProvider = { it.album.all.value.size },
    ),
    Artists(
        icon = Icons.Filled.Group,
        label = AppStrings.Artists,
        countProvider = { it.artist.all.value.size },
    ),
    AlbumArtists(
        icon = Icons.Filled.Face,
        label = AppStrings.AlbumArtists,
        countProvider = { it.albumArtist.all.value.size },
    ),
    Genres(
        icon = Icons.Filled.Tune,
        label = AppStrings.Genres,
        countProvider = { it.genre.all.value.size },
    ),
    Playlists(
        icon = Icons.AutoMirrored.Filled.QueueMusic,
        label = AppStrings.Playlists,
        countProvider = { it.playlist.all.value.size },
    ),
    Browser(
        icon = Icons.Filled.Folder,
        label = AppStrings.Browser,
        countProvider = null,
    ),
    Folders(
        icon = Icons.Filled.FolderOpen,
        label = AppStrings.Folders,
        countProvider = null,
    ),
    Tree(
        icon = Icons.Filled.AccountTree,
        label = AppStrings.Tree,
        countProvider = null,
    );

    // swipe configuration used by the song rows this pill shows
    val songSwipeScope
        get() = when (this) {
            Albums, Artists, AlbumArtists, Genres -> SongSwipeScope.Collections
            Browser, Folders, Tree -> SongSwipeScope.Folders
            ForYou, Songs, Playlists -> SongSwipeScope.Songs
        }

    companion object {
        // order used until the user reorders the tabs, new pills not listed here go last
        val defaultOrder = listOf(ForYou, Playlists).let { pinned ->
            pinned + entries.filterNot { it in pinned }
        }
    }
}
