package dev.anujyadav.harmoniq.services.radio

import android.app.Notification
import androidx.core.app.NotificationManagerCompat
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.utils.EventUnsubscribeFn

class RadioNotificationManager(val symphony: Symphony) {
    private var manager = NotificationManagerCompat.from(symphony.applicationContext)
    private var isServiceStarting = false
    private var unsubscribeFromService: EventUnsubscribeFn? = null

    private val service: RadioNotificationService?
        get() = RadioNotificationService.instance

    fun prepare() {
        RadioNotification.createChannel(symphony.applicationContext)
        unsubscribeFromService = RadioNotificationService.events.subscribe {
            when (it) {
                RadioNotificationService.Event.START -> onServiceStart()
                RadioNotificationService.Event.STOP -> onServiceStop()
                RadioNotificationService.Event.TASK_REMOVED -> symphony.radio.stopIfIdle()
            }
        }
    }

    fun cancel() {
        destroyNotification()
        RadioNotificationService.destroy()
    }

    fun destroy() {
        cancel()
        unsubscribeFromService?.invoke()
        unsubscribeFromService = null
    }

    fun notify(notification: Notification) {
        if (service == null) {
            createService(notification)
            return
        }
        try {
            manager.notify(RadioNotification.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // NOTE: the notification updates even without permission...
        }
    }

    private fun destroyNotification() {
        isServiceStarting = false
        RadioNotificationService.pendingNotification = null
        manager.cancel(RadioNotification.NOTIFICATION_ID)
    }

    private fun createService(notification: Notification) {
        RadioNotificationService.pendingNotification = notification
        if (isServiceStarting) {
            return
        }
        isServiceStarting = RadioNotificationService.start(symphony.applicationContext)
        if (!isServiceStarting) {
            RadioNotificationService.pendingNotification = null
        }
    }

    private fun onServiceStart() {
        isServiceStarting = false
    }

    private fun onServiceStop() {
        destroyNotification()
    }
}
