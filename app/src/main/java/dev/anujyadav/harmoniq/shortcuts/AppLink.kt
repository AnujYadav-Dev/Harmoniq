package dev.anujyadav.harmoniq.shortcuts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.quicksettings.TileService
import dev.anujyadav.harmoniq.HarmoniqActivity
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand

/** Where something outside the app wants to land inside it. */
sealed interface AppDestination {
    data object Search : AppDestination
    data object Favorites : AppDestination
    data object Settings : AppDestination
    data class Playlist(val id: String) : AppDestination

    val key: String
        get() = when (this) {
            Search -> KEY_SEARCH
            Favorites -> KEY_FAVORITES
            Settings -> KEY_SETTINGS
            is Playlist -> "$PLAYLIST_PREFIX$id"
        }

    companion object {
        private const val KEY_SEARCH = "search"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_SETTINGS = "settings"
        private const val PLAYLIST_PREFIX = "playlist:"

        fun fromKey(key: String?) = when {
            key == null -> null
            key == KEY_SEARCH -> Search
            key == KEY_FAVORITES -> Favorites
            key == KEY_SETTINGS -> Settings
            key.startsWith(PLAYLIST_PREFIX) -> Playlist(key.removePrefix(PLAYLIST_PREFIX))
            else -> null
        }
    }
}

/**
 * What a launcher shortcut asks of the app: somewhere to open, something to play, or both.
 * Only strings go into the intent, as Android has to store it along with a pinned shortcut.
 */
data class AppLink(
    val destination: AppDestination? = null,
    val command: PlaybackCommand? = null,
) {
    fun createIntent(context: Context): Intent = Intent(ACTION_OPEN)
        .setClass(context, HarmoniqActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .apply {
            destination?.let { putExtra(EXTRA_DESTINATION, it.key) }
            command?.let { putExtra(EXTRA_COMMAND, it.toUri().toString()) }
        }

    companion object {
        const val ACTION_OPEN = "dev.anujyadav.harmoniq.shortcut.OPEN"
        private const val EXTRA_DESTINATION = "destination"
        private const val EXTRA_COMMAND = "command"

        /**
         * Null for anything else, the plain launcher intent for one. Also for an intent that
         * only came back through Recents: the shortcut was tapped long ago, playing something
         * again now would be a surprise.
         */
        fun fromIntent(intent: Intent?): AppLink? {
            if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
                return null
            }
            return when (intent.action) {
                TileService.ACTION_QS_TILE_PREFERENCES -> AppLink(AppDestination.Settings)
                ACTION_OPEN -> AppLink(
                    destination = AppDestination.fromKey(intent.getStringExtra(EXTRA_DESTINATION)),
                    command = intent.getStringExtra(EXTRA_COMMAND)
                        ?.let { PlaybackCommand.fromUri(Uri.parse(it)) },
                )

                else -> null
            }
        }
    }
}
