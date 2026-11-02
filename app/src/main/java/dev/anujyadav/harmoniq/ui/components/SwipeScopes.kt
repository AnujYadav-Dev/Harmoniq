package dev.anujyadav.harmoniq.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dev.anujyadav.harmoniq.utils.AppStrings

enum class SwipeDirection(val key: String) {
    Right("right"),
    Left("left"),
}

/**
 * The places song rows are shown in. Each one has its own swipe configuration,
 * defaulting to what suits that place best.
 */
enum class SongSwipeScope(
    val key: String,
    val label: String,
    private val defaultRight: SongAction?,
    private val defaultLeft: SongAction?,
) {
    Songs("songs", AppStrings.Songs, SongAction.PlayNext, SongAction.AddToQueue),
    Collections("collections", AppStrings.AlbumsArtistsGenres, SongAction.PlayNext, SongAction.AddToQueue),
    Folders("folders", AppStrings.FoldersAndTree, SongAction.PlayNext, SongAction.AddToQueue),
    Search("search", AppStrings.Search, SongAction.PlayNext, SongAction.AddToQueue),
    Playlist("playlist", AppStrings.Playlist, SongAction.PlayNext, SongAction.Remove),

    // "favorite" toggles, so in favorites it removes the song from them
    Favorites("favorites", AppStrings.Favorites, SongAction.PlayNext, SongAction.Favorite),
    Queue("queue", AppStrings.Queue, SongAction.Play, SongAction.Remove);

    fun defaultAction(direction: SwipeDirection) = when (direction) {
        SwipeDirection.Right -> defaultRight
        SwipeDirection.Left -> defaultLeft
    }
}

/**
 * The places playlist rows are shown in, see [SongSwipeScope].
 */
enum class PlaylistSwipeScope(
    val key: String,
    val label: String,
    private val defaultRight: PlaylistAction?,
    private val defaultLeft: PlaylistAction?,
) {
    Library("library", AppStrings.Library, PlaylistAction.Play, PlaylistAction.AddToQueue),
    Search("search", AppStrings.Search, PlaylistAction.Play, PlaylistAction.AddToQueue);

    fun defaultAction(direction: SwipeDirection) = when (direction) {
        SwipeDirection.Right -> defaultRight
        SwipeDirection.Left -> defaultLeft
    }
}

// provided once per screen, so nested lists pick up the right configuration
val LocalSongSwipeScope = staticCompositionLocalOf { SongSwipeScope.Songs }
val LocalPlaylistSwipeScope = staticCompositionLocalOf { PlaylistSwipeScope.Library }

/**
 * Sets the swipe configuration used by every song and playlist row inside [content].
 */
@Composable
fun ProvideSwipeScope(
    songScope: SongSwipeScope = LocalSongSwipeScope.current,
    playlistScope: PlaylistSwipeScope = LocalPlaylistSwipeScope.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSongSwipeScope provides songScope,
        LocalPlaylistSwipeScope provides playlistScope,
        content = content,
    )
}
