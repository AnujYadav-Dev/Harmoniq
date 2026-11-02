package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.lazy.grid.itemsIndexed


import androidx.compose.material.icons.Icons


import androidx.compose.material.icons.filled.Album


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


import dev.anujyadav.harmoniq.services.groove.repositories.AlbumRepository


import dev.anujyadav.harmoniq.ui.helpers.ViewContext

import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.ui.components.MediaLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumGrid(
    context: ViewContext,
    albumIds: List<String>,
    albumsCount: Int? = null,
) {
    val sortBy by context.symphony.settings.lastUsedAlbumsSortBy.flow.collectAsState()
    val sortReverse by context.symphony.settings.lastUsedAlbumsSortReverse.flow.collectAsState()
    val sortedAlbumIds by remember(albumIds, sortBy, sortReverse) {
        derivedStateOf {
            context.symphony.groove.album.sort(albumIds, sortBy, sortReverse)
        }
    }
    val horizontalGridColumns by context.symphony.settings.lastUsedAlbumsHorizontalGridColumns.flow.collectAsState()
    val verticalGridColumns by context.symphony.settings.lastUsedAlbumsVerticalGridColumns.flow.collectAsState()
    val layout by context.symphony.settings.lastUsedAlbumsLayout.flow.collectAsState()
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
                    context.symphony.settings.lastUsedAlbumsSortReverse.setValue(it)
                },
                sort = sortBy,
                sorts = AlbumRepository.SortBy.entries.associateWith { x ->
                    ViewContext.parameterizedFn { x.label(it) }
                },
                onSortChange = {
                    context.symphony.settings.lastUsedAlbumsSortBy.setValue(it)
                },
                label = {
                    Text(AppStrings.XAlbums((albumsCount ?: albumIds.size).toString()))
                },
                layout = layout,
                onLayoutChange = { context.symphony.settings.lastUsedAlbumsLayout.setValue(it) },
                onShowModifyLayout = {
                    showModifyLayoutSheet = true
                },
            )
        },
        content = {
            when {
                albumIds.isEmpty() -> IconTextBody(
                    icon = { modifier ->
                        Icon(
                            Icons.Filled.Album,
                            null,
                            modifier = modifier,
                        )
                    },
                    content = { Text(AppStrings.DamnThisIsSoEmpty) }
                )

                else -> ResponsiveGrid(gridColumns, layout) {
                    itemsIndexed(
                        sortedAlbumIds,
                        key = { i, x -> "$i-$x" },
                        contentType = { _, _ -> Groove.Kind.ALBUM }
                    ) { _, albumId ->
                        context.symphony.groove.album.get(albumId)?.let { album ->
                            when (layout) {
                                MediaLayout.Grid -> AlbumTile(context, album)
                                MediaLayout.List -> AlbumListItem(context, album)
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
                        context.symphony.settings.lastUsedAlbumsHorizontalGridColumns.setValue(
                            it.horizontal
                        )
                        context.symphony.settings.lastUsedAlbumsVerticalGridColumns.setValue(
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

private fun AlbumRepository.SortBy.label(context: ViewContext) = when (this) {
    AlbumRepository.SortBy.CUSTOM -> AppStrings.Custom
    AlbumRepository.SortBy.ALBUM_NAME -> AppStrings.Album
    AlbumRepository.SortBy.ARTIST_NAME -> AppStrings.Artist
    AlbumRepository.SortBy.TRACKS_COUNT -> AppStrings.TrackCount
    AlbumRepository.SortBy.YEAR -> AppStrings.Year
}
