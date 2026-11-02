package dev.anujyadav.harmoniq.ui.components


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.services.groove.Playlist
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.theme.ThemeColors
import dev.anujyadav.harmoniq.ui.view.PlaylistViewRoute

@Composable
fun PlaylistTile(context: ViewContext, playlist: Playlist) {
    val updateId by context.symphony.groove.playlist.updateId.collectAsState()

    PlaylistActionsHost(context, playlist) { actions ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            onClick = {
                context.navController.navigate(PlaylistViewRoute(playlist.id))
            }
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        AsyncImage(
                            // TODO: remove this hack after moving to reactive objects
                            remember(updateId, playlist) {
                                playlist.createArtworkImageRequest(context.symphony).build()
                            },
                            null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp)
                        ) {
                            var showOptionsMenu by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { showOptionsMenu = !showOptionsMenu }
                            ) {
                                Icon(Icons.Filled.MoreVert, null)
                                PlaylistDropdownMenu(
                                    actions,
                                    expanded = showOptionsMenu,
                                    onDismissRequest = {
                                        showOptionsMenu = false
                                    }
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            IconButton(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .then(Modifier.size(36.dp)),
                                onClick = {
                                    actions.play()
                                }
                            ) {
                                Icon(Icons.Filled.PlayArrow, null)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        playlist.title,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDropdownMenu(
    context: ViewContext,
    playlist: Playlist,
    expanded: Boolean,
    onSongsChanged: (() -> Unit) = {},
    onRename: (() -> Unit) = {},
    onDelete: (() -> Unit) = {},
    onDismissRequest: () -> Unit,
) {
    PlaylistActionsHost(
        context,
        playlist,
        onSongsChanged = onSongsChanged,
        onRename = onRename,
        onDelete = onDelete,
    ) { actions ->
        PlaylistDropdownMenu(
            actions,
            expanded = expanded,
            onDismissRequest = onDismissRequest,
        )
    }
}

@Composable
fun PlaylistDropdownMenu(
    actions: PlaylistActions,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        PlaylistAction.menuActions
            .filter { actions.isAvailable(it) }
            .forEach { action ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            actions.icon(action),
                            null,
                            tint = when (action) {
                                PlaylistAction.Delete -> ThemeColors.Red
                                else -> LocalContentColor.current
                            },
                        )
                    },
                    text = {
                        Text(actions.label(action))
                    },
                    onClick = {
                        onDismissRequest()
                        actions.perform(action)
                    }
                )
            }
    }
}
