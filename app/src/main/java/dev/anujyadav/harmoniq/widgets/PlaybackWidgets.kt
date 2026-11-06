package dev.anujyadav.harmoniq.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.DurationUtils
import kotlinx.coroutines.flow.first

/** Draws from [PlaybackWidgetState], and redraws as it changes for as long as Android keeps it. */
internal abstract class PlaybackWidget(private val artworkSizeDp: Int) : GlanceAppWidget() {
    protected abstract val sizes: Set<DpSize>

    override val sizeMode: SizeMode get() = SizeMode.Responsive(sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val density = context.resources.displayMetrics.density
        val states = PlaybackWidgetState.observe(
            context,
            artworkSizePx = (artworkSizeDp * density).toInt(),
            // Android keeps a layout for each size, each with the image
            imagesInWidget = sizes.size,
        )
        val initial = states.first()
        provideContent {
            val state by states.collectAsState(initial)
            WidgetTheme(state.appearance, context) {
                Content(state)
            }
        }
    }

    @Composable
    protected abstract fun Content(state: PlaybackWidgetState)
}

/**
 * Tells the publisher about a first widget, which has nothing to show until it publishes.
 * Only then: a publish updates the widgets, so doing this on every update would never end.
 */
internal abstract class PlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: android.content.Context) {
        super.onEnabled(context)
        (context.applicationContext as? HarmoniqApplication)
            ?.symphonyIfCreated
            ?.playbackPublisher
            ?.publishSoon()
    }
}

private object PlayerSizes {
    val Bar = DpSize(180.dp, 40.dp)
    val WideBar = DpSize(250.dp, 40.dp)
    val Full = DpSize(250.dp, 110.dp)
    val All = setOf(Bar, WideBar, Full)
    const val ARTWORK_DP = 96
}

private object QuickActionSizes {
    val Single = DpSize(40.dp, 40.dp)
    val Both = DpSize(110.dp, 40.dp)
    val All = setOf(Single, Both)

    // no artwork is shown, there's no need for more than the state requires
    const val ARTWORK_DP = 1
}

private object AlbumArtSizes {
    val Small = DpSize(110.dp, 110.dp)
    val All = setOf(Small)
    const val ARTWORK_DP = 320
}

/** Numbers 1 and 2: one widget that fits whichever size it's given, so both can be resized. */
internal class PlayerWidget : PlaybackWidget(PlayerSizes.ARTWORK_DP) {
    override val sizes get() = PlayerSizes.All

    @Composable
    override fun Content(state: PlaybackWidgetState) = PlayerContent(state)
}

internal class AlbumArtWidget : PlaybackWidget(AlbumArtSizes.ARTWORK_DP) {
    override val sizes get() = AlbumArtSizes.All

    @Composable
    override fun Content(state: PlaybackWidgetState) = AlbumArtContent(state)
}

internal class QuickActionsWidget : PlaybackWidget(QuickActionSizes.ARTWORK_DP) {
    override val sizes get() = QuickActionSizes.All

    @Composable
    override fun Content(state: PlaybackWidgetState) = QuickActionsContent(state)
}

internal class NowPlayingBarReceiver : PlaybackWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlayerWidget()
}

internal class FullPlayerReceiver : PlaybackWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlayerWidget()
}

internal class AlbumArtReceiver : PlaybackWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlbumArtWidget()
}

internal class QuickActionsReceiver : PlaybackWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}

internal class QuickActionsSmallReceiver : PlaybackWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}

@Composable
internal fun PlayerContent(state: PlaybackWidgetState) {
    val size = LocalSize.current
    WidgetRoot {
        when {
            size.height >= PlayerSizes.Full.height -> FullPlayer(state)
            else -> PlayerBar(state, showPrevious = size.width >= PlayerSizes.WideBar.width)
        }
    }
}

