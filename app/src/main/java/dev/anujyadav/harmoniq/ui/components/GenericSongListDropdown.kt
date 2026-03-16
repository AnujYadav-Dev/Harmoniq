package dev.anujyadav.harmoniq.ui.components

import androidx.compose.material.icons.Icons


import androidx.compose.material.icons.automirrored.filled.PlaylistAdd


import androidx.compose.material.icons.automirrored.filled.PlaylistPlay


import androidx.compose.material3.DropdownMenu


import androidx.compose.material3.DropdownMenuItem


import androidx.compose.material3.Icon


import androidx.compose.material3.Text


import androidx.compose.runtime.Composable


import androidx.compose.runtime.getValue


import androidx.compose.runtime.mutableStateOf


import androidx.compose.runtime.remember


import androidx.compose.runtime.setValue


import dev.anujyadav.harmoniq.ui.helpers.ViewContext

import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun GenericSongListDropdown(
    context: ViewContext,
    songIds: List<String>,
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
                context.symphony.radio.shorty.playQueue(songIds, shuffle = true)
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
                    songIds,
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
                context.symphony.radio.queue.add(songIds)
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
            songIds = songIds,
            onDismissRequest = {
                showAddToPlaylistDialog = false
            }
        )
    }
}
