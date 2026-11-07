package dev.anujyadav.harmoniq.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshot
import dev.anujyadav.harmoniq.services.playback.WidgetCover
import dev.anujyadav.harmoniq.utils.AppStrings

internal object LibraryWidgetDimens {
    val HeaderPadding = 4.dp
    val RowPadding = 6.dp
    val RowSpacing = 4.dp
    val RowRadius = 12.dp
    val CoverSize = 40.dp
    val Sizes = setOf(DpSize(180.dp, 110.dp))
    val PlaylistSizeOneColumn = DpSize(180.dp, 110.dp)
    val PlaylistSizeTwoColumns = DpSize(300.dp, 110.dp)
    val PlaylistSizes = setOf(PlaylistSizeOneColumn, PlaylistSizeTwoColumns)
}

/** Two tiles side by side, each with its cover, leave a narrow widget no room for a playlist's name. */
internal fun playlistColumns(widgetWidth: Dp) = when {
    widgetWidth >= LibraryWidgetDimens.PlaylistSizeTwoColumns.width -> 2
    else -> 1
}

private fun listCoversOf(of: (PlaybackSnapshot) -> List<WidgetCover?>) = { density: Float ->
    ListCovers(
        sizePx = (LibraryWidgetDimens.CoverSize.value * density).toInt(),
        of = { snapshot -> of(snapshot).filterNotNull() },
    )
}

/*
 * Every row's image goes out with every update of the widget, so these are kept as small as
 * they're shown. PlaybackArtwork makes sure all of them together stay within Android's limit.
 */
internal class PlaylistShortcutsWidget : PlaybackWidget(artworkSizeDp = null) {
    override val sizes get() = LibraryWidgetDimens.PlaylistSizes
    override val listCovers = listCoversOf { snapshot -> snapshot.playlists.map { it.cover } }

    @Composable
    override fun Content(state: PlaybackWidgetState) = PlaylistShortcutsContent(state)
}

internal class UpNextWidget : PlaybackWidget(artworkSizeDp = null) {
    override val sizes get() = LibraryWidgetDimens.Sizes
    override val listCovers = listCoversOf { snapshot -> snapshot.queue.map { it.cover } }

    @Composable
    override fun Content(state: PlaybackWidgetState) = UpNextContent(state)
}

internal class SuggestedWidget : PlaybackWidget(artworkSizeDp = null) {
    override val sizes get() = LibraryWidgetDimens.Sizes
    override val listCovers = listCoversOf { snapshot -> snapshot.suggestions.map { it.cover } }

    @Composable
    override fun Content(state: PlaybackWidgetState) = SuggestedContent(state)
}

internal class PlaylistShortcutsReceiver : PlaybackWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaylistShortcutsWidget()
}

internal class UpNextReceiver : PlaybackWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpNextWidget()
}

internal class SuggestedReceiver : PlaybackWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SuggestedWidget()
}

@Composable
private fun LibraryWidgetFrame(
    title: String,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    WidgetRoot {
        Column(modifier = GlanceModifier.fillMaxSize().padding(WidgetDimens.ContentPadding)) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = LibraryWidgetDimens.HeaderPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetTitle(
                    title,
                    GlanceModifier.defaultWeight().padding(vertical = LibraryWidgetDimens.HeaderPadding),
                )
                trailing()
            }
            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                content()
            }
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        WidgetSubtitle(message, maxLines = EMPTY_MESSAGE_MAX_LINES)
    }
}

private const val EMPTY_MESSAGE_MAX_LINES = 3

