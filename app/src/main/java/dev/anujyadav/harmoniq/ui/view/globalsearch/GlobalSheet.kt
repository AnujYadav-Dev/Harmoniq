package dev.anujyadav.harmoniq.ui.view.globalsearch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearchMessages
import dev.anujyadav.harmoniq.services.globalsearch.api.PlaylistDto
import dev.anujyadav.harmoniq.services.globalsearch.api.PlaylistResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerJson
import dev.anujyadav.harmoniq.ui.components.LabeledIconButton
import dev.anujyadav.harmoniq.ui.components.OnlineSwipeScope
import dev.anujyadav.harmoniq.ui.components.ProvideSwipeScope
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/** A page of the Global Search sheet. Serializable, so an open sheet survives rotation. */
@Serializable
sealed interface GlobalSheetPage {
    @Serializable
    @SerialName("playlist")
    data class Playlist(val playlist: PlaylistResultDto) : GlobalSheetPage

    @Serializable
    @SerialName("preview")
    data class Preview(val result: SearchResultDto, val sourcePlaylistId: String? = null) : GlobalSheetPage
}

/**
 * The pages open in the one Global Search sheet, e.g. Playlist → Preview. A single sheet
 * with a back stack, so two bottom sheets never stack on top of each other.
 */
class GlobalSheetNavigator(initial: List<GlobalSheetPage> = emptyList()) {
    var stack by mutableStateOf(initial)
        private set

    val current get() = stack.lastOrNull()
    val canGoBack get() = stack.size > 1

    fun open(page: GlobalSheetPage) {
        stack = listOf(page)
    }

    fun push(page: GlobalSheetPage) {
        stack = stack + page
    }

    fun back() {
        stack = stack.dropLast(1)
    }

    fun close() {
        stack = emptyList()
    }

    companion object {
        private val serializer = ListSerializer(GlobalSheetPage.serializer())

        val Saver = Saver<GlobalSheetNavigator, String>(
            save = { ServerJson.encodeToString(serializer, it.stack) },
            restore = { GlobalSheetNavigator(runCatching { ServerJson.decodeFromString(serializer, it) }.getOrDefault(emptyList())) },
        )
    }
}

@Composable
fun rememberGlobalSheetNavigator() = rememberSaveable(saver = GlobalSheetNavigator.Saver) { GlobalSheetNavigator() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSheetHost(context: ViewContext, navigator: GlobalSheetNavigator) {
    val page = navigator.current ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { navigator.close() },
        sheetState = sheetState,
        // back goes to the previous page first, then closes the sheet
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !navigator.canGoBack),
    ) {
        BackHandler(enabled = navigator.canGoBack) { navigator.back() }
        if (navigator.canGoBack) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                LabeledIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = AppStrings.Back,
                    onClick = { navigator.back() },
                )
                (navigator.stack.getOrNull(navigator.stack.size - 2) as? GlobalSheetPage.Playlist)?.let {
                    Text(
                        it.playlist.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when (page) {
            is GlobalSheetPage.Playlist -> GlobalPlaylistPage(context, page.playlist) { track ->
                navigator.push(GlobalSheetPage.Preview(track, page.playlist.playlistId))
            }

            is GlobalSheetPage.Preview -> GlobalPreviewPage(context, page.result, page.sourcePlaylistId)
        }
    }
}

/** A playlist's songs, loaded from `/playlists/{id}`; tapping one opens its preview on top. */
@Composable
fun GlobalPlaylistPage(
    context: ViewContext,
    playlist: PlaylistResultDto,
    onOpenTrack: (SearchResultDto) -> Unit,
) {
    var attempt by rememberSaveable(playlist.playlistId) { mutableStateOf(0) }
    val details by produceState<Loadable<PlaylistDto>>(Loadable.Loading, playlist.playlistId, attempt) {
        value = Loadable.Loading
        value = Loadable.of(context.symphony.globalSearch.playlist(playlist.playlistId))
    }
    val loaded = (details as? Loadable.Ready)?.value

    Column(modifier = Modifier.navigationBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp, 0.dp, 8.dp, 12.dp),
        ) {
            PlaylistCover(loaded?.bestThumbnail() ?: playlist.bestThumbnail(), modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    loaded?.title ?: playlist.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(
                    loaded?.author ?: playlist.author,
                    (loaded?.trackCount ?: playlist.itemCount)?.let { AppStrings.XSongs(it.toString()) },
                ).joinToString(SUBTITLE_SEPARATOR)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                if (loaded?.truncated == true) {
                    Text(
                        AppStrings.ShowingFirstXSongs(loaded.tracks.size.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LabeledIconButton(
                icon = Icons.Filled.Share,
                label = AppStrings.Share,
                onClick = {
                    shareLink(
                        context,
                        loaded?.title ?: playlist.title,
                        loaded?.shareUrl() ?: playlist.shareUrl(),
                    )
                },
            )
        }

        when (val state = details) {
            Loadable.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is Loadable.Failed -> PlaylistMessage(
                GlobalSearchMessages.forError(state.error),
                onRetry = { attempt++ },
                onOpen = { openLink(context, playlist.shareUrl()) },
            )

            is Loadable.Ready -> when {
                state.value.tracks.isEmpty() -> PlaylistMessage(
                    AppStrings.PlaylistHasNoSongs,
                    onOpen = { openLink(context, state.value.shareUrl()) },
                )

                else -> ProvideSwipeScope(onlineScope = OnlineSwipeScope.Playlist) {
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState) {
                        items(state.value.tracks, key = { it.videoId }) { track ->
                            OnlineTrackRow(
                                context,
                                track,
                                onClick = { onOpenTrack(track) },
                                sourcePlaylistId = playlist.playlistId,
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
private fun PlaylistMessage(text: String, onRetry: (() -> Unit)? = null, onOpen: () -> Unit) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CloudOff, null)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onRetry?.let { TextButton(onClick = it) { Text(AppStrings.Retry) } }
                OutlinedButton(onClick = onOpen) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.OpenInYoutube)
                }
            }
        }
    }
}
