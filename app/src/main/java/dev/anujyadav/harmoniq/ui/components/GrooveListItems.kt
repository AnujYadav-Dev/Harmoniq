package dev.anujyadav.harmoniq.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.anujyadav.harmoniq.services.groove.Album
import dev.anujyadav.harmoniq.services.groove.AlbumArtist
import dev.anujyadav.harmoniq.services.groove.Artist
import dev.anujyadav.harmoniq.services.groove.Genre
import dev.anujyadav.harmoniq.services.groove.Playlist
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.AlbumArtistViewRoute
import dev.anujyadav.harmoniq.ui.view.AlbumViewRoute
import dev.anujyadav.harmoniq.ui.view.ArtistViewRoute
import dev.anujyadav.harmoniq.ui.view.GenreViewRoute
import dev.anujyadav.harmoniq.ui.view.PlaylistViewRoute
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.joinToStringIfNotEmpty
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/*
 * Row versions of the grid tiles, used by list layouts and search results.
 */

@Composable
fun AlbumListItem(context: ViewContext, album: Album) {
    GenericGrooveCard(
        image = album.createArtworkImageRequest(context.symphony).build(),
        title = { Text(album.name) },
        subtitle = album.artists.joinToStringIfNotEmpty()?.let { { Text(it) } },
        options = { expanded, onDismissRequest ->
            AlbumDropdownMenu(
                context, album,
                expanded = expanded,
                onDismissRequest = onDismissRequest,
            )
        },
        onClick = {
            context.navController.navigate(AlbumViewRoute(album.id))
        }
    )
}

@Composable
fun ArtistListItem(context: ViewContext, artist: Artist) {
    GenericGrooveCard(
        image = artist.createArtworkImageRequest(context.symphony).build(),
        title = { Text(artist.name) },
        options = { expanded, onDismissRequest ->
            ArtistDropdownMenu(
                context, artist,
                expanded = expanded,
                onDismissRequest = onDismissRequest,
            )
        },
        onClick = {
            context.navController.navigate(ArtistViewRoute(artist.name))
        }
    )
}

@Composable
fun AlbumArtistListItem(context: ViewContext, albumArtist: AlbumArtist) {
    GenericGrooveCard(
        image = albumArtist.createArtworkImageRequest(context.symphony).build(),
        title = { Text(albumArtist.name) },
        options = { expanded, onDismissRequest ->
            AlbumArtistDropdownMenu(
                context, albumArtist,
                expanded = expanded,
                onDismissRequest = onDismissRequest,
            )
        },
        onClick = {
            context.navController.navigate(AlbumArtistViewRoute(albumArtist.name))
        }
    )
}

@Composable
fun GenreListItem(context: ViewContext, genre: Genre) {
    GenericGrooveCard(
        image = null,
        title = { Text(genre.name) },
        subtitle = { Text(AppStrings.XSongs(genre.numberOfTracks.toString())) },
        options = null,
        onClick = {
            context.navController.navigate(GenreViewRoute(genre.name))
        }
    )
}

@Composable
fun PlaylistListItem(context: ViewContext, playlist: Playlist) {
    val updateId by context.symphony.groove.playlist.updateId.collectAsState()
    val image = remember(updateId, playlist) {
        playlist.createArtworkImageRequest(context.symphony).build()
    }

    PlaylistActionsHost(context, playlist) { actions ->
        PlaylistSwipeActionsBox(context, actions) {
            GenericGrooveCard(
                image = image,
                title = { Text(playlist.title) },
                subtitle = { Text(AppStrings.XSongs(playlist.numberOfTracks.toString())) },
                options = { expanded, onDismissRequest ->
                    PlaylistDropdownMenu(
                        actions,
                        expanded = expanded,
                        onDismissRequest = onDismissRequest,
                    )
                },
                onClick = {
                    context.navController.navigate(PlaylistViewRoute(playlist.id))
                }
            )
        }
    }
}
