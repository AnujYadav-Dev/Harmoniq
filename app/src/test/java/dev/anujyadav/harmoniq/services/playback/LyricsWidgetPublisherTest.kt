package dev.anujyadav.harmoniq.services.playback

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.playback.LyricsWidgetContent.Kind
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.advance
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.advanceUntil
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.allowPlayback
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.song
import dev.anujyadav.harmoniq.services.radio.ShadowPlayableMediaPlayer
import dev.anujyadav.harmoniq.utils.TimedContent
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.toJavaDuration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowPlayableMediaPlayer::class])
class LyricsWidgetPublisherTest {
    private class FakeHost(var widgets: Boolean = true) : PlaybackWidgetHost {
        val updates = AtomicInteger()
        val checks = AtomicInteger()
        override fun hasWidgets(): Boolean {
            checks.incrementAndGet()
            return widgets
        }

        override fun update() {
            updates.incrementAndGet()
        }
    }

    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()
    private val symphony = Symphony(application)
    private val radio = symphony.radio
    private val host = FakeHost()
    private val publisher = LyricsWidgetPublisher(symphony, host).also { it.start() }
    private val power = shadowOf(application.getSystemService(Context.POWER_SERVICE) as PowerManager)
    private val content get() = LyricsWidgetStore.content.value
    private val interval = LyricsWidgetPublisher.LINE_UPDATE_INTERVAL.toJavaDuration()
    private val synced = "[00:00.00]First\n[00:10.00]Second\n[00:20.00]Third\n[00:30.00]Fourth"

    @After
    fun tearDown() {
        publisher.stop()
        LyricsWidgetStore.set(LyricsWidgetContent())
    }

    private fun enable() {
        power.setIsInteractive(true)
        symphony.settings.lyricsWidgetEnabled.setValue(true)
    }

    private fun play(lyrics: String? = synced) {
        val song = song("a")
        allowPlayback(application, song)
        symphony.groove.onSongScanned(song)
        lyrics?.let { symphony.database.lyricsCache.put("a", it) }
        radio.queue.add("a")
        advanceUntil { radio.isPlaying }
        publisher.refresh()
    }

    private fun setScreen(isOn: Boolean) {
        power.setIsInteractive(isOn)
        application.sendBroadcast(Intent(if (isOn) Intent.ACTION_SCREEN_ON else Intent.ACTION_SCREEN_OFF))
        advance(Duration.ZERO)
    }

    @Test
    fun itIsOffByDefaultAndDoesNothingAtAll() {
        play()
        publisher.refresh()
        advance(interval)
        Thread.sleep(SETTLE_MS)
        assertEquals(LyricsWidgetContent(), content)
        assertEquals("not even asking whether a widget exists", 0, host.checks.get())
    }

    @Test
    fun withoutAWidgetLyricsAreNotEvenLoaded() {
        enable()
        host.widgets = false
        play()
        advance(interval)
        Thread.sleep(SETTLE_MS)
        assertEquals(Kind.Idle, content.kind)
    }

    @Test
    fun syncedLyricsShowTheLinesAroundThePosition() {
        enable()
        play()
        advanceUntil { content.kind == Kind.Synced }
        advanceUntil { content.current == "First" }
        assertNull(content.previous)
        assertEquals("Second", content.next)
        assertEquals("a", content.songTitle)
    }

    @Test
    fun aSeekMovesToTheRightLine() {
        enable()
        play()
        advanceUntil { content.current == "First" }
        advance(interval)
        radio.seek(21_000L)
        advanceUntil { content.current == "Third" }
        assertEquals("Second", content.previous)
        assertEquals("Fourth", content.next)
    }

    @Test
    fun aWidgetUpdateOnlyGoesOutWhenTheLineChanged() {
        enable()
        play()
        advanceUntil { content.current == "First" }
        Thread.sleep(SETTLE_MS)
        val before = host.updates.get()
        publisher.refresh()
        advance(interval)
        Thread.sleep(SETTLE_MS)
        assertEquals(before, host.updates.get())
    }