@Composable
private fun PlayerBar(state: PlaybackWidgetState, showPrevious: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(WidgetDimens.ContentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(
            state.artwork,
            state.snapshot.song?.title,
            GlanceModifier
                .size(WidgetDimens.ArtworkBarSize)
                .cornerRadius(WidgetDimens.ArtworkRadius),
        )
        Spacer(GlanceModifier.width(WidgetDimens.Spacing))
        SongText(state, GlanceModifier.defaultWeight())
        if (state.snapshot.song == null) {
            ShuffleAllControl()
        } else if (showPrevious) {
            WidgetControl(R.drawable.material_icon_skip_previous, AppStrings.Previous, PlaybackCommand.Previous)
        }
        PlayPauseButton(state)
        if (state.snapshot.song != null) {
            WidgetControl(R.drawable.material_icon_skip_next, AppStrings.Next, PlaybackCommand.Next)
        }
    }
}

@Composable
private fun FullPlayer(state: PlaybackWidgetState) {
    val snapshot = state.snapshot
    Column(modifier = GlanceModifier.fillMaxSize().padding(WidgetDimens.ContentPadding)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetArtwork(
                state.artwork,
                snapshot.song?.title,
                GlanceModifier
                    .size(WidgetDimens.ArtworkFullSize)
                    .cornerRadius(WidgetDimens.ArtworkRadius),
            )
            Spacer(GlanceModifier.width(WidgetDimens.Spacing))
            SongText(state, GlanceModifier.defaultWeight())
            if (snapshot.song != null) {
                ToggleControl(favoriteToggle(snapshot.isFavorite), PlaybackCommand.ToggleFavorite)
            }
        }
        if (snapshot.song != null) {
            ProgressBar(state)
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (snapshot.song == null) {
                ShuffleAllControl()
                PlayPauseButton(state)
            } else {
                ToggleControl(shuffleToggle(snapshot.shuffle), PlaybackCommand.ToggleShuffle)
                Spacer(GlanceModifier.defaultWeight())
                WidgetControl(R.drawable.material_icon_skip_previous, AppStrings.Previous, PlaybackCommand.Previous)
                PlayPauseButton(state)
                WidgetControl(R.drawable.material_icon_skip_next, AppStrings.Next, PlaybackCommand.Next)
                Spacer(GlanceModifier.defaultWeight())
                ToggleControl(repeatToggle(snapshot.loopMode), PlaybackCommand.CycleRepeat)
            }
        }
    }
}

@Composable
private fun SongText(state: PlaybackWidgetState, modifier: GlanceModifier) {
    val song = state.snapshot.song
    Column(modifier = modifier) {
        WidgetTitle(song?.title ?: AppStrings.NothingIsBeingPlayedRightNow)
        WidgetSubtitle(song?.artist ?: AppStrings.WidgetNothingPlayedHint)
    }
}

// where the song was at the last event, it isn't kept moving: that would mean constant updates
@Composable
private fun ProgressBar(state: PlaybackWidgetState) {
    val snapshot = state.snapshot
    val description = AppStrings.PlayedXOfY(
        DurationUtils.formatMs(snapshot.positionMs),
        DurationUtils.formatMs(snapshot.durationMs),
    )
    LinearProgressIndicator(
        progress = progressOf(snapshot.positionMs, snapshot.durationMs),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.surfaceVariant,
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(WidgetDimens.ProgressHeight)
            .semantics { contentDescription = description },
    )
}

@Composable
internal fun AlbumArtContent(state: PlaybackWidgetState) {
    WidgetRoot {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            WidgetArtwork(
                state.artwork,
                state.snapshot.song?.title ?: AppStrings.NothingIsBeingPlayedRightNow,
                GlanceModifier.fillMaxSize(),
            )
            Box(modifier = GlanceModifier.padding(WidgetDimens.ContentPadding)) {
                PlayPauseButton(state)
            }
        }
    }
}

/**
 * Shuffle all and play/pause side by side. A single cell only has room for one good target: the
 * button for what's there to do, which is play/pause unless nothing has been played yet.
 */
@Composable
internal fun QuickActionsContent(state: PlaybackWidgetState) {
    val isSingle = LocalSize.current.width < QuickActionSizes.Both.width
    WidgetRoot {
        Row(
            modifier = GlanceModifier.fillMaxHeight().fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                !isSingle -> {
                    ShuffleAllControl()
                    Spacer(GlanceModifier.width(WidgetDimens.Spacing))
                    PlayPauseButton(state)
                }

                state.snapshot.song == null -> ShuffleAllControl()
                else -> PlayPauseButton(state)
            }
        }
    }
}
