package dev.anujyadav.harmoniq.services.playback

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.anujyadav.harmoniq.widgets.AlbumArtReceiver
import dev.anujyadav.harmoniq.widgets.FullPlayerReceiver
import dev.anujyadav.harmoniq.widgets.NowPlayingBarReceiver
import dev.anujyadav.harmoniq.widgets.QuickActionsReceiver
import dev.anujyadav.harmoniq.widgets.QuickActionsSmallReceiver

/** The widgets on the home screen, as far as [PlaybackPublisher] needs to know. */
interface PlaybackWidgetHost {
    fun hasWidgets(): Boolean

    /** Called after a new snapshot was saved, widgets read it from there. */
    fun update()
}

class AppWidgetPlaybackHost(context: Context) : PlaybackWidgetHost {
    private val context = context.applicationContext

    // some devices have no widget support at all, this is null there
    private val manager = AppWidgetManager.getInstance(this.context)

    override fun hasWidgets() = PROVIDERS.any { idsOf(it).isNotEmpty() }

    override fun update() {
        PROVIDERS.forEach { provider ->
            val ids = idsOf(provider)
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setClass(context, provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                )
            }
        }
    }

    private fun idsOf(provider: Class<*>) =
        manager?.getAppWidgetIds(ComponentName(context, provider)) ?: IntArray(0)

    private companion object {
        // every widget provider of the app goes here
        val PROVIDERS = listOf<Class<*>>(
            NowPlayingBarReceiver::class.java,
            FullPlayerReceiver::class.java,
            AlbumArtReceiver::class.java,
            QuickActionsReceiver::class.java,
            QuickActionsSmallReceiver::class.java,
        )
    }
}
