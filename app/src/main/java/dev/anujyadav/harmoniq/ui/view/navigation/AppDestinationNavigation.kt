package dev.anujyadav.harmoniq.ui.view.navigation

import android.widget.Toast
import dev.anujyadav.harmoniq.shortcuts.AppDestination
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.MainShellViewRoute
import dev.anujyadav.harmoniq.ui.view.PlaylistViewRoute
import dev.anujyadav.harmoniq.ui.view.SettingsViewRoute
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

// beyond this a playlist that exists is unlikely to show up, and the user is left looking at nothing
private val PLAYLISTS_WAIT_TIMEOUT = 30.seconds

/** Goes where a shortcut or the Quick Settings tile asked for, through the app's own navigation. */
suspend fun ViewContext.open(destination: AppDestination) {
    when (destination) {
        AppDestination.Search -> {
            navController.popBackStack(MainShellViewRoute, inclusive = false)
            symphony.settings.lastMainTab.setValue(MainTab.SEARCH)
        }

        AppDestination.Settings -> navController.navigate(SettingsViewRoute()) { launchSingleTop = true }
        AppDestination.Favorites -> openPlaylist { symphony.groove.playlist.getFavorites().id }
        is AppDestination.Playlist -> openPlaylist { destination.id }
    }
}

// on a cold start playlists are still loading, which isn't the same as not existing
private suspend fun ViewContext.openPlaylist(id: () -> String) {
    withTimeoutOrNull(PLAYLISTS_WAIT_TIMEOUT) { symphony.groove.readyDeferred.await() }
    val playlistId = id()
    when {
        symphony.groove.playlist.get(playlistId) != null -> navController.navigate(
            PlaylistViewRoute(playlistId)
        ) { launchSingleTop = true }

        else -> Toast.makeText(activity, AppStrings.CommandPlaylistMissing, Toast.LENGTH_SHORT).show()
    }
}
