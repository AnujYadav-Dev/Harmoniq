package dev.anujyadav.harmoniq.services.playback

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.playback.PlaybackCommandDispatcher.Outcome
import dev.anujyadav.harmoniq.services.radio.RadioQueue
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.advanceUntil
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.allowPlayback
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.song
import dev.anujyadav.harmoniq.services.radio.ShadowPlayableMediaPlayer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowPlayableMediaPlayer::class])
class PlaybackCommandDispatcherTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()
    private val symphony: Symphony = application.symphony
    private val radio = symphony.radio
    private val dispatcher = PlaybackCommandDispatcher(symphony)

    @After
    fun tearDown() {
        application.releaseSymphony()
    }

    private fun hasFolders() {
        symphony.settings.mediaFolders.setValue(setOf(Uri.parse("content://docs/tree/none")))
    }

    private fun library(vararg ids: String) {
        hasFolders()
        ids.map { song(it) }.forEach {
            allowPlayback(application, it)
            symphony.groove.onSongScanned(it)
        }
    }

    private fun playing(vararg ids: String) {
        library(*ids)
        radio.queue.add(ids.toList())
        advanceUntil { radio.isPlaying }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun run(command: PlaybackCommand): Outcome {
        var outcome: Outcome? = null
        GlobalScope.launch { outcome = dispatcher.run(command) }
        advanceUntil { outcome != null }
        return outcome!!
    }

    private fun savedQueue(vararg ids: String) {
        symphony.settings.previousSongQueue.setValue(
            RadioQueue.Serialized(0, 0L, ids.toList(), ids.toList(), false)
        )
    }

    @Test
    fun playPauseTogglesMusic() {
        playing("a")
        assertEquals(Outcome.Done, run(PlaybackCommand.PlayPause))
        advanceUntil { !radio.isPlaying }
        assertEquals(Outcome.Done, run(PlaybackCommand.PlayPause))
        advanceUntil { radio.isPlaying }
    }

    @Test
    fun playPauseWithNothingLoadedStartsTheSavedQueue() {
        library("a")
        savedQueue("a")
        assertEquals(Outcome.Done, run(PlaybackCommand.PlayPause))
        advanceUntil { radio.isPlaying }
    }

    @Test
    fun resumeNeverPauses() {
        playing("a")
        assertEquals(Outcome.Done, run(PlaybackCommand.Resume))
        assertTrue(radio.isPlaying)
    }

    @Test
    fun nextAndPreviousMoveThroughTheQueue() {
        playing("a", "b")
        assertEquals(Outcome.Done, run(PlaybackCommand.Next))
        assertEquals("b", radio.queue.currentSongId)
        advanceUntil { radio.isPlaying }
        assertEquals(Outcome.Done, run(PlaybackCommand.Previous))
        assertEquals("a", radio.queue.currentSongId)
    }

    @Test
    fun shuffleAndRepeatChange() {
        playing("a", "b")
        run(PlaybackCommand.ToggleShuffle)
        assertTrue(radio.queue.currentShuffleMode)
        run(PlaybackCommand.CycleRepeat)
        assertEquals(RadioQueue.LoopMode.Queue, radio.queue.currentLoopMode)
    }

    @Test
    fun favoriteToggles() {
        playing("a")
        run(PlaybackCommand.ToggleFavorite)
        advanceUntil { symphony.groove.playlist.favorites.value.contains("a") }
        run(PlaybackCommand.ToggleFavorite)
        advanceUntil { !symphony.groove.playlist.favorites.value.contains("a") }
    }

    @Test
    fun shuffleAllPlaysTheWholeLibrary() {
        library("a", "b", "c")
        assertEquals(Outcome.Done, run(PlaybackCommand.ShuffleAll))
        assertEquals(setOf("a", "b", "c"), radio.queue.currentQueue.toSet())
        assertTrue(radio.queue.currentShuffleMode)
    }

    @Test
    fun shuffleAllTellsWhatIsMissing() {
        assertEquals(Outcome.NoMediaFolders, run(PlaybackCommand.ShuffleAll))
        hasFolders()
        assertEquals(Outcome.EmptyLibrary, run(PlaybackCommand.ShuffleAll))
    }

    @Test
    fun aPlaylistPlaysAndCountsAsPlayed() {
        library("a", "b")
        val playlist = symphony.groove.playlist.create("Mix", listOf("a", "b"))
        symphony.groove.playlist.add(playlist)

        assertEquals(Outcome.Done, run(PlaybackCommand.PlayPlaylist(playlist.id)))

        assertEquals(listOf("a", "b"), radio.queue.currentQueue.toList())
        assertNotNull(symphony.settings.playlistLastPlayed.value[playlist.id])
    }

    @Test
    fun aMissingOrEmptyPlaylistIsReported() {
        library("a")
        assertEquals(Outcome.PlaylistMissing, run(PlaybackCommand.PlayPlaylist("deleted")))
        val empty = symphony.groove.playlist.create("Empty", emptyList())
        symphony.groove.playlist.add(empty)
        assertEquals(Outcome.PlaylistEmpty, run(PlaybackCommand.PlayPlaylist(empty.id)))
        assertTrue(radio.queue.isEmpty())
    }

    @Test
    fun aSongPlaysByItsPath() {
        library("a", "b")
        assertEquals(Outcome.Done, run(PlaybackCommand.PlaySong(song("b").path)))
        assertEquals("b", radio.queue.currentSongId)
    }

    @Test
    fun aDeletedSongIsReported() {
        library("a")
        assertEquals(Outcome.SongMissing, run(PlaybackCommand.PlaySong("primary:Music/deleted.mp3")))
    }

    @Test
    fun jumpingFindsASongThatMoved() {
        playing("a", "b", "c")
        assertEquals(Outcome.Done, run(PlaybackCommand.JumpToQueueItem(1, song("c").path)))
        assertEquals("c", radio.queue.currentSongId)
    }

    @Test
    fun jumpingToASongThatLeftIsReported() {
        playing("a")
        assertEquals(Outcome.QueueItemGone, run(PlaybackCommand.JumpToQueueItem(1, song("gone").path)))
        assertEquals("a", radio.queue.currentSongId)
    }

    @Test
    fun aSleepTimerNeedsMusic() {
        assertEquals(Outcome.NothingPlaying, run(PlaybackCommand.SetSleepTimer(60_000L, false)))
        assertNull(radio.sleepTimer)
    }

    @Test
    fun aSleepTimerCanBeSetAndCleared() {
        playing("a")
        assertEquals(Outcome.Done, run(PlaybackCommand.SetSleepTimer(60_000L, true)))
        assertEquals(true, radio.sleepTimer?.quitOnEnd)
        assertEquals(Outcome.Done, run(PlaybackCommand.ClearSleepTimer))
        assertNull(radio.sleepTimer)
    }

    @Test
    fun refreshingSuggestionsNeedsALibrary() {
        assertEquals(Outcome.NoMediaFolders, run(PlaybackCommand.RefreshSuggestions))
        hasFolders()
        assertEquals(Outcome.EmptyLibrary, run(PlaybackCommand.RefreshSuggestions))
    }

    @Test
    fun refreshingSuggestionsStartsNoMusic() {
        library("a", "b")
        assertEquals(Outcome.Done, run(PlaybackCommand.RefreshSuggestions))
        assertTrue(radio.queue.isEmpty())
    }

    @Test
    fun clearingASleepTimerNeverBringsMusicBack() {
        library("a")
        savedQueue("a")
        assertEquals(Outcome.Done, run(PlaybackCommand.ClearSleepTimer))
        assertFalse(radio.isPlaying)
    }
}
