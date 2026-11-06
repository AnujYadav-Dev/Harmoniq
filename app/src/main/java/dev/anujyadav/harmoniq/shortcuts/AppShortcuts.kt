package dev.anujyadav.harmoniq.shortcuts

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.services.groove.Playlist
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.Logger

/**
 * The app's launcher shortcuts. These are published from code, and go straight to
 * HarmoniqActivity: declared in the manifest, they would have to be repeated on every app icon's
 * activity-alias.
 */
object AppShortcuts {
    private const val TAG = "AppShortcuts"
    private const val ID_CONTINUE = "continue_playing"
    private const val ID_SHUFFLE_ALL = "shuffle_all"
    private const val ID_FAVORITES = "favorites"
    private const val ID_SEARCH = "search"
    private const val PLAYLIST_ID_PREFIX = "playlist_"

    enum class PinResult {
        Requested,
        NotSupported,
        Failed,
    }

    /**
     * Android removes these when the app icon's alias they hang on is disabled, so this runs
     * again after each switch. Safe to repeat: the same ids just replace themselves.
     */
    fun publish(context: Context) {
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, launcherShortcuts(context))
        } catch (err: Exception) {
            // something like the rate limit, the ones from last time are still there
            Logger.warn(TAG, "unable to publish shortcuts", err)
        }
    }

    internal fun launcherShortcuts(context: Context) = listOf(
        shortcut(
            context,
            ID_CONTINUE,
            AppStrings.ContinuePlaying,
            R.drawable.shortcut_continue,
            AppLink(command = PlaybackCommand.Resume),
        ),
        shortcut(
            context,
            ID_SHUFFLE_ALL,
            AppStrings.ShuffleAll,
            R.drawable.shortcut_shuffle,
            AppLink(command = PlaybackCommand.ShuffleAll),
        ),
        shortcut(
            context,
            ID_FAVORITES,
            AppStrings.Favorites,
            R.drawable.shortcut_favorites,
            AppLink(AppDestination.Favorites),
        ),
        shortcut(
            context,
            ID_SEARCH,
            AppStrings.Search,
            R.drawable.shortcut_search,
            AppLink(AppDestination.Search),
        ),
    )

    /** Opens the playlist and plays it. Android asks the user where to put it. */
    fun pinPlaylist(context: Context, playlist: Playlist): PinResult {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return PinResult.NotSupported
        }
        return try {
            when {
                ShortcutManagerCompat.requestPinShortcut(context, playlistShortcut(context, playlist), null) ->
                    PinResult.Requested

                else -> PinResult.Failed
            }
        } catch (err: Exception) {
            Logger.warn(TAG, "unable to pin ${playlist.id}", err)
            PinResult.Failed
        }
    }

    /** Whatever's left pinned on the home screen shows as disabled and explains why. */
    fun onPlaylistDeleted(context: Context, playlistId: String) {
        try {
            ShortcutManagerCompat.disableShortcuts(
                context,
                listOf(playlistShortcutId(playlistId)),
                AppStrings.CommandPlaylistMissing,
            )
        } catch (err: Exception) {
            Logger.warn(TAG, "unable to disable $playlistId", err)
        }
    }

    fun onPlaylistRenamed(context: Context, playlist: Playlist) {
        try {
            val id = playlistShortcutId(playlist.id)
            val isPinned = ShortcutManagerCompat
                .getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .any { it.id == id }
            if (isPinned) {
                ShortcutManagerCompat.updateShortcuts(context, listOf(playlistShortcut(context, playlist)))
            }
        } catch (err: Exception) {
            Logger.warn(TAG, "unable to rename ${playlist.id}", err)
        }
    }

    internal fun playlistShortcutId(playlistId: String) = "$PLAYLIST_ID_PREFIX$playlistId"

    private fun playlistShortcut(context: Context, playlist: Playlist) = shortcut(
        context,
        playlistShortcutId(playlist.id),
        playlist.title,
        R.drawable.shortcut_playlist,
        AppLink(AppDestination.Playlist(playlist.id), PlaybackCommand.PlayPlaylist(playlist.id)),
    )

    private fun shortcut(
        context: Context,
        id: String,
        label: String,
        @DrawableRes icon: Int,
        link: AppLink,
    ) = ShortcutInfoCompat.Builder(context, id)
        .setShortLabel(label)
        .setIcon(IconCompat.createWithResource(context, icon))
        .setIntent(link.createIntent(context))
        .build()
}
