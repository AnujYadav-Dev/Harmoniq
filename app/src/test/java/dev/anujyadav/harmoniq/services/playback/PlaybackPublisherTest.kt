package dev.anujyadav.harmoniq.services.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.radio.Radio
import dev.anujyadav.harmoniq.services.radio.RadioQueue
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.advance
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.advanceUntil
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.allowPlayback
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.song
import dev.anujyadav.harmoniq.services.radio.ShadowPlayableMediaPlayer
import dev.anujyadav.harmoniq.ui.theme.ThemeMode
import java.io.File
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.toJavaDuration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowPlayableMediaPlayer::class])
class PlaybackPublisherTest {
    private class FakeHost(var widgets: Boolean = true) : PlaybackWidgetHost {
        val updates = AtomicInteger()
        override fun hasWidgets() = widgets
        override fun update() {
            updates.incrementAndGet()
        }
    }

    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()
    private val symphony = Symphony(application)
    private val radio = symphony.radio
    private val host = FakeHost()
    private val publisher = PlaybackPublisher(symphony, host).also { it.start() }
    private val store = PlaybackSnapshotStore(application)
    private val interval = PlaybackPublisher.PUBLISH_INTERVAL.toJavaDuration()

    @After
    fun tearDown() {
        publisher.stop()
    }

    private fun library(vararg ids: String) = ids.map { song(it) }.forEach {
        allowPlayback(application, it)
        symphony.groove.onSongScanned(it)
    }

    private fun play(vararg ids: String) {
        library(*ids)
        radio.queue.add(ids.toList())
        advanceUntil { radio.isPlaying }
    }

    private fun settle() {
        advance(interval)
        advanceUntil { store.read().isPlaying == radio.isPlaying }
        // saves run in the background, what was already on its way has to land before counting
        Thread.sleep(BACKGROUND_SETTLE_MS)
    }

    private companion object {
        const val BACKGROUND_SETTLE_MS = 250L
        val UNSAFE = Regex("[^A-Za-z0-9._-]")
    }

    @Test
    fun theSnapshotDescribesWhatIsPlaying() {
        play("a", "b", "c")
        settle()
        val snapshot = store.read()
        assertEquals(song("a").path, snapshot.song?.path)
        assertEquals("a", snapshot.song?.title)
        assertEquals("Artist", snapshot.song?.artist)
        assertEquals("Album", snapshot.song?.album)
        assertTrue(snapshot.isPlaying)
        assertEquals(listOf(song("b").path, song("c").path), snapshot.queue.map { it.path })
        assertEquals(listOf(1, 2), snapshot.queue.map { it.queueIndex })
        assertTrue(host.updates.get() > 0)
    }

    @Test
    fun aNewLookRedrawsWidgets() {
        Thread.sleep(BACKGROUND_SETTLE_MS)
        val before = host.updates.get()
        symphony.settings.themeMode.setValue(ThemeMode.DARK)
        advanceUntil { host.updates.get() > before }
    }

    @Test
    fun itDoesNothingWithoutWidgets() {
        host.widgets = false
        play("a")
        advance(interval)
        advance(interval)
        assertNull(store.read().song)
        assertEquals(0, host.updates.get())
    }

    @Test
    fun theQueuePreviewIsCapped() {
        val ids = (0..PlaybackSnapshot.QUEUE_PREVIEW_LIMIT + 5).map { "s$it" }.toTypedArray()
        play(*ids)
        settle()
        assertEquals(PlaybackSnapshot.QUEUE_PREVIEW_LIMIT, store.read().queue.size)
    }

