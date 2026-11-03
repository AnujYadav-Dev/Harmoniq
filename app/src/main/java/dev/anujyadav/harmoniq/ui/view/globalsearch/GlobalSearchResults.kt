package dev.anujyadav.harmoniq.ui.view.globalsearch

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.text.font.FontWeight
import dev.anujyadav.harmoniq.services.globalsearch.SearchPage
import dev.anujyadav.harmoniq.services.globalsearch.api.PlaylistResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchType
import dev.anujyadav.harmoniq.ui.components.SeeAllButton
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
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
import dev.anujyadav.harmoniq.services.globalsearch.connection.ConnectionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    context: ViewContext,
    state: GlobalSearch.SearchState,
    meta: GlobalSearch.MetaState,
    onRetry: () -> Unit,
    onOpenTrack: (SearchResultDto) -> Unit,
    onOpenPlaylist: (PlaylistResultDto) -> Unit,
    onSeeAll: (SearchType) -> Unit,
) {
    val connectionState by context.symphony.globalSearch.connectionState.collectAsState()
    val connectionMessage by context.symphony.globalSearch.connectionMessage.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        // a paused server shows its notice as the page itself, below
        if (connectionState !is ConnectionState.Disabled) connectionMessage?.let { NoticeBanner(it) }
        YoutubeStatusBanner(meta)
        Box(modifier = Modifier.fillMaxSize()) {
            if (connectionState is ConnectionState.Disabled) {
                CenteredMessage(Icons.Filled.CloudOff, connectionMessage ?: AppStrings.GlobalSearchPaused)
                return@Box
            }
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
                    val page = state.page
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .drawScrollBar(listState),
                    ) {
                        when (state.type) {
                            SearchType.All -> SearchPage.Section.entries.forEach { section ->
                                allSection(context, page, section, onOpenTrack, onOpenPlaylist, onSeeAll)
                            }

                            SearchType.Playlist -> items(page.playlists, key = { "p:${it.playlistId}" }) {
                                OnlinePlaylistRow(it) { onOpenPlaylist(it) }
                            }

                            else -> items(page.tracks, key = { "t:${it.videoId}" }) {
                                OnlineTrackRow(context, it, onClick = { onOpenTrack(it) })
                            }
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

// one section of the "All" tab: heading, its rows (or why there are none) and "See all"
private fun LazyListScope.allSection(
    context: ViewContext,
    page: SearchPage,
    section: SearchPage.Section,
    onOpenTrack: (SearchResultDto) -> Unit,
    onOpenPlaylist: (PlaylistResultDto) -> Unit,
    onSeeAll: (SearchType) -> Unit,
) {
    val count = when (section) {
        SearchPage.Section.Songs -> page.songs.size
        SearchPage.Section.Videos -> page.videos.size
        SearchPage.Section.Playlists -> page.playlists.size
    }
    item(key = "h:${section.name}") {
        Text(
            section.label(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 4.dp),
        )
    }
    when (section) {
        SearchPage.Section.Songs -> items(page.songs, key = { "s:${it.videoId}" }) {
            OnlineTrackRow(context, it, onClick = { onOpenTrack(it) })
        }

        SearchPage.Section.Videos -> items(page.videos, key = { "v:${it.videoId}" }) {
            OnlineTrackRow(context, it, onClick = { onOpenTrack(it) })
        }

        SearchPage.Section.Playlists -> items(page.playlists, key = { "p:${it.playlistId}" }) {
            OnlinePlaylistRow(it) { onOpenPlaylist(it) }
        }
    }
    item(key = "f:${section.name}") {
        when {
            page.sectionErrors.containsKey(section) -> SectionMessage(
                "${AppStrings.SectionUnavailable}: ${GlobalSearchMessages.forCode(page.sectionErrors.getValue(section))}",
                isError = true,
            )

            count == 0 -> SectionMessage(AppStrings.NoResultsFound, isError = false)
            else -> SeeAllButton(AppStrings.SeeAll, modifier = Modifier.padding(end = 4.dp)) {
                onSeeAll(section.type)
            }
        }
    }
}

@Composable
private fun SectionMessage(text: String, isError: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

fun SearchPage.Section.label() = when (this) {
    SearchPage.Section.Songs -> AppStrings.Songs
    SearchPage.Section.Videos -> AppStrings.Videos
    SearchPage.Section.Playlists -> AppStrings.Playlists
}

// a notice from the server config file, e.g. "Server maintenance tonight"
@Composable
private fun NoticeBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp, 4.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
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
    resultSubtitleDuration(result),
).joinToString(SUBTITLE_SEPARATOR)

fun resultSubtitleDuration(result: SearchResultDto) =
    result.durationText ?: result.duration?.let { DurationUtils.formatMs(TimeUnit.SECONDS.toMillis(it.toLong())) }

internal const val SUBTITLE_SEPARATOR = " · "
private const val EXPLICIT_LETTER = "E"
