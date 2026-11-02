package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.clickable


import androidx.compose.foundation.layout.ColumnScope


import androidx.compose.foundation.layout.Spacer


import androidx.compose.foundation.layout.height


import androidx.compose.foundation.layout.padding


import androidx.compose.foundation.lazy.LazyColumn


import androidx.compose.foundation.lazy.LazyListScope


import androidx.compose.foundation.lazy.itemsIndexed


import androidx.compose.foundation.lazy.rememberLazyListState


import androidx.compose.material.icons.Icons


import androidx.compose.material.icons.filled.MusicNote


import androidx.compose.material3.Icon


import androidx.compose.material3.MaterialTheme


import androidx.compose.material3.Text


import androidx.compose.runtime.Composable


import androidx.compose.runtime.collectAsState


import androidx.compose.runtime.derivedStateOf


import androidx.compose.runtime.getValue


import androidx.compose.runtime.remember


import androidx.compose.ui.Modifier


import androidx.compose.ui.text.style.TextAlign


import androidx.compose.ui.unit.dp


import dev.anujyadav.harmoniq.services.groove.Groove


import dev.anujyadav.harmoniq.services.groove.Song


import dev.anujyadav.harmoniq.services.groove.repositories.SongRepository


import dev.anujyadav.harmoniq.services.radio.Radio


import dev.anujyadav.harmoniq.ui.helpers.ViewContext


import dev.anujyadav.harmoniq.ui.view.SettingsViewRoute


import dev.anujyadav.harmoniq.ui.view.settings.GrooveSettingsViewRoute

import dev.anujyadav.harmoniq.utils.AppStrings

enum class SongListType {
    Default,
    Playlist,
    Album,
}

enum class MediaSortBarPlacement {
    // pinned above the list
    Sticky,

    // scrolls with the list, right after its leading content
    Inline,
}

@Composable
fun SongList(
    context: ViewContext,
    songIds: List<String>,
    songsCount: Int? = null,
    leadingContent: (LazyListScope.() -> Unit)? = null,
    trailingContent: (LazyListScope.() -> Unit)? = null,
    trailingOptionsContent: (@Composable ColumnScope.(Int, Song, () -> Unit) -> Unit)? = null,
    songRemoveAction: ((Int, Song) -> SongRemoveAction?)? = null,
    onPlayStarted: (() -> Unit)? = null,
    cardThumbnailLabel: (@Composable (Int, Song) -> Unit)? = null,
    cardThumbnailLabelStyle: SongCardThumbnailLabelStyle = SongCardThumbnailLabelStyle.Default,
    type: SongListType = SongListType.Default,
    disableHeartIcon: Boolean = false,
    enableAddMediaFoldersHint: Boolean = false,
    sortBarPlacement: MediaSortBarPlacement = MediaSortBarPlacement.Sticky,
    // only songs matching these terms are listed, in the selected sort order
    filterTerms: String = "",
) {
    val sortBy by type.getLastUsedSortBy(context).flow.collectAsState()
    val sortReverse by type.getLastUsedSortReverse(context).flow.collectAsState()
    val sortedSongIds by remember(songIds, sortBy, sortReverse) {
        derivedStateOf {
            context.symphony.groove.song.sort(songIds, sortBy, sortReverse)
        }
    }
    val visibleSongIds by remember(sortedSongIds, filterTerms) {
        derivedStateOf {
            context.symphony.groove.song.filter(sortedSongIds, filterTerms)
        }
    }

    val mediaSortBar = @Composable {
        MediaSortBar(
            context,
            reverse = sortReverse,
            onReverseChange = {
                type.setLastUsedSortReverse(context, it)
            },
            sort = sortBy,
            sorts = SongRepository.SortBy.entries
                .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
            onSortChange = {
                type.setLastUsedSortBy(context, it)
            },
            label = {
                Text(AppStrings.XSongs((songsCount ?: songIds.size).toString()))
            },
            onShufflePlay = {
                onPlayStarted?.invoke()
                context.symphony.radio.shorty.playQueue(visibleSongIds, shuffle = true)
            }
        )
    }

    val content = @Composable {
        when {
            // a list with its own header keeps showing it, with the empty state below
            songIds.isEmpty() && leadingContent == null -> SongListEmptyBody(
                context,
                enableAddMediaFoldersHint,
            )

            else -> {
                val lazyListState = rememberLazyListState()
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.drawScrollBar(lazyListState)
                ) {
                    leadingContent?.invoke(this)
                    if (sortBarPlacement == MediaSortBarPlacement.Inline) {
                        item(contentType = SongListItemType.SortBar) {
                            mediaSortBar()
                        }
                    }
                    if (visibleSongIds.isEmpty()) {
                        item(contentType = SongListItemType.Empty) {
                            SubtleCaptionText(
                                when {
                                    songIds.isEmpty() -> AppStrings.DamnThisIsSoEmpty
                                    else -> AppStrings.NoResultsFound
                                }
                            )
                        }
                    }
                    itemsIndexed(
                        visibleSongIds,
                        key = { i, x -> "$i-$x" },
                        contentType = { _, _ -> Groove.Kind.SONG }
                    ) { i, songId ->
                        context.symphony.groove.song.get(songId)?.let { song ->
                            SongCard(
                                context,
                                song = song,
                                thumbnailLabel = cardThumbnailLabel?.let {
                                    { it(i, song) }
                                },
                                thumbnailLabelStyle = cardThumbnailLabelStyle,
                                disableHeartIcon = disableHeartIcon,
                                trailingOptionsContent = trailingOptionsContent?.let {
                                    { onDismissRequest -> it(i, song, onDismissRequest) }
                                },
                                removeAction = songRemoveAction?.invoke(i, song),
                            ) {
                                onPlayStarted?.invoke()
                                context.symphony.radio.shorty.playQueue(
                                    visibleSongIds,
                                    Radio.PlayOptions(index = i)
                                )
                            }
                        }
                    }
                    trailingContent?.invoke(this)
                }
            }
        }
    }

    when (sortBarPlacement) {
        MediaSortBarPlacement.Sticky -> MediaSortBarScaffold(
            mediaSortBar = mediaSortBar,
            content = content,
        )

        MediaSortBarPlacement.Inline -> content()
    }
}