    @Test
    fun theFirstChangeGoesOutAtOnceAndABurstWaits() {
        play("a")
        settle()
        val before = host.updates.get()
        advance(interval)

        radio.queue.setLoopMode(RadioQueue.LoopMode.Queue)
        advance(Duration.ZERO)
        advanceUntil { store.read().loopMode == RadioQueue.LoopMode.Queue }
        advanceUntil { host.updates.get() >= before + 1 }
        Thread.sleep(BACKGROUND_SETTLE_MS)
        assertEquals(before + 1, host.updates.get())

        radio.queue.setLoopMode(RadioQueue.LoopMode.Song)
        radio.queue.setLoopMode(RadioQueue.LoopMode.None)
        radio.queue.setLoopMode(RadioQueue.LoopMode.Song)
        advance(interval.minusMillis(100))
        assertEquals(RadioQueue.LoopMode.Queue, store.read().loopMode)
        advance(interval)
        advanceUntil { store.read().loopMode == RadioQueue.LoopMode.Song }
        advanceUntil { host.updates.get() >= before + 2 }
        Thread.sleep(BACKGROUND_SETTLE_MS)
        assertEquals(before + 2, host.updates.get())
    }

    @Test
    fun nothingNewMeansNoRedraw() {
        play("a")
        radio.pauseInstant()
        settle()
        advance(interval)
        val before = host.updates.get()
        radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
        advance(interval)
        Thread.sleep(100)
        assertEquals(before, host.updates.get())
    }

    @Test
    fun stoppingKeepsTheLastSongUp() {
        play("a")
        settle()
        radio.stop()
        settle()
        assertEquals(song("a").path, store.read().song?.path)
        assertFalse(store.read().isPlaying)
    }

    @Test
    fun aGoneSongIsClearedOnceTheLibraryIsKnown() {
        store.write(PlaybackSnapshot(song = PlaybackSnapshot.Item("gone", "gone", "x")))
        library("a")
        symphony.groove.readyDeferred.complete(true)
        advance(interval)
        advanceUntil { store.read().song == null }
    }

    @Test
    fun aStoppedQueueLosesWhatLeftTheLibrary() {
        store.write(
            PlaybackSnapshot(
                song = PlaybackSnapshot.Item(song("a").path, "a", "x"),
                queue = listOf(
                    PlaybackSnapshot.Item(song("a").path, "a", "x", queueIndex = 1),
                    PlaybackSnapshot.Item("gone", "gone", "x", queueIndex = 2),
                ),
            )
        )
        library("a")
        symphony.groove.readyDeferred.complete(true)
        advance(interval)
        advanceUntil { store.read().queue.size == 1 }
        assertEquals(song("a").path, store.read().queue.single().path)
    }

    @Test
    fun playlistsAreWaitedForThenPublished() {
        val mix = symphony.groove.playlist.create("Mix", emptyList())
        symphony.groove.playlist.add(mix)
        play("a")
        settle()
        assertTrue(store.read().playlists.isEmpty())
        assertFalse(store.read().isLibraryLoaded)

        symphony.groove.readyDeferred.complete(true)
        advance(interval)
        advanceUntil { store.read().playlists.any { it.id == mix.id } }
        assertTrue(store.read().isLibraryLoaded)
        assertEquals("Mix", store.read().playlists.single { it.id == mix.id }.title)
    }

    @Test
    fun aPlaylistShowsItsFirstSongsCoverUnlessItHasItsOwn() {
        val first = song("a").copy(coverFile = "a.jpg")
        symphony.groove.onSongScanned(first)
        val mix = symphony.groove.playlist.create("Mix", listOf(first.id))
        symphony.groove.playlist.add(mix)
        symphony.groove.readyDeferred.complete(true)
        advance(interval)
        advanceUntil { store.read().playlists.any { it.id == mix.id } }
        val fromSong = store.read().playlists.single { it.id == mix.id }
        assertEquals(symphony.database.artworkCache.get("a.jpg").absolutePath, fromSong.artworkPath)
        assertEquals(0L, fromSong.artworkVersion)

        val own = File(application.filesDir, "playlist_covers/${mix.id.replace(UNSAFE, "_")}.jpg")
        own.parentFile!!.mkdirs()
        own.writeText("a picture")
        symphony.groove.playlist.update(mix.id, listOf(first.id, first.id))
        advance(interval)
        advanceUntil { store.read().playlists.single { it.id == mix.id }.artworkPath == own.absolutePath }
    }

