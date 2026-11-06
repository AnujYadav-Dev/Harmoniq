package dev.anujyadav.harmoniq

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Looper

/**
 * Owns the one [Symphony] of the process, so playback no longer depends on an activity.
 *
 * The process also starts for download work and, later, widget updates, none of which need the
 * player: [onCreate] has to stay light, the database and the library scan only start once
 * something asks for [symphony].
 */
class HarmoniqApplication : Application() {
    @Volatile
    var symphonyIfCreated: Symphony? = null
        private set

    val symphony: Symphony
        get() {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "Symphony must be created and fetched on the main thread"
            }
            return symphonyIfCreated ?: Symphony(this).also { symphonyIfCreated = it }
        }

    /** The activity on screen, if any. Only held while it's started, so it can't leak. */
    @Volatile
    var visibleActivity: Activity? = null
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(visibilityTracker)
        Thread.setDefaultUncaughtExceptionHandler(
            AppCrashHandler(this, Thread.getDefaultUncaughtExceptionHandler())
        )
    }

    /** The next use of [symphony] starts over, like opening the app does after a crash. */
    internal fun releaseSymphony() {
        val released = symphonyIfCreated ?: return
        symphonyIfCreated = null
        released.emitDestroy()
    }

    private val visibilityTracker = object : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            visibleActivity = activity
        }

        // the next activity starts before the previous one stops, which must not clear it
        override fun onActivityStopped(activity: Activity) {
            if (visibleActivity === activity) {
                visibleActivity = null
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
