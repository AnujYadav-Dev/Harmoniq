package dev.anujyadav.harmoniq.ui.view

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.anujyadav.harmoniq.services.groove.Groove
import dev.anujyadav.harmoniq.ui.components.AlbumArtistDropdownMenu
import dev.anujyadav.harmoniq.ui.components.AlbumDropdownMenu
import dev.anujyadav.harmoniq.ui.components.ArtistDropdownMenu
import dev.anujyadav.harmoniq.ui.components.GenericGrooveCard
import dev.anujyadav.harmoniq.ui.components.IconTextBody
import dev.anujyadav.harmoniq.ui.components.PlaylistDropdownMenu
import dev.anujyadav.harmoniq.ui.components.SongCard
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.joinToStringIfNotEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SearchTabResult(
    val songIds: List<String>,
    val artistNames: List<String>,
    val albumIds: List<String>,
    val albumArtistNames: List<String>,
    val genreNames: List<String>,
    val playlistIds: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTabContent(context: ViewContext) {
    val coroutineScope = rememberCoroutineScope()
    var terms by rememberSaveable { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<SearchTabResult?>(null) }
    var selectedChip by rememberSaveable { mutableStateOf<Groove.Kind?>(null) }

    fun isChipSelected(kind: Groove.Kind) = selectedChip == null || selectedChip == kind

    var currentTermsRoutine: Job? = null
    fun setTerms(nTerms: String) {
        terms = nTerms
        isSearching = true
        currentTermsRoutine?.cancel()
        currentTermsRoutine = coroutineScope.launch {
            withContext(Dispatchers.Default) {
                delay(250)
                val songIds = mutableListOf<String>()
                val artistNames = mutableListOf<String>()
                val albumIds = mutableListOf<String>()
                val albumArtistNames = mutableListOf<String>()
                val genreNames = mutableListOf<String>()
                val playlistIds = mutableListOf<String>()

                if (nTerms.isNotEmpty()) {
                    if (isChipSelected(Groove.Kind.SONG)) {
                        songIds.addAll(
                            context.symphony.groove.song
                                .search(context.symphony.groove.song.ids(), terms)
                                .map { it.entity }
                        )
                    }
                    if (isChipSelected(Groove.Kind.ARTIST)) {
                        artistNames.addAll(
                            context.symphony.groove.artist
                                .search(context.symphony.groove.artist.ids(), terms)
                                .map { it.entity }
                        )
                    }
                    if (isChipSelected(Groove.Kind.ALBUM)) {
                        albumIds.addAll(
                            context.symphony.groove.album
                                .search(context.symphony.groove.album.ids(), terms)
                                .map { it.entity }
                        )
                    }
                    if (isChipSelected(Groove.Kind.ALBUM_ARTIST)) {
                        albumArtistNames.addAll(
                            context.symphony.groove.albumArtist
                                .search(context.symphony.groove.albumArtist.ids(), terms)
                                .map { it.entity }
                        )
                    }
                    if (isChipSelected(Groove.Kind.GENRE)) {
                        genreNames.addAll(
                            context.symphony.groove.genre
                                .search(context.symphony.groove.genre.ids(), terms)
                                .map { it.entity }
                        )
                    }
                    if (isChipSelected(Groove.Kind.PLAYLIST)) {
                        playlistIds.addAll(
                            context.symphony.groove.playlist
                                .search(context.symphony.groove.playlist.ids(), terms)
                                .map { it.entity }
                        )
                    }

                    results = SearchTabResult(
                        songIds = songIds,
                        artistNames = artistNames,
                        albumIds = albumIds,
                        albumArtistNames = albumArtistNames,
                        genreNames = genreNames,
                        playlistIds = playlistIds,
                    )
                }
                isSearching = false
            }
        }
    }

    val configuration = LocalConfiguration.current
    val textFieldFocusRequester = FocusRequester()
    val chipsScrollState = rememberScrollState()

    LaunchedEffect(LocalContext.current) {
        snapshotFlow { configuration.orientation }.collect {
            setTerms(terms)
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .clipToBounds()) {
        // Search bar (no back button â€” embedded in tab)
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(textFieldFocusRequester),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            singleLine = true,
            value = terms,
            onValueChange = { setTerms(it) },
            placeholder = {
                Text(AppStrings.SearchYourMusic)
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, null)
            },
            trailingIcon = {
                if (terms.isNotEmpty()) {
                    IconButton(
                        onClick = { setTerms("") }
                    ) {
                        Icon(Icons.Filled.Close, null)
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(chipsScrollState)
        ) {
            Spacer(modifier = Modifier.width(4.dp))
            FilterChip(
                selected = selectedChip == null,
                label = { Text(AppStrings.All) },
                onClick = {
                    selectedChip = null
                    setTerms(terms)
                }
            )
            Groove.Kind.entries.map {
                FilterChip(
                    selected = selectedChip == it,
                    label = { Text(it.searchTabLabel()) },
                    onClick = {
                        selectedChip = it
                        setTerms(terms)
                    }
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))

        // Results content
        results?.run {
            val hasSongs = isChipSelected(Groove.Kind.SONG) && songIds.isNotEmpty()
            val hasArtists = isChipSelected(Groove.Kind.ARTIST) && artistNames.isNotEmpty()
            val hasAlbums = isChipSelected(Groove.Kind.ALBUM) && albumIds.isNotEmpty()
            val hasAlbumArtists =
                isChipSelected(Groove.Kind.ALBUM_ARTIST) && albumArtistNames.isNotEmpty()
            val hasPlaylists =
                isChipSelected(Groove.Kind.PLAYLIST) && playlistIds.isNotEmpty()
            val hasGenres = isChipSelected(Groove.Kind.GENRE) && genreNames.isNotEmpty()
            val hasNoResults =
                !hasSongs && !hasArtists && !hasAlbums && !hasAlbumArtists && !hasPlaylists && !hasGenres

            Box(modifier = Modifier.fillMaxSize()) {
                if (terms.isNotEmpty()) {
                    when {
                        isSearching -> {
                            Box(modifier = Modifier.align(Alignment.Center)) {
                                IconTextBody(
                                    icon = { modifier ->
                                        Icon(Icons.Filled.Search, null, modifier = modifier)
                                    },
                                    content = { Text(AppStrings.FilteringResults) }
                                )
                            }
                        }

                        hasNoResults -> {
                            Box(modifier = Modifier.align(Alignment.Center)) {
                                IconTextBody(
                                    icon = { modifier ->
                                        Icon(Icons.Filled.PriorityHigh, null, modifier = modifier)
                                    },
                                    content = { Text(AppStrings.NoResultsFound) }
                                )
                            }
                        }

                        else -> {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                if (hasSongs) {
                                    SearchTabSideHeading(AppStrings.Songs)
                                    songIds.forEach { songId ->
                                        context.symphony.groove.song.get(songId)?.let { song ->
                                            SongCard(context, song) {
                                                context.symphony.radio.shorty.playQueue(song.id)
                                            }
                                        }
                                    }
                                }
                                if (hasArtists) {
                                    SearchTabSideHeading(AppStrings.Artists)
                                    artistNames.forEach { artistName ->
                                        context.symphony.groove.artist.get(artistName)
                                            ?.let { artist ->
                                                GenericGrooveCard(
                                                    image = artist
                                                        .createArtworkImageRequest(context.symphony)
                                                        .build(),
                                                    title = { Text(artist.name) },
                                                    options = { expanded, onDismissRequest ->
                                                        ArtistDropdownMenu(
                                                            context, artist,
                                                            expanded = expanded,
                                                            onDismissRequest = onDismissRequest,
                                                        )
                                                    },
                                                    onClick = {
                                                        context.navController.navigate(
                                                            ArtistViewRoute(artist.name)
                                                        )
                                                    }
                                                )
                                            }
                                    }
                                }
                                if (hasAlbums) {
                                    SearchTabSideHeading(AppStrings.Albums)
                                    albumIds.forEach { albumId ->
                                        context.symphony.groove.album.get(albumId)
                                            ?.let { album ->
                                                GenericGrooveCard(
                                                    image = album
                                                        .createArtworkImageRequest(context.symphony)
                                                        .build(),
                                                    title = { Text(album.name) },
                                                    subtitle = album.artists
                                                        .joinToStringIfNotEmpty()
                                                        ?.let { { Text(it) } },
                                                    options = { expanded, onDismissRequest ->
                                                        AlbumDropdownMenu(
                                                            context, album,
                                                            expanded = expanded,
                                                            onDismissRequest = onDismissRequest,
                                                        )
                                                    },
                                                    onClick = {
                                                        context.navController.navigate(
                                                            AlbumViewRoute(album.id)
                                                        )
                                                    }
                                                )
                                            }
                                    }
                                }
                                if (hasAlbumArtists) {
                                    SearchTabSideHeading(AppStrings.AlbumArtists)
                                    albumArtistNames.forEach { albumArtistName ->
                                        context.symphony.groove.albumArtist.get(albumArtistName)
                                            ?.let { albumArtist ->
                                                GenericGrooveCard(
                                                    image = albumArtist
                                                        .createArtworkImageRequest(context.symphony)
                                                        .build(),
                                                    title = { Text(albumArtist.name) },
                                                    options = { expanded, onDismissRequest ->
                                                        AlbumArtistDropdownMenu(
                                                            context, albumArtist,
                                                            expanded = expanded,
                                                            onDismissRequest = onDismissRequest,
                                                        )
                                                    },
                                                    onClick = {
                                                        context.navController.navigate(
                                                            AlbumArtistViewRoute(albumArtist.name)
                                                        )
                                                    }
                                                )
                                            }
                                    }
                                }
                                if (hasPlaylists) {
                                    SearchTabSideHeading(AppStrings.Playlists)
                                    playlistIds.forEach { playlistId ->
                                        context.symphony.groove.playlist.get(playlistId)
                                            ?.let { playlist ->
                                                GenericGrooveCard(
                                                    image = playlist
                                                        .createArtworkImageRequest(context.symphony)
                                                        .build(),
                                                    title = { Text(playlist.title) },
                                                    options = { expanded, onDismissRequest ->
                                                        PlaylistDropdownMenu(
                                                            context, playlist,
                                                            expanded = expanded,
                                                            onDismissRequest = onDismissRequest,
                                                        )
                                                    },
                                                    onClick = {
                                                        context.navController.navigate(
                                                            PlaylistViewRoute(playlist.id)
                                                        )
                                                    }
                                                )
                                            }
                                    }
                                }
                                if (hasGenres) {
                                    SearchTabSideHeading(AppStrings.Genres)
                                    genreNames.forEach { genreName ->
                                        context.symphony.groove.genre.get(genreName)
                                            ?.let { genre ->
                                                GenericGrooveCard(
                                                    image = null,
                                                    title = { Text(genre.name) },
                                                    subtitle = {
                                                        Text(
                                                            AppStrings.XSongs(
                                                                genre.numberOfTracks.toString()
                                                            )
                                                        )
                                                    },
                                                    options = null,
                                                    onClick = {
                                                        context.navController.navigate(
                                                            GenreViewRoute(genre.name)
                                                        )
                                                    }
                                                )
                                            }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTabSideHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 4.dp)
    )
}

private fun Groove.Kind.searchTabLabel() = when (this) {
    Groove.Kind.SONG -> AppStrings.Songs
    Groove.Kind.ALBUM -> AppStrings.Albums
    Groove.Kind.ARTIST -> AppStrings.Artists
    Groove.Kind.ALBUM_ARTIST -> AppStrings.AlbumArtists
    Groove.Kind.GENRE -> AppStrings.Genres
    Groove.Kind.PLAYLIST -> AppStrings.Playlists
}
