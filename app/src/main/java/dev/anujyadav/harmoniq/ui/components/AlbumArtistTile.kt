package dev.anujyadav.harmoniq.ui.components

import androidx.compose.material.icons.Icons


import androidx.compose.material.icons.automirrored.filled.PlaylistAdd


import androidx.compose.material.icons.automirrored.filled.PlaylistPlay


import androidx.compose.material3.DropdownMenu


import androidx.compose.material3.DropdownMenuItem


import androidx.compose.material3.Icon


import androidx.compose.material3.MaterialTheme


import androidx.compose.material3.Text


import androidx.compose.runtime.Composable


import androidx.compose.runtime.getValue


import androidx.compose.runtime.mutableStateOf


import androidx.compose.runtime.remember


import androidx.compose.runtime.setValue


import androidx.compose.ui.text.style.TextAlign


import androidx.compose.ui.text.style.TextOverflow


import dev.anujyadav.harmoniq.services.groove.AlbumArtist


import dev.anujyadav.harmoniq.ui.helpers.ViewContext


import dev.anujyadav.harmoniq.ui.view.AlbumArtistViewRoute

import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun AlbumArtistTile(context: ViewContext, albumArtist: AlbumArtist) {
    SquareGrooveTile(
        image = albumArtist.createArtworkImageRequest(context.symphony).build(),
        options = { expanded, onDismissRequest ->
            AlbumArtistDropdownMenu(
                context,
                albumArtist,
                expanded = expanded,
                onDismissRequest = onDismissRequest,
            )
        },
        content = {
            Text(
                albumArtist.name,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onPlay = {
            context.symphony.radio.shorty.playQueue(albumArtist.getSortedSongIds(context.symphony))
        },
        onClick = {
            context.navController.navigate(AlbumArtistViewRoute(albumArtist.name))
        }
    )
}

@Composable
fun AlbumArtistDropdownMenu(
    context: ViewContext,
    albumArtist: AlbumArtist,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
            },
            text = {
                Text(AppStrings.ShufflePlay)
            },
            onClick = {
                onDismissRequest()
                context.symphony.radio.shorty.playQueue(
                    albumArtist.getSortedSongIds(context.symphony),
                    shuffle = true,
                )
            }
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
            },
            text = {
                Text(AppStrings.PlayNext)
            },
            onClick = {
                onDismissRequest()
                context.symphony.radio.queue.add(
                    albumArtist.getSortedSongIds(context.symphony),
                    context.symphony.radio.queue.currentSongIndex + 1
                )
            }
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
            },
            text = {
                Text(AppStrings.AddToQueue)
            },
            onClick = {
                onDismissRequest()
                context.symphony.radio.queue.add(albumArtist.getSortedSongIds(context.symphony))
            }
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
            },
            text = {
                Text(AppStrings.AddToPlaylist)
            },
            onClick = {
                onDismissRequest()
                showAddToPlaylistDialog = true
            }
        )
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            context,
            songIds = albumArtist.getSongIds(context.symphony),
            onDismissRequest = {
                showAddToPlaylistDialog = false
            }
        )
    }
}
