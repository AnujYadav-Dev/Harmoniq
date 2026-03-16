package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.layout.Box


import androidx.compose.foundation.layout.fillMaxWidth


import androidx.compose.foundation.layout.padding


import androidx.compose.material3.DividerDefaults


import androidx.compose.material3.OutlinedTextField


import androidx.compose.material3.Text


import androidx.compose.material3.TextButton


import androidx.compose.material3.TextFieldDefaults


import androidx.compose.runtime.Composable


import androidx.compose.runtime.getValue


import androidx.compose.runtime.mutableStateOf


import androidx.compose.runtime.remember


import androidx.compose.runtime.setValue


import androidx.compose.ui.Modifier


import androidx.compose.ui.focus.FocusRequester


import androidx.compose.ui.focus.focusRequester


import androidx.compose.ui.graphics.Color


import androidx.compose.ui.unit.dp


import dev.anujyadav.harmoniq.services.groove.Playlist


import dev.anujyadav.harmoniq.ui.helpers.ViewContext

import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun RenamePlaylistDialog(
    context: ViewContext,
    playlist: Playlist,
    onRename: () -> Unit = {},
    onDismissRequest: () -> Unit,
) {
    var input by remember { mutableStateOf(playlist.title) }
    val focusRequester = remember { FocusRequester() }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(AppStrings.RenamePlaylist)
        },
        content = {
            Box(
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = DividerDefaults.color,
                    ),
                    value = input,
                    onValueChange = {
                        input = it
                    }
                )
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text(AppStrings.Cancel)
            }
            TextButton(
                enabled = input.isNotBlank() && input != playlist.title,
                onClick = {
                    onRename()
                    onDismissRequest()
                    context.symphony.groove.playlist.renamePlaylist(playlist, input)
                }
            ) {
                Text(AppStrings.Done)
            }
        },
    )
}
