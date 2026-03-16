package dev.anujyadav.harmoniq.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.anujyadav.harmoniq.ui.components.AlbumArtistGrid
import dev.anujyadav.harmoniq.ui.components.LoaderScaffold
import dev.anujyadav.harmoniq.ui.helpers.ViewContext

@Composable
fun AlbumArtistsView(context: ViewContext) {
    val isUpdating by context.symphony.groove.albumArtist.isUpdating.collectAsState()
    val albumArtistNames by context.symphony.groove.albumArtist.all.collectAsState()
    val albumArtistsCount by context.symphony.groove.albumArtist.count.collectAsState()

    LoaderScaffold(context, isLoading = isUpdating) {
        AlbumArtistGrid(
            context,
            albumArtistNames = albumArtistNames,
            albumArtistsCount = albumArtistsCount,
        )
    }
}
