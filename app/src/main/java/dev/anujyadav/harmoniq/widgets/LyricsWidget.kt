package dev.anujyadav.harmoniq.widgets

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.AppearanceSettings
import dev.anujyadav.harmoniq.services.playback.LyricsWidgetContent
import dev.anujyadav.harmoniq.services.playback.LyricsWidgetStore
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn

internal data class LyricsWidgetState(
    val content: LyricsWidgetContent,
    val isEnabled: Boolean,
    val appearance: WidgetAppearance,
    val artwork: Bitmap? = null,
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

        fun observe(context: Context): Flow<LyricsWidgetState> {
            val density = context.resources.displayMetrics.density
            val coverLoader = CoverLoader(
                context.applicationContext,
                sizePx = (LyricsWidgetSizes.COVER_DP * density).toInt(),
                layouts = LyricsWidgetSizes.All.size,
            )
            return combine(
                LyricsWidgetStore.content,
                isEnabled(context),
                WidgetAppearance.changes(context),
            ) { content, isEnabled, appearance ->
                val cover = content.cover.takeIf { isEnabled }
                val artwork = coverLoader.load(listOfNotNull(cover))[cover]
                LyricsWidgetState(content, isEnabled, appearance, artwork)
            }.flowOn(Dispatchers.IO)
        }
    }
}

internal class LyricsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(LyricsWidgetSizes.All)

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

internal object LyricsWidgetSizes {
    val Narrow = DpSize(180.dp, 110.dp)
    val Wide = DpSize(250.dp, 110.dp)
    val Large = DpSize(320.dp, 130.dp)
    val Huge = DpSize(320.dp, 200.dp)
    val All = setOf(Narrow, Wide, Large, Huge)

    // sent again with every line, so not as large as the largest place it's shown in
    const val COVER_DP = 120
}

/** Grows with the widget, so that the cover and the lines take up what's there. */
internal data class LyricsLayout(
    val coverSize: Dp?,
    val showsSong: Boolean,
    val showsArtist: Boolean,
    val currentFontSize: TextUnit,
    val neighbourFontSize: TextUnit,
    val currentMaxLines: Int,
)

internal fun lyricsLayoutOf(size: DpSize) = when {
    size.width >= LyricsWidgetSizes.Huge.width && size.height >= LyricsWidgetSizes.Huge.height ->
        LyricsLayout(140.dp, showsSong = true, showsArtist = true, 20.sp, 16.sp, currentMaxLines = 3)

    size.width >= LyricsWidgetSizes.Large.width && size.height >= LyricsWidgetSizes.Large.height ->
        LyricsLayout(110.dp, showsSong = true, showsArtist = false, 18.sp, 14.sp, currentMaxLines = 2)

    size.width >= LyricsWidgetSizes.Wide.width ->
        LyricsLayout(72.dp, showsSong = false, showsArtist = false, 16.sp, 13.sp, currentMaxLines = 2)

    else -> LyricsLayout(null, showsSong = false, showsArtist = false, 16.sp, 13.sp, currentMaxLines = 2)
}

private const val NEIGHBOUR_MAX_LINES = 1
private const val MESSAGE_MAX_LINES = 2
private val SongToLinesGap = 4.dp

@Composable
internal fun LyricsContent(state: LyricsWidgetState) {
    val layout = lyricsLayoutOf(LocalSize.current)
    val content = state.content
    val hasSong = state.isEnabled && content.kind != LyricsWidgetContent.Kind.Idle
    val coverSize = layout.coverSize.takeIf { hasSong }
    WidgetRoot {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(WidgetDimens.ContentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            coverSize?.let { size ->
                CoverImage(
                    cover = state.artwork,
                    size = size,
                    tileColor = GlanceTheme.colors.surfaceVariant,
                    description = content.songTitle?.let { AppStrings.ArtworkOfX(it) },
                )
                Spacer(GlanceModifier.width(WidgetDimens.Spacing))
            }
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = when (coverSize) {
                    null -> Alignment.CenterHorizontally
                    else -> Alignment.Start
                },
            ) {
                val textAlign = if (coverSize == null) TextAlign.Center else TextAlign.Start
                when {
                    !state.isEnabled -> CalmMessage(AppStrings.LyricsWidgetIsOff, null)
                    content.kind == LyricsWidgetContent.Kind.Idle -> CalmMessage(AppStrings.LyricsWidgetIdle, null)
                    content.kind == LyricsWidgetContent.Kind.Missing ->
                        CalmMessage(AppStrings.NoLyrics, content.songTitle)

                    content.kind == LyricsWidgetContent.Kind.Unsynced ->
                        CalmMessage(AppStrings.LyricsAreNotSynced, content.songTitle)

                    else -> SyncedLines(content, layout, textAlign)
                }
            }
        }
    }
}

@Composable
private fun SyncedLines(content: LyricsWidgetContent, layout: LyricsLayout, textAlign: TextAlign) {
    if (layout.showsSong) {
        content.songTitle?.let { WidgetTitle(it) }
        if (layout.showsArtist) {
            content.songArtist?.let { WidgetSubtitle(it) }
        }
        Spacer(GlanceModifier.height(SongToLinesGap))
    }
    NeighbourLine(content.previous, layout, textAlign)
    Text(
        text = content.current ?: AppStrings.LyricsPause,
        maxLines = layout.currentMaxLines,
        style = TextStyle(
            // not the accent color, which can be too pale to read on a light surface
            color = GlanceTheme.colors.onSurface,
            fontSize = layout.currentFontSize,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign,
        ),
    )
    NeighbourLine(content.next, layout, textAlign)
}

@Composable
private fun NeighbourLine(text: String?, layout: LyricsLayout, textAlign: TextAlign) {
    Text(
        // kept when empty, so the line being sung doesn't jump around
        text = text.orEmpty(),
        maxLines = NEIGHBOUR_MAX_LINES,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = layout.neighbourFontSize,
            textAlign = textAlign,
        ),
    )
}

@Composable
private fun CalmMessage(message: String, songTitle: String?) {
    songTitle?.let { WidgetTitle(it) }
    WidgetSubtitle(message, maxLines = MESSAGE_MAX_LINES)
}
