package dev.anujyadav.harmoniq.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.anujyadav.harmoniq.services.groove.MediaExposer
import dev.anujyadav.harmoniq.services.groove.Playlist
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.Logger
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Image
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every action that can be run on a playlist. The three-dot menu and the swipe
 * actions both run through [PlaylistActions], so the behavior stays identical.
 */
enum class PlaylistAction {
    Play,
    ShufflePlay,
    PlayNext,
    AddToQueue,
    AddToPlaylist,
    ManageSongs,
    Details,
    ChangeCover,
    RemoveCover,
    Export,
    Rename,
    Delete;

    companion object {
        // offered when tapping a playlist's cover
        val coverActions = listOf(ChangeCover, RemoveCover)

        // order of the items in the three-dot menu
        val menuActions = listOf(
            ShufflePlay,
            PlayNext,
            AddToQueue,
            AddToPlaylist,
            ManageSongs,
            Details,
            ChangeCover,
            RemoveCover,
            Export,
            Rename,
            Delete,
        )
    }
}

fun PlaylistAction.settingsLabel() = when (this) {
    PlaylistAction.Play -> AppStrings.Play
    PlaylistAction.ShufflePlay -> AppStrings.ShufflePlay
    PlaylistAction.PlayNext -> AppStrings.PlayNext
    PlaylistAction.AddToQueue -> AppStrings.AddToQueue
    PlaylistAction.AddToPlaylist -> AppStrings.AddToPlaylist
    PlaylistAction.ManageSongs -> AppStrings.ManageSongs
    PlaylistAction.Details -> AppStrings.Details
    PlaylistAction.ChangeCover -> AppStrings.ChangeCover
    PlaylistAction.RemoveCover -> AppStrings.RemoveCover
    PlaylistAction.Export -> AppStrings.Export
    PlaylistAction.Rename -> AppStrings.Rename
    PlaylistAction.Delete -> AppStrings.Delete
}

class PlaylistActions(
    val context: ViewContext,
    val playlist: Playlist,
    private val onShowDialog: (PlaylistAction) -> Unit,
    private val onExport: () -> Unit,
    private val onChangeCover: () -> Unit,
) {
    private val isBuiltIn get() = context.symphony.groove.playlist.isBuiltInPlaylist(playlist)

    fun isAvailable(action: PlaylistAction) = when (action) {
        // local playlists are backed by an m3u file and are read-only
        PlaylistAction.ManageSongs,
        PlaylistAction.Export,
        PlaylistAction.Rename -> playlist.isNotLocal

        PlaylistAction.Delete -> !isBuiltIn
        // a custom cover is stored by the app, so every playlist (favorites too) can have one
        PlaylistAction.RemoveCover -> context.symphony.groove.playlist.hasCustomCover(playlist.id)
        else -> true
    }

    fun label(action: PlaylistAction) = action.settingsLabel()

    fun icon(action: PlaylistAction): ImageVector = when (action) {
        PlaylistAction.Play -> Icons.Filled.PlayArrow
        PlaylistAction.ShufflePlay,
        PlaylistAction.PlayNext,
        PlaylistAction.AddToQueue -> Icons.AutoMirrored.Filled.PlaylistPlay
        PlaylistAction.AddToPlaylist, PlaylistAction.ManageSongs -> Icons.AutoMirrored.Filled.PlaylistAdd
        PlaylistAction.Details -> Icons.Filled.Info
        PlaylistAction.ChangeCover -> Icons.Filled.Image
        PlaylistAction.RemoveCover -> Icons.Filled.HideImage
        PlaylistAction.Export -> Icons.Filled.Save
        PlaylistAction.Rename -> Icons.Filled.Edit
        PlaylistAction.Delete -> Icons.Filled.DeleteForever
    }

    fun perform(action: PlaylistAction) {
        if (!isAvailable(action)) {
            return
        }
        when (action) {
            PlaylistAction.Play -> play()
            PlaylistAction.ShufflePlay -> play(shuffle = true)
            PlaylistAction.PlayNext -> playNext()
            PlaylistAction.AddToQueue -> addToQueue()
            PlaylistAction.Export -> onExport()
            PlaylistAction.ChangeCover -> onChangeCover()
            PlaylistAction.RemoveCover -> context.symphony.groove.playlist.removeCover(playlist.id)
            PlaylistAction.AddToPlaylist,
            PlaylistAction.ManageSongs,
            PlaylistAction.Details,
            PlaylistAction.Rename,
            PlaylistAction.Delete -> onShowDialog(action)
        }
    }

    fun play(shuffle: Boolean = false) {
        context.symphony.groove.playlist.markPlayed(playlist.id)
        context.symphony.radio.shorty.playQueue(
            playlist.getSortedSongIds(context.symphony),
            shuffle = shuffle,
        )
    }

    fun playNext() = context.symphony.radio.queue.add(
        playlist.getSortedSongIds(context.symphony),
        context.symphony.radio.queue.currentSongIndex + 1
    )

    fun addToQueue() = context.symphony.radio.queue.add(playlist.getSongIds(context.symphony))
}

