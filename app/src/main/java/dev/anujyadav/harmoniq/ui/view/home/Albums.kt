package dev.anujyadav.harmoniq.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.anujyadav.harmoniq.ui.components.AlbumGrid
import dev.anujyadav.harmoniq.ui.components.LoaderScaffold
import dev.anujyadav.harmoniq.ui.helpers.ViewContext

@Composable
fun AlbumsView(context: ViewContext) {
    val isUpdating by context.symphony.groove.album.isUpdating.collectAsState()
    val albumIds by context.symphony.groove.album.all.collectAsState()
    val albumsCount by context.symphony.groove.album.count.collectAsState()

    LoaderScaffold(context, isLoading = isUpdating) {
        AlbumGrid(
            context,
            albumIds = albumIds,
            albumsCount = albumsCount,
        )
    }
}
