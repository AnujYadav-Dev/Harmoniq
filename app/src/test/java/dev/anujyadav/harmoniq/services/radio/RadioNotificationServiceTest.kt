package dev.anujyadav.harmoniq.services.radio

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.advanceUntil
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.allowPlayback
import dev.anujyadav.harmoniq.services.radio.RadioTestSupport.song
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowPlayableMediaPlayer::class])
class RadioNotificationServiceTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()
    private val notifications = application.getSystemService(Context.NOTIFICATION_SERVICE)
        .let { shadowOf(it as NotificationManager) }
    private var nextStartId = 1
    private val started = mutableListOf<ServiceController<RadioNotificationService>>()

    private val status: Notification?
        get() = notifications.getNotification(RadioNotification.STATUS_NOTIFICATION_ID)

    // the service's statics outlive a test, nothing of this one may still be listening to them
    @After
    fun tearDown() {
        started.forEach { it.destroy() }
        RadioNotificationService.destroy(stop = false)
        RadioNotificationService.pendingNotification = null
        application.releaseSymphony()
    }

    private fun intent(command: PlaybackCommand? = null) =
        command?.createIntent(application) ?: Intent(application, RadioNotificationService::class.java)

    private fun start(command: PlaybackCommand? = null) =
        Robolectric.buildService(RadioNotificationService::class.java, intent(command))
            .create()
            .startCommand(0, nextStartId++)
            .also { started.add(it) }

    private fun ServiceController<RadioNotificationService>.startAgain(command: PlaybackCommand? = null) =
        withIntent(intent(command)).startCommand(0, nextStartId++)

    private val ServiceController<RadioNotificationService>.shadow get() = shadowOf(get())

    private fun text(notification: Notification?) =
        notification?.extras?.getString(NotificationCompat.EXTRA_TEXT)

    @Test
    fun itAlwaysReachesForegroundEvenWithNothingToShow() {
        // stopping right after is what clears what's shown, which only comes after being shown
        assertTrue(start().shadow.isForegroundStopped)
    }

    @Test
    fun aCommandShowsLoadingUntilThereIsMusic() {
        application.symphony.settings.mediaFolders.setValue(setOf(Uri.parse("content://docs/tree/none")))
        val service = start(PlaybackCommand.Resume)
        assertEquals(RadioNotification.NOTIFICATION_ID, service.shadow.lastForegroundNotificationId)
        assertEquals(AppStrings.Loading, text(service.shadow.lastForegroundNotification))
    }

    @Test
    fun withNothingWantingItItStopsItself() {
        val service = start()
        assertTrue(service.shadow.isStoppedBySelf)
        assertNull(RadioNotificationService.instance)
    }

    @Test
    fun aWaitingNotificationIsShownInsteadOfLoading() {
        val waiting = RadioNotification.createLoading(application)
        RadioNotificationService.pendingNotification = waiting

        val service = start()

        assertSame(waiting, service.shadow.lastForegroundNotification)
        assertNull(RadioNotificationService.pendingNotification)
        assertFalse(service.shadow.isStoppedBySelf)
    }

    @Test
    fun startingAgainLeavesTheShownNotificationAlone() {
        val waiting = RadioNotification.createLoading(application)
        RadioNotificationService.pendingNotification = waiting
        val service = start()

        service.startAgain()

        assertSame(waiting, service.shadow.lastForegroundNotification)
        assertFalse(service.shadow.isStoppedBySelf)
    }

    @Test
    fun plainUseDoesNotCreateSymphony() {
        start()
        assertNull(application.symphonyIfCreated)
    }

    @Test
    fun aCommandCreatesSymphony() {
        assertNull(application.symphonyIfCreated)
        start(PlaybackCommand.Resume)
        assertNotNull(application.symphonyIfCreated)
    }

    @Test
    fun resumeWithoutMusicFoldersSaysSoAndStops() {
        val service = start(PlaybackCommand.Resume)
        advanceUntil { service.shadow.isStoppedBySelf }
        assertEquals(AppStrings.CommandNoMediaFolders, text(status))
        assertNull(RadioNotificationService.instance)
    }

    @Test
    fun resumeWithAnEmptyLibrarySaysSoAndStops() {
        application.symphony.settings.mediaFolders.setValue(setOf(Uri.parse("content://docs/tree/none")))
        val service = start(PlaybackCommand.Resume)
        advanceUntil { service.shadow.isStoppedBySelf }
        assertEquals(AppStrings.CommandEmptyLibrary, text(status))
    }

    @Test
    fun resumePlaysTheSavedQueueWithoutWaitingForTheScan() {
        val symphony = application.symphony
        val song = song("a")
        allowPlayback(application, song)
        runBlocking { symphony.database.songCache.insert(song) }
        symphony.settings.mediaFolders.setValue(setOf(Uri.parse("content://docs/tree/none")))
        symphony.settings.previousSongQueue.setValue(
            RadioQueue.Serialized(0, 0L, listOf("a"), listOf("a"), false)
        )

        start(PlaybackCommand.Resume)
        advanceUntil { symphony.radio.isPlaying }

        assertEquals("a", symphony.radio.queue.currentSongId)
        assertNull(status)
    }

    @Test
    fun swipingAwayStopsPausedMusic() {
        val symphony = application.symphony
        symphony.emitReady()
        symphony.radio.queue.originalQueue.add("a")
        symphony.radio.queue.currentQueue.add("a")
        RadioNotificationService.pendingNotification = RadioNotification.createLoading(application)
        val service = start()

        service.get().onTaskRemoved(null)

        assertTrue(symphony.radio.queue.isEmpty())
        assertTrue(service.shadow.isStoppedBySelf)
        assertEquals(listOf("a"), symphony.settings.previousSongQueue.value?.originalQueue)
    }

    @Test
    fun swipingAwayKeepsMusicPlaying() {
        val symphony = application.symphony
        val song = song("a")
        allowPlayback(application, song)
        symphony.emitReady()
        symphony.groove.onSongScanned(song)
        symphony.radio.queue.add("a")
        advanceUntil { symphony.radio.isPlaying }
        RadioNotificationService.pendingNotification = RadioNotification.createLoading(application)
        val service = start()

        service.get().onTaskRemoved(null)

        assertTrue(symphony.radio.isPlaying)
        assertFalse(service.shadow.isStoppedBySelf)
    }

    @Test
    fun aFailedCommandLeavesPlayingMusicAlone() {
        val symphony = application.symphony
        val song = song("a")
        allowPlayback(application, song)
        symphony.settings.mediaFolders.setValue(setOf(Uri.parse("content://docs/tree/none")))
        symphony.emitReady()
        symphony.groove.onSongScanned(song)
        symphony.radio.queue.add("a")
        advanceUntil { symphony.radio.isPlaying }

        val service = start(PlaybackCommand.PlayPlaylist("no-such-playlist"))
        advanceUntil { status != null }

        assertEquals(AppStrings.CommandPlaylistMissing, text(status))
        assertTrue(symphony.radio.isPlaying)
        assertFalse(service.shadow.isStoppedBySelf)
    }

    @Test
    fun aCommandThatLeavesNoMusicLetsTheServiceGo() {
        val service = start(PlaybackCommand.ClearSleepTimer)
        advanceUntil { service.shadow.isStoppedBySelf }
        assertNull(status)
    }

    @Test
    fun commandsRunInOrder() {
        val symphony = application.symphony
        val songs = listOf("a", "b", "c").map { song(it) }
        songs.forEach { allowPlayback(application, it) }
        symphony.emitReady()
        songs.forEach { symphony.groove.onSongScanned(it) }
        symphony.radio.queue.add(songs.map { it.id })
        advanceUntil { symphony.radio.isPlaying }

        val service = start(PlaybackCommand.Next)
        service.startAgain(PlaybackCommand.Next)
        advanceUntil { symphony.radio.queue.currentSongId == "c" }
    }
}
