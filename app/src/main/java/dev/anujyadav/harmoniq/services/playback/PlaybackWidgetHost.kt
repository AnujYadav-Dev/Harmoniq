package dev.anujyadav.harmoniq.services.playback

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.anujyadav.harmoniq.widgets.AlbumArtReceiver
import dev.anujyadav.harmoniq.widgets.FullPlayerReceiver
import dev.anujyadav.harmoniq.widgets.NowPlayingBarReceiver
import dev.anujyadav.harmoniq.widgets.PlaylistShortcutsReceiver
import dev.anujyadav.harmoniq.widgets.QuickActionsReceiver
import dev.anujyadav.harmoniq.widgets.QuickActionsSmallReceiver
import dev.anujyadav.harmoniq.widgets.SuggestedReceiver
import dev.anujyadav.harmoniq.widgets.UpNextReceiver

/** The widgets on the home screen, as far as [PlaybackPublisher] needs to know. */
interface PlaybackWidgetHost {
    fun hasWidgets(): Boolean

    /** Called after a new snapshot was saved, widgets read it from there. */
    fun update()
}

class AppWidgetPlaybackHost(
    context: Context,
    private val providers: List<Class<*>> = PLAYER_AND_LIBRARY_PROVIDERS,
) : PlaybackWidgetHost {
    private val context = context.applicationContext

    // some devices have no widget support at all, this is null there
    private val manager = AppWidgetManager.getInstance(this.context)

    override fun hasWidgets() = providers.any { idsOf(it).isNotEmpty() }

    override fun update() {
        providers.forEach { provider ->
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
        // what reads the snapshot, the lyrics widget has a publisher of its own
        val PLAYER_AND_LIBRARY_PROVIDERS = listOf<Class<*>>(
            NowPlayingBarReceiver::class.java,
            FullPlayerReceiver::class.java,
            AlbumArtReceiver::class.java,
            QuickActionsReceiver::class.java,
            QuickActionsSmallReceiver::class.java,
            PlaylistShortcutsReceiver::class.java,
            UpNextReceiver::class.java,
            SuggestedReceiver::class.java,
        )
    }
}
