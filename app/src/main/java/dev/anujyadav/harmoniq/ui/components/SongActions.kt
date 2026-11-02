package dev.anujyadav.harmoniq.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.anujyadav.harmoniq.services.groove.Song
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.AlbumArtistViewRoute
import dev.anujyadav.harmoniq.ui.view.AlbumViewRoute
import dev.anujyadav.harmoniq.ui.view.ArtistViewRoute
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.Logger

/**
 * Every action that can be run on a song. The three-dot menu and the swipe
 * actions both run through [SongActions], so the behavior stays identical.
 */
enum class SongAction {
    Favorite,
    Play,
    PlayNext,
    AddToQueue,
    AddToPlaylist,
    ViewArtist,
    ViewAlbumArtist,
    ViewAlbum,
    Share,
    Details,

    // removes the song from the list being viewed (queue, playlist), when supported there
    Remove;

    companion object {
        // order of the items in the three-dot menu
        val menuActions = listOf(
            Favorite,
            PlayNext,
            AddToQueue,
            AddToPlaylist,
            ViewArtist,
            ViewAlbumArtist,
            ViewAlbum,
            Share,
            Details,
        )
    }
}

fun SongAction.settingsLabel() = when (this) {
    SongAction.Favorite -> "${AppStrings.Favorite} / ${AppStrings.Unfavorite}"
    SongAction.Play -> AppStrings.Play
    SongAction.PlayNext -> AppStrings.PlayNext
    SongAction.AddToQueue -> AppStrings.AddToQueue
    SongAction.AddToPlaylist -> AppStrings.AddToPlaylist
    SongAction.ViewArtist -> AppStrings.ViewArtist
    SongAction.ViewAlbumArtist -> AppStrings.ViewAlbumArtist
    SongAction.ViewAlbum -> AppStrings.ViewAlbum
    SongAction.Share -> AppStrings.ShareSong
    SongAction.Details -> AppStrings.Details
    SongAction.Remove -> AppStrings.RemoveFromList
}

/**
 * A screen specific removal, e.g. "Remove from queue" or "Remove from playlist".
 */
data class SongRemoveAction(
    val label: String,
    val onRemove: () -> Unit,
    // restores the song exactly where it was, offered when the screen supports undo
    val onUndo: (() -> Unit)? = null,
)

