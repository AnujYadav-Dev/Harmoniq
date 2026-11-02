package dev.anujyadav.harmoniq.ui.components.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anujyadav.harmoniq.services.groove.repositories.PlaylistRepository
import dev.anujyadav.harmoniq.ui.components.IconTextBody
import dev.anujyadav.harmoniq.ui.components.ReorderDragHandle
import dev.anujyadav.harmoniq.ui.components.ReorderableItemDefaults
import dev.anujyadav.harmoniq.ui.components.ScaffoldDialog
import dev.anujyadav.harmoniq.ui.components.ScaffoldDialogDefaults
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

fun PlaylistRepository.HomeSortBy.label() = when (this) {
    PlaylistRepository.HomeSortBy.CUSTOM -> AppStrings.Custom
    PlaylistRepository.HomeSortBy.RECENTLY_PLAYED -> AppStrings.RecentlyPlayed
    PlaylistRepository.HomeSortBy.RECENTLY_CREATED -> AppStrings.RecentlyCreated
    PlaylistRepository.HomeSortBy.TITLE -> AppStrings.Title
    PlaylistRepository.HomeSortBy.TRACKS_COUNT -> AppStrings.TrackCount
}

@Composable
fun SettingsHomePlaylistsSection(context: ViewContext) {
    val settings = context.symphony.settings
    val sortBy by settings.homePlaylistsSortBy.flow.collectAsState()
    val limit by settings.homePlaylistsLimit.flow.collectAsState()
    val showFavorites by settings.homeShowFavorites.flow.collectAsState()
    val selectedIds by settings.homePlaylistIds.flow.collectAsState()
    val allPlaylistIds by context.symphony.groove.playlist.all.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    val selectedCount = selectedIds.count {
        allPlaylistIds.contains(it) && !context.symphony.groove.playlist.isFavoritesId(it)
    }

    SettingsSideHeading(AppStrings.HomePlaylists)
    SettingsOptionTile(
        icon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
        title = { Text(AppStrings.SortPlaylistsBy) },
        value = sortBy,
        values = PlaylistRepository.HomeSortBy.entries.associateWith { it.label() },
        onChange = { settings.homePlaylistsSortBy.setValue(it) },
    )
    HorizontalDivider()
    SettingsSimpleTile(
        icon = { Icon(Icons.Filled.Reorder, null) },
        title = { Text(AppStrings.ChoosePlaylists) },
        subtitle = {
            Text(
                AppStrings.XOfYSelected(
                    selectedCount.toString(),
                    PlaylistRepository.HOME_PLAYLISTS_MAX.toString(),
                )
            )
        },
        enabled = sortBy == PlaylistRepository.HomeSortBy.CUSTOM,
        onClick = { showPicker = true },
    )
    HorizontalDivider()
    SettingsSliderTile(
        context,
        icon = { Icon(Icons.Filled.Numbers, null) },
        title = { Text(AppStrings.NumberOfPlaylists) },
        label = { value -> Text(value.roundToInt().toString()) },
        range = 0f..PlaylistRepository.HOME_PLAYLISTS_MAX.toFloat(),
        initialValue = limit.toFloat(),
        onValue = { value -> value.roundToInt().toFloat() },
        onChange = { value -> settings.homePlaylistsLimit.setValue(value.roundToInt()) },
        onReset = { settings.homePlaylistsLimit.setValue(settings.homePlaylistsLimit.defaultValue) },
    )
    HorizontalDivider()
    SettingsSwitchTile(
        icon = { Icon(Icons.Filled.Favorite, null) },
        title = { Text(AppStrings.ShowFavorites) },
        value = showFavorites,
        onChange = { settings.homeShowFavorites.setValue(it) },
    )

    if (showPicker) {
        HomePlaylistsPickerDialog(
            context,
            onDone = { settings.homePlaylistIds.setValue(it) },
            onDismissRequest = { showPicker = false },
        )
    }
}

/**
 * Selected playlists are kept at the top, in display order, and are the only
 * ones that can be dragged.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePlaylistsPickerDialog(
    context: ViewContext,
    onDone: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val repository = context.symphony.groove.playlist
    val selected = remember {
        context.symphony.settings.homePlaylistIds.value
            .filter { repository.get(it) != null }
            .toMutableStateList()
    }
    val items = remember {
        val unselected = repository.values()
            .filterNot { selected.contains(it.id) }
            .sortedBy { it.title }
            .map { it.id }
        (selected + unselected).toMutableStateList()
    }
    val selectedCount = selected.count { !repository.isFavoritesId(it) }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (to.index < selected.size) {
            items.add(to.index, items.removeAt(from.index))
            selected.add(to.index, selected.removeAt(from.index))
        }
    }

    fun toggle(id: String) {
        items.remove(id)
        if (selected.remove(id)) {
            items.add(selected.size, id)
        } else {
            selected.add(id)
            items.add(selected.size - 1, id)
        }
    }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(AppStrings.ChoosePlaylists) },
        contentHeight = ScaffoldDialogDefaults.PreferredMaxHeight,
        content = {
            if (items.isEmpty()) {
                IconTextBody(
                    icon = { modifier ->
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null, modifier = modifier)
                    },
                    content = { Text(AppStrings.NoPlaylistsFound) },
                )
            } else LazyColumn(
                state = lazyListState,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(items, key = { it }) { id ->
                    val playlist = repository.get(id) ?: return@items
                    val isSelected = selected.contains(id)
                    // favorites does not count towards the limit
                    val canSelect = isSelected
                            || repository.isFavoritesId(id)
                            || selectedCount < PlaylistRepository.HOME_PLAYLISTS_MAX

                    ReorderableItem(reorderState, key = id) { isDragging ->
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = ReorderableItemDefaults.containerColor(isDragging)
                            ),
                            leadingContent = {
                                Checkbox(
                                    checked = isSelected,
                                    enabled = canSelect,
                                    onCheckedChange = { toggle(id) },
                                )
                            },
                            headlineContent = { Text(playlist.title) },
                            trailingContent = {
                                Box {
                                    ReorderDragHandle(enabled = isSelected)
                                }
                            },
                        )
                    }
                }
            }
        },
        actions = {
            Text(
                AppStrings.XOfYSelected(
                    selectedCount.toString(),
                    PlaylistRepository.HOME_PLAYLISTS_MAX.toString(),
                ),
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onDismissRequest) {
                Text(AppStrings.Cancel)
            }
            TextButton(
                onClick = {
                    onDone(selected.toList())
                    onDismissRequest()
                }
            ) {
                Text(AppStrings.Done)
            }
        },
    )
}
