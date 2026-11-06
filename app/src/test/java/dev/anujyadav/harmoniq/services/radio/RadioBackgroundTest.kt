package dev.anujyadav.harmoniq.services.radio

import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.advance
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.advanceUntil
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.allowPlayback
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.song
import java.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Playback with no activity to lean on: the process can die at any moment, so the queue has to
 * be saved along the way, and stopping can't count on anything to close.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowPlayableMediaPlayer::class])
class RadioBackgroundTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()
    private val symphony = Symphony(application)
    private val radio = symphony.radio
    private val saved get() = symphony.settings.previousSongQueue.value
    private val debounce = Radio.QUEUE_SAVE_DEBOUNCE.toJavaDuration()

    // the queue as it would be with these songs in it, without needing a player
    private fun fillQueue(vararg songIds: String) {
        radio.queue.originalQueue.addAll(songIds)
        radio.queue.currentQueue.addAll(songIds)
        radio.queue.currentSongIndex = 0
    }

    private fun saveQueueOf(vararg songIds: String, playedDuration: Long = 0L) {
        symphony.settings.previousSongQueue.setValue(
            RadioQueue.Serialized(
                currentSongIndex = 0,
                playedDuration = playedDuration,
                originalQueue = songIds.toList(),
                currentQueue = songIds.toList(),
                shuffled = false,
            )
        )
    }

    @Test
    fun aChangedQueueIsSavedAfterTheDebounce() {
        fillQueue("a", "b")
        advance(debounce.minusMillis(1))
        assertNull(saved)
        advance(Duration.ofMillis(1))
        assertEquals(listOf("a", "b"), saved?.originalQueue)
    }

    @Test
    fun aBurstOfChangesIsSavedOnce() {
        fillQueue("a")
        advance(debounce.minusMillis(1))
        radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
        advance(debounce.minusMillis(1))
        assertNull(saved)
        advance(Duration.ofMillis(1))
        assertEquals(listOf("a"), saved?.originalQueue)
    }

    @Test
    fun shuffleIsSavedAsWell() {
        fillQueue("a", "b")
        advance(debounce)
        radio.queue.setShuffleMode(true)
        advance(debounce)
        assertEquals(true, saved?.shuffled)
    }

    @Test
    fun anEmptyQueueLeavesTheSavedOneAlone() {
        saveQueueOf("a")
        radio.onUpdate.dispatch(Radio.Events.Queue.Cleared)
        advance(debounce)
        assertEquals(listOf("a"), saved?.originalQueue)
    }

    @Test
    fun stoppingSavesRightAwayWithoutWaiting() {
        fillQueue("a", "b")
        radio.stop()
        assertEquals(listOf("a", "b"), saved?.originalQueue)
        assertTrue(radio.queue.isEmpty())
    }

    @Test
    fun theSavedQueueSurvivesWhatComesAfterStopping() {
        fillQueue("a")
        radio.stop()
        advance(debounce)
        assertEquals(listOf("a"), saved?.originalQueue)
    }

    @Test
    fun replacingTheQueueDoesNotSaveTheOldOne() {
        fillQueue("a")
        radio.stop(ended = false)
        assertNull(saved)
    }

    @Test
    fun aSaveWhilePreparingKeepsThePositionItWillStartFrom() {
        val song = song("a")
        allowPlayback(application, song, RadioTestSupport.NEVER_PREPARED_DELAY_MS)
        symphony.groove.onSongScanned(song)
        saveQueueOf("a", playedDuration = 12_345L)

        radio.restorePreviousQueue()
        advance(debounce)

        assertFalse(radio.hasPlayer)
        assertEquals(12_345L, saved?.playedDuration)
    }

    @Test
    fun aQueuedSongGoneFromTheLibraryIsRemoved() {
        fillQueue("gone", "gone-too")
        radio.play(Radio.PlayOptions(index = 0))
        advance(Duration.ZERO)
        assertTrue(radio.queue.isEmpty())
    }

    @Test
    fun idleMeansStoppedAndSaved() {
        fillQueue("a")
        radio.stopIfIdle()
        assertTrue(radio.queue.isEmpty())
        assertEquals(listOf("a"), saved?.originalQueue)
    }

    @Test
    fun musicKeepsGoingWhenNotIdle() {
        val song = song("a")
        allowPlayback(application, song)
        symphony.groove.onSongScanned(song)
        radio.queue.add("a")
        advanceUntil { radio.isPlaying }

        radio.stopIfIdle()

        assertTrue(radio.isPlaying)
        assertFalse(radio.queue.isEmpty())
    }

    @Test
    fun sleepTimerQuitsWithNoActivity() {
        assertNull(symphony.closeApp)
        fillQueue("a")
        radio.onSleepTimerEnd(quitOnEnd = true)
        assertTrue(radio.queue.isEmpty())
        assertEquals(listOf("a"), saved?.originalQueue)
    }

    @Test
    fun sleepTimerQuitClosesTheActivityIfThereIsOne() {
        var closed = false
        symphony.closeApp = { closed = true }
        fillQueue("a")
        radio.onSleepTimerEnd(quitOnEnd = true)
        assertTrue(closed)
    }

    @Test
    fun sleepTimerWithoutQuitKeepsTheQueue() {
        fillQueue("a")
        radio.onSleepTimerEnd(quitOnEnd = false)
        assertFalse(radio.queue.isEmpty())
    }

    @Test
    fun sleepTimerQuitFadesOutMusicFirst() {
        val song = song("a")
        allowPlayback(application, song)
        symphony.groove.onSongScanned(song)
        radio.queue.add("a")
        advanceUntil { radio.isPlaying }

        radio.onSleepTimerEnd(quitOnEnd = true)
        advanceUntil { radio.queue.isEmpty() }

        assertFalse(radio.isPlaying)
        assertEquals(listOf("a"), saved?.originalQueue)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun aSavedQueueIsRestoredFromTheSongCacheWithoutAScan() {
        val song = song("a")
        allowPlayback(application, song)
        runBlocking { symphony.database.songCache.insert(song) }
        saveQueueOf("a", "deleted-since")

        var done = false
        GlobalScope.launch {
            radio.restoreQueueFromSongCache()
            done = true
        }
        advanceUntil { done }

        assertEquals(listOf("a"), radio.queue.currentQueue.toList())
        assertEquals("a", symphony.groove.song.get("a")?.id)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun theSongCacheDoesNotOverrideWhatAScanFound() {
        val cached = song("old-id")
        val scanned = cached.copy(id = "new-id")
        runBlocking { symphony.database.songCache.insert(cached) }
        symphony.groove.onSongScanned(scanned)

        var done = false
        GlobalScope.launch {
            symphony.groove.loadFromSongCache(setOf("old-id"))
            done = true
        }
        advanceUntil { done }

        assertNull(symphony.groove.song.get("old-id"))
        assertEquals("new-id", symphony.groove.song.get("new-id")?.id)
    }

    @Test
    fun aSongLoadedFromTheCacheIsNotCountedTwiceByTheScan() {
        val song = song("a")
        symphony.groove.onSongScanned(song)
        symphony.groove.onSongScanned(song)
        assertEquals(listOf("a"), symphony.groove.song.all.value)
    }
}