@Composable
private fun CommandRow(
    command: PlaybackCommand,
    sideGap: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val context = LocalContext.current
    // the gap between rows is what's outside of the padding, so taps can't land on the wrong one
    Box(
        modifier = GlanceModifier.fillMaxWidth().padding(
            start = sideGap,
            end = sideGap,
            bottom = LibraryWidgetDimens.RowSpacing,
        ),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(LibraryWidgetDimens.RowRadius)
                .padding(LibraryWidgetDimens.RowPadding)
                .clickable(context.commandAction(command)),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun RowScope.RowCover(state: PlaybackWidgetState, cover: WidgetCover?) {
    CoverImage(
        cover = cover?.let { state.covers[it] },
        size = LibraryWidgetDimens.CoverSize,
        tileColor = GlanceTheme.colors.surface,
    )
    Spacer(GlanceModifier.width(WidgetDimens.Spacing))
}

@Composable
private fun SongRow(state: PlaybackWidgetState, item: PlaybackSnapshot.Item, command: PlaybackCommand) {
    CommandRow(command) {
        RowCover(state, item.cover)
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetTitle(item.title)
            WidgetSubtitle(item.artist)
        }
    }
}

@Composable
internal fun PlaylistShortcutsContent(state: PlaybackWidgetState) {
    val snapshot = state.snapshot
    val columns = playlistColumns(LocalSize.current.width)
    LibraryWidgetFrame(AppStrings.Playlists) {
        when {
            snapshot.playlists.isNotEmpty() -> LazyVerticalGrid(
                gridCells = GridCells.Fixed(columns),
                modifier = GlanceModifier.fillMaxSize(),
            ) {
                items(snapshot.playlists, itemId = { it.id.hashCode().toLong() }) { playlist ->
                    PlaylistTile(state, playlist)
                }
            }

            // publishing needs the app to have run: a widget can't load playlists on its own
            !snapshot.isLibraryLoaded -> EmptyMessage(AppStrings.WidgetOpenAppToLoadPlaylists)
            else -> EmptyMessage(AppStrings.WidgetNoPlaylists)
        }
    }
}

@Composable
private fun PlaylistTile(state: PlaybackWidgetState, playlist: PlaybackSnapshot.PlaylistItem) {
    CommandRow(PlaybackCommand.PlayPlaylist(playlist.id), sideGap = LibraryWidgetDimens.RowSpacing / 2) {
        RowCover(state, playlist.cover)
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetTitle(playlist.title)
            WidgetSubtitle(AppStrings.XSongs(playlist.songCount.toString()))
        }
    }
}

@Composable
internal fun UpNextContent(state: PlaybackWidgetState) {
    val queue = state.snapshot.queue
    LibraryWidgetFrame(AppStrings.UpNext) {
        when {
            queue.isEmpty() -> EmptyMessage(AppStrings.WidgetNothingUpNext)
            else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                // the same song can be queued twice, its place in the queue is what's unique
                items(queue, itemId = { (it.queueIndex ?: it.path.hashCode()).toLong() }) { item ->
                    SongRow(
                        state,
                        item,
                        PlaybackCommand.JumpToQueueItem(item.queueIndex ?: NOT_IN_QUEUE, item.path),
                    )
                }
            }
        }
    }
}

// finds it by path then, see PlaybackCommand.JumpToQueueItem
private const val NOT_IN_QUEUE = -1

@Composable
internal fun SuggestedContent(state: PlaybackWidgetState) {
    val snapshot = state.snapshot
    LibraryWidgetFrame(
        AppStrings.SuggestedForYou,
        trailing = {
            WidgetControl(
                R.drawable.material_icon_refresh,
                AppStrings.Refresh,
                PlaybackCommand.RefreshSuggestions,
                tint = GlanceTheme.colors.onSurfaceVariant,
            )
        },
    ) {
        when {
            snapshot.suggestions.isNotEmpty() -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(snapshot.suggestions, itemId = { it.path.hashCode().toLong() }) { item ->
                    SongRow(state, item, PlaybackCommand.PlaySong(item.path))
                }
            }

            snapshot.isLibraryLoaded -> EmptyMessage(AppStrings.CommandEmptyLibrary)
            else -> EmptyMessage(AppStrings.WidgetTapRefreshForSuggestions)
        }
    }
}
