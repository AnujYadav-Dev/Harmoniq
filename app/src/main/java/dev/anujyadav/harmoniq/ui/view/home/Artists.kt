package dev.anujyadav.harmoniq.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.anujyadav.harmoniq.ui.components.ArtistGrid
import dev.anujyadav.harmoniq.ui.components.LoaderScaffold
import dev.anujyadav.harmoniq.ui.helpers.ViewContext

@Composable
fun ArtistsView(context: ViewContext) {
    val isUpdating by context.symphony.groove.artist.isUpdating.collectAsState()
    val artistNames by context.symphony.groove.artist.all.collectAsState()
    val artistsCount by context.symphony.groove.artist.count.collectAsState()

    LoaderScaffold(context, isLoading = isUpdating) {
        ArtistGrid(
            context,
            artistName = artistNames,
            artistsCount = artistsCount,
        )
    }
}
