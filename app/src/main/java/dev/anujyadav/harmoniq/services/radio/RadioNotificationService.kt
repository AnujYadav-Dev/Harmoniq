package dev.anujyadav.harmoniq.services.radio

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dev.anujyadav.harmoniq.utils.Eventer

class RadioNotificationService : Service() {
    enum class Event {
        START,
        STOP,
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        events.dispatch(Event.START)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        destroy(false)
    }

    companion object {
        val events = Eventer<Event>()
        var instance: RadioNotificationService? = null

        fun destroy(stop: Boolean = true) {
            instance?.let {
                instance = null
                if (stop) {
                    it.stopForeground(STOP_FOREGROUND_REMOVE)
                    it.stopSelf()
                }
                events.dispatch(Event.STOP)
            }
        }
    }
}