    @Test
    fun manyLinesInARowAreDrawnTogether() {
        enable()
        play()
        advanceUntil { content.current == "First" }
        radio.seek(11_000L)
        advanceUntil { content.current == "Second" }
        Thread.sleep(SETTLE_MS)
        val before = host.updates.get()
        radio.seek(21_000L)
        radio.seek(31_000L)
        advanceUntil { content.current == "Fourth" }
        Thread.sleep(SETTLE_MS)
        assertEquals(before + 1, host.updates.get())
    }

    @Test
    fun theScreenGoingOffStopsItAndComingBackCatchesUp() {
        enable()
        play()
        advanceUntil { content.current == "First" }
        setScreen(false)
        radio.seek(21_000L)
        advance(interval)
        Thread.sleep(SETTLE_MS)
        assertEquals("First", content.current)

        setScreen(true)
        advanceUntil { content.current == "Third" }
    }

    @Test
    fun whilePausedTheLineStays() {
        enable()
        play()
        advanceUntil { content.current == "First" }
        radio.pauseInstant()
        advance(Duration.ZERO)
        radio.seek(21_000L)
        advance(interval)
        Thread.sleep(SETTLE_MS)
        assertEquals("First", content.current)
    }

    @Test
    fun unsyncedLyricsSaySo() {
        enable()
        play("just some words\nwith no times")
        advanceUntil { content.kind == Kind.Unsynced }
        assertEquals("a", content.songTitle)
    }

    @Test
    fun noLyricsSaySo() {
        enable()
        play(lyrics = null)
        advanceUntil { content.kind == Kind.Missing }
    }

    @Test
    fun stoppingGoesBackToIdle() {
        enable()
        play()
        advanceUntil { content.current == "First" }
        radio.stop()
        advanceUntil { content.kind == Kind.Idle }
    }

    @Test
    fun whatKindOfLyricsTheseAre() {
        fun kind(text: String?) = LyricsWidgetPublisher.kindOf(text?.let { TimedContent.fromLyrics(it) })
        assertEquals(Kind.Missing, kind(null))
        assertEquals(Kind.Missing, kind(""))
        assertEquals(Kind.Missing, kind("  \n\n "))
        assertEquals(Kind.Unsynced, kind("words\nmore words"))
        assertEquals(Kind.Synced, kind(synced))
    }

    @Test
    fun theLineAtAPosition() {
        val lines = listOf(5_000L to "a", 10_000L to "b")
        assertEquals(LyricsWidgetPublisher.NO_LINE, LyricsWidgetPublisher.lineIndexAt(lines, 0L))
        assertEquals(0, LyricsWidgetPublisher.lineIndexAt(lines, 5_000L))
        assertEquals(0, LyricsWidgetPublisher.lineIndexAt(lines, 9_999L))
        assertEquals(1, LyricsWidgetPublisher.lineIndexAt(lines, 99_000L))
    }

    @Test
    fun blankLinesAreSkippedAroundTheCurrentOne() {
        val lines = listOf(0L to "a", 1L to "", 2L to "b", 3L to "", 4L to "c")
        val atB = LyricsWidgetPublisher.contentAt(lines, 2, "t")
        assertEquals("a", atB.previous)
        assertEquals("b", atB.current)
        assertEquals("c", atB.next)
        val atPause = LyricsWidgetPublisher.contentAt(lines, 3, "t")
        assertNull(atPause.current)
        assertEquals("b", atPause.previous)
        assertEquals("c", atPause.next)
    }

    @Test
    fun beforeTheFirstLineItIsComing() {
        val content = LyricsWidgetPublisher.contentAt(listOf(5L to "a"), LyricsWidgetPublisher.NO_LINE, "t")
        assertNull(content.previous)
        assertNull(content.current)
        assertEquals("a", content.next)
    }

    private companion object {
        const val SETTLE_MS = 250L
    }
}