private enum class SongListItemType {
    SortBar,
    Empty,
}

@Composable
private fun SongListEmptyBody(context: ViewContext, enableAddMediaFoldersHint: Boolean) {
    IconTextBody(
        icon = { modifier ->
            Icon(Icons.Filled.MusicNote, null, modifier = modifier)
        },
        content = {
            Text(AppStrings.DamnThisIsSoEmpty)
            if (enableAddMediaFoldersHint) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    AppStrings.HintAddMediaFolders,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable {
                            context.navController.navigate(
                                GrooveSettingsViewRoute(SettingsViewRoute.ELEMENT_MEDIA_FOLDERS)
                            )
                        }
                        .padding(2.dp),
                )
            }
        }
    )
}

fun SongRepository.SortBy.label(context: ViewContext) = when (this) {
    SongRepository.SortBy.CUSTOM -> AppStrings.Custom
    SongRepository.SortBy.TITLE -> AppStrings.Title
    SongRepository.SortBy.ARTIST -> AppStrings.Artist
    SongRepository.SortBy.ALBUM -> AppStrings.Album
    SongRepository.SortBy.DURATION -> AppStrings.Duration
    SongRepository.SortBy.DATE_MODIFIED -> AppStrings.LastModified
    SongRepository.SortBy.COMPOSER -> AppStrings.Composer
    SongRepository.SortBy.ALBUM_ARTIST -> AppStrings.AlbumArtist
    SongRepository.SortBy.YEAR -> AppStrings.Year
    SongRepository.SortBy.FILENAME -> AppStrings.Filename
    SongRepository.SortBy.TRACK_NUMBER -> AppStrings.TrackNumber
}

fun SongListType.getLastUsedSortBy(context: ViewContext) = when (this) {
    SongListType.Default -> context.symphony.settings.lastUsedSongsSortBy
    SongListType.Album -> context.symphony.settings.lastUsedAlbumSongsSortBy
    SongListType.Playlist -> context.symphony.settings.lastUsedPlaylistSongsSortBy
}

fun SongListType.setLastUsedSortBy(context: ViewContext, sort: SongRepository.SortBy) =
    when (this) {
        SongListType.Default -> context.symphony.settings.lastUsedSongsSortBy.setValue(sort)
        SongListType.Playlist -> context.symphony.settings.lastUsedPlaylistSongsSortBy.setValue(sort)
        SongListType.Album -> context.symphony.settings.lastUsedAlbumSongsSortBy.setValue(sort)
    }

fun SongListType.getLastUsedSortReverse(context: ViewContext) = when (this) {
    SongListType.Default -> context.symphony.settings.lastUsedSongsSortReverse
    SongListType.Playlist -> context.symphony.settings.lastUsedPlaylistSongsSortReverse
    SongListType.Album -> context.symphony.settings.lastUsedAlbumSongsSortReverse
}

fun SongListType.setLastUsedSortReverse(context: ViewContext, reverse: Boolean) = when (this) {
    SongListType.Default -> context.symphony.settings.lastUsedSongsSortReverse.setValue(reverse)
    SongListType.Playlist -> context.symphony.settings.lastUsedPlaylistSongsSortReverse.setValue(
        reverse
    )

    SongListType.Album -> context.symphony.settings.lastUsedAlbumSongsSortReverse.setValue(reverse)
}
