package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.ui.helpers.ScreenOrientation
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

object PlaylistHeaderDefaults {
    // cover width as a fraction of the screen width, capped by MaxCoverSize
    const val COVER_WIDTH_FRACTION_PORTRAIT = 0.6f
    const val COVER_WIDTH_FRACTION_LANDSCAPE = 0.25f
    val MaxCoverSize = 280.dp
    val CoverElevation = 12.dp
    val HorizontalPadding = 20.dp
    val PlayButtonSize = 56.dp
    val CoverBadgePadding = 8.dp
    val CoverBadgeInnerPadding = 6.dp
    val CoverBadgeIconSize = 16.dp
    const val BACKGROUND_ALPHA = 0.5f
}

/**
 * Header of a playlist: search, cover, title, details and the main actions,
 * all of them run through [PlaylistActions]. While searching, only the search
 * field stays so the results (and the song toolbar below) come first.
 */
@Composable
fun PlaylistHeader(
    actions: PlaylistActions,
    songIds: List<String>,
    searchTerms: String,
    onSearchTermsChange: (String) -> Unit,
    isSearching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val padding = PlaylistHeaderDefaults.HorizontalPadding
    val backgroundAlpha by animateFloatAsState(
        if (isSearching) 0f else PlaylistHeaderDefaults.BACKGROUND_ALPHA,
        label = "playlist-header-background",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = backgroundAlpha),
                    1f to MaterialTheme.colorScheme.background,
                )
            )
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = padding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ListSearchField(
                value = searchTerms,
                onValueChange = onSearchTermsChange,
                placeholder = AppStrings.FindInPlaylist,
                modifier = Modifier.weight(1f),
                onFocusChange = { focused ->
                    if (focused) onSearchingChange(true)
                },
            )
            AnimatedVisibility(visible = isSearching) {
                TextButton(
                    onClick = {
                        onSearchTermsChange("")
                        onSearchingChange(false)
                        focusManager.clearFocus()
                    },
                ) {
                    Text(AppStrings.Cancel)
                }
            }
        }
        AnimatedVisibility(
            visible = !isSearching,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            PlaylistHeaderDetails(actions, songIds)
        }
    }
}

@Composable
private fun PlaylistHeaderDetails(actions: PlaylistActions, songIds: List<String>) {
    val playlist = actions.playlist
    val updateId by actions.context.symphony.groove.playlist.updateId.collectAsState()
    val padding = PlaylistHeaderDefaults.HorizontalPadding

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(20.dp))
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val fraction = when (ScreenOrientation.fromConfiguration(LocalConfiguration.current)) {
                ScreenOrientation.PORTRAIT -> PlaylistHeaderDefaults.COVER_WIDTH_FRACTION_PORTRAIT
                ScreenOrientation.LANDSCAPE -> PlaylistHeaderDefaults.COVER_WIDTH_FRACTION_LANDSCAPE
            }
            val coverSize = minOf(maxWidth * fraction, PlaylistHeaderDefaults.MaxCoverSize)
            val shape = MaterialTheme.shapes.medium
            // re-read on every playlist update, so "remove" appears once a cover is set
            val coverActions = remember(updateId, playlist) {
                PlaylistAction.coverActions.filter { actions.isAvailable(it) }
            }
            var showCoverMenu by remember { mutableStateOf(false) }

            // tapping the cover edits it, the badge makes that discoverable. With a
            // custom cover set, it offers changing or removing it, otherwise it opens
            // the image picker straight away.
            Box {
                WithTooltip(actions.label(PlaylistAction.ChangeCover)) {
                    AsyncImage(
                        // TODO: remove this hack after moving to reactive objects
                        remember(updateId, playlist) {
                            playlist.createArtworkImageRequest(actions.context.symphony).build()
                        },
                        actions.label(PlaylistAction.ChangeCover),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(coverSize)
                            .shadow(PlaylistHeaderDefaults.CoverElevation, shape)
                            .clip(shape)
                            .clickable {
                                when (coverActions.size) {
                                    1 -> actions.perform(coverActions.first())
                                    else -> showCoverMenu = true
                                }
                            },
                    )
                }
                DropdownMenu(
                    expanded = showCoverMenu,
                    onDismissRequest = { showCoverMenu = false },
                ) {
                    coverActions.forEach { action ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(actions.icon(action), null) },
                            text = { Text(actions.label(action)) },
                            onClick = {
                                showCoverMenu = false
                                actions.perform(action)
                            },
                        )
                    }
                }
                Icon(
                    Icons.Filled.Edit,
                    null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(PlaylistHeaderDefaults.CoverBadgePadding)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        .padding(PlaylistHeaderDefaults.CoverBadgeInnerPadding)
                        .size(PlaylistHeaderDefaults.CoverBadgeIconSize),
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            playlist.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = padding),
        )
        Spacer(modifier = Modifier.height(4.dp))
        PlaylistMetadata(actions, songIds, modifier = Modifier.padding(horizontal = padding))
        Spacer(modifier = Modifier.height(8.dp))
        PlaylistActionRow(actions, hasSongs = songIds.isNotEmpty())
    }
}

@Composable
private fun PlaylistMetadata(actions: PlaylistActions, songIds: List<String>, modifier: Modifier) {
    val totalDuration = remember(songIds) {
        songIds.sumOf { actions.context.symphony.groove.song.get(it)?.duration ?: 0L }.milliseconds
    }
    val details = listOfNotNull(
        AppStrings.XSongs(songIds.size.toString()),
        totalDuration.toString(),
        AppStrings.LocalPlaylist.takeIf { actions.playlist.isLocal },
    )

    Text(
        details.joinToString(" • "),
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        ),
        modifier = modifier,
    )
}

@Composable
private fun PlaylistActionRow(actions: PlaylistActions, hasSongs: Boolean) {
    var showOptionsMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = PlaylistHeaderDefaults.HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledIconButton(
            icon = actions.icon(PlaylistAction.AddToQueue),
            label = actions.label(PlaylistAction.AddToQueue),
            enabled = hasSongs,
            onClick = { actions.perform(PlaylistAction.AddToQueue) },
        )
        if (actions.isAvailable(PlaylistAction.ManageSongs)) {
            LabeledIconButton(
                icon = actions.icon(PlaylistAction.ManageSongs),
                label = actions.label(PlaylistAction.ManageSongs),
                onClick = { actions.perform(PlaylistAction.ManageSongs) },
            )
        }
        Box {
            LabeledIconButton(
                icon = Icons.Filled.MoreVert,
                label = AppStrings.MoreOptions,
                onClick = { showOptionsMenu = true },
            )
            PlaylistDropdownMenu(
                actions,
                expanded = showOptionsMenu,
                onDismissRequest = { showOptionsMenu = false },
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        LabeledIconButton(
            icon = Icons.Filled.Shuffle,
            label = actions.label(PlaylistAction.ShufflePlay),
            enabled = hasSongs,
            onClick = { actions.perform(PlaylistAction.ShufflePlay) },
        )
        Spacer(modifier = Modifier.width(8.dp))
        WithTooltip(actions.label(PlaylistAction.Play)) {
            FilledIconButton(
                enabled = hasSongs,
                onClick = { actions.perform(PlaylistAction.Play) },
                modifier = Modifier.size(PlaylistHeaderDefaults.PlayButtonSize),
            ) {
                Icon(Icons.Filled.PlayArrow, actions.label(PlaylistAction.Play))
            }
        }
    }
}
