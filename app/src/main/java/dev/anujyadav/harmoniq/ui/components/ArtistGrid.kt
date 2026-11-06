package dev.anujyadav.harmoniq.ui.components

import dev.anujyadav.harmoniq.utils.AlphabetIndex
import androidx.compose.foundation.lazy.grid.itemsIndexed


import androidx.compose.material.icons.Icons


import androidx.compose.material.icons.filled.Person


import androidx.compose.material3.ExperimentalMaterial3Api


import androidx.compose.material3.Icon


import androidx.compose.material3.Text


import androidx.compose.runtime.Composable


import androidx.compose.runtime.collectAsState


import androidx.compose.runtime.derivedStateOf


import androidx.compose.runtime.getValue


import androidx.compose.runtime.mutableStateOf


import androidx.compose.runtime.remember


import androidx.compose.runtime.setValue


import dev.anujyadav.harmoniq.services.groove.Groove


import dev.anujyadav.harmoniq.services.groove.repositories.ArtistRepository


import dev.anujyadav.harmoniq.ui.helpers.ViewContext

import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.ui.components.MediaLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistGrid(
    context: ViewContext,
    artistName: List<String>,
    artistsCount: Int? = null,
) {
    val sortBy by context.symphony.settings.lastUsedArtistsSortBy.flow.collectAsState()
    val sortReverse by context.symphony.settings.lastUsedArtistsSortReverse.flow.collectAsState()
    val sortedArtistNames by remember(artistName, sortBy, sortReverse) {
        derivedStateOf {
            context.symphony.groove.artist.sort(artistName, sortBy, sortReverse)
        }
    }
    val horizontalGridColumns by context.symphony.settings.lastUsedArtistsHorizontalGridColumns.flow.collectAsState()
    val verticalGridColumns by context.symphony.settings.lastUsedArtistsVerticalGridColumns.flow.collectAsState()
    val layout by context.symphony.settings.lastUsedArtistsLayout.flow.collectAsState()
    val gridColumns by remember(horizontalGridColumns, verticalGridColumns) {
        derivedStateOf {
            ResponsiveGridColumns(horizontalGridColumns, verticalGridColumns)
        }
    }
    val alphabetIndex = rememberAlphabetIndex(
        context,
        items = sortedArtistNames,
        sortKey = sortBy,
        minItems = AlphabetIndex.MIN_COLLECTION_ITEMS,
        textOf = when (sortBy) {
            ArtistRepository.SortBy.ARTIST_NAME -> { name: String -> name }
            else -> null
        },
    )
    var showModifyLayoutSheet by remember { mutableStateOf(false) }

    MediaSortBarScaffold(
        mediaSortBar = {
            MediaSortBar(
                context,
                reverse = sortReverse,
                onReverseChange = {
                    context.symphony.settings.lastUsedArtistsSortReverse.setValue(it)
                },
                sort = sortBy,
                sorts = ArtistRepository.SortBy.entries
                    .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
                onSortChange = {
                    context.symphony.settings.lastUsedArtistsSortBy.setValue(it)
                },
                label = {
                    Text(AppStrings.XArtists((artistsCount ?: artistName.size).toString()))
                },
                layout = layout,
                onLayoutChange = { context.symphony.settings.lastUsedArtistsLayout.setValue(it) },
                onShowModifyLayout = {
                    showModifyLayoutSheet = true
                },
            )
        },
        content = {
            when {
                artistName.isEmpty() -> IconTextBody(
                    icon = { modifier ->
                        Icon(
                            Icons.Filled.Person,
                            null,
                            modifier = modifier,
                        )
                    },
                    content = { Text(AppStrings.DamnThisIsSoEmpty) }
                )

                else -> ResponsiveGrid(gridColumns, layout, alphabetIndex = alphabetIndex) {
                    itemsIndexed(
                        sortedArtistNames,
                        key = { i, x -> "$i-$x" },
                        contentType = { _, _ -> Groove.Kind.ARTIST }
                    ) { _, artistName ->
                        context.symphony.groove.artist.get(artistName)?.let { artist ->
                            when (layout) {
                                MediaLayout.Grid -> ArtistTile(context, artist)
                                MediaLayout.List -> ArtistListItem(context, artist)
                            }
                        }
                    }
                }
            }

            if (showModifyLayoutSheet) {
                ResponsiveGridSizeAdjustBottomSheet(
                    context,
                    columns = gridColumns,
                    onColumnsChange = {
                        context.symphony.settings.lastUsedArtistsHorizontalGridColumns.setValue(
                            it.horizontal
                        )
                        context.symphony.settings.lastUsedArtistsVerticalGridColumns.setValue(
                            it.vertical
                        )
                    },
                    onDismissRequest = {
                        showModifyLayoutSheet = false
                    }
                )
            }
        }
    )
}

private fun ArtistRepository.SortBy.label(context: ViewContext) = when (this) {
    ArtistRepository.SortBy.CUSTOM -> AppStrings.Custom
    ArtistRepository.SortBy.ARTIST_NAME -> AppStrings.Artist
    ArtistRepository.SortBy.ALBUMS_COUNT -> AppStrings.AlbumCount
    ArtistRepository.SortBy.TRACKS_COUNT -> AppStrings.TrackCount
}
