package dev.anujyadav.harmoniq.ui.view.globalsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearch
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearchMessages
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.YoutubeStatus
import dev.anujyadav.harmoniq.ui.components.IconTextBody
import dev.anujyadav.harmoniq.ui.components.drawScrollBar
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.DurationUtils
import java.util.concurrent.TimeUnit

@Composable
fun GlobalSearchResults(
    state: GlobalSearch.SearchState,
    meta: GlobalSearch.MetaState,
    downloadedVideoIds: Set<String>,
    onRetry: () -> Unit,
    onOpen: (SearchResultDto) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        YoutubeStatusBanner(meta)
        Box(modifier = Modifier.fillMaxSize()) {
            when (state) {
                GlobalSearch.SearchState.Idle -> CenteredMessage(Icons.Filled.Public, AppStrings.SearchSongsToDownload)
                is GlobalSearch.SearchState.Loading -> CenteredMessage(Icons.Filled.Search, AppStrings.SearchingServer)
                is GlobalSearch.SearchState.Empty -> CenteredMessage(Icons.Filled.PriorityHigh, AppStrings.NoResultsFound)
                is GlobalSearch.SearchState.Failed -> CenteredMessage(
                    Icons.Filled.CloudOff,
                    GlobalSearchMessages.forError(state.error),
                    action = { TextButton(onClick = onRetry) { Text(AppStrings.Retry) } },
                )

                is GlobalSearch.SearchState.Results -> {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .drawScrollBar(listState),
                    ) {
                        items(state.results, key = { it.videoId }) { result ->
                            GlobalSearchResultRow(
                                result = result,
                                isDownloaded = downloadedVideoIds.contains(result.videoId),
                                onClick = { onOpen(result) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun YoutubeStatusBanner(meta: GlobalSearch.MetaState) {
    val status = (meta as? GlobalSearch.MetaState.Ready)?.meta?.youtubeStatus ?: return
    val text = when (status) {
        YoutubeStatus.Blocked -> AppStrings.YoutubeBlockedBanner
        YoutubeStatus.Degraded -> AppStrings.YoutubeDegradedBanner
        else -> return
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp, 4.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    text: String,
    action: (@Composable () -> Unit)? = null,
) {
    IconTextBody(
        icon = { modifier -> Icon(icon, null, modifier = modifier) },
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text)
                action?.invoke()
            }
        },
    )
}

@Composable
fun GlobalSearchResultRow(result: SearchResultDto, isDownloaded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp, 12.dp, 16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = result.bestThumbnail(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(45.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
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
        if (isDownloaded) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Filled.DownloadDone,
                contentDescription = AppStrings.Downloaded,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun ExplicitBadge() {
    Box(
        modifier = Modifier
            .semantics { contentDescription = AppStrings.Explicit }
            .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp))
            .padding(horizontal = 3.dp),
    ) {
        Text(
            EXPLICIT_LETTER,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.surface,
        )
    }
}

// "Daft Punk, Pharrell Williams · Random Access Memories · 6:10"
fun resultSubtitle(result: SearchResultDto) = listOfNotNull(
    result.displayArtists.takeIf { it.isNotEmpty() }?.joinToString(),
    result.album?.name,
    result.durationText ?: result.duration?.let { DurationUtils.formatMs(TimeUnit.SECONDS.toMillis(it.toLong())) },
).joinToString(SUBTITLE_SEPARATOR)

private const val SUBTITLE_SEPARATOR = " · "
private const val EXPLICIT_LETTER = "E"
