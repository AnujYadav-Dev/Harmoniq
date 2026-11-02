package dev.anujyadav.harmoniq.ui.components

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


import dev.anujyadav.harmoniq.services.groove.repositories.AlbumArtistRepository


import dev.anujyadav.harmoniq.ui.helpers.ViewContext

import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.ui.components.MediaLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumArtistGrid(
    context: ViewContext,
    albumArtistNames: List<String>,
    albumArtistsCount: Int? = null,
) {
    val sortBy by context.symphony.settings.lastUsedAlbumArtistsSortBy.flow.collectAsState()
    val sortReverse by context.symphony.settings.lastUsedAlbumArtistsSortReverse.flow.collectAsState()
    val sortedAlbumArtistNames by remember(albumArtistNames, sortBy, sortReverse) {
        derivedStateOf {
            context.symphony.groove.albumArtist.sort(albumArtistNames, sortBy, sortReverse)
        }
    }
    val horizontalGridColumns by context.symphony.settings.lastUsedAlbumArtistsHorizontalGridColumns.flow.collectAsState()
    val verticalGridColumns by context.symphony.settings.lastUsedAlbumArtistsVerticalGridColumns.flow.collectAsState()
    val layout by context.symphony.settings.lastUsedAlbumArtistsLayout.flow.collectAsState()
    val gridColumns by remember(horizontalGridColumns, verticalGridColumns) {
        derivedStateOf {
            ResponsiveGridColumns(horizontalGridColumns, verticalGridColumns)
        }
    }
    var showModifyLayoutSheet by remember { mutableStateOf(false) }

    MediaSortBarScaffold(
        mediaSortBar = {
            MediaSortBar(
                context,
                reverse = sortReverse,
                onReverseChange = {
                    context.symphony.settings.lastUsedAlbumArtistsSortReverse.setValue(it)
                },
                sort = sortBy,
                sorts = AlbumArtistRepository.SortBy.entries
                    .associateWith { x -> ViewContext.parameterizedFn { x.label(context) } },
                onSortChange = {
                    context.symphony.settings.lastUsedAlbumArtistsSortBy.setValue(it)
                },
                label = {
                    Text(
                        AppStrings.XArtists(
                            (albumArtistsCount ?: albumArtistNames.size).toString()
                        )
                    )
                },
                layout = layout,
                onLayoutChange = { context.symphony.settings.lastUsedAlbumArtistsLayout.setValue(it) },
                onShowModifyLayout = {
                    showModifyLayoutSheet = true
                },
            )
        },
        content = {
            when {
                albumArtistNames.isEmpty() -> IconTextBody(
                    icon = { modifier ->
                        Icon(
                            Icons.Filled.Person,
                            null,
                            modifier = modifier,
                        )
                    },
                    content = { Text(AppStrings.DamnThisIsSoEmpty) }
                )

                else -> ResponsiveGrid(gridColumns, layout) {
                    itemsIndexed(
                        sortedAlbumArtistNames,
                        key = { i, x -> "$i-$x" },
                        contentType = { _, _ -> Groove.Kind.ARTIST }
                    ) { _, albumArtistName ->
                        context.symphony.groove.albumArtist.get(albumArtistName)
                            ?.let { albumArtist ->
                                when (layout) {
                                    MediaLayout.Grid -> AlbumArtistTile(context, albumArtist)
                                    MediaLayout.List -> AlbumArtistListItem(context, albumArtist)
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
                        context.symphony.settings.lastUsedAlbumArtistsHorizontalGridColumns.setValue(
                            it.horizontal
                        )
                        context.symphony.settings.lastUsedAlbumArtistsVerticalGridColumns.setValue(
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

private fun AlbumArtistRepository.SortBy.label(context: ViewContext) = when (this) {
    AlbumArtistRepository.SortBy.CUSTOM -> AppStrings.Custom
    AlbumArtistRepository.SortBy.ARTIST_NAME -> AppStrings.Artist
    AlbumArtistRepository.SortBy.ALBUMS_COUNT -> AppStrings.AlbumCount
    AlbumArtistRepository.SortBy.TRACKS_COUNT -> AppStrings.TrackCount
}
