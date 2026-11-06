package dev.anujyadav.harmoniq.services.radio


import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.anujyadav.harmoniq.HarmoniqActivity
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

    fun destroy() {
        manager.destroy()
    }

    fun update(req: RadioSession.UpdateRequest) {
        val notification = NotificationCompat.Builder(
            symphony.applicationContext,
            CHANNEL_ID
        ).run {
            setSmallIcon(R.drawable.material_icon_music_note)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setContentIntent(openAppIntent(symphony.applicationContext))
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
                        AppStrings.Pause,
                        RadioSession.ACTION_PLAY_PAUSE
                    )

                    else -> createAction(
                        R.drawable.material_icon_play,
                        AppStrings.Play,
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
            selectedActions(symphony).forEach { action ->
                val presentation = action.present(symphony, req.song.id)
                addAction(createAction(presentation.icon, presentation.title, action.actionId))
            }
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
            Intent(action).setPackage(symphony.applicationContext.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        val CHANNEL_ID = "${R.string.app_name}_media_notification"
        const val NOTIFICATION_ID = 69421

        // not the media one: that goes away with the service, which is stopped after showing this
        const val STATUS_NOTIFICATION_ID = 69422

        fun selectedActions(symphony: Symphony) = symphony.settings.notificationActions.value
            .take(RadioNotificationAction.MAX_SELECTED)

        fun createChannel(context: Context) {
            NotificationManagerCompat.from(context).createNotificationChannel(
                NotificationChannelCompat.Builder(
                    CHANNEL_ID,
                    NotificationManagerCompat.IMPORTANCE_LOW,
                ).run {
                    setName(context.getString(R.string.app_name))
                    setLightsEnabled(false)
                    setVibrationEnabled(false)
                    setShowBadge(false)
                    build()
                }
            )
        }

        /** What the service shows while there's nothing to play yet. Needs no Symphony. */
        fun createLoading(context: Context): Notification {
            createChannel(context)
            return baseBuilder(context, AppStrings.Loading).setOngoing(true).build()
        }

        /** Tells why a command played nothing, stays after the service is gone. */
        fun showStatus(context: Context, message: String) {
            createChannel(context)
            try {
                NotificationManagerCompat.from(context).notify(
                    STATUS_NOTIFICATION_ID,
                    baseBuilder(context, message)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                        .setAutoCancel(true)
                        .build(),
                )
            } catch (err: SecurityException) {
                // notifications are denied, the log is all that's left
                Logger.warn("RadioNotification", message, err)
            }
        }

        private fun baseBuilder(context: Context, text: String) =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.material_icon_music_note)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openAppIntent(context))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setSilent(true)

        private fun openAppIntent(context: Context) = PendingIntent.getActivity(
            context,
            0,
            Intent(context, HarmoniqActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
