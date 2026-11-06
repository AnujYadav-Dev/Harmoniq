package dev.anujyadav.harmoniq

import android.app.Application
import dev.anujyadav.harmoniq.services.Permissions
import dev.anujyadav.harmoniq.services.Settings
import dev.anujyadav.harmoniq.services.database.Database
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearch
import dev.anujyadav.harmoniq.services.groove.Groove
import dev.anujyadav.harmoniq.services.radio.Radio

class Symphony(private val application: Application) : Symphony.Hooks {
    interface Hooks {
        fun onSymphonyReady() {}
        fun onSymphonyDestroy() {}
        fun onSymphonyActivityReady() {}

        /** The app was opened again in a process that stayed alive, e.g. for the music. */
        fun onSymphonyActivityReopen() {}
        fun onSymphonyActivityPause() {}
        fun onSymphonyActivityDestroy() {}
    }

    val permission = Permissions(this)
    val settings = Settings(this)
    val database = Database(this)
    val groove = Groove(this)
    val radio = Radio(this)
    val globalSearch = GlobalSearch(this)

    val applicationContext get() = application.applicationContext

    /** Closes the open activity. Null while there is none, Symphony outlives activities. */
    var closeApp: (() -> Unit)? = null
    private var isReady = false
    private var hooks = listOf(this, radio, groove, globalSearch)

    private val isActivityVisible
        get() = (application as? HarmoniqApplication)?.visibleActivity != null

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

    /**
     * @param isFreshStart false when the activity is only being recreated, like after a
     * configuration change, which isn't the user opening the app.
     */
    internal fun emitActivityReady(isFreshStart: Boolean = true) {
        val isReopen = isReady && isFreshStart
        emitReady()
        notifyHooks { onSymphonyActivityReady() }
        if (isReopen) {
            notifyHooks { onSymphonyActivityReopen() }
        }
    }

    internal fun emitActivityPause() {
        notifyHooks { onSymphonyActivityPause() }
    }

    internal fun emitActivityDestroy() {
        notifyHooks { onSymphonyActivityDestroy() }
    }

    // waits for a moment with no music, as switching closes the app, see AppIcon.applyIfNeeded
    internal fun applyChosenAppIcon() {
        if (!radio.isPlaying) {
            settings.appIcon.value.applyIfNeeded(applicationContext)
        }
    }

    override fun onSymphonyReady() {
        // stopped while away, maybe with no activity at all: there may be no "leaving the app" after
        radio.onUpdate.subscribe { event ->
            if (event == Radio.Events.Player.Ended && !isActivityVisible) {
                applyChosenAppIcon()
            }
        }
    }

    private fun notifyHooks(fn: Hooks.() -> Unit) {
        hooks.forEach { fn.invoke(it) }
    }

}
