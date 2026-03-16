package dev.anujyadav.harmoniq.services.radio


import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.anujyadav.harmoniq.MainActivity
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.Logger

class RadioNotification(private val symphony: Symphony) {
    private var manager = RadioNotificationManager(symphony)

    fun start() {
        manager.prepare()
    }

    fun cancel() {
        manager.cancel()
    }

    fun update(req: RadioSession.UpdateRequest) {
        val notification = NotificationCompat.Builder(
            symphony.applicationContext,
            CHANNEL_ID
        ).run {
            setSmallIcon(R.drawable.material_icon_music_note)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setContentIntent(
                PendingIntent.getActivity(
                    symphony.applicationContext,
                    0,
                    Intent(symphony.applicationContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            setContentTitle(req.song.title)
            setContentText(req.song.artists.joinToString(", "))
            setLargeIcon(req.artworkBitmap)
            setOngoing(req.isPlaying)
            addAction(
                createAction(
                    R.drawable.material_icon_skip_previous,
                    AppStrings.Previous,
                    RadioSession.ACTION_PREVIOUS
                )
            )
            addAction(
                when {
                    req.isPlaying -> createAction(
                        R.drawable.material_icon_pause,
                        AppStrings.Play,
                        RadioSession.ACTION_PLAY_PAUSE
                    )

                    else -> createAction(
                        R.drawable.material_icon_play,
                        AppStrings.Pause,
                        RadioSession.ACTION_PLAY_PAUSE
                    )
                }
            )
            addAction(
                createAction(
                    R.drawable.material_icon_skip_next,
                    AppStrings.Next,
                    RadioSession.ACTION_NEXT
                )
            )
            addAction(
                createAction(
                    R.drawable.material_icon_stop,
                    AppStrings.Stop,
                    RadioSession.ACTION_STOP
                )
            )
            setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(symphony.radio.session.mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }
        try {
            manager.notify(notification.build())
        } catch (err: Exception) {
            Logger.error("RadioNotification", "unable to update notification", err)
        }
    }

    private fun createAction(icon: Int, title: String, action: String): NotificationCompat.Action {
        return NotificationCompat.Action
            .Builder(icon, title, createActionIntent(action))
            .build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        return PendingIntent.getBroadcast(
            symphony.applicationContext,
            0,
            Intent(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        val CHANNEL_ID = "${R.string.app_name}_media_notification"
        const val NOTIFICATION_ID = 69421
    }
}
