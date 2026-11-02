package dev.anujyadav.harmoniq.ui.components


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.services.groove.Song
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun SongCard(
    context: ViewContext,
    song: Song,
    highlighted: Boolean = false,
    autoHighlight: Boolean = true,
    disableHeartIcon: Boolean = false,
    leading: @Composable () -> Unit = {},
    thumbnailLabel: (@Composable () -> Unit)? = null,
    thumbnailLabelStyle: SongCardThumbnailLabelStyle = SongCardThumbnailLabelStyle.Default,
    trailingOptionsContent: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
    selected: Boolean = false,
    swipeActionsEnabled: Boolean = true,
    removeAction: SongRemoveAction? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val queue by context.symphony.radio.observatory.queue.collectAsState()
    val queueIndex by context.symphony.radio.observatory.queueIndex.collectAsState()
    val isCurrentPlaying by remember(autoHighlight, song, queue) {
        derivedStateOf { autoHighlight && song.id == queue.getOrNull(queueIndex) }
    }

    // swiping "play" behaves exactly like tapping the row on that screen
    SongActionsHost(context, song, onPlay = onClick, removeAction = removeAction) { actions ->
        SongSwipeActionsBox(context, actions, enabled = swipeActionsEnabled) {
            SongCardContent(
                context,
                actions,
                highlighted = highlighted || isCurrentPlaying,
                selected = selected,
                disableHeartIcon = disableHeartIcon,
                leading = leading,
                thumbnailLabel = thumbnailLabel,
                thumbnailLabelStyle = thumbnailLabelStyle,
                trailingOptionsContent = trailingOptionsContent,
                onLongClick = onLongClick,
                onClick = onClick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongCardContent(
    context: ViewContext,
    actions: SongActions,
    highlighted: Boolean,
    selected: Boolean,
    disableHeartIcon: Boolean,
    leading: @Composable () -> Unit,
    thumbnailLabel: (@Composable () -> Unit)?,
    thumbnailLabelStyle: SongCardThumbnailLabelStyle,
    trailingOptionsContent: (@Composable ColumnScope.(() -> Unit) -> Unit)?,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val song = actions.song
    val isFavorite = actions.isFavorite

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> Color.Transparent
            }
        ),
    ) {
        Box(modifier = Modifier.padding(12.dp, 12.dp, 4.dp, 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading()
                Box {
                    AsyncImage(
                        song.createArtworkImageRequest(context.symphony).build(),
                        null,
                        modifier = Modifier
                            .size(45.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    thumbnailLabel?.let { it ->
                        val backgroundColor =
                            thumbnailLabelStyle.backgroundColor(MaterialTheme.colorScheme)
                        val contentColor =
                            thumbnailLabelStyle.contentColor(MaterialTheme.colorScheme)

                        Box(
                            modifier = Modifier
                                .offset(y = 8.dp)
                                .align(Alignment.BottomCenter)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        backgroundColor,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(3.dp, 0.dp)
                            ) {
                                ProvideTextStyle(
                                    MaterialTheme.typography.labelSmall.copy(
                                        color = contentColor
                                    )
                                ) { it() }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = when {
                                highlighted -> MaterialTheme.colorScheme.primary
                                else -> LocalTextStyle.current.color
                            }
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (song.artists.isNotEmpty()) {
                        Text(
                            song.artists.joinToString(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(15.dp))

                Row {
                    if (!disableHeartIcon && isFavorite) {
                        IconButton(
                            modifier = Modifier.offset(4.dp, 0.dp),
                            onClick = {
                                context.symphony.groove.playlist.unfavorite(song.id)
                            }
                        ) {
                            Icon(
                                Icons.Filled.Favorite,
                                null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    var showOptionsMenu by remember { mutableStateOf(false) }
                    LabeledIconButton(
                        icon = Icons.Filled.MoreVert,
                        label = AppStrings.MoreOptions,
                        iconModifier = Modifier.size(24.dp),
                        onClick = { showOptionsMenu = !showOptionsMenu },
                    ) {
                        SongDropdownMenu(
                            actions,
                            trailingContent = trailingOptionsContent,
                            expanded = showOptionsMenu,
                            onDismissRequest = {
                                showOptionsMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SongDropdownMenu(
    context: ViewContext,
    song: Song,
    trailingContent: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    SongActionsHost(context, song) { actions ->
        SongDropdownMenu(
            actions,
            trailingContent = trailingContent,
            expanded = expanded,
            onDismissRequest = onDismissRequest,
        )
    }
}

@Composable
fun SongDropdownMenu(
    actions: SongActions,
    trailingContent: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    val song = actions.song

    @Composable
    fun ActionItem(action: SongAction, label: String = actions.label(action), onClick: () -> Unit) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(actions.icon(action), null)
            },
            text = {
                Text(label)
            },
            onClick = {
                onDismissRequest()
                onClick()
            }
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        SongAction.menuActions
            .filter { actions.isAvailable(it) }
            .forEach { action ->
                when (action) {
                    // one entry per artist, unlike the swipe action which uses the first one
                    SongAction.ViewArtist -> song.artists.forEach { artistName ->
                        ActionItem(action, "${AppStrings.ViewArtist}: $artistName") {
                            actions.viewArtist(artistName)
                        }
                    }

                    SongAction.ViewAlbumArtist -> song.albumArtists.forEach { albumArtist ->
                        ActionItem(action, "${AppStrings.ViewAlbumArtist}: $albumArtist") {
                            actions.viewAlbumArtist(albumArtist)
                        }
                    }

                    else -> ActionItem(action) {
                        actions.perform(action)
                    }
                }
            }
        trailingContent?.invoke(this, onDismissRequest)
    }
}

enum class SongCardThumbnailLabelStyle {
    Default,
    Subtle,
}

private fun SongCardThumbnailLabelStyle.backgroundColor(colorScheme: ColorScheme) = when (this) {
    SongCardThumbnailLabelStyle.Default -> colorScheme.surfaceVariant
    SongCardThumbnailLabelStyle.Subtle -> colorScheme.surfaceVariant
}

private fun SongCardThumbnailLabelStyle.contentColor(colorScheme: ColorScheme) = when (this) {
    SongCardThumbnailLabelStyle.Default -> colorScheme.primary
    SongCardThumbnailLabelStyle.Subtle -> colorScheme.onSurfaceVariant
}
