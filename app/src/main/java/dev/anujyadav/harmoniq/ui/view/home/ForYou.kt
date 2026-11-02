package dev.anujyadav.harmoniq.ui.view.home


import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.services.groove.repositories.SongRepository
import dev.anujyadav.harmoniq.services.radio.Radio
import dev.anujyadav.harmoniq.ui.components.IconTextBody
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.AlbumArtistViewRoute
import dev.anujyadav.harmoniq.ui.view.AlbumViewRoute
import dev.anujyadav.harmoniq.ui.view.ArtistViewRoute
import dev.anujyadav.harmoniq.ui.view.PlaylistViewRoute
import dev.anujyadav.harmoniq.ui.view.navigation.LibraryPill
import dev.anujyadav.harmoniq.ui.view.navigation.navigateToLibraryPill
import dev.anujyadav.harmoniq.ui.components.SeeAllButton
import dev.anujyadav.harmoniq.services.groove.Playlist
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.randomSubList
import dev.anujyadav.harmoniq.utils.runIfOrDefault
import dev.anujyadav.harmoniq.utils.subListNonStrict
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.anujyadav.harmoniq.services.groove.SongSuggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.anujyadav.harmoniq.ui.components.LabeledIconButton
import dev.anujyadav.harmoniq.ui.components.MediaLayout
import dev.anujyadav.harmoniq.ui.components.MediaLayoutToggle
import dev.anujyadav.harmoniq.ui.components.SongCard

