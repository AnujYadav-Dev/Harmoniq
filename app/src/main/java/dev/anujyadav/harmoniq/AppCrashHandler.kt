package dev.anujyadav.harmoniq

import dev.anujyadav.harmoniq.utils.Logger

/**
 * Shows the error screen when a crash happens while the app is in use. With nothing on screen
 * (music playing in the background, a download) there's no one to show it to, so the system
 * handles the crash as it would for any other app.
 */
internal class AppCrashHandler(
    private val application: HarmoniqApplication,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        Logger.error(TAG, "uncaught exception", error)
        val activity = application.visibleActivity
        // the error screen failing as well would otherwise open itself forever
        if (activity == null || activity is ErrorActivity) {
            previous?.uncaughtException(thread, error)
            return
        }
        // leaving the app through the error screen has always ended playback
        runCatching { application.releaseSymphony() }
            .onFailure { Logger.warn(TAG, "unable to release symphony", it) }
        ErrorActivity.start(activity, error)
        when (activity) {
            is HarmoniqActivity -> activity.onUncaughtCrash()
            else -> activity.finish()
        }
    }

    private companion object {
        const val TAG = "AppCrashHandler"
    }
}
