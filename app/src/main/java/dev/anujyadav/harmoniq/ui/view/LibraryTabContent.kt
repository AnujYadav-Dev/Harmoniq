package dev.anujyadav.harmoniq.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import dev.anujyadav.harmoniq.ui.view.globalsearch.WatchListView
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import dev.anujyadav.harmoniq.services.groove.Groove
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.services.groove.Playlist
import dev.anujyadav.harmoniq.ui.components.AlbumTile
import dev.anujyadav.harmoniq.ui.components.ArtistTile
import dev.anujyadav.harmoniq.ui.components.PlaylistActionsHost
import dev.anujyadav.harmoniq.ui.components.PlaylistDropdownMenu
import dev.anujyadav.harmoniq.ui.components.PlaylistSwipeActionsBox
import dev.anujyadav.harmoniq.ui.components.ReorderDragHandle
import dev.anujyadav.harmoniq.ui.components.SeeAllButton
import dev.anujyadav.harmoniq.ui.components.ReorderableItemDefaults
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.home.AlbumArtistsView
import dev.anujyadav.harmoniq.ui.view.home.AlbumsView
import dev.anujyadav.harmoniq.ui.view.home.ArtistsView
import dev.anujyadav.harmoniq.ui.view.home.BrowserView
import dev.anujyadav.harmoniq.ui.view.home.FoldersView
import dev.anujyadav.harmoniq.ui.view.home.GenresView
import dev.anujyadav.harmoniq.ui.view.home.PlaylistsView
import dev.anujyadav.harmoniq.ui.view.home.SongsView
import dev.anujyadav.harmoniq.ui.view.home.TreeView
import dev.anujyadav.harmoniq.ui.view.home.rememberHomePlaylists
import dev.anujyadav.harmoniq.ui.view.navigation.LibraryPill
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import dev.anujyadav.harmoniq.ui.components.ProvideSwipeScope

