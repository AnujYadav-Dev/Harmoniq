package dev.anujyadav.harmoniq.shortcuts

import android.content.ComponentName
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import dev.anujyadav.harmoniq.services.playback.SleepTimerPresets
import dev.anujyadav.harmoniq.services.radio.RadioNotificationService
import dev.anujyadav.harmoniq.utils.AppStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepTimerTileTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()
    private val minute = 60_000L

    @After
    fun tearDown() {
        // these never finish starting Symphony, which destroying it expects
        runCatching { application.releaseSymphony() }
        RadioNotificationService.destroy(stop = false)
    }

    @Test
    fun nothingPlayingIsUnavailable() {
        val state = SleepTimerTileState.of(isPlaying = false, endsAtMs = null, nowMs = 0L)
        assertEquals(Tile.STATE_UNAVAILABLE, state.tileState)
        assertEquals(AppStrings.NothingPlaying, state.subtitle)
    }

    @Test
    fun musicWithoutATimerIsOff() {
        val state = SleepTimerTileState.of(isPlaying = true, endsAtMs = null, nowMs = 0L)
        assertEquals(Tile.STATE_INACTIVE, state.tileState)
    }

    @Test
    fun aRunningTimerSaysWhatIsLeft() {
        val state = SleepTimerTileState.of(isPlaying = true, endsAtMs = 25 * minute, nowMs = 0L)
        assertEquals(Tile.STATE_ACTIVE, state.tileState)
        assertEquals(AppStrings.XMinLeft("25"), state.subtitle)
    }

    @Test
    fun aRunningTimerStaysUsableWhenPaused() {
        val state = SleepTimerTileState.of(isPlaying = false, endsAtMs = minute, nowMs = 0L)
        assertEquals(Tile.STATE_ACTIVE, state.tileState)
    }

    @Test
    fun timeLeftRoundsUpAndNeverGoesNegative() {
        assertEquals(AppStrings.XMinLeft("1"), SleepTimerTileState.of(true, 1L, 0L).subtitle)
        assertEquals(AppStrings.XMinLeft("0"), SleepTimerTileState.of(true, 0L, 5L).subtitle)
    }

    @Test
    fun aTapStartsTheLastDurationAndAnotherClearsIt() {
        assertEquals(
            PlaybackCommand.SetSleepTimer(45 * minute, quitOnEnd = false),
            SleepTimerTileState.commandForTap(isTimerRunning = false, lastMinutes = 45),
        )
        assertEquals(
            PlaybackCommand.ClearSleepTimer,
            SleepTimerTileState.commandForTap(isTimerRunning = true, lastMinutes = 45),
        )
    }

    @Test
    fun theDefaultIsOneOfThePresets() {
        assertTrue(
            SleepTimerPresets.all.any { it.hours * 60 + it.minutes == SleepTimerPresets.DEFAULT_MINUTES.toLong() }
        )
    }

    @Test
    fun aTimerIsRememberedForTheNextTap() {
        val symphony = application.symphony
        assertEquals(SleepTimerPresets.DEFAULT_MINUTES, symphony.settings.lastSleepTimerMinutes.value)
        symphony.radio.setSleepTimer(45 * minute, quitOnEnd = false)
        symphony.radio.clearSleepTimer()
        assertEquals(45, symphony.settings.lastSleepTimerMinutes.value)
    }

    @Test
    fun aVeryShortTimerIsStillAValidOne() {
        val symphony = application.symphony
        symphony.radio.setSleepTimer(1L, quitOnEnd = false)
        symphony.radio.clearSleepTimer()
        assertEquals(1, symphony.settings.lastSleepTimerMinutes.value)
    }

    @Test
    fun theTileNeverCreatesSymphony() {
        val service = Robolectric.buildService(SleepTimerTileService::class.java).create().get()
        service.onStartListening()
        service.onClick()
        service.onStopListening()
        assertNull(application.symphonyIfCreated)
        assertNull(shadowOf(application).nextStartedService)
    }

    @Test
    fun theTileIsDeclaredForTheSystemOnly() {
        val info = application.packageManager.getServiceInfo(
            ComponentName(application, SleepTimerTileService::class.java),
            PackageManager.GET_META_DATA,
        )
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", info.permission)
        assertTrue(info.metaData.getBoolean("android.service.quicksettings.TOGGLEABLE_TILE"))
    }
}
