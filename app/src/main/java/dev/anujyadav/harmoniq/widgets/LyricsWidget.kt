package dev.anujyadav.harmoniq.widgets

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.AppearanceSettings
import dev.anujyadav.harmoniq.services.playback.LyricsWidgetContent
import dev.anujyadav.harmoniq.services.playback.LyricsWidgetStore
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

internal data class LyricsWidgetState(
    val content: LyricsWidgetContent,
    val isEnabled: Boolean,
    val appearance: WidgetAppearance,
) {
    companion object {
        private fun isEnabled(context: Context): Flow<Boolean> = callbackFlow {
            val preferences = context.applicationContext
                .getSharedPreferences(AppearanceSettings.PREFERENCES_NAME, Context.MODE_PRIVATE)
            fun read() = preferences.getBoolean(
                AppearanceSettings.KEY_LYRICS_WIDGET,
                AppearanceSettings.DEFAULT_LYRICS_WIDGET,
            )
            trySend(read())
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()

        fun observe(context: Context) = combine(
            LyricsWidgetStore.content,
            isEnabled(context),
            WidgetAppearance.changes(context),
        ) { content, isEnabled, appearance ->
            LyricsWidgetState(content, isEnabled, appearance)
        }
    }
}

internal class LyricsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val states = LyricsWidgetState.observe(context)
        val initial = states.first()
        provideContent {
            val state by states.collectAsState(initial)
            WidgetTheme(state.appearance, context) {
                LyricsContent(state)
            }
        }
    }
}

/** Not a [PlaybackWidgetReceiver]: this widget has a publisher of its own, and nothing else does. */
internal class LyricsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LyricsWidget()

    private fun refreshPublisher(context: Context) {
        (context.applicationContext as? HarmoniqApplication)
            ?.symphonyIfCreated
            ?.lyricsWidgetPublisher
            ?.refresh()
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refreshPublisher(context)
    }

    // the last one is gone, and what it took to follow the lyrics can go too
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        refreshPublisher(context)
    }
}

private object LyricsWidgetText {
    val CurrentFontSize = 16.sp
    val NeighbourFontSize = 13.sp
    const val CURRENT_MAX_LINES = 2
    const val NEIGHBOUR_MAX_LINES = 1
}

@Composable
internal fun LyricsContent(state: LyricsWidgetState) {
    WidgetRoot {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(WidgetDimens.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val content = state.content
            when {
                !state.isEnabled -> CalmMessage(AppStrings.LyricsWidgetIsOff, null)
                content.kind == LyricsWidgetContent.Kind.Idle -> CalmMessage(AppStrings.LyricsWidgetIdle, null)
                content.kind == LyricsWidgetContent.Kind.Missing -> CalmMessage(AppStrings.NoLyrics, content.songTitle)
                content.kind == LyricsWidgetContent.Kind.Unsynced ->
                    CalmMessage(AppStrings.LyricsAreNotSynced, content.songTitle)

                else -> SyncedLines(content)
            }
        }
    }
}

@Composable
private fun SyncedLines(content: LyricsWidgetContent) {
    NeighbourLine(content.previous)
    Text(
        text = content.current ?: AppStrings.LyricsPause,
        maxLines = LyricsWidgetText.CURRENT_MAX_LINES,
        style = TextStyle(
            color = GlanceTheme.colors.primary,
            fontSize = LyricsWidgetText.CurrentFontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
    )
    NeighbourLine(content.next)
}

@Composable
private fun NeighbourLine(text: String?) {
    Text(
        // kept when empty, so the line being sung doesn't jump around
        text = text.orEmpty(),
        maxLines = LyricsWidgetText.NEIGHBOUR_MAX_LINES,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = LyricsWidgetText.NeighbourFontSize,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun CalmMessage(message: String, songTitle: String?) {
    songTitle?.let { WidgetTitle(it) }
    WidgetSubtitle(message, maxLines = LyricsWidgetText.CURRENT_MAX_LINES)
}
