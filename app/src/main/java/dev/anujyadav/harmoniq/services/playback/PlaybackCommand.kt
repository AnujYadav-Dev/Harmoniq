package dev.anujyadav.harmoniq.services.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.anujyadav.harmoniq.services.radio.RadioNotificationService

/**
 * Everything a widget, shortcut or tile can ask of playback. They all deliver it the same way:
 * [createPendingIntent], which reaches [RadioNotificationService] even with the app closed, and
 * from there [PlaybackCommandDispatcher].
 *
 * Songs are named by path: anything kept outside the process can outlive a song's id.
 */
sealed interface PlaybackCommand {
    /** Continues what was playing, restoring the saved queue if needed. Never pauses. */
    data object Resume : PlaybackCommand
    data object PlayPause : PlaybackCommand
    data object Next : PlaybackCommand
    data object Previous : PlaybackCommand
    data object ToggleShuffle : PlaybackCommand
    data object CycleRepeat : PlaybackCommand
    data object ToggleFavorite : PlaybackCommand
    data object ShuffleAll : PlaybackCommand
    data object ClearSleepTimer : PlaybackCommand
    data class PlayPlaylist(val playlistId: String) : PlaybackCommand
    data class PlaySong(val path: String) : PlaybackCommand

    /** [index] is where it was when asked. If the queue moved on, [path] still finds it. */
    data class JumpToQueueItem(val index: Int, val path: String) : PlaybackCommand
    data class SetSleepTimer(val durationMs: Long, val quitOnEnd: Boolean) : PlaybackCommand

    fun toUri(): Uri {
        val builder = Uri.Builder().scheme(URI_SCHEME)
        return when (this) {
            Resume -> builder.authority(RESUME)
            PlayPause -> builder.authority(PLAY_PAUSE)
            Next -> builder.authority(NEXT)
            Previous -> builder.authority(PREVIOUS)
            ToggleShuffle -> builder.authority(TOGGLE_SHUFFLE)
            CycleRepeat -> builder.authority(CYCLE_REPEAT)
            ToggleFavorite -> builder.authority(TOGGLE_FAVORITE)
            ShuffleAll -> builder.authority(SHUFFLE_ALL)
            ClearSleepTimer -> builder.authority(CLEAR_SLEEP_TIMER)
            is PlayPlaylist -> builder.authority(PLAY_PLAYLIST).appendQueryParameter(ARG_ID, playlistId)
            is PlaySong -> builder.authority(PLAY_SONG).appendQueryParameter(ARG_PATH, path)
            is JumpToQueueItem -> builder.authority(JUMP_TO_QUEUE_ITEM)
                .appendQueryParameter(ARG_INDEX, index.toString())
                .appendQueryParameter(ARG_PATH, path)

            is SetSleepTimer -> builder.authority(SET_SLEEP_TIMER)
                .appendQueryParameter(ARG_DURATION, durationMs.toString())
                .appendQueryParameter(ARG_QUIT, quitOnEnd.toString())
        }.build()
    }

    fun createIntent(context: Context) = Intent(ACTION_RUN, toUri())
        .setClass(context, RadioNotificationService::class.java)

    /** The same for the same command, across restarts of the app too. */
    val requestCode get() = toUri().toString().hashCode()

    /**
     * Android also treats these as the same PendingIntent if only their data differs, so
     * commands with different arguments can't overwrite each other, whatever their request code.
     */
    fun createPendingIntent(context: Context): PendingIntent = PendingIntent.getForegroundService(
        context,
        requestCode,
        createIntent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_RUN = "dev.anujyadav.harmoniq.playback.RUN"
        private const val URI_SCHEME = "harmoniq-command"

        private const val RESUME = "resume"
        private const val PLAY_PAUSE = "play_pause"
        private const val NEXT = "next"
        private const val PREVIOUS = "previous"
        private const val TOGGLE_SHUFFLE = "toggle_shuffle"
        private const val CYCLE_REPEAT = "cycle_repeat"
        private const val TOGGLE_FAVORITE = "toggle_favorite"
        private const val SHUFFLE_ALL = "shuffle_all"
        private const val CLEAR_SLEEP_TIMER = "clear_sleep_timer"
        private const val PLAY_PLAYLIST = "play_playlist"
        private const val PLAY_SONG = "play_song"
        private const val JUMP_TO_QUEUE_ITEM = "jump_to_queue_item"
        private const val SET_SLEEP_TIMER = "set_sleep_timer"

        private const val ARG_ID = "id"
        private const val ARG_PATH = "path"
        private const val ARG_INDEX = "index"
        private const val ARG_DURATION = "duration"
        private const val ARG_QUIT = "quit"

        /** Null for anything that isn't a complete, known command. */
        fun fromIntent(intent: Intent?): PlaybackCommand? {
            if (intent?.action != ACTION_RUN) {
                return null
            }
            return intent.data?.let { fromUri(it) }
        }

        fun fromUri(uri: Uri): PlaybackCommand? {
            if (uri.scheme != URI_SCHEME) {
                return null
            }
            fun arg(name: String) = uri.getQueryParameter(name)
            return when (uri.authority) {
                RESUME -> Resume
                PLAY_PAUSE -> PlayPause
                NEXT -> Next
                PREVIOUS -> Previous
                TOGGLE_SHUFFLE -> ToggleShuffle
                CYCLE_REPEAT -> CycleRepeat
                TOGGLE_FAVORITE -> ToggleFavorite
                SHUFFLE_ALL -> ShuffleAll
                CLEAR_SLEEP_TIMER -> ClearSleepTimer
                PLAY_PLAYLIST -> arg(ARG_ID)?.let { PlayPlaylist(it) }
                PLAY_SONG -> arg(ARG_PATH)?.let { PlaySong(it) }
                JUMP_TO_QUEUE_ITEM -> {
                    val index = arg(ARG_INDEX)?.toIntOrNull()
                    val path = arg(ARG_PATH)
                    if (index != null && path != null) JumpToQueueItem(index, path) else null
                }

                SET_SLEEP_TIMER -> {
                    val duration = arg(ARG_DURATION)?.toLongOrNull()
                    val quit = arg(ARG_QUIT)?.toBooleanStrictOrNull()
                    if (duration != null && quit != null) SetSleepTimer(duration, quit) else null
                }

                else -> null
            }
        }
    }
}
