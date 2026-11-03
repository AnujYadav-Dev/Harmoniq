package dev.anujyadav.harmoniq.ui.view.globalsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.services.globalsearch.OnlineDownloadState
import dev.anujyadav.harmoniq.services.globalsearch.api.PlaylistResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.ui.components.LabeledIconButton
import dev.anujyadav.harmoniq.ui.components.OnlineTrackActions
import dev.anujyadav.harmoniq.ui.components.SongRemoveAction
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/**
 * One Global Search song or video, wherever it's shown (search, playlist drawer, Watch List):
 * thumbnail with the download badge, title, subtitle, three-dot menu and swipe actions.
 */
@Composable
fun OnlineTrackRow(
    context: ViewContext,
    result: SearchResultDto,
    onClick: () -> Unit,
    removeAction: SongRemoveAction? = null,
    sourcePlaylistId: String? = null,
) {
    // swiping "preview" behaves exactly like tapping the row
    OnlineTrackActionsHost(
        context,
        result,
        onPreview = onClick,
        removeAction = removeAction,
        sourcePlaylistId = sourcePlaylistId,
    ) { actions ->
        OnlineSwipeActionsBox(context, actions) {
            OnlineTrackRowContent(actions, onClick)
        }
    }
}

@Composable
private fun OnlineTrackRowContent(actions: OnlineTrackActions, onClick: () -> Unit) {
    val result = actions.result
    val isDownloaded = actions.downloadState == OnlineDownloadState.Done
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDownloaded) MaterialTheme.colorScheme.primary.copy(alpha = DOWNLOADED_ROW_TINT_ALPHA)
                else Color.Transparent
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp, 12.dp, 4.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = result.bestThumbnail(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(45.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            DownloadStatusBadge(
                actions.downloadState,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(BADGE_OFFSET, BADGE_OFFSET),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (result.isExplicit == true) {
                    ExplicitBadge()
                }
                Text(
                    resultSubtitle(result),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        var showOptionsMenu by remember { mutableStateOf(false) }
        LabeledIconButton(
            icon = Icons.Filled.MoreVert,
            label = AppStrings.MoreOptions,
            iconModifier = Modifier.size(24.dp),
            onClick = { showOptionsMenu = !showOptionsMenu },
        ) {
            OnlineTrackDropdownMenu(
                actions,
                expanded = showOptionsMenu,
                onDismissRequest = { showOptionsMenu = false },
            )
        }
    }
}

/** ✓ downloaded, a small progress ring while downloading, ! when the last attempt failed. */
@Composable
fun DownloadStatusBadge(state: OnlineDownloadState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        OnlineDownloadState.None -> {}
        OnlineDownloadState.Done -> BadgeIcon(Icons.Filled.Check, colors.primary, colors.onPrimary, AppStrings.Downloaded, modifier)
        OnlineDownloadState.Failed -> BadgeIcon(
            Icons.Filled.PriorityHigh,
            colors.error,
            colors.onError,
            AppStrings.DownloadFailedTapToRetry,
            modifier,
        )

        OnlineDownloadState.Queued, is OnlineDownloadState.Running -> Box(
            modifier = modifier
                .size(BADGE_SIZE)
                .background(colors.surface, CircleShape)
                .padding(BADGE_PADDING)
                .semantics { contentDescription = AppStrings.Downloading },
        ) {
            val fraction = (state as? OnlineDownloadState.Running)?.fraction
            when (fraction) {
                null -> CircularProgressIndicator(strokeWidth = BADGE_STROKE)
                else -> CircularProgressIndicator(progress = { fraction }, strokeWidth = BADGE_STROKE)
            }
        }
    }
}

@Composable
private fun BadgeIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    tint: Color,
    description: String,
    modifier: Modifier,
) {
    Icon(
        icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier
            .size(BADGE_SIZE)
            .background(background, CircleShape)
            .padding(BADGE_PADDING),
    )
}

/** A playlist found by Global Search; tapping it opens the playlist drawer. */
@Composable
fun OnlinePlaylistRow(playlist: PlaylistResultDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp, 12.dp, 16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistCover(playlist.bestThumbnail())
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                playlist.author,
                playlist.itemCount?.let { AppStrings.XSongs(it.toString()) },
            ).joinToString(SUBTITLE_SEPARATOR)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.Filled.ChevronRight, null)
    }
}

// a cover with a second card peeking out behind it, so playlists read differently from songs
@Composable
fun PlaylistCover(url: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(45.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

private val BADGE_SIZE = 18.dp
private val BADGE_PADDING = 2.dp
private val BADGE_STROKE = 2.dp
private val BADGE_OFFSET = 4.dp
private const val DOWNLOADED_ROW_TINT_ALPHA = 0.06f
