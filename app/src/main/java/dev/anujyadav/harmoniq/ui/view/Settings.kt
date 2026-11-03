package dev.anujyadav.harmoniq.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.anujyadav.harmoniq.ui.components.IconButtonPlaceholder
import dev.anujyadav.harmoniq.ui.components.TopAppBarMinimalTitle
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSimpleTile
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.settings.AppearanceSettingsViewRoute
import dev.anujyadav.harmoniq.ui.view.settings.GlobalSearchSettingsViewRoute
import dev.anujyadav.harmoniq.ui.view.settings.GrooveSettingsViewRoute
import dev.anujyadav.harmoniq.ui.view.settings.MiniPlayerSettingsViewRoute
import dev.anujyadav.harmoniq.ui.view.settings.NowPlayingSettingsViewRoute
import dev.anujyadav.harmoniq.ui.view.settings.PlayerSettingsViewRoute
import dev.anujyadav.harmoniq.ui.view.settings.AboutSettingsViewRoute
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.serialization.Serializable
import androidx.compose.material.icons.filled.Swipe
import dev.anujyadav.harmoniq.ui.view.settings.SwipeActionsSettingsViewRoute

@Serializable
data class SettingsViewRoute(val initialElement: String? = null) {
    companion object {
        const val ELEMENT_MEDIA_FOLDERS = "media_folders"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(context: ViewContext, route: SettingsViewRoute) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text(AppStrings.Settings)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            context.navController.popBackStack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButtonPlaceholder()
                },
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                SettingsSimpleTile(
                    icon = {
                        Icon(Icons.Filled.LibraryMusic, null)
                    },
                    title = {
                        Text(AppStrings.Groove)
                    },
                    onClick = {
                        context.navController.navigate(
                            GrooveSettingsViewRoute(route.initialElement)
                        )
                    },
                )
                if (context.symphony.globalSearch.isAvailable) {
                    HorizontalDivider()
                    SettingsSimpleTile(
                        icon = {
                            Icon(Icons.Filled.Public, null)
                        },
                        title = {
                            Text(AppStrings.GlobalSearch)
                        },
                        onClick = {
                            context.navController.navigate(GlobalSearchSettingsViewRoute)
                        },
                    )
                }
                HorizontalDivider()
                SettingsSimpleTile(
                    icon = {
                        Icon(Icons.Filled.Radio, null)
                    },
                    title = {
                        Text(AppStrings.Player)
                    },
                    onClick = {
                        context.navController.navigate(PlayerSettingsViewRoute)
                    },
                )
                HorizontalDivider()
                SettingsSimpleTile(
                    icon = {
                        Icon(Icons.Filled.Palette, null)
                    },
                    title = {
                        Text(AppStrings.Appearance)
                    },
                    onClick = {
                        context.navController.navigate(AppearanceSettingsViewRoute)
                    },
                )
                HorizontalDivider()
                SettingsSimpleTile(
                    icon = {
                        Icon(Icons.Filled.Swipe, null)
                    },
                    title = {
                        Text(AppStrings.SwipeActions)
                    },
                    onClick = {
                        context.navController.navigate(SwipeActionsSettingsViewRoute)
                    },
                )
                HorizontalDivider()
                SettingsSimpleTile(
                    icon = {
                        Icon(Icons.Filled.MusicNote, null)
                    },
                    title = {
                        Text(AppStrings.MiniPlayer)
                    },
                    onClick = {
                        context.navController.navigate(MiniPlayerSettingsViewRoute)
                    },
                )
                HorizontalDivider()
                SettingsSimpleTile(
                    icon = {
                        Icon(Icons.Filled.MusicNote, null)
                    },
                    title = {
                        Text(AppStrings.NowPlaying)
                    },
                    onClick = {
                        context.navController.navigate(NowPlayingSettingsViewRoute)
                    },
                )
                HorizontalDivider()
                SettingsSimpleTile(
                    icon = {
                        Icon(Icons.Filled.Info, null)
                    },
                    title = {
                        Text(AppStrings.About)
                    },
                    onClick = {
                        context.navController.navigate(AboutSettingsViewRoute)
                    },
                )
            }
        }
    )
}
