package dev.anujyadav.harmoniq.services.radio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.activity.result.contract.ActivityResultContract
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.groove.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RadioSession(val symphony: Symphony) {
    data class UpdateRequest(
        val song: Song,
        val artworkUri: Uri,
        val artworkBitmap: Bitmap,
        val playbackPosition: RadioPlayer.PlaybackPosition,
        val isPlaying: Boolean,
    )

    internal val mediaSession = MediaSessionCompat(symphony.applicationContext, MEDIA_SESSION_ID)
    private val artworkCacher = RadioArtworkCacher(symphony)
    private val notification = RadioNotification(symphony)

    private var actionStateUpdates: Job? = null
    private var receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                handleAction(action)
            }
        }
    }

    fun start() {
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_NEXT)
            addAction(ACTION_STOP)
            RadioNotificationAction.entries.forEach { addAction(it.actionId) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // only our own notification sends these, other apps control us through the media session
            symphony.applicationContext.registerReceiver(
                receiver,
                intentFilter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            symphony.applicationContext.registerReceiver(receiver, intentFilter)
        }
        mediaSession.setCallback(
            object : MediaSessionCompat.Callback() {
                // controllers can repeat a command, "play" while playing must not pause
                override fun onPlay() {
                    super.onPlay()
                    if (!symphony.radio.isPlaying) {
                        handleAction(ACTION_PLAY_PAUSE)
                    }
                }

                override fun onPause() {
                    super.onPause()
                    if (symphony.radio.isPlaying) {
                        handleAction(ACTION_PLAY_PAUSE)
                    }
                }

                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    handleAction(ACTION_PREVIOUS)
                }

                override fun onSkipToNext() {
                    super.onSkipToNext()
                    handleAction(ACTION_NEXT)
                }

                override fun onStop() {
                    super.onStop()
                    handleAction(ACTION_STOP)
                }

                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                    symphony.radio.seek(pos)
                }

                override fun onRewind() {
                    super.onRewind()
                    val duration = symphony.settings.seekBackDuration.value
                    symphony.radio.shorty.seekFromCurrent(-duration)
                }

                override fun onFastForward() {
                    super.onFastForward()
                    val duration = symphony.settings.seekForwardDuration.value
                    symphony.radio.shorty.seekFromCurrent(duration)
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    super.onCustomAction(action, extras)
                    action?.let { handleAction(it) }
                }

                override fun onMediaButtonEvent(intent: Intent?): Boolean {
                    val handled = super.onMediaButtonEvent(intent)
                    if (handled) {
                        return true
                    }
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent?.getParcelableExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent::class.java,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    return when (keyEvent?.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                            -> {
                            handleAction(ACTION_PREVIOUS)
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            handleAction(ACTION_NEXT)
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_CLOSE,
                        KeyEvent.KEYCODE_MEDIA_STOP,
                            -> {
                            handleAction(ACTION_STOP)
                            true
                        }

                        else -> false
                    }
                }
            }
        )
        notification.start()
        symphony.radio.onUpdate.subscribe {
            when (it) {
                Radio.Events.Player.Ended -> cancel()
                is Radio.Events.Player -> update()
                Radio.Events.QueueOption.LoopModeChanged,
                Radio.Events.QueueOption.ShuffleModeChanged,
                Radio.Events.QueueOption.SpeedChanged,
                    -> updateIfPlaying()

                else -> {}
            }
        }
        // the buttons reflect these too, drop(1) skips the values they already start with
        actionStateUpdates = symphony.groove.coroutineScope.launch {
            merge(
                symphony.groove.playlist.favorites.drop(1),
                symphony.settings.notificationActions.flow.drop(1),
                symphony.settings.seekBackDuration.flow.drop(1),
                symphony.settings.seekForwardDuration.flow.drop(1),
            ).collect { updateIfPlaying() }
        }
    }

    fun handleAction(action: String) {
        when (action) {
            ACTION_PLAY_PAUSE -> symphony.radio.shorty.playPause()
            ACTION_PREVIOUS -> symphony.radio.shorty.previous()
            ACTION_NEXT -> symphony.radio.shorty.skip()
            ACTION_STOP -> symphony.radio.stop()
            else -> RadioNotificationAction.fromActionId(action)?.perform(symphony)
        }
    }

    /** For changes the notification doesn't hear about by itself. */
    fun refreshNotification() = updateIfPlaying()

    fun cancel() {
        notification.cancel()
        mediaSession.isActive = false
    }

    fun destroy() {
        actionStateUpdates?.cancel()
        cancel()
        notification.destroy()
        symphony.applicationContext.unregisterReceiver(receiver)
    }

    fun createEqualizerActivityContract() = object : ActivityResultContract<Unit, Unit>() {
        override fun createIntent(
            context: Context,
            input: Unit,
        ) = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, symphony.applicationContext.packageName)
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, symphony.radio.audioSessionId ?: 0)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }

        override fun parseResult(
            resultCode: Int,
            intent: Intent?,
        ) {
        }
    }

    private fun update() {
        symphony.groove.coroutineScope.launch {
            updateAsync()
        }
    }

    private fun updateIfPlaying() {
        if (symphony.radio.hasPlayer) {
            update()
        }
    }

    private suspend fun updateAsync() {
        val song = symphony.radio.queue.currentSongId
            ?.let { symphony.groove.song.get(it) } ?: return
        val artworkUri = symphony.groove.song.getArtworkUri(song.id)
        val artworkBitmap = artworkCacher.getArtwork(song)
        // on the main thread, like Radio.stop: it can't then happen in between this check and
        // posting, which would bring back the notification and its service after stopping
        withContext(Dispatchers.Main) {
            // skipped or stopped while the artwork was loading, this is outdated
            if (symphony.radio.queue.currentSongId != song.id) {
                return@withContext
            }
            val playbackPosition = symphony.radio.currentPlaybackPosition
                ?: RadioPlayer.PlaybackPosition(played = 0L, total = song.duration)
            val req = UpdateRequest(
                song = song,
                artworkUri = artworkUri,
                artworkBitmap = artworkBitmap,
                playbackPosition = playbackPosition,
                isPlaying = symphony.radio.isPlaying,
            )
            updateSession(req)
            notification.update(req)
        }
    }

    private fun updateSession(req: UpdateRequest) {
        ensureEnabled()
        mediaSession.run {
            setMetadata(
                MediaMetadataCompat.Builder().run {
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, req.song.title)
                    if (req.song.artists.isNotEmpty()) {
                        putString(
                            MediaMetadataCompat.METADATA_KEY_ARTIST,
                            req.song.artists.joinToString()
                        )
                    }
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM, req.song.album)
                    req.artworkBitmap.let {
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
                    }
                    putLong(
                        MediaMetadataCompat.METADATA_KEY_DURATION,
                        req.playbackPosition.total.toLong()
                    )
                    build()
                }
            )
            setPlaybackState(
                PlaybackStateCompat.Builder().run {
                    setState(
                        when {
                            req.isPlaying -> PlaybackStateCompat.STATE_PLAYING
                            else -> PlaybackStateCompat.STATE_PAUSED
                        },
                        req.playbackPosition.played.toLong(),
                        // the system advances its seek bar by this while playing
                        symphony.radio.currentSpeed,
                    )
                    setActions(
                        PlaybackStateCompat.ACTION_PLAY
                                or PlaybackStateCompat.ACTION_PAUSE
                                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                or PlaybackStateCompat.ACTION_STOP
                                or PlaybackStateCompat.ACTION_REWIND
                                or PlaybackStateCompat.ACTION_FAST_FORWARD
                                or PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    // Android 13+ builds its media buttons from these, not the notification's
                    RadioNotification.selectedActions(symphony).forEach { action ->
                        val presentation = action.present(symphony, req.song.id)
                        addCustomAction(action.actionId, presentation.title, presentation.icon)
                    }
                    build()
                }
            )
        }
    }

    private fun ensureEnabled() {
        if (!mediaSession.isActive) {
            mediaSession.isActive = true
        }
    }

    companion object {
        val MEDIA_SESSION_ID = "${R.string.app_name}_media_session"

        val ACTION_PLAY_PAUSE = "${R.string.app_name}_play_pause"
        val ACTION_PREVIOUS = "${R.string.app_name}_previous"
        val ACTION_NEXT = "${R.string.app_name}_next"
        val ACTION_STOP = "${R.string.app_name}_stop"
    }
}
