package dev.anujyadav.harmoniq.services.radio

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.utils.Eventer
import dev.anujyadav.harmoniq.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Holds the foreground notification that keeps the process alive for music, Radio itself owns
 * playback. It's also how commands reach Radio when the process may have no activity and no
 * Symphony yet, see [RadioCommand].
 */
class RadioNotificationService : Service() {
    enum class Event {
        START,
        STOP,
        TASK_REMOVED,
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var commandJob: Job? = null
    private var isForeground = false
    private var lastStartId = 0

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        instance = this
        val notification = pendingNotification
        pendingNotification = null
        val wasForeground = isForeground
        // Android crashes the app if this doesn't happen soon after startForegroundService, so
        // it comes before anything else, even when there's nothing to show yet
        if (!wasForeground && !enterForeground(notification ?: RadioNotification.createLoading(this))) {
            destroy()
            return START_NOT_STICKY
        }
        events.dispatch(Event.START)
        val command = RadioCommand.fromAction(intent?.action)
        when {
            command != null -> run(command)
            // stopped before this got to start, nothing wants the service anymore
            notification == null && !wasForeground -> destroy()
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(notification: Notification) = try {
        ServiceCompat.startForeground(
            this,
            RadioNotification.NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
        isForeground = true
        true
    } catch (err: Exception) {
        Logger.error(TAG, "unable to enter foreground", err)
        false
    }

    private fun run(command: RadioCommand) {
        commandJob?.cancel()
        commandJob = scope.launch {
            val failure = try {
                // the first command of a process creates Symphony here, safely in foreground
                val symphony = (application as HarmoniqApplication).symphony
                RadioCommandRunner(symphony).run(command).failureMessage
            } catch (err: CancellationException) {
                throw err
            } catch (err: Exception) {
                Logger.error(TAG, "command $command failed", err)
                RadioCommandRunner.Outcome.PlaybackFailed.failureMessage
            }
            failure?.let {
                RadioNotification.showStatus(this@RadioNotificationService, it)
                destroy()
            }
        }
    }

    // only while nothing's on its way to playing: a command was just asked for by the user
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (commandJob?.isActive != true) {
            events.dispatch(Event.TASK_REMOVED)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        destroy(false)
    }

    companion object {
        private const val TAG = "RadioNotificationService"

        val events = Eventer<Event>()
        var instance: RadioNotificationService? = null

        /** Shown as soon as the service starts, in place of the loading notification. */
        internal var pendingNotification: Notification? = null

        /**
         * Android can refuse a foreground start from the background, a refusal must not
         * crash the app.
         *
         * @return false if refused, nothing happens then.
         */
        fun start(context: Context, command: RadioCommand? = null) = try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RadioNotificationService::class.java).setAction(command?.action),
            )
            true
        } catch (err: Exception) {
            Logger.warn(TAG, "foreground start refused", err)
            false
        }

        fun destroy(stop: Boolean = true) {
            instance?.let {
                instance = null
                if (stop) {
                    it.isForeground = false
                    it.stopForeground(STOP_FOREGROUND_REMOVE)
                    // a start that's already on its way keeps the service, Android would crash
                    // the app otherwise for that start never reaching the foreground
                    it.stopSelf(it.lastStartId)
                }
                events.dispatch(Event.STOP)
            }
        }
    }
}
