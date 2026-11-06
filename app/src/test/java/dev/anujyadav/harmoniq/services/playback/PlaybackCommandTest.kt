package dev.anujyadav.harmoniq.services.playback

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.radio.RadioNotificationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackCommandTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()

    private val all = listOf(
        PlaybackCommand.Resume,
        PlaybackCommand.PlayPause,
        PlaybackCommand.Next,
        PlaybackCommand.Previous,
        PlaybackCommand.ToggleShuffle,
        PlaybackCommand.CycleRepeat,
        PlaybackCommand.ToggleFavorite,
        PlaybackCommand.ShuffleAll,
        PlaybackCommand.ClearSleepTimer,
        PlaybackCommand.PlayPlaylist("playlist-1"),
        // paths hold whatever a file can be named
        PlaybackCommand.PlaySong("primary:Music/Rock & Roll/100% é?#=.mp3"),
        PlaybackCommand.JumpToQueueItem(3, "primary:Music/a b.mp3"),
        PlaybackCommand.SetSleepTimer(1_800_000L, true),
        PlaybackCommand.SetSleepTimer(60_000L, false),
    )

    @Test
    fun everyCommandSurvivesAnIntent() {
        all.forEach { assertEquals(it, PlaybackCommand.fromIntent(it.createIntent(application))) }
    }

    @Test
    fun intentsAreExplicit() {
        all.forEach {
            assertEquals(
                RadioNotificationService::class.java.name,
                it.createIntent(application).component?.className,
            )
        }
    }

    @Test
    fun anythingElseIsNoCommand() {
        assertNull(PlaybackCommand.fromIntent(null))
        assertNull(PlaybackCommand.fromIntent(Intent()))
        assertNull(PlaybackCommand.fromIntent(Intent("other", PlaybackCommand.Next.toUri())))
        listOf(
            "harmoniq-command://unknown",
            "https://next",
            "harmoniq-command://play_song",
            "harmoniq-command://jump_to_queue_item?index=x&path=a",
            "harmoniq-command://set_sleep_timer?duration=10",
            "harmoniq-command://set_sleep_timer?duration=10&quit=maybe",
        ).forEach { assertNull(it, PlaybackCommand.fromUri(Uri.parse(it))) }
    }

    @Test
    fun requestCodesAreStableAndDiffer() {
        assertEquals("harmoniq-command://next".hashCode(), PlaybackCommand.Next.requestCode)
        assertEquals(all.size, all.map { it.requestCode }.toSet().size)
    }

    @Test
    fun pendingIntentsStartTheServiceInForegroundAndCanNotBeChanged() {
        all.forEach {
            val pending = it.createPendingIntent(application)
            assertTrue(pending.isImmutable)
            assertTrue(shadowOf(pending).isForegroundService)
            assertEquals(it, PlaybackCommand.fromIntent(shadowOf(pending).savedIntent))
        }
    }

    @Test
    fun aMovedQueueItemIsFoundByItsPath() {
        val find = PlaybackCommandDispatcher.Companion::findQueueIndex
        val queue = listOf("a", null, "b", "c")
        assertEquals(2, find(queue, 2, "b"))
        assertEquals(2, find(queue, 0, "b"))
        assertEquals(2, find(queue, 99, "b"))
        assertNull(find(queue, 2, "gone"))
    }

    @Test
    fun theSameSongTwiceStaysWhereItWasAsked() {
        assertEquals(2, PlaybackCommandDispatcher.findQueueIndex(listOf("a", "b", "a"), 2, "a"))
    }
}