// Full-screen route wrappers for library categories
@Serializable
object SongsFullViewRoute
@Serializable
object AlbumsFullViewRoute
@Serializable
object ArtistsFullViewRoute
@Serializable
object AlbumArtistsFullViewRoute
@Serializable
object GenresFullViewRoute
@Serializable
object PlaylistsFullViewRoute
@Serializable
object BrowserFullViewRoute
@Serializable
object FoldersFullViewRoute
@Serializable
object TreeFullViewRoute
@Serializable
object FavoritesViewRoute

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryTabContent(context: ViewContext) {
    val savedPillOrder by context.symphony.settings.libraryPillOrder.flow.collectAsState()
    val isGlobalSearchEnabled by context.symphony.globalSearch.isEnabled.collectAsState()
    // Global Search pills (Watch List) only while it's on; their place in the order is kept
    val libraryPillOrder = remember(savedPillOrder, isGlobalSearchEnabled) {
        savedPillOrder.filter { isGlobalSearchEnabled || !it.isGlobalSearch }
    }
    var showReorderSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // pager is the single source of truth for the selected pill. syncing it two-way
    // with a separate state cancels long jumps midway (each intermediate page
    // re-targets the scroll), so pills only drive the pager and read from it.
    val pendingPill by context.libraryNavigator.pendingPill.collectAsState()
    val pagerState = rememberPagerState(
        // opening the library from elsewhere starts directly on the requested pill
        initialPage = libraryPillOrder.indexOf(pendingPill ?: LibraryPill.ForYou).coerceAtLeast(0),
        pageCount = { libraryPillOrder.size }
    )
    val selectedPill = libraryPillOrder.getOrNull(pagerState.targetPage)

    LaunchedEffect(pendingPill) {
        val pill = pendingPill ?: return@LaunchedEffect
        val page = libraryPillOrder.indexOf(pill)
        if (page != -1 && pagerState.currentPage != page) {
            pagerState.scrollToPage(page)
        }
        context.libraryNavigator.consume(pill)
    }
    val selectPill: (LibraryPill) -> Unit = { pill ->
        val page = libraryPillOrder.indexOf(pill)
        if (page != -1) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(page)
            }
        }
    }

    val isUpdating by context.symphony.groove.exposer.isUpdating.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isUpdating,
        onRefresh = { context.symphony.groove.fetch(Groove.FetchOptions(resetInMemoryCache = true)) },
        modifier = Modifier.fillMaxSize(),
        state = pullRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isUpdating,
                state = pullRefreshState,
                color = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        PillNavRow(
            context = context,
            pills = libraryPillOrder,
            selectedPill = selectedPill,
            onPillSelected = selectPill,
            onReorderClick = { showReorderSheet = true }
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val pillForPage = libraryPillOrder.getOrNull(page) ?: return@HorizontalPager
            LibraryPillContent(
                context = context,
                pill = pillForPage,
                modifier = Modifier.fillMaxSize(),
                onPillChange = selectPill
            )
        }
    }
}

    if (showReorderSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showReorderSheet = false },
            sheetState = sheetState,
            // keeps the sheet below the status bar and display cutout when it grows tall
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            ),
        ) {
            val list = remember(libraryPillOrder) { libraryPillOrder.toMutableStateList() }
            val lazyListState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                val item = list.removeAt(from.index)
                list.add(to.index, item)
                context.symphony.settings.libraryPillOrder.setValue(list.toList())
            }

            Text(
                AppStrings.ReorderTabs,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(list, key = { it.name }) { pill ->
                    ReorderableItem(reorderState, key = pill.name) { isDragging ->
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = ReorderableItemDefaults.containerColor(isDragging)
                            ),
                            headlineContent = { Text(pill.label) },
                            leadingContent = { Icon(pill.icon, null) },
                            trailingContent = { ReorderDragHandle() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillNavRow(
    context: ViewContext,
    pills: List<LibraryPill>,
    selectedPill: LibraryPill?,
    onPillSelected: (LibraryPill) -> Unit,
    onReorderClick: () -> Unit
) {
    val rowState = rememberLazyListState()
    val selectedIndex = selectedPill?.let { pills.indexOf(it) } ?: -1

    LaunchedEffect(selectedIndex) {
        if (selectedIndex != -1 && !rowState.isItemFullyVisible(selectedIndex)) {
            rowState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        state = rowState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(pills, key = { it.name }) { pill ->
            val count = pill.countProvider?.invoke(context.symphony.groove)
            FilterChip(
                selected = selectedPill == pill,
                onClick = { onPillSelected(pill) },
                label = {
                    Text(if (count != null) "${pill.label} $count" else pill.label)
                },
                leadingIcon = {
                    Icon(pill.icon, null)
                }
            )
        }
        item {
            IconButton(onClick = onReorderClick) {
                Icon(Icons.Filled.Menu, contentDescription = AppStrings.ReorderTabs)
            }
        }
    }
}

private fun LazyListState.isItemFullyVisible(index: Int): Boolean {
    val info = layoutInfo
    val item = info.visibleItemsInfo.find { it.index == index } ?: return false
    return item.offset >= info.viewportStartOffset &&
            item.offset + item.size <= info.viewportEndOffset
}

@Composable
private fun LibraryPillContent(
    context: ViewContext,
    pill: LibraryPill,
    modifier: Modifier = Modifier,
    onPillChange: (LibraryPill) -> Unit
) {
    Box(modifier = modifier) {
        ProvideSwipeScope(songScope = pill.songSwipeScope) {
            when (pill) {
                LibraryPill.ForYou -> LibraryForYouContent(context, onPillChange)
                LibraryPill.Songs -> SongsView(context)
                LibraryPill.WatchList -> WatchListView(context)
                LibraryPill.Albums -> AlbumsView(context)
                LibraryPill.Artists -> ArtistsView(context)
                LibraryPill.AlbumArtists -> AlbumArtistsView(context)
                LibraryPill.Genres -> GenresView(context)
                LibraryPill.Playlists -> PlaylistsView(context)
                LibraryPill.Browser -> BrowserView(context)
                LibraryPill.Folders -> FoldersView(context)
                LibraryPill.Tree -> TreeView(context)
            }
        }
    }
}

@Composable
private fun LibraryForYouContent(
    context: ViewContext,
    onPillChange: (LibraryPill) -> Unit
) {
    val albumIds by context.symphony.groove.album.all.collectAsState()
    val artistNames by context.symphony.groove.artist.all.collectAsState()
    val recentPlaylists = rememberHomePlaylists(context)

    val recentAlbums by remember(albumIds) {
        derivedStateOf {
            context.symphony.groove.album.get(albumIds.toList()).take(10)
        }
    }

    val recentArtists by remember(artistNames) {
        derivedStateOf {
            context.symphony.groove.artist.get(artistNames.toList()).take(10)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (recentPlaylists.isNotEmpty()) {
            Column {
                SideHeading(onClick = { onPillChange(LibraryPill.Playlists) }) {
                    Text(AppStrings.Playlists)
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    recentPlaylists.forEach { playlist ->
                        PlaylistRowItem(context, playlist)
                    }
                }
                SeeAllButton(
                    AppStrings.SeeAllPlaylists,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    onPillChange(LibraryPill.Playlists)
                }
            }
        }

        if (recentAlbums.isNotEmpty()) {
            Column {
                SideHeading(onClick = { onPillChange(LibraryPill.Albums) }) {
                    Text(AppStrings.Albums)
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    recentAlbums.forEach { album ->
                        AlbumRowItem(context, album)
                    }
                }
            }
        }

        if (recentArtists.isNotEmpty()) {
            Column {
                SideHeading(onClick = { onPillChange(LibraryPill.Artists) }) {
                    Text(AppStrings.Artists)
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    recentArtists.forEach { artist ->
                        ArtistRowItem(context, artist)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AlbumRowItem(context: ViewContext, album: dev.anujyadav.harmoniq.services.groove.Album) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    
    ListItem(
        modifier = Modifier.clickable {
            context.navController.navigate(dev.anujyadav.harmoniq.ui.view.AlbumViewRoute(album.id))
        },
        headlineContent = {
            Text(
                album.name,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                "Album" + if (album.artists.isNotEmpty()) " • ${album.artists.joinToString()}" else "",
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            AsyncImage(
                remember(album) {
                    album.createArtworkImageRequest(context.symphony).build()
                },
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showOptionsMenu = true }) {
                    Icon(Icons.Filled.MoreVert, null)
                }
                dev.anujyadav.harmoniq.ui.components.AlbumDropdownMenu(
                    context = context,
                    album = album,
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false }
                )
            }
        }
    )
}

@Composable
private fun ArtistRowItem(context: ViewContext, artist: dev.anujyadav.harmoniq.services.groove.Artist) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    
    ListItem(
        modifier = Modifier.clickable {
            context.navController.navigate(dev.anujyadav.harmoniq.ui.view.ArtistViewRoute(artist.name))
        },
        headlineContent = {
            Text(
                artist.name,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                "Artist",
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            AsyncImage(
                remember(artist) {
                    artist.createArtworkImageRequest(context.symphony).build()
                },
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp)) // Spotify artists are fully rounded
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showOptionsMenu = true }) {
                    Icon(Icons.Filled.MoreVert, null)
                }
                dev.anujyadav.harmoniq.ui.components.ArtistDropdownMenu(
                    context = context,
                    artist = artist,
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false }
                )
            }
        }
    )
}

@Composable
private fun PlaylistRowItem(context: ViewContext, playlist: Playlist) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    val updateId by context.symphony.groove.playlist.updateId.collectAsState()

    PlaylistActionsHost(context, playlist) { actions ->
        PlaylistSwipeActionsBox(context, actions) {
            ListItem(
                modifier = Modifier.clickable {
                    context.navController.navigate(PlaylistViewRoute(playlist.id))
                },
                headlineContent = {
                    Text(
                        playlist.title,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        "Playlist",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    AsyncImage(
                        remember(updateId, playlist) {
                            playlist.createArtworkImageRequest(context.symphony).build()
                        },
                        null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                },
                trailingContent = {
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Filled.MoreVert, null)
                        }
                        PlaylistDropdownMenu(
                            actions,
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun SideHeading(onClick: (() -> Unit)? = null, text: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = onClick != null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick?.invoke() }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
            text()
        }
    }
}
