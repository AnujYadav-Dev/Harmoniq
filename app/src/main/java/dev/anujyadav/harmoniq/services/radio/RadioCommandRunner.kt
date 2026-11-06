package dev.anujyadav.harmoniq.services.radio

import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gets Radio ready to run a [RadioCommand], which may be the first thing that happens in the
 * process: Symphony has only just been created and the library isn't loaded.
 */
class RadioCommandRunner(private val symphony: Symphony) {
    /**
     * @param failureMessage why nothing is playing, for the user. Null when all went well.
     */
    enum class Outcome(val failureMessage: String?) {
        Started(null),
        NoMediaFolders(AppStrings.CommandNoMediaFolders),
        LibraryStillLoading(AppStrings.CommandLibraryStillLoading),
        EmptyLibrary(AppStrings.CommandEmptyLibrary),
        NothingToResume(AppStrings.CommandNothingToResume),
        PlaybackFailed(AppStrings.CommandPlaybackFailed),
    }

    suspend fun run(command: RadioCommand) = withContext(Dispatchers.Main.immediate) {
        symphony.emitReady()
        when (command) {
            RadioCommand.Resume -> resume()
        }
    }

    private suspend fun resume(): Outcome {
        val radio = symphony.radio
        if (radio.queue.isEmpty()) {
            if (symphony.settings.mediaFolders.value.isEmpty()) {
                return Outcome.NoMediaFolders
            }
            radio.restoreQueueFromSongCache()
        }
        // the song cache had nothing usable, like after it was cleared: only a scan can tell
        if (radio.queue.isEmpty()) {
            withTimeoutOrNull(LIBRARY_WAIT_TIMEOUT) { symphony.groove.readyDeferred.await() }
                ?: return Outcome.LibraryStillLoading
            radio.restorePreviousQueue()
        }
        if (radio.queue.isEmpty()) {
            return when (symphony.groove.song.count.value) {
                0 -> Outcome.EmptyLibrary
                else -> Outcome.NothingToResume
            }
        }
        radio.resumeWhenReady()
        return awaitPlayer()
    }

    // unavailable songs leave the queue one at a time, which may leave nothing to play
    private suspend fun awaitPlayer(): Outcome {
        val radio = symphony.radio
        withTimeoutOrNull(PLAYER_WAIT_TIMEOUT) {
            while (!radio.hasPlayer && !radio.queue.isEmpty()) {
                delay(PLAYER_POLL_INTERVAL)
            }
        }
        return when {
            radio.queue.isEmpty() -> Outcome.PlaybackFailed
            // a slow file may still be preparing, its notification is up in the meantime
            else -> Outcome.Started
        }
    }

    private companion object {
        // long enough for a first scan of a large library, a foreground service isn't stopped
        // by the system for waiting while it plays nothing, but the user shouldn't wait forever
        val LIBRARY_WAIT_TIMEOUT = 90.seconds
        val PLAYER_WAIT_TIMEOUT = 15.seconds
        val PLAYER_POLL_INTERVAL = 100.milliseconds
    }
}