class SongActions(
    private val context: ViewContext,
    val song: Song,
    val isFavorite: Boolean,
    private val onPlay: (() -> Unit)?,
    private val removeAction: SongRemoveAction?,
    private val onShowAddToPlaylist: () -> Unit,
    private val onShowDetails: () -> Unit,
    private val undoHost: UndoHost? = null,
) {
    private val albumId get() = context.symphony.groove.album.getIdFromSong(song)

    fun isAvailable(action: SongAction) = when (action) {
        SongAction.ViewArtist -> song.artists.isNotEmpty()
        SongAction.ViewAlbumArtist -> song.albumArtists.isNotEmpty()
        SongAction.ViewAlbum -> albumId != null
        SongAction.Remove -> removeAction != null
        else -> true
    }

    fun label(action: SongAction) = when (action) {
        SongAction.Favorite -> if (isFavorite) AppStrings.Unfavorite else AppStrings.Favorite
        SongAction.Remove -> removeAction?.label ?: action.settingsLabel()
        else -> action.settingsLabel()
    }

    fun icon(action: SongAction): ImageVector = when (action) {
        SongAction.Favorite -> Icons.Filled.Favorite
        SongAction.Play -> Icons.Filled.PlayArrow
        SongAction.PlayNext, SongAction.AddToQueue -> Icons.AutoMirrored.Filled.PlaylistPlay
        SongAction.AddToPlaylist -> Icons.AutoMirrored.Filled.PlaylistAdd
        SongAction.ViewArtist, SongAction.ViewAlbumArtist -> Icons.Filled.Person
        SongAction.ViewAlbum -> Icons.Filled.Album
        SongAction.Share -> Icons.Filled.Share
        SongAction.Details -> Icons.Filled.Info
        SongAction.Remove -> Icons.Filled.RemoveCircleOutline
    }

    fun perform(action: SongAction) {
        if (!isAvailable(action)) {
            return
        }
        when (action) {
            SongAction.Favorite -> toggleFavorite()
            SongAction.Play -> play()
            SongAction.PlayNext -> playNext()
            SongAction.AddToQueue -> addToQueue()
            SongAction.AddToPlaylist -> onShowAddToPlaylist()
            SongAction.ViewArtist -> viewArtist(song.artists.first())
            SongAction.ViewAlbumArtist -> viewAlbumArtist(song.albumArtists.first())
            SongAction.ViewAlbum -> viewAlbum()
            SongAction.Share -> share()
            SongAction.Details -> onShowDetails()
            SongAction.Remove -> remove()
        }
    }

    fun toggleFavorite() = context.symphony.groove.playlist.run {
        when {
            isFavorite -> {
                val favorites = getFavorites()
                val previousSongIds = favorites.getSongIds(context.symphony)
                unfavorite(song.id)
                undoHost?.show(AppStrings.RemovedXFromFavorites(song.title)) {
                    update(favorites.id, previousSongIds)
                }
            }

            else -> favorite(song.id)
        }
    }

    fun remove() {
        val action = removeAction ?: return
        action.onRemove()
        action.onUndo?.let { onUndo ->
            undoHost?.show(AppStrings.RemovedX(song.title), onUndo)
        }
    }

    // screens decide what "play" means (e.g. jump to the song in the queue)
    fun play() = onPlay?.invoke() ?: context.symphony.radio.shorty.playQueue(song.id)

    fun playNext() = context.symphony.radio.queue.add(
        song.id,
        context.symphony.radio.queue.currentSongIndex + 1
    )

    fun addToQueue() = context.symphony.radio.queue.add(song.id)

    fun viewArtist(artistName: String) =
        context.navController.navigate(ArtistViewRoute(artistName))

    fun viewAlbumArtist(albumArtist: String) =
        context.navController.navigate(AlbumArtistViewRoute(albumArtist))

    fun viewAlbum() {
        albumId?.let { context.navController.navigate(AlbumViewRoute(it)) }
    }

    fun share() {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_STREAM, song.uri)
                type = context.activity.contentResolver.getType(song.uri)
            }
            context.activity.startActivity(intent)
        } catch (err: Exception) {
            Logger.error("SongActions", "share failed", err)
            Toast.makeText(
                context.activity,
                AppStrings.ShareFailedX(err.localizedMessage ?: err.toString()),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

/**
 * Provides [SongActions] to [content] and hosts the dialogs some actions open.
 */
@Composable
fun SongActionsHost(
    context: ViewContext,
    song: Song,
    onPlay: (() -> Unit)? = null,
    removeAction: SongRemoveAction? = null,
    content: @Composable (SongActions) -> Unit,
) {
    val favoriteSongIds by context.symphony.groove.playlist.favorites.collectAsState()
    val undoHost = LocalUndoHost.current
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    val actions = SongActions(
        context = context,
        song = song,
        isFavorite = favoriteSongIds.contains(song.id),
        onPlay = onPlay,
        removeAction = removeAction,
        onShowAddToPlaylist = { showAddToPlaylistDialog = true },
        onShowDetails = { showInfoDialog = true },
        undoHost = undoHost,
    )

    content(actions)

    if (showInfoDialog) {
        SongInformationDialog(
            context,
            song = song,
            onDismissRequest = {
                showInfoDialog = false
            }
        )
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            context,
            songIds = listOf(song.id),
            onDismissRequest = {
                showAddToPlaylistDialog = false
            }
        )
    }
}

fun SongActions.toSwipeAction(action: SongAction?) = action
    ?.takeIf { isAvailable(it) }
    ?.let { SwipeAction(label(it), icon(it)) { perform(it) } }

/**
 * Wraps a song row with the swipe actions configured for the current [LocalSongSwipeScope].
 */
@Composable
fun SongSwipeActionsBox(
    context: ViewContext,
    actions: SongActions,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = LocalSongSwipeScope.current
    val settings = context.symphony.settings
    val swipeRightAction by settings.songSwipeAction(scope, SwipeDirection.Right).flow.collectAsState()
    val swipeLeftAction by settings.songSwipeAction(scope, SwipeDirection.Left).flow.collectAsState()

    SwipeActionsBox(
        swipeRight = actions.toSwipeAction(swipeRightAction),
        swipeLeft = actions.toSwipeAction(swipeLeftAction),
        enabled = enabled,
        content = content,
    )
}
