package dev.anujyadav.harmoniq.services.playback

import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.radio.RadioNotificationAction
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs every [PlaybackCommand], the one way into playback from outside the app's screens. It
 * may be the first thing that happens in the process: Symphony has only just been created and
 * the library isn't loaded.
 */
class PlaybackCommandDispatcher(private val symphony: Symphony) {
    /**
     * @param failureMessage why the command did nothing, for the user. Null when all went well.
     */
    enum class Outcome(val failureMessage: String?) {
        Done(null),
        NoMediaFolders(AppStrings.CommandNoMediaFolders),
        LibraryStillLoading(AppStrings.CommandLibraryStillLoading),
        EmptyLibrary(AppStrings.CommandEmptyLibrary),
        NothingToResume(AppStrings.CommandNothingToResume),
        NothingPlaying(AppStrings.NothingIsBeingPlayedRightNow),
        PlaylistMissing(AppStrings.CommandPlaylistMissing),
        PlaylistEmpty(AppStrings.PlaylistHasNoSongs),
        SongMissing(AppStrings.SongUnavailable),
        QueueItemGone(AppStrings.CommandQueueItemGone),
        PlaybackFailed(AppStrings.CommandPlaybackFailed),
    }

    private val radio get() = symphony.radio
    private val groove get() = symphony.groove

    suspend fun run(command: PlaybackCommand): Outcome = withContext(Dispatchers.Main.immediate) {
        symphony.emitReady()
        when (command) {
            PlaybackCommand.Resume -> resume()
            PlaybackCommand.PlayPause -> withPlayer { radio.shorty.playPause() }
            PlaybackCommand.Next -> withPlayer { radio.shorty.skip() }
            PlaybackCommand.Previous -> withPlayer { radio.shorty.previous() }
            PlaybackCommand.ToggleShuffle -> withPlayer { RadioNotificationAction.Shuffle.perform(symphony) }
            PlaybackCommand.CycleRepeat -> withPlayer { RadioNotificationAction.Repeat.perform(symphony) }
            PlaybackCommand.ToggleFavorite -> toggleFavorite()
            PlaybackCommand.ShuffleAll -> shuffleAll()
            is PlaybackCommand.PlayPlaylist -> playPlaylist(command.playlistId)
            is PlaybackCommand.PlaySong -> playSong(command.path)
            is PlaybackCommand.JumpToQueueItem -> jumpToQueueItem(command)
            is PlaybackCommand.SetSleepTimer -> setSleepTimer(command)
            PlaybackCommand.ClearSleepTimer -> {
                radio.clearSleepTimer()
                Outcome.Done
            }
        }
    }

    private suspend fun resume(): Outcome {
        ensureQueue()?.let { return it }
        radio.resumeWhenReady()
        return awaitPlayer()
    }

    // what needs the current song needs its player: not even prepared yet, these do nothing
    private suspend fun withPlayer(action: () -> Unit): Outcome {
        ensureQueue()?.let { return it }
        return awaitPlayer().also {
            if (radio.hasPlayer) {
                action()
            }
        }
    }

    private suspend fun toggleFavorite(): Outcome {
        // favorites aren't known before this, the toggle wouldn't do anything
        awaitLibrary()?.let { return it }
        return withPlayer { RadioNotificationAction.Favorite.perform(symphony) }
    }

    private suspend fun shuffleAll(): Outcome {
        awaitLibrary()?.let { return it }
        val songIds = groove.song.ids()
        if (songIds.isEmpty()) {
            return Outcome.EmptyLibrary
        }
        radio.shorty.playQueue(songIds, shuffle = true)
        return Outcome.Done
    }

    private suspend fun playPlaylist(playlistId: String): Outcome {
        awaitLibrary()?.let { return it }
        val playlist = groove.playlist.get(playlistId) ?: return Outcome.PlaylistMissing
        val songIds = playlist.getSongIds(symphony)
        if (songIds.isEmpty()) {
            return Outcome.PlaylistEmpty
        }
        radio.shorty.playQueue(songIds)
        groove.playlist.markPlayed(playlistId)
        return Outcome.Done
    }

    private suspend fun playSong(path: String): Outcome {
        if (!groove.song.pathCache.containsKey(path)) {
            awaitLibrary()?.let { return it }
        }
        val songId = groove.song.pathCache[path] ?: return Outcome.SongMissing
        radio.shorty.playQueue(songId)
        return Outcome.Done
    }

    private suspend fun jumpToQueueItem(command: PlaybackCommand.JumpToQueueItem): Outcome {
        ensureQueue()?.let { return it }
        val index = findQueueIndex(
            queuePaths = radio.queue.currentQueue.map { groove.song.get(it)?.path },
            index = command.index,
            path = command.path,
        ) ?: return Outcome.QueueItemGone
        radio.jumpTo(index)
        return Outcome.Done
    }

    private fun setSleepTimer(command: PlaybackCommand.SetSleepTimer): Outcome {
        if (!radio.hasPlayer) {
            return Outcome.NothingPlaying
        }
        radio.setSleepTimer(command.durationMs, command.quitOnEnd)
        return Outcome.Done
    }

    /** Puts the saved queue back if there's none yet. Returns what stopped it, if anything. */
    private suspend fun ensureQueue(): Outcome? {
        if (!radio.queue.isEmpty()) {
            return null
        }
        if (symphony.settings.mediaFolders.value.isEmpty()) {
            return Outcome.NoMediaFolders
        }
        radio.restoreQueueFromSongCache()
        // the song cache had nothing usable, like after it was cleared: only a scan can tell
        if (radio.queue.isEmpty()) {
            awaitLibrary()?.let { return it }
            radio.restorePreviousQueue()
        }
        return when {
            !radio.queue.isEmpty() -> null
            groove.song.count.value == 0 -> Outcome.EmptyLibrary
            else -> Outcome.NothingToResume
        }
    }

    private suspend fun awaitLibrary(): Outcome? {
        if (symphony.settings.mediaFolders.value.isEmpty()) {
            return Outcome.NoMediaFolders
        }
        return when (withTimeoutOrNull(LIBRARY_WAIT_TIMEOUT) { groove.readyDeferred.await() }) {
            null -> Outcome.LibraryStillLoading
            else -> null
        }
    }

    // unavailable songs leave the queue one at a time, which may leave nothing to play
    private suspend fun awaitPlayer(): Outcome {
        withTimeoutOrNull(PLAYER_WAIT_TIMEOUT) {
            while (!radio.hasPlayer && !radio.queue.isEmpty()) {
                delay(PLAYER_POLL_INTERVAL)
            }
        }
        return when {
            radio.queue.isEmpty() -> Outcome.PlaybackFailed
            // a slow file may still be preparing, its notification is up in the meantime
            else -> Outcome.Done
        }
    }

    internal companion object {
        // long enough for a first scan of a large library, a foreground service isn't stopped
        // by the system for waiting while it plays nothing, but the user shouldn't wait forever
        private val LIBRARY_WAIT_TIMEOUT = 90.seconds
        private val PLAYER_WAIT_TIMEOUT = 15.seconds
        private val PLAYER_POLL_INTERVAL = 100.milliseconds

        /** Where the song asked for is now, or null if it left the queue. */
        fun findQueueIndex(queuePaths: List<String?>, index: Int, path: String) = when {
            queuePaths.getOrNull(index) == path -> index
            else -> queuePaths.indexOf(path).takeIf { it >= 0 }
        }
    }
}
