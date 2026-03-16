package dev.anujyadav.harmoniq.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.anujyadav.harmoniq.ui.components.NowPlayingBottomBar
import dev.anujyadav.harmoniq.ui.components.TopAppBarMinimalTitle
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
import dev.anujyadav.harmoniq.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenCategoryWrapper(
    context: ViewContext,
    title: String,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(onClick = { context.navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                title = {
                    TopAppBarMinimalTitle { Text(title) }
                },
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                content()
            }
        },
        bottomBar = {
            NowPlayingBottomBar(context, false)
        }
    )
}

@Composable
fun SongsFullView(context: ViewContext) {
    FullScreenCategoryWrapper(context, AppStrings.Songs) {
        SongsView(context)
    }
}

@Composable
fun AlbumsFullView(context: ViewContext) {
    FullScreenCategoryWrapper(context, AppStrings.Albums) {
        AlbumsView(context)
    }
}

@Composable
fun ArtistsFullView(context: ViewContext) {
    FullScreenCategoryWrapper(context, AppStrings.Artists) {
        ArtistsView(context)
    }
}

@Composable
fun AlbumArtistsFullView(context: ViewContext) {
    FullScreenCategoryWrapper(context, AppStrings.AlbumArtists) {
        AlbumArtistsView(context)
    }
}

@Composable
fun GenresFullView(context: ViewContext) {
    FullScreenCategoryWrapper(context, AppStrings.Genres) {
        GenresView(context)
    }
}

@Composable
fun PlaylistsFullView(context: ViewContext) {
    FullScreenCategoryWrapper(context, AppStrings.Playlists) {
        PlaylistsView(context)
    }
}

@Composable
fun BrowserFullView(context: ViewContext) {
    FullScreenCategoryWrapper(context, AppStrings.Browser) {
        BrowserView(context)
    }
}

@Composable
fun FoldersFullView(context: ViewContext) {
    FullScreenCategoryWrapper(context, AppStrings.Folders) {
        FoldersView(context)
    }
}

@Composable
fun TreeFullView(context: ViewContext) {
    FullScreenCategoryWrapper(context, AppStrings.Tree) {
        TreeView(context)
    }
}
