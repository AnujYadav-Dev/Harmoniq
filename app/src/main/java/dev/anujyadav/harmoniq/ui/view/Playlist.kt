package dev.anujyadav.harmoniq.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.anujyadav.harmoniq.services.groove.Playlist
import dev.anujyadav.harmoniq.services.groove.Song
import dev.anujyadav.harmoniq.ui.components.AdaptiveSnackbar
import dev.anujyadav.harmoniq.ui.components.AnimatedNowPlayingBottomBar
import dev.anujyadav.harmoniq.ui.components.IconTextBody
import dev.anujyadav.harmoniq.ui.components.LocalUndoHost
import dev.anujyadav.harmoniq.ui.components.MediaSortBarPlacement
import dev.anujyadav.harmoniq.ui.components.PlaylistActions
import dev.anujyadav.harmoniq.ui.components.PlaylistActionsHost
import dev.anujyadav.harmoniq.ui.components.PlaylistDropdownMenu
import dev.anujyadav.harmoniq.ui.components.PlaylistHeader
import dev.anujyadav.harmoniq.ui.components.ProvideSwipeScope
import dev.anujyadav.harmoniq.ui.components.SongList
import dev.anujyadav.harmoniq.ui.components.SongListType
import dev.anujyadav.harmoniq.ui.components.SongRemoveAction
import dev.anujyadav.harmoniq.ui.components.SongSwipeScope
import dev.anujyadav.harmoniq.ui.components.TopAppBarMinimalTitle
import dev.anujyadav.harmoniq.ui.components.rememberUndoHost
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.theme.ThemeColors
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.mutate
import kotlinx.serialization.Serializable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import dev.anujyadav.harmoniq.ui.components.LabeledIconButton

@Serializable
data class PlaylistViewRoute(val playlistId: String)

// favorites is a playlist too, so it shares this screen (and its search)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistView(context: ViewContext, route: PlaylistViewRoute) {
    val allPlaylistIds by context.symphony.groove.playlist.all.collectAsState()
    val updateId by context.symphony.groove.playlist.updateId.collectAsState()
    var updateCounter by remember { mutableIntStateOf(0) }
    val playlist by remember(route.playlistId, updateId) {
        derivedStateOf { context.symphony.groove.playlist.get(route.playlistId) }
    }
    val isViable by remember(allPlaylistIds, route.playlistId) {
        derivedStateOf { allPlaylistIds.contains(route.playlistId) }
    }
    val undoHost = rememberUndoHost()

    val incrementUpdateCounter = {
        updateCounter = if (updateCounter > 25) 0 else updateCounter + 1
    }

    @Composable
    fun PlaylistScaffold(actions: PlaylistActions?, content: @Composable () -> Unit) {
        var showOptionsMenu by remember { mutableStateOf(false) }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = {
                SnackbarHost(undoHost.snackbarHostState) {
                    AdaptiveSnackbar(it)
                }
            },
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        LabeledIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = AppStrings.Back,
                            onClick = { context.navController.popBackStack() },
                        )
                    },
                    title = {
                        TopAppBarMinimalTitle {
                            Text(
                                AppStrings.Playlist
                                        + (playlist?.let { " - ${it.title}" } ?: "")
                            )
                        }
                    },
                    actions = {
                        actions?.let {
                            LabeledIconButton(
                                icon = Icons.Filled.MoreVert,
                                label = AppStrings.MoreOptions,
                                onClick = {
                                    showOptionsMenu = true
                                },
                            ) {
                                PlaylistDropdownMenu(
                                    it,
                                    expanded = showOptionsMenu,
                                    onDismissRequest = {
                                        showOptionsMenu = false
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                )
            },
            content = { contentPadding ->
                Box(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxSize()
                ) {
                    content()
                }
            },
            bottomBar = {
                AnimatedNowPlayingBottomBar(context)
            }
        )
    }

    when (val currentPlaylist = playlist?.takeIf { isViable }) {
        null -> PlaylistScaffold(actions = null) {
            UnknownPlaylist(context, route.playlistId)
        }

        else -> PlaylistActionsHost(
            context,
            currentPlaylist,
            onSongsChanged = {
                incrementUpdateCounter()
            },
            onRename = {
                incrementUpdateCounter()
            },
            onDelete = {
                context.navController.popBackStack()
            },
        ) { actions ->
            val isFavoritesPlaylist = context.symphony.groove.playlist.isFavoritesPlaylist(currentPlaylist)

            CompositionLocalProvider(LocalUndoHost provides undoHost) {
                ProvideSwipeScope(
                    songScope = when {
                        isFavoritesPlaylist -> SongSwipeScope.Favorites
                        else -> SongSwipeScope.Playlist
                    },
                ) {
                    PlaylistScaffold(actions) {
                        PlaylistSongs(context, actions, isFavoritesPlaylist)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSongs(
    context: ViewContext,
    actions: PlaylistActions,
    isFavoritesPlaylist: Boolean,
) {
    val playlist = actions.playlist
    val songIds = remember(playlist) { playlist.getSongIds(context.symphony) }
    var searchTerms by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    // search mode starts when the field is focused and lasts until cancelled,
    // so closing the keyboard keeps the results in view
    val isSearching = isSearchActive || searchTerms.isNotEmpty()
    val focusManager = LocalFocusManager.current

    BackHandler(isSearching) {
        searchTerms = ""
        isSearchActive = false
        focusManager.clearFocus()
    }
    val removeFromPlaylist = rememberRemoveFromPlaylist(context, playlist, songIds)

    SongList(
        context,
        songIds = songIds,
        type = SongListType.Playlist,
        disableHeartIcon = isFavoritesPlaylist,
        sortBarPlacement = MediaSortBarPlacement.Inline,
        filterTerms = searchTerms,
        leadingContent = {
            item {
                PlaylistHeader(
                    actions,
                    songIds = songIds,
                    searchTerms = searchTerms,
                    onSearchTermsChange = { searchTerms = it },
                    isSearching = isSearching,
                    onSearchingChange = { isSearchActive = it },
                )
            }
        },
        onPlayStarted = {
            context.symphony.groove.playlist.markPlayed(playlist.id)
        },
        trailingOptionsContent = { _, song, onDismissRequest ->
            removeFromPlaylist?.let { remove ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            Icons.Filled.DeleteForever,
                            null,
                            tint = ThemeColors.Red,
                        )
                    },
                    text = {
                        Text(AppStrings.RemoveFromPlaylist)
                    },
                    onClick = {
                        onDismissRequest()
                        remove(song)
                    }
                )
            }
        },
        songRemoveAction = { _, song ->
            removeFromPlaylist?.let { remove ->
                SongRemoveAction(
                    label = AppStrings.RemoveFromPlaylist,
                    onRemove = { remove(song) },
                    // restores the exact previous order
                    onUndo = { context.symphony.groove.playlist.update(playlist.id, songIds) },
                )
            }
        },
    )
}

// local (m3u) playlists are read-only
@Composable
private fun rememberRemoveFromPlaylist(
    context: ViewContext,
    playlist: Playlist,
    songIds: List<String>,
): ((Song) -> Unit)? = remember(playlist, songIds) {
    playlist.takeIf { it.isNotLocal }?.let {
        { song ->
            context.symphony.groove.playlist.update(
                it.id,
                songIds.mutate { remove(song.id) },
            )
        }
    }
}

@Composable
private fun UnknownPlaylist(context: ViewContext, playlistId: String) {
    IconTextBody(
        icon = { modifier ->
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                null,
                modifier = modifier
            )
        },
        content = {
            Text(AppStrings.UnknownPlaylistX(playlistId))
        }
    )
}
