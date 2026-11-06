package dev.anujyadav.harmoniq.ui.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.anujyadav.harmoniq.ui.components.MediaFoldersDialog
import dev.anujyadav.harmoniq.ui.components.NewPlaylistDialog
import dev.anujyadav.harmoniq.ui.components.NowPlayingBottomBar
import dev.anujyadav.harmoniq.ui.helpers.ScaleTransition
import dev.anujyadav.harmoniq.ui.helpers.SlideTransition
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.navigation.MainTab
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable
import dev.anujyadav.harmoniq.ui.components.PlaylistSwipeScope
import dev.anujyadav.harmoniq.ui.components.ProvideSwipeScope
import dev.anujyadav.harmoniq.ui.components.SongSwipeScope
import dev.anujyadav.harmoniq.ui.components.LabeledIconButton

@Serializable
object MainShellViewRoute

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainShellView(context: ViewContext) {

    val currentTab by context.symphony.settings.lastMainTab.flow.collectAsState()
    val showBottomNavLabels by context.symphony.settings.showBottomNavLabels.flow.collectAsState()
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var showMediaFoldersDialog by remember { mutableStateOf(false) }
    // an event rather than state, so coming back to the tab doesn't replay the last toggle
    val searchFocusToggles = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    if (showNewPlaylistDialog) {
        NewPlaylistDialog(
            context = context,
            onDone = { playlist ->
                showNewPlaylistDialog = false
                context.symphony.groove.playlist.add(playlist)
            },
            onDismissRequest = { showNewPlaylistDialog = false }
        )
    }

    if (showMediaFoldersDialog) {
        MediaFoldersDialog(
            context = context,
            onDismissRequest = { showMediaFoldersDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                title = {
                    Crossfade(
                        label = "main-shell-title",
                        targetState = currentTab.label,
                    ) {
                        if (currentTab == MainTab.LIBRARY) {
                            Column {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    ),
                                )
                                val songIds by context.symphony.groove.song.all.collectAsState()
                                val albumIds by context.symphony.groove.album.all.collectAsState()
                                val artistNames by context.symphony.groove.artist.all.collectAsState()
                                Text(
                                    "${AppStrings.XSongs(songIds.size.toString())} • ${AppStrings.XAlbums(albumIds.size.toString())} • ${AppStrings.XArtists(artistNames.size.toString())}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        } else {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                ),
                            )
                        }
                    }
                },
                actions = {
                    LabeledIconButton(
                        icon = Icons.Filled.CreateNewFolder,
                        label = AppStrings.ManageMediaFolders,
                        onClick = { showMediaFoldersDialog = true },
                    )
                    LabeledIconButton(
                        icon = Icons.Filled.Add,
                        label = AppStrings.NewPlaylist,
                        onClick = { showNewPlaylistDialog = true },
                    )
                    LabeledIconButton(
                        icon = Icons.Filled.Settings,
                        label = AppStrings.Settings,
                        onClick = {
                            context.navController.navigate(SettingsViewRoute())
                        },
                    )
                }
            )
        },
        content = { contentPadding ->
            AnimatedContent(
                label = "main-shell-content",
                targetState = currentTab,
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
                transitionSpec = {
                    SlideTransition.slideUp.enterTransition()
                        .togetherWith(ScaleTransition.scaleDown.exitTransition())
                },
            ) { tab ->
                when (tab) {
                    MainTab.HOME -> HomeTabContent(context)
                    MainTab.SEARCH -> ProvideSwipeScope(
                        songScope = SongSwipeScope.Search,
                        playlistScope = PlaylistSwipeScope.Search,
                    ) {
                        SearchTabContent(context, focusToggles = searchFocusToggles)
                    }
                    MainTab.LIBRARY -> LibraryTabContent(context)
                }
            }
        },
        bottomBar = {
            // on Search the bar rides above the keyboard, so it can be tapped to dismiss it
            val isOnSearch = currentTab == MainTab.SEARCH
            Column(modifier = if (isOnSearch) Modifier.imePadding() else Modifier) {
                if (!isOnSearch || !WindowInsets.isImeVisible) {
                    NowPlayingBottomBar(context, false)
                }
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            icon = {
                                Crossfade(
                                    label = "main-shell-nav-icon",
                                    targetState = isSelected,
                                ) { selected ->
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        tab.label,
                                    )
                                }
                            },
                            label = if (showBottomNavLabels) {
                                {
                                    Text(
                                        tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                    )
                                }
                            } else null,
                            alwaysShowLabel = showBottomNavLabels,
                            onClick = {
                                when {
                                    !isSelected -> context.symphony.settings.lastMainTab.setValue(tab)
                                    tab == MainTab.SEARCH -> searchFocusToggles.tryEmit(Unit)
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}
