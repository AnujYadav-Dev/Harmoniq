package dev.anujyadav.harmoniq.ui.view.globalsearch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anujyadav.harmoniq.services.globalsearch.watchlist.WatchListSortBy
import dev.anujyadav.harmoniq.ui.components.IconTextBody
import dev.anujyadav.harmoniq.ui.components.LocalUndoHost
import dev.anujyadav.harmoniq.ui.components.MediaSortBar
import dev.anujyadav.harmoniq.ui.components.OnlineSwipeScope
import dev.anujyadav.harmoniq.ui.components.ProvideSwipeScope
import dev.anujyadav.harmoniq.ui.components.SongRemoveAction
import dev.anujyadav.harmoniq.ui.components.drawScrollBar
import dev.anujyadav.harmoniq.ui.components.rememberUndoHost
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings
import androidx.compose.runtime.getValue

/**
 * Library → Watch List: songs and videos saved from Global Search. Stored locally, so it
 * opens offline; previews, downloads and details need the server like everywhere else.
 */
@Composable
fun WatchListView(context: ViewContext) {
    val settings = context.symphony.settings
    val watchList = context.symphony.globalSearch.watchList
    val items by watchList.items.collectAsState()
    val sortBy by settings.watchListSortBy.flow.collectAsState()
    val reverse by settings.watchListSortReverse.flow.collectAsState()
    val sorted = remember(items, sortBy, reverse) { sortBy.sort(items, reverse) }
    val undoHost = rememberUndoHost()
    val sheetNavigator = rememberGlobalSheetNavigator()

    CompositionLocalProvider(LocalUndoHost provides undoHost) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                MediaSortBar(
                    context,
                    reverse = reverse,
                    onReverseChange = { settings.watchListSortReverse.setValue(it) },
                    sort = sortBy,
                    sorts = WatchListSortBy.entries.associateWith { sort -> { _ -> sort.label } },
                    onSortChange = { settings.watchListSortBy.setValue(it) },
                    label = { Text(AppStrings.XSongs(items.size.toString())) },
                )
                when {
                    sorted.isEmpty() -> IconTextBody(
                        icon = { modifier -> Icon(Icons.Outlined.WatchLater, null, modifier = modifier) },
                        content = { Text(AppStrings.WatchListEmpty) },
                    )

                    else -> ProvideSwipeScope(onlineScope = OnlineSwipeScope.WatchList) {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .drawScrollBar(listState),
                        ) {
                            items(sorted, key = { it.videoId }) { item ->
                                val result = remember(item) { item.toSearchResult() }
                                OnlineTrackRow(
                                    context,
                                    result,
                                    onClick = {
                                        sheetNavigator.open(GlobalSheetPage.Preview(result, item.sourcePlaylistId))
                                    },
                                    removeAction = SongRemoveAction(
                                        label = AppStrings.RemoveFromWatchList,
                                        onRemove = { watchList.remove(item.videoId) },
                                        onUndo = { watchList.restore(item) },
                                    ),
                                    sourcePlaylistId = item.sourcePlaylistId,
                                )
                            }
                            item { Spacer(modifier = Modifier.height(12.dp)) }
                        }
                    }
                }
            }
            SnackbarHost(undoHost.snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
        GlobalSheetHost(context, sheetNavigator)
    }
}
