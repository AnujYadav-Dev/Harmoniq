package dev.anujyadav.harmoniq

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.anujyadav.harmoniq.services.Permissions
import dev.anujyadav.harmoniq.services.Settings
import dev.anujyadav.harmoniq.services.database.Database
import dev.anujyadav.harmoniq.services.groove.Groove
import dev.anujyadav.harmoniq.services.radio.Radio

class Symphony(application: Application) : AndroidViewModel(application), Symphony.Hooks {
    interface Hooks {
        fun onSymphonyReady() {}
        fun onSymphonyDestroy() {}
        fun onSymphonyActivityReady() {}
        fun onSymphonyActivityPause() {}
        fun onSymphonyActivityDestroy() {}
    }

    val permission = Permissions(this)
    val settings = Settings(this)
    val database = Database(this)
    val groove = Groove(this)
    val radio = Radio(this)

    val applicationContext get() = getApplication<Application>().applicationContext
    var closeApp: (() -> Unit)? = null
    private var isReady = false
    private var hooks = listOf(this, radio, groove)

    internal fun emitReady() {
        if (isReady) {
            return
        }
        isReady = true
        notifyHooks { onSymphonyReady() }
    }

    internal fun emitDestroy() {
        notifyHooks { onSymphonyDestroy() }
    }

    internal fun emitActivityReady() {
        emitReady()
        notifyHooks { onSymphonyActivityReady() }
    }

    internal fun emitActivityPause() {
        notifyHooks { onSymphonyActivityPause() }
    }

    internal fun emitActivityDestroy() {
        notifyHooks { onSymphonyActivityDestroy() }
    }

    override fun onSymphonyReady() {
    }

    override fun onCleared() {
        super.onCleared()
        emitDestroy()
    }

    private fun notifyHooks(fn: Hooks.() -> Unit) {
        hooks.forEach { fn.invoke(it) }
    }

}