enum class ForYou(val label: (context: ViewContext) -> String) {
    Songs(label = { AppStrings.SuggestedSongs }),
    Albums(label = { AppStrings.SuggestedAlbums }),
    Artists(label = { AppStrings.SuggestedArtists }),
    AlbumArtists(label = { AppStrings.SuggestedAlbumArtists })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForYouView(context: ViewContext) {
    val albumArtistsIsUpdating by context.symphony.groove.albumArtist.isUpdating.collectAsState()
    val albumsIsUpdating by context.symphony.groove.album.isUpdating.collectAsState()
    val artistsIsUpdating by context.symphony.groove.artist.isUpdating.collectAsState()
    val songsIsUpdating by context.symphony.groove.song.isUpdating.collectAsState()
    val albumArtistNames by context.symphony.groove.albumArtist.all.collectAsState()
    val albumIds by context.symphony.groove.album.all.collectAsState()
    val artistNames by context.symphony.groove.artist.all.collectAsState()
    val songIds by context.symphony.groove.song.all.collectAsState()
    val sortBy by context.symphony.settings.lastUsedSongsSortBy.flow.collectAsState()
    val sortReverse by context.symphony.settings.lastUsedSongsSortReverse.flow.collectAsState()

    when {
        songIds.isNotEmpty() -> {
            val sortedSongIds by remember(songsIsUpdating, songIds, sortBy, sortReverse) {
                derivedStateOf {
                    runIfOrDefault(!songsIsUpdating, listOf()) {
                        context.symphony.groove.song.sort(songIds.toList(), sortBy, sortReverse)
                    }
                }
            }
            val recentlyAddedSongs by remember(songsIsUpdating, songIds) {
                derivedStateOf {
                    runIfOrDefault(!songsIsUpdating, listOf()) {
                        context.symphony.groove.song.sort(
                            songIds.toList(),
                            SongRepository.SortBy.DATE_MODIFIED,
                            true
                        )
                    }
                }
            }
            val randomAlbums by remember(albumsIsUpdating, albumIds) {
                derivedStateOf {
                    runIfOrDefault(!albumsIsUpdating, listOf()) {
                        albumIds.randomSubList(6)
                    }
                }
            }
            val randomArtists by remember(artistsIsUpdating, artistNames) {
                derivedStateOf {
                    runIfOrDefault(!artistsIsUpdating, listOf()) {
                        artistNames.randomSubList(6)
                    }
                }
            }
            val randomAlbumArtists by remember(albumArtistsIsUpdating, albumArtistNames) {
                derivedStateOf {
                    runIfOrDefault(!albumArtistsIsUpdating, listOf()) {
                        albumArtistNames.randomSubList(6)
                    }
                }
            }
            val homePlaylists = rememberHomePlaylists(context)

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PlaylistShortcutRow(
                    context = context,
                    playlists = homePlaylists,
                )

                Row(modifier = Modifier.padding(20.dp, 0.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ForYouButton(
                            icon = Icons.Filled.PlayArrow,
                            text = {
                                Text(AppStrings.PlayAll)
                            },
                            enabled = !songsIsUpdating,
                            onClick = {
                                context.symphony.radio.shorty.playQueue(sortedSongIds)
                            },
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        ForYouButton(
                            icon = Icons.Filled.Shuffle,
                            text = {
                                Text(AppStrings.ShufflePlay)
                            },
                            enabled = !songsIsUpdating,
                            onClick = {
                                context.symphony.radio.shorty.playQueue(
                                    songIds.toList(),
                                    shuffle = true,
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                SideHeading {
                    Text(AppStrings.RecentlyAddedSongs)
                }
                Spacer(modifier = Modifier.height(12.dp))
                StatedSongShortcutRow(
                    context,
                    isLoading = songsIsUpdating,
                    songIds = recentlyAddedSongs,
                )
                val contents by context.symphony.settings.forYouContents.flow.collectAsState()
                contents.forEach {
                    when (it) {
                        ForYou.Songs -> SuggestedSongs(
                            context,
                            isLoading = songsIsUpdating,
                            songIds = songIds.toList(),
                        )

                        ForYou.Albums -> SuggestedAlbums(
                            context,
                            isLoading = albumsIsUpdating,
                            albumIds = randomAlbums,
                        )

                        ForYou.Artists -> SuggestedArtists(
                            context,
                            label = AppStrings.SuggestedArtists,
                            isLoading = artistsIsUpdating,
                            artistNames = randomArtists,
                        )

                        ForYou.AlbumArtists -> SuggestedAlbumArtists(
                            context,
                            label = AppStrings.SuggestedAlbumArtists,
                            isLoading = albumArtistsIsUpdating,
                            albumArtistNames = randomAlbumArtists,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        else -> IconTextBody(
            icon = { modifier ->
                Icon(
                    Icons.Filled.MusicNote,
                    null,
                    modifier = modifier,
                )
            },
            content = { Text(AppStrings.DamnThisIsSoEmpty) },
        )
    }
}

// how many songs a horizontally scrolling song row shows
private const val SONG_SHORTCUT_ROW_LIMIT = 5

/**
 * A horizontally scrolling row of song cards, tapping one plays [songIds] from it.
 */
@Composable
private fun StatedSongShortcutRow(
    context: ViewContext,
    isLoading: Boolean,
    songIds: List<String>,
    limit: Int = SONG_SHORTCUT_ROW_LIMIT,
) {
    when {
        isLoading -> SixGridLoading()
        songIds.isEmpty() -> SixGridEmpty(context)
        else -> BoxWithConstraints {
            val tileWidth = this@BoxWithConstraints.maxWidth.times(0.7f)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(modifier = Modifier.width(12.dp))
                songIds.subListNonStrict(limit).forEachIndexed { i, songId ->
                    val tileHeight = 96.dp
                    val backgroundColor = MaterialTheme.colorScheme.surface
                    val song = context.symphony.groove.song.get(songId)
                        ?: return@forEachIndexed

                    ElevatedCard(
                        modifier = Modifier
                            .width(tileWidth)
                            .height(tileHeight),
                        onClick = {
                            context.symphony.radio.shorty.playQueue(
                                songIds,
                                options = Radio.PlayOptions(index = i),
                            )
                        }
                    ) {
                        Box {
                            AsyncImage(
                                song.createArtworkImageRequest(context.symphony)
                                    .build(),
                                null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.matchParentSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                backgroundColor.copy(alpha = 0.2f),
                                                backgroundColor.copy(alpha = 0.7f),
                                                backgroundColor.copy(alpha = 0.8f),
                                            ),
                                        )
                                    )
                            )
                            Row(modifier = Modifier.padding(8.dp)) {
                                Box {
                                    AsyncImage(
                                        song.createArtworkImageRequest(context.symphony)
                                            .build(),
                                        null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp)),
                                    )
                                    Box(
                                        modifier = Modifier.matchParentSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    backgroundColor.copy(alpha = 0.25f),
                                                    CircleShape,
                                                )
                                                .padding(1.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        song.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (song.artists.isNotEmpty()) {
                                        Text(
                                            song.artists.joinToString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun SideHeading(text: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(20.dp, 0.dp)) {
        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
            text()
        }
    }
}

@Composable
private fun ForYouButton(
    icon: ImageVector,
    text: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ElevatedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            text()
        }
    }
}

@Composable
private fun SixGridLoading() {
    Box(
        modifier = Modifier
            .height((LocalConfiguration.current.screenHeightDp * 0.2f).dp)
            .fillMaxWidth()
            .padding(0.dp, 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SixGridEmpty(context: ViewContext) {
    val height = (LocalConfiguration.current.screenHeightDp * 0.15f).dp
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .padding(0.dp, 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            AppStrings.DamnThisIsSoEmpty,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        )
    }
}

@Composable
private fun <T> StatedSixGrid(
    context: ViewContext,
    isLoading: Boolean,
    items: List<T>,
    content: @Composable (T) -> Unit,
) {
    when {
        isLoading -> SixGridLoading()
        items.isEmpty() -> SixGridEmpty(context)
        else -> SixGrid(items) {
            content(it)
        }
    }
}

@Composable
private fun <T> SixGrid(
    items: List<T>,
    content: @Composable (T) -> Unit,
) {
    val gap = 12.dp
    Row(
        modifier = Modifier.padding(20.dp, 0.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        (0..2).forEach { i ->
            val item = items.getOrNull(i)
            Box(modifier = Modifier.weight(1f)) {
                item?.let { content(it) }
            }
        }
    }
    if (items.size > 3) {
        Spacer(modifier = Modifier.height(gap))
        Row(
            modifier = Modifier.padding(20.dp, 0.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            (3..5).forEach { i ->
                val item = items.getOrNull(i)
                Box(modifier = Modifier.weight(1f)) {
                    item?.let { content(it) }
                }
            }
        }
    }
}

/**
 * Songs picked by [SongSuggestions], with category chips to steer them.
 */
@Composable
private fun SuggestedSongs(
    context: ViewContext,
    isLoading: Boolean,
    songIds: List<String>,
) {
    val suggestions = context.symphony.groove.suggestions
    val categoryKey by context.symphony.settings.suggestedSongsCategory.flow.collectAsState()
    val category = SongSuggestions.Category.fromKey(categoryKey) ?: SongSuggestions.Category.ForYou
    var refreshCount by remember { mutableIntStateOf(0) }
    var genres by remember { mutableStateOf(emptyList<String>()) }
    var suggestedSongIds by remember { mutableStateOf<List<String>?>(null) }
    val layout by context.symphony.settings.suggestedSongsLayout.flow.collectAsState()
    val selectCategory = { value: SongSuggestions.Category ->
        context.symphony.settings.suggestedSongsCategory.setValue(value.key)
    }

    LaunchedEffect(isLoading, songIds, category, refreshCount) {
        if (isLoading) {
            return@LaunchedEffect
        }
        // scoring goes through the whole library, keep it off the main thread
        val (topGenres, picked) = withContext(Dispatchers.Default) {
            suggestions.topGenres() to suggestions.suggest(category)
        }
        genres = topGenres
        suggestedSongIds = picked
        // a saved genre that no longer has songs falls back to the default
        if (category is SongSuggestions.Category.Genre && picked.isEmpty()) {
            selectCategory(SongSuggestions.Category.ForYou)
        }
    }

    val categories = buildList {
        add(SongSuggestions.Category.ForYou)
        add(SongSuggestions.Category.Rediscover)
        genres.forEach { add(SongSuggestions.Category.Genre(it)) }
        if (category is SongSuggestions.Category.Genre && !genres.contains(category.name)) {
            add(category)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Row(
        modifier = Modifier.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            SideHeading {
                Text(AppStrings.SuggestedSongs)
            }
        }
        MediaLayoutToggle(
            layout = layout,
            onLayoutChange = { context.symphony.settings.suggestedSongsLayout.setValue(it) },
        )
        LabeledIconButton(
            icon = Icons.Filled.Refresh,
            label = AppStrings.Refresh,
            enabled = !isLoading,
            onClick = { refreshCount++ },
        )
        LabeledIconButton(
            icon = Icons.Filled.PlayArrow,
            label = AppStrings.PlayAll,
            enabled = !suggestedSongIds.isNullOrEmpty(),
            onClick = {
                suggestedSongIds?.let { context.symphony.radio.shorty.playQueue(it) }
            },
        )
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.key }) {
            FilterChip(
                selected = it == category,
                onClick = { selectCategory(it) },
                label = { Text(it.label()) },
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    when (layout) {
        MediaLayout.Grid -> StatedSongShortcutRow(
            context,
            isLoading = isLoading || suggestedSongIds == null,
            songIds = suggestedSongIds ?: emptyList(),
            limit = SongSuggestions.SUGGESTIONS_LIMIT,
        )

        MediaLayout.List -> StatedSongRows(
            context,
            isLoading = isLoading || suggestedSongIds == null,
            songIds = suggestedSongIds ?: emptyList(),
        )
    }
}

// how many suggestions the list layout shows, "play all" still plays every one
private const val SUGGESTED_SONGS_LIST_LIMIT = 5

/**
 * The first few [songIds] as regular song rows, tapping one plays [songIds] from it.
 */
@Composable
private fun StatedSongRows(
    context: ViewContext,
    isLoading: Boolean,
    songIds: List<String>,
) {
    when {
        isLoading -> SixGridLoading()
        songIds.isEmpty() -> SixGridEmpty(context)
        else -> Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            songIds.subListNonStrict(SUGGESTED_SONGS_LIST_LIMIT).forEachIndexed { i, songId ->
                context.symphony.groove.song.get(songId)?.let { song ->
                    SongCard(context, song) {
                        context.symphony.radio.shorty.playQueue(
                            songIds,
                            options = Radio.PlayOptions(index = i),
                        )
                    }
                }
            }
        }
    }
}

private fun SongSuggestions.Category.label() = when (this) {
    SongSuggestions.Category.ForYou -> AppStrings.ForYou
    SongSuggestions.Category.Rediscover -> AppStrings.Rediscover
    is SongSuggestions.Category.Genre -> name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedAlbums(
    context: ViewContext,
    isLoading: Boolean,
    albumIds: List<String>,
) {
    val albums by remember(albumIds) {
        derivedStateOf {
            context.symphony.groove.album.get(albumIds)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    SideHeading {
        Text(AppStrings.SuggestedAlbums)
    }
    Spacer(modifier = Modifier.height(12.dp))
    StatedSixGrid(context, isLoading, albums) { album ->
        Card(
            onClick = {
                context.navController.navigate(AlbumViewRoute(album.id))
            }
        ) {
            AsyncImage(
                album.createArtworkImageRequest(context.symphony).build(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedArtists(
    context: ViewContext,
    label: String,
    isLoading: Boolean,
    artistNames: List<String>,
) {
    val artists by remember(artistNames) {
        derivedStateOf {
            context.symphony.groove.artist.get(artistNames)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    SideHeading {
        Text(label)
    }
    Spacer(modifier = Modifier.height(12.dp))
    StatedSixGrid(context, isLoading, artists) { artist ->
        Card(
            onClick = {
                context.navController.navigate(ArtistViewRoute(artist.name))
            }
        ) {
            AsyncImage(
                artist.createArtworkImageRequest(context.symphony).build(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedAlbumArtists(
    context: ViewContext,
    label: String,
    isLoading: Boolean,
    albumArtistNames: List<String>,
) {
    val albumArtists by remember(albumArtistNames) {
        derivedStateOf {
            context.symphony.groove.albumArtist.get(albumArtistNames)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    SideHeading {
        Text(label)
    }
    Spacer(modifier = Modifier.height(12.dp))
    StatedSixGrid(context, isLoading, albumArtists) { albumArtist ->
        Card(
            onClick = {
                context.navController.navigate(AlbumArtistViewRoute(albumArtist.name))
            }
        ) {
            AsyncImage(
                albumArtist.createArtworkImageRequest(context.symphony).build(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun PlaylistShortcutRow(
    context: ViewContext,
    playlists: List<Playlist>,
) {
    if (playlists.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp, 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tileHeight = 56.dp
        playlists.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { playlist ->
                    PlaylistShortcutCard(
                        context = context,
                        playlist = playlist,
                        modifier = Modifier
                            .weight(1f)
                            .height(tileHeight)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        SeeAllButton(AppStrings.SeeAllPlaylists) {
            context.navigateToLibraryPill(LibraryPill.Playlists)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistShortcutCard(
    context: ViewContext,
    playlist: Playlist,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
        onClick = {
            context.navController.navigate(PlaylistViewRoute(playlist.id))
        }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                playlist.createArtworkImageRequest(context.symphony).build(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxHeight(),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                playlist.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

/**
 * Playlist shortcuts configured in settings, recomputed whenever playlists or the
 * related settings change. Shared by the home screen and the library.
 */
@Composable
fun rememberHomePlaylists(context: ViewContext): List<Playlist> {
    val settings = context.symphony.settings
    val playlistIds by context.symphony.groove.playlist.all.collectAsState()
    val updateId by context.symphony.groove.playlist.updateId.collectAsState()
    val sortBy by settings.homePlaylistsSortBy.flow.collectAsState()
    val limit by settings.homePlaylistsLimit.flow.collectAsState()
    val showFavorites by settings.homeShowFavorites.flow.collectAsState()
    val selectedIds by settings.homePlaylistIds.flow.collectAsState()
    val lastPlayed by settings.playlistLastPlayed.flow.collectAsState()

    return remember(playlistIds, updateId, sortBy, limit, showFavorites, selectedIds, lastPlayed) {
        context.symphony.groove.playlist.getHomePlaylists()
    }
}
