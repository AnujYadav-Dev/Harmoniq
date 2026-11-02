package dev.anujyadav.harmoniq.ui.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anujyadav.harmoniq.services.groove.Groove
import dev.anujyadav.harmoniq.ui.components.IconButtonPlaceholderSize
import dev.anujyadav.harmoniq.ui.components.NewPlaylistDialog
import dev.anujyadav.harmoniq.ui.components.ReorderDragHandle
import dev.anujyadav.harmoniq.ui.components.SongCard
import dev.anujyadav.harmoniq.ui.components.SongRemoveAction
import dev.anujyadav.harmoniq.ui.components.TopAppBarMinimalTitle
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.nowPlaying.NothingPlayingBody
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.CompositionLocalProvider
import dev.anujyadav.harmoniq.ui.components.AdaptiveSnackbar
import dev.anujyadav.harmoniq.ui.components.LocalSongSwipeScope
import dev.anujyadav.harmoniq.ui.components.LocalUndoHost
import dev.anujyadav.harmoniq.ui.components.SongSwipeScope
import dev.anujyadav.harmoniq.ui.components.rememberUndoHost
import dev.anujyadav.harmoniq.ui.components.LabeledIconButton

@Serializable
object QueueViewRoute

/**
 * A queue may contain the same song more than once, so the key also carries the
 * occurrence. Unlike an index based key, it stays stable while items are dragged.
 */
private data class QueueEntry(val key: String, val songId: String)

private fun List<String>.toQueueEntries(): List<QueueEntry> {
    val occurrences = mutableMapOf<String, Int>()
    return map { songId ->
        val occurrence = occurrences.getOrDefault(songId, 0)
        occurrences[songId] = occurrence + 1
        QueueEntry("$songId#$occurrence", songId)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueView(context: ViewContext) {
    val coroutineScope = rememberCoroutineScope()
    val queue by context.symphony.radio.observatory.queue.collectAsState()
    val queueIndex by context.symphony.radio.observatory.queueIndex.collectAsState()
    val selectedSongIndices = remember { mutableStateListOf<Int>() }
    val isSelecting = selectedSongIndices.isNotEmpty()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = queueIndex,
    )
    var showSaveDialog by remember { mutableStateOf(false) }
    val undoHost = rememberUndoHost()

    // dragging reorders this local copy, the queue itself is only moved once on drop
    var entries by remember(queue) { mutableStateOf(queue.toQueueEntries()) }
    var draggedEntry by remember { mutableStateOf<Pair<QueueEntry, Int>?>(null) }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        entries = entries.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    fun toggleSelection(index: Int) {
        if (selectedSongIndices.contains(index)) {
            selectedSongIndices.remove(index)
        } else {
            selectedSongIndices.add(index)
        }
    }

    LaunchedEffect(queue) {
        selectedSongIndices.removeAll { it >= queue.size }
    }

    BackHandler(isSelecting) {
        selectedSongIndices.clear()
    }

    CompositionLocalProvider(
        LocalSongSwipeScope provides SongSwipeScope.Queue,
        LocalUndoHost provides undoHost,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = {
                SnackbarHost(undoHost.snackbarHostState) {
                    AdaptiveSnackbar(it)
                }
            },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        TopAppBarMinimalTitle(
                            modifier = Modifier.padding(start = IconButtonPlaceholderSize)
                        ) {
                            Text(
                                when {
                                    isSelecting -> AppStrings.XSelected(selectedSongIndices.size.toString())
                                    else -> AppStrings.Queue
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.mediumTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    navigationIcon = {
                        LabeledIconButton(
                            icon = Icons.Filled.ExpandMore,
                            label = AppStrings.Back,
                            iconModifier = Modifier.size(32.dp),
                            onClick = {
                                context.navController.popBackStack()
                            },
                        )
                    },
                    actions = {
                        when {
                            isSelecting -> LabeledIconButton(
                                icon = Icons.Filled.Delete,
                                label = AppStrings.Delete,
                                onClick = {
                                    context.symphony.radio.queue.remove(selectedSongIndices.toList())
                                    selectedSongIndices.clear()
                                },
                            )

                            else -> LabeledIconButton(
                                icon = Icons.Default.Save,
                                label = AppStrings.SaveAsPlaylist,
                                onClick = {
                                    showSaveDialog = !showSaveDialog
                                },
                            )
                        }

                        LabeledIconButton(
                            icon = Icons.Filled.ClearAll,
                            label = AppStrings.ClearQueue,
                            onClick = {
                                context.symphony.radio.stop()
                                selectedSongIndices.clear()
                            },
                        )
                    }
                )
            },
            content = { contentPadding ->
                Box(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxSize()
                ) {
                    if (queue.isEmpty()) {
                        NothingPlayingBody(context)
                    } else {
                        LazyColumn(state = listState) {
                            itemsIndexed(
                                entries,
                                key = { _, entry -> entry.key },
                                contentType = { _, _ -> Groove.Kind.SONG },
                            ) { i, entry ->
                                ReorderableItem(reorderState, key = entry.key) {
                                    context.symphony.groove.song.get(entry.songId)?.let { song ->
                                        Box {
                                            SongCard(
                                                context,
                                                song,
                                                autoHighlight = false,
                                                highlighted = i == queueIndex,
                                                selected = selectedSongIndices.contains(i),
                                                swipeActionsEnabled = !isSelecting,
                                                removeAction = SongRemoveAction(
                                                    label = AppStrings.RemoveFromQueue,
                                                    onRemove = {
                                                        context.symphony.radio.queue.remove(i)
                                                    },
                                                    onUndo = {
                                                        context.symphony.radio.queue.add(entry.songId, i)
                                                    },
                                                ),
                                                leading = {
                                                    // indices are what selection tracks, so no reordering meanwhile
                                                    ReorderDragHandle(
                                                        enabled = !isSelecting,
                                                        onDragStarted = {
                                                            draggedEntry = entry to i
                                                        },
                                                        onDragStopped = {
                                                            draggedEntry?.let { (dragged, from) ->
                                                                val to = entries.indexOf(dragged)
                                                                if (to != -1 && to != from) {
                                                                    context.symphony.radio.queue.move(from, to)
                                                                }
                                                            }
                                                            draggedEntry = null
                                                        },
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                },
                                                thumbnailLabel = {
                                                    Text((i + 1).toString())
                                                },
                                                onLongClick = {
                                                    toggleSelection(i)
                                                },
                                                onClick = {
                                                    when {
                                                        isSelecting -> toggleSelection(i)
                                                        else -> {
                                                            context.symphony.radio.jumpTo(i)
                                                            coroutineScope.launch {
                                                                listState.animateScrollToItem(i)
                                                            }
                                                        }
                                                    }
                                                },
                                            )
                                            if (i < queueIndex) {
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .background(
                                                            MaterialTheme.colorScheme.background.copy(alpha = 0.3f)
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (showSaveDialog) {
        NewPlaylistDialog(
            context,
            initialSongIds = queue.toList(),
            onDone = { playlist ->
                showSaveDialog = false
                context.symphony.groove.playlist.add(playlist)
            },
            onDismissRequest = {
                showSaveDialog = false
            }
        )
    }
}
