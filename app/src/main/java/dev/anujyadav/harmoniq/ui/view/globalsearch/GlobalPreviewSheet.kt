package dev.anujyadav.harmoniq.ui.view.globalsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.services.globalsearch.api.PreviewDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SongMetadataDto
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.collectAsState
import dev.anujyadav.harmoniq.ui.components.LabeledIconButton
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/**
 * Preview of one song or video: what the server will tag the file with (`/track`) and the
 * official YouTube player (`/preview`), the actions menu and the download controls.
 * Shown as a page of [GlobalSheetHost], so it can sit on top of a playlist.
 */
@Composable
fun GlobalPreviewPage(
    context: ViewContext,
    result: SearchResultDto,
    sourcePlaylistId: String? = null,
) {
    val globalSearch = context.symphony.globalSearch
    val autoPlay by context.symphony.settings.globalSearchAutoplayPreviews.flow.collectAsState()
    val track by produceState<Loadable<SongMetadataDto>>(Loadable.Loading, result.videoId) {
        value = Loadable.of(globalSearch.track(result.videoId))
    }
    val preview by produceState<Loadable<PreviewDto>>(Loadable.Loading, result.videoId) {
        value = Loadable.of(globalSearch.preview(result.videoId))
    }
    var playerFailed by remember(result.videoId) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        val readyPreview = (preview as? Loadable.Ready)?.value
        val canEmbed = readyPreview?.embeddable == true && !readyPreview.ageRestricted && !playerFailed
        Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
            when {
                // only created once /preview says it can embed, so it never plays before that
                canEmbed -> YouTubePreviewPlayer(
                    videoId = result.videoId,
                    autoPlay = autoPlay,
                    // one audio source at a time
                    onPlaying = { context.symphony.radio.pause() },
                    onError = { playerFailed = true },
                )

                else -> AsyncImage(
                    model = (track as? Loadable.Ready)?.value?.artworkUrl ?: result.bestThumbnail(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(COVER_ASPECT_RATIO),
                )
            }
        }
        if (preview is Loadable.Loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (!canEmbed && preview !is Loadable.Loading) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    AppStrings.PreviewNotAvailable,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { openLink(context, readyPreview?.watchUrl ?: result.youtubeUrl()) }
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(modifier = Modifier.padding(start = 6.dp))
                    Text(AppStrings.OpenInYoutube)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        val metadata = (track as? Loadable.Ready)?.value
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    metadata?.title ?: result.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (metadata?.artists?.takeIf { it.isNotEmpty() } ?: result.displayArtists).joinToString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // every action except Preview, which is this page
            OnlineTrackActionsHost(context, result, sourcePlaylistId = sourcePlaylistId) { actions ->
                var showOptionsMenu by remember { mutableStateOf(false) }
                LabeledIconButton(
                    icon = Icons.Filled.MoreVert,
                    label = AppStrings.MoreOptions,
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
        Spacer(modifier = Modifier.height(8.dp))
        when (val state = track) {
            Loadable.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth(TRACK_LOADING_WIDTH))
            is Loadable.Failed -> result.album?.name?.let { DetailLine(it) }
            is Loadable.Ready -> TrackDetails(state.value, result)
        }

        Spacer(modifier = Modifier.height(20.dp))
        GlobalDownloadControls(context, result)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun TrackDetails(track: SongMetadataDto, result: SearchResultDto) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val albumLine = listOfNotNull(
            track.album ?: result.album?.name,
            track.year?.toString() ?: track.date?.take(YEAR_LENGTH),
        ).joinToString(DETAIL_SEPARATOR)
        if (albumLine.isNotEmpty()) DetailLine(albumLine)

        val positionLine = listOfNotNull(
            track.trackNumber?.let { number ->
                track.trackTotal?.let { AppStrings.TrackXOfY(number.toString(), it.toString()) }
                    ?: AppStrings.TrackX(number.toString())
            },
            track.discNumber?.takeIf { (track.discTotal ?: 1) > 1 }?.let { AppStrings.DiscX(it.toString()) },
        ).joinToString(DETAIL_SEPARATOR)
        if (positionLine.isNotEmpty()) DetailLine(positionLine)

        if (track.genres.isNotEmpty()) DetailLine(track.genres.joinToString())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (track.explicit == true || result.isExplicit == true) ExplicitBadge()
            if (track.lyrics?.available == true) DetailLine(AppStrings.LyricsAvailable)
        }
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

private const val DETAIL_SEPARATOR = " · "
private const val YEAR_LENGTH = 4
private const val COVER_ASPECT_RATIO = 16f / 9f
private const val TRACK_LOADING_WIDTH = 0.5f
