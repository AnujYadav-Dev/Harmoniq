package dev.anujyadav.harmoniq.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.anujyadav.harmoniq.ui.components.GenreGrid
import dev.anujyadav.harmoniq.ui.components.LoaderScaffold
import dev.anujyadav.harmoniq.ui.helpers.ViewContext

@Composable
fun GenresView(context: ViewContext) {
    val isUpdating by context.symphony.groove.genre.isUpdating.collectAsState()
    val genreNames by context.symphony.groove.genre.all.collectAsState()
    val genresCount by context.symphony.groove.genre.count.collectAsState()

    LoaderScaffold(context, isLoading = isUpdating) {
        GenreGrid(
            context,
            genreNames = genreNames,
            genresCount = genresCount,
        )
    }
}