/**
 * Provides [PlaylistActions] to [content] and hosts the dialogs and the export
 * launcher some actions use.
 */
@Composable
fun PlaylistActionsHost(
    context: ViewContext,
    playlist: Playlist,
    onSongsChanged: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    content: @Composable (PlaylistActions) -> Unit,
) {
    val savePlaylistLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MediaExposer.MIMETYPE_M3U)
    ) { uri ->
        uri?.let { _ ->
            try {
                context.symphony.groove.playlist.savePlaylistToUri(playlist, uri)
                Toast.makeText(
                    context.activity,
                    AppStrings.ExportedX(playlist.title),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (err: Exception) {
                Logger.error("PlaylistActions", "export failed (activity result)", err)
                showExportFailedToast(context, err)
            }
        }
    }
    var dialog by remember { mutableStateOf<PlaylistAction?>(null) }
    // "image/*" lets the user pick from the gallery or any file/cloud provider
    val pickCoverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // not tied to the screen, so leaving it does not cancel saving the cover
        context.symphony.groove.coroutineScope.launch {
            val updated = context.symphony.groove.playlist.setCover(playlist.id, uri)
            if (!updated) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context.activity,
                        AppStrings.ChangingCoverFailed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    val actions = PlaylistActions(
        context = context,
        playlist = playlist,
        onShowDialog = { dialog = it },
        onExport = {
            try {
                savePlaylistLauncher.launch("${playlist.title}.m3u")
            } catch (err: Exception) {
                Logger.error("PlaylistActions", "export failed", err)
                showExportFailedToast(context, err)
            }
        },
        onChangeCover = {
            try {
                pickCoverLauncher.launch(COVER_MIME_TYPE)
            } catch (err: Exception) {
                Logger.error("PlaylistActions", "opening image picker failed", err)
                Toast.makeText(
                    context.activity,
                    AppStrings.ChangingCoverFailed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
    )

    content(actions)

    val onDismissRequest = { dialog = null }
    when (dialog) {
        PlaylistAction.Details -> PlaylistInformationDialog(
            context,
            playlist = playlist,
            onDismissRequest = onDismissRequest,
        )

        PlaylistAction.ManageSongs -> PlaylistManageSongsDialog(
            context,
            selectedSongIds = playlist.getSongIds(context.symphony),
            onDone = {
                context.symphony.groove.playlist.update(playlist.id, it)
                onSongsChanged()
                dialog = null
            }
        )

        PlaylistAction.Delete -> ConfirmationDialog(
            context,
            title = {
                Text(AppStrings.DeletePlaylist)
            },
            description = {
                Text(AppStrings.AreYouSureThatYouWantToDeleteThisPlaylist)
            },
            onResult = { result ->
                dialog = null
                if (result) {
                    onDelete()
                    context.symphony.groove.playlist.delete(playlist.id)
                }
            }
        )

        PlaylistAction.AddToPlaylist -> AddToPlaylistDialog(
            context,
            songIds = playlist.getSongIds(context.symphony),
            onDismissRequest = onDismissRequest,
        )

        PlaylistAction.Rename -> RenamePlaylistDialog(
            context,
            playlist = playlist,
            onRename = onRename,
            onDismissRequest = onDismissRequest,
        )

        else -> {}
    }
}

private const val COVER_MIME_TYPE = "image/*"

private fun showExportFailedToast(context: ViewContext, err: Exception) {
    Toast.makeText(
        context.activity,
        AppStrings.ExportFailedX(err.localizedMessage ?: err.toString()),
        Toast.LENGTH_SHORT,
    ).show()
}

fun PlaylistActions.toSwipeAction(action: PlaylistAction?) = action
    ?.takeIf { isAvailable(it) }
    ?.let { SwipeAction(label(it), icon(it)) { perform(it) } }

/**
 * Wraps a playlist row with the swipe actions configured for the current [LocalPlaylistSwipeScope].
 */
@Composable
fun PlaylistSwipeActionsBox(
    context: ViewContext,
    actions: PlaylistActions,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = LocalPlaylistSwipeScope.current
    val settings = context.symphony.settings
    val swipeRightAction by settings.playlistSwipeAction(scope, SwipeDirection.Right).flow.collectAsState()
    val swipeLeftAction by settings.playlistSwipeAction(scope, SwipeDirection.Left).flow.collectAsState()

    SwipeActionsBox(
        swipeRight = actions.toSwipeAction(swipeRightAction),
        swipeLeft = actions.toSwipeAction(swipeLeftAction),
        enabled = enabled,
        content = content,
    )
}
