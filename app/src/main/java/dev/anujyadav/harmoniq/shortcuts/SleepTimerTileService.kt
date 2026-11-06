package dev.anujyadav.harmoniq.shortcuts

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import dev.anujyadav.harmoniq.services.playback.SleepTimerPresets
import dev.anujyadav.harmoniq.services.radio.Radio
import dev.anujyadav.harmoniq.services.radio.RadioNotificationService
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.EventUnsubscribeFn
import kotlin.math.ceil
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What the tile shows and does, with nothing of Android in it. */
internal data class SleepTimerTileState(val tileState: Int, val subtitle: String) {
    companion object {
        fun of(isPlaying: Boolean, endsAtMs: Long?, nowMs: Long) = when {
            endsAtMs != null -> SleepTimerTileState(
                Tile.STATE_ACTIVE,
                AppStrings.XMinLeft(minutesLeft(endsAtMs, nowMs).toString()),
            )

            isPlaying -> SleepTimerTileState(Tile.STATE_INACTIVE, AppStrings.SleepTimerOff)
            else -> SleepTimerTileState(Tile.STATE_UNAVAILABLE, AppStrings.NothingPlaying)
        }

        // rounded up, so it says "1" until it really ends
        private fun minutesLeft(endsAtMs: Long, nowMs: Long) =
            ceil((endsAtMs - nowMs).coerceAtLeast(0L).toDouble() / 1.minutes.inWholeMilliseconds).toLong()

        /** A tap: clears a running timer, or starts one like the last one used. */
        fun commandForTap(isTimerRunning: Boolean, lastMinutes: Int): PlaybackCommand = when {
            isTimerRunning -> PlaybackCommand.ClearSleepTimer
            else -> PlaybackCommand.SetSleepTimer(
                durationMs = lastMinutes.coerceAtLeast(1).minutes.inWholeMilliseconds,
                quitOnEnd = false,
            )
        }
    }
}

/**
 * Sleep timer in Quick Settings. Never creates Symphony: with no process around nothing is
 * playing, which is all the tile needs to know.
 */
class SleepTimerTileService : TileService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { refreshTile() }
    private var unsubscribeFromRadio: EventUnsubscribeFn? = null

    private val radio get() = (application as HarmoniqApplication).symphonyIfCreated?.radio

    override fun onStartListening() {
        super.onStartListening()
        unsubscribeFromRadio = radio?.onUpdate?.subscribe { event ->
            when (event) {
                is Radio.Events.Player,
                Radio.Events.QueueOption.SleepTimerChanged,
                    -> mainHandler.post(refresh)

                else -> {}
            }
        }
        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        unsubscribeFromRadio?.invoke()
        unsubscribeFromRadio = null
        mainHandler.removeCallbacks(refresh)
    }

    override fun onClick() {
        super.onClick()
        val radio = radio ?: return
        val symphony = (application as HarmoniqApplication).symphonyIfCreated ?: return
        RadioNotificationService.start(
            this,
            SleepTimerTileState.commandForTap(
                isTimerRunning = radio.sleepTimer != null,
                lastMinutes = symphony.settings.lastSleepTimerMinutes.value,
            ),
        )
    }

    private fun refreshTile() {
        mainHandler.removeCallbacks(refresh)
        val tile = qsTile ?: return
        val radio = radio
        val endsAt = radio?.sleepTimer?.endsAt
        val state = SleepTimerTileState.of(radio?.isPlaying == true, endsAt, System.currentTimeMillis())
        tile.state = state.tileState
        tile.contentDescription = "${AppStrings.SleepTimer}, ${state.subtitle}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = state.subtitle
        }
        tile.updateTile()
        // only ever while the shade is open: Android stops listening as it closes
        if (endsAt != null) {
            mainHandler.postDelayed(refresh, REFRESH_INTERVAL.inWholeMilliseconds)
        }
    }

    private companion object {
        val REFRESH_INTERVAL = 30.seconds
    }
}
