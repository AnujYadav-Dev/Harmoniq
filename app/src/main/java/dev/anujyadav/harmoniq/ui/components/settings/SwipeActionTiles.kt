package dev.anujyadav.harmoniq.ui.components.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.anujyadav.harmoniq.services.Settings
import dev.anujyadav.harmoniq.ui.components.PlaylistAction
import dev.anujyadav.harmoniq.ui.components.PlaylistSwipeScope
import dev.anujyadav.harmoniq.ui.components.SongAction
import dev.anujyadav.harmoniq.ui.components.SongSwipeScope
import dev.anujyadav.harmoniq.ui.components.SwipeDirection
import dev.anujyadav.harmoniq.ui.components.settingsLabel
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.settings.SwipeActionsSettingsViewRoute
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlin.enums.EnumEntries

/**
 * Opens the swipe actions settings page.
 */
@Composable
fun SettingsSwipeActionsLinkTile(context: ViewContext) {
    SettingsSimpleTile(
        icon = { Icon(Icons.Filled.Swipe, null) },
        title = { Text(AppStrings.SwipeActions) },
        subtitle = { Text(AppStrings.SwipeActionsHint) },
        onClick = { context.navController.navigate(SwipeActionsSettingsViewRoute) },
    )
}

/**
 * Left and right swipe options for every song and playlist scope.
 */
@Composable
fun SettingsSwipeActionsSections(context: ViewContext) {
    val settings = context.symphony.settings

    SongSwipeScope.entries.forEach { scope ->
        SettingsSideHeading("${AppStrings.Songs} - ${scope.label}")
        SwipeDirection.entries.forEachIndexed { i, direction ->
            if (i > 0) HorizontalDivider()
            SwipeActionTile(
                direction,
                settings.songSwipeAction(scope, direction),
                SongAction.entries,
            ) { it.settingsLabel() }
        }
    }
    PlaylistSwipeScope.entries.forEach { scope ->
        SettingsSideHeading("${AppStrings.Playlists} - ${scope.label}")
        SwipeDirection.entries.forEachIndexed { i, direction ->
            if (i > 0) HorizontalDivider()
            SwipeActionTile(
                direction,
                settings.playlistSwipeAction(scope, direction),
                PlaylistAction.entries,
            ) { it.settingsLabel() }
        }
    }
    SettingsSideHeading(AppStrings.Reset)
    SettingsSimpleTile(
        icon = { Icon(Icons.Filled.RestartAlt, null) },
        title = { Text(AppStrings.ResetToDefaults) },
        onClick = {
            SwipeDirection.entries.forEach { direction ->
                SongSwipeScope.entries.forEach {
                    settings.songSwipeAction(it, direction).reset()
                }
                PlaylistSwipeScope.entries.forEach {
                    settings.playlistSwipeAction(it, direction).reset()
                }
            }
        },
    )
}

@Composable
private fun <T : Enum<T>> SwipeActionTile(
    direction: SwipeDirection,
    entry: Settings.OptionalEnumEntry<T>,
    actions: EnumEntries<T>,
    label: (T) -> String,
) {
    val value by entry.flow.collectAsState()

    SettingsOptionTile(
        icon = {
            Icon(
                when (direction) {
                    SwipeDirection.Right -> Icons.Filled.SwipeRight
                    SwipeDirection.Left -> Icons.Filled.SwipeLeft
                },
                null,
            )
        },
        title = { Text(direction.label()) },
        value = value,
        values = mapOf<T?, String>(null to AppStrings.None) + actions.associateWith(label),
        onChange = { entry.setValue(it) },
    )
}

private fun SwipeDirection.label() = when (this) {
    SwipeDirection.Right -> AppStrings.SwipeRight
    SwipeDirection.Left -> AppStrings.SwipeLeft
}