    @Test
    fun aPlaylistWithNoSongsHasNoCover() {
        val mix = symphony.groove.playlist.create("Empty", emptyList())
        symphony.groove.playlist.add(mix)
        symphony.groove.readyDeferred.complete(true)
        advance(interval)
        advanceUntil { store.read().playlists.any { it.id == mix.id } }
        assertNull(store.read().playlists.single { it.id == mix.id }.artworkPath)
    }

    @Test
    fun aDeletedPlaylistLeaves() {
        val mix = symphony.groove.playlist.create("Mix", emptyList())
        symphony.groove.playlist.add(mix)
        symphony.groove.readyDeferred.complete(true)
        advance(interval)
        advanceUntil { store.read().playlists.any { it.id == mix.id } }

        symphony.groove.playlist.delete(mix.id)
        advance(interval)
        advanceUntil { store.read().playlists.none { it.id == mix.id } }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun aRefreshReplacesSuggestions() {
        val ids = (0..20).map { "s$it" }.toTypedArray()
        library(*ids)
        GlobalScope.launch { publisher.refreshSuggestionsNow() }
        advanceUntil { store.read().suggestions.size == PlaybackSnapshot.SUGGESTIONS_PREVIEW_LIMIT }
    }

    @Test
    fun whenToClearASong() {
        fun clear(loaded: Boolean, inLibrary: Boolean, size: Int, folders: Boolean) =
            PlaybackPublisher.shouldClearSong(loaded, inLibrary, size, folders)
        assertTrue(clear(loaded = true, inLibrary = false, size = 5, folders = true))
        assertTrue(clear(loaded = true, inLibrary = false, size = 0, folders = false))
        assertFalse("still scanning", clear(loaded = false, inLibrary = false, size = 5, folders = true))
        assertFalse("it's there", clear(loaded = true, inLibrary = true, size = 5, folders = true))
        assertFalse("folder away for now", clear(loaded = true, inLibrary = false, size = 0, folders = true))
    }

    @Test
    fun suggestionsAreStoredByPathAndCapped() {
        val ids = (0..PlaybackSnapshot.SUGGESTIONS_PREVIEW_LIMIT + 3).map { "s$it" }.toTypedArray()
        library(*ids)
        symphony.groove.readyDeferred.complete(true)
        advanceUntil { store.read().suggestions.isNotEmpty() }
        val suggestions = store.read().suggestions
        assertEquals(PlaybackSnapshot.SUGGESTIONS_PREVIEW_LIMIT, suggestions.size)
        assertTrue(suggestions.all { it.path.startsWith("primary:") })
    }

    @Test
    fun theStoreSurvivesARestartAndBadData() {
        val snapshot = PlaybackSnapshot(
            song = PlaybackSnapshot.Item("p", "t", "a", "b", "/art", null),
            isPlaying = true,
            positionMs = 5L,
            durationMs = 10L,
            shuffle = true,
            loopMode = RadioQueue.LoopMode.Song,
            sleepTimerEndsAtMs = 99L,
            queue = listOf(PlaybackSnapshot.Item("q", "t", "a", queueIndex = 4)),
        )
        store.write(snapshot)
        assertEquals(snapshot, PlaybackSnapshotStore(application).read())

        application.getSharedPreferences("playback_snapshot", Context.MODE_PRIVATE)
            .edit().putString("snapshot", "{not json").commit()
        assertEquals(PlaybackSnapshot(), store.read())
    }

    @Test
    fun onlyTheTimeChangingIsNoChange() {
        val snapshot = PlaybackSnapshot(capturedAtMs = 1L)
        assertTrue(snapshot.hasSameContentAs(snapshot.copy(capturedAtMs = 2L)))
        assertFalse(snapshot.hasSameContentAs(snapshot.copy(positionMs = 2L)))
    }
}
