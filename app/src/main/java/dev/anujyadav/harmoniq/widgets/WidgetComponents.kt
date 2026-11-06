package dev.anujyadav.harmoniq.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.anujyadav.harmoniq.HarmoniqActivity
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import dev.anujyadav.harmoniq.services.radio.RadioQueue
import dev.anujyadav.harmoniq.utils.AppStrings

/** Sizes shared by the widgets. */
internal object WidgetDimens {
    val ContentPadding = 8.dp
    val Spacing = 8.dp
    val ControlSize = 40.dp
    val ControlIconPadding = 8.dp
    val PlayButtonSize = 48.dp
    val PlayButtonIconPadding = 10.dp
    val ArtworkRadius = 12.dp
    val ArtworkBarSize = 48.dp
    val ArtworkFullSize = 72.dp
    val ProgressHeight = 4.dp
    val TitleFontSize = 14.sp
    val SubtitleFontSize = 12.sp
}

internal fun Context.openAppAction(): Action = actionStartActivity(
    Intent(this, HarmoniqActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
)

internal fun Context.commandAction(command: PlaybackCommand): Action =
    actionStartService(command.createIntent(this), isForegroundService = true)

/** Tapping anywhere that isn't a button opens the app. */
@Composable
internal fun WidgetRoot(
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .widgetBackground()
            .clickable(context.openAppAction())
            .then(modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// only Android 12+ can round any view, older versions get their corners from a shape
@Composable
private fun GlanceModifier.widgetBackground(): GlanceModifier = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> background(GlanceTheme.colors.surface)
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)

    else -> background(
        ImageProvider(R.drawable.widget_background),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.surface),
    )
}

@Composable
internal fun WidgetArtwork(
    artwork: Bitmap?,
    title: String?,
    modifier: GlanceModifier = GlanceModifier,
) {
    if (artwork == null) {
        return
    }
    Image(
        provider = ImageProvider(artwork),
        contentDescription = title?.let { AppStrings.ArtworkOfX(it) },
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
internal fun WidgetControl(
    @DrawableRes icon: Int,
    description: String,
    command: PlaybackCommand,
    tint: ColorProvider = GlanceTheme.colors.onSurface,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier
            .size(WidgetDimens.ControlSize)
            .padding(WidgetDimens.ControlIconPadding)
            .clickable(context.commandAction(command)),
    )
}

/** The widget's main button, filled with the accent color. */
@Composable
internal fun WidgetPlayButton(
    @DrawableRes icon: Int,
    description: String,
    command: PlaybackCommand,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
        modifier = modifier
            .size(WidgetDimens.PlayButtonSize)
            .circleBackground(GlanceTheme.colors.primary)
            .padding(WidgetDimens.PlayButtonIconPadding)
            .clickable(context.commandAction(command)),
    )
}

@Composable
private fun GlanceModifier.circleBackground(color: ColorProvider): GlanceModifier = background(
    ImageProvider(R.drawable.widget_circle),
    colorFilter = ColorFilter.tint(color),
)

@Composable
internal fun WidgetTitle(text: String, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text = text,
        maxLines = 1,
        modifier = modifier,
        style = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = WidgetDimens.TitleFontSize,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
internal fun WidgetSubtitle(text: String, modifier: GlanceModifier = GlanceModifier, maxLines: Int = 1) {
    Text(
        text = text,
        maxLines = maxLines,
        modifier = modifier,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = WidgetDimens.SubtitleFontSize,
        ),
    )
}

/** Play or pause when there's something to control, otherwise start on the saved queue. */
@Composable
internal fun PlayPauseButton(state: PlaybackWidgetState, modifier: GlanceModifier = GlanceModifier) {
    when {
        state.isPlaying -> WidgetPlayButton(
            R.drawable.material_icon_pause,
            AppStrings.Pause,
            PlaybackCommand.PlayPause,
            modifier,
        )

        else -> WidgetPlayButton(
            R.drawable.material_icon_play,
            AppStrings.Play,
            // never pauses, so what's stale in the snapshot can't make it do the opposite
            PlaybackCommand.Resume,
            modifier,
        )
    }
}

@Composable
internal fun ShuffleAllControl(modifier: GlanceModifier = GlanceModifier) {
    WidgetControl(
        R.drawable.material_icon_shuffle,
        AppStrings.ShuffleAll,
        PlaybackCommand.ShuffleAll,
        modifier = modifier,
    )
}

internal data class WidgetToggle(@DrawableRes val icon: Int, val description: String, val isActive: Boolean)

internal fun shuffleToggle(isOn: Boolean) = when {
    isOn -> WidgetToggle(R.drawable.material_icon_shuffle, AppStrings.ShuffleIsOn, true)
    else -> WidgetToggle(R.drawable.material_icon_shuffle_off, AppStrings.ShuffleIsOff, false)
}

internal fun repeatToggle(mode: RadioQueue.LoopMode) = when (mode) {
    RadioQueue.LoopMode.None -> WidgetToggle(R.drawable.material_icon_repeat_off, AppStrings.RepeatIsOff, false)
    RadioQueue.LoopMode.Queue -> WidgetToggle(R.drawable.material_icon_repeat, AppStrings.RepeatingQueue, true)
    RadioQueue.LoopMode.Song -> WidgetToggle(R.drawable.material_icon_repeat_one, AppStrings.RepeatingSong, true)
}

internal fun favoriteToggle(isFavorite: Boolean) = when {
    isFavorite -> WidgetToggle(R.drawable.material_icon_favorite, AppStrings.Unfavorite, true)
    else -> WidgetToggle(R.drawable.material_icon_favorite_border, AppStrings.Favorite, false)
}

@Composable
internal fun ToggleControl(toggle: WidgetToggle, command: PlaybackCommand) {
    WidgetControl(
        toggle.icon,
        toggle.description,
        command,
        tint = when {
            toggle.isActive -> GlanceTheme.colors.primary
            else -> GlanceTheme.colors.onSurfaceVariant
        },
    )
}

/** Drawn at 0 when there's no length to measure against. */
internal fun progressOf(positionMs: Long, durationMs: Long) = when {
    durationMs <= 0L -> 0f
    else -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
