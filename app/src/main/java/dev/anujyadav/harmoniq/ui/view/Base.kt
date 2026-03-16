package dev.anujyadav.harmoniq.ui.view

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.anujyadav.harmoniq.MainActivity
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.ui.helpers.ScaleTransition
import dev.anujyadav.harmoniq.ui.helpers.SlideTransition
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.theme.SymphonyTheme
import dev.anujyadav.harmoniq.ui.view.settings.AboutSettingsView
import dev.anujyadav.harmoniq.ui.view.settings.AboutSettingsViewRoute
import dev.anujyadav.harmoniq.ui.view.settings.AppearanceSettingsView
import dev.anujyadav.harmoniq.ui.view.settings.AppearanceSettingsViewRoute
import dev.anujyadav.harmoniq.ui.view.settings.GrooveSettingsView
import dev.anujyadav.harmoniq.ui.view.settings.GrooveSettingsViewRoute
import dev.anujyadav.harmoniq.ui.view.settings.MiniPlayerSettingsView
import dev.anujyadav.harmoniq.ui.view.settings.MiniPlayerSettingsViewRoute
import dev.anujyadav.harmoniq.ui.view.settings.NowPlayingSettingsView
import dev.anujyadav.harmoniq.ui.view.settings.NowPlayingSettingsViewRoute
import dev.anujyadav.harmoniq.ui.view.settings.PlayerSettingsView
import dev.anujyadav.harmoniq.ui.view.settings.PlayerSettingsViewRoute
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.serializer

@Composable
fun BaseView(symphony: Symphony, activity: MainActivity) {
    val navController = rememberNavController()
    val context = remember {
        ViewContext(
            symphony = symphony,
            activity = activity,
            navController = navController,
        )
    }

    SymphonyTheme(context) {
        Surface(color = MaterialTheme.colorScheme.background) {
            NavHost(
                navController = navController,
                startDestination = MainShellViewRoute,
            ) {
                baseComposable<MainShellViewRoute> {
                    MainShellView(context)
                }
                baseComposable<NowPlayingViewRoute> {
                    NowPlayingView(context)
                }
                baseComposable<QueueViewRoute> {
                    QueueView(context)
                }
                baseComposable<ArtistViewRoute> {
                    ArtistView(context, it.toRoute())
                }
                baseComposable<AlbumViewRoute> {
                    AlbumView(context, it.toRoute())
                }
                baseComposable<AlbumArtistViewRoute> {
                    AlbumArtistView(context, it.toRoute())
                }
                baseComposable<GenreViewRoute> {
                    GenreView(context, it.toRoute())
                }
                baseComposable<PlaylistViewRoute> {
                    PlaylistView(context, it.toRoute())
                }
                baseComposable<LyricsViewRoute> {
                    LyricsView(context)
                }
                baseComposable<SettingsViewRoute> {
                    SettingsView(context, it.toRoute())
                }
                baseComposable<AppearanceSettingsViewRoute> {
                    AppearanceSettingsView(context)
                }
                baseComposable<GrooveSettingsViewRoute> {
                    GrooveSettingsView(context, it.toRoute())
                }
                baseComposable<MiniPlayerSettingsViewRoute> {
                    MiniPlayerSettingsView(context)
                }
                baseComposable<NowPlayingSettingsViewRoute> {
                    NowPlayingSettingsView(context)
                }
                baseComposable<PlayerSettingsViewRoute> {
                    PlayerSettingsView(context)
                }
                baseComposable<AboutSettingsViewRoute> {
                    AboutSettingsView(context)
                }
                // Full-screen library category routes
                baseComposable<SongsFullViewRoute> {
                    SongsFullView(context)
                }
                baseComposable<AlbumsFullViewRoute> {
                    AlbumsFullView(context)
                }
                baseComposable<ArtistsFullViewRoute> {
                    ArtistsFullView(context)
                }
                baseComposable<AlbumArtistsFullViewRoute> {
                    AlbumArtistsFullView(context)
                }
                baseComposable<GenresFullViewRoute> {
                    GenresFullView(context)
                }
                baseComposable<PlaylistsFullViewRoute> {
                    PlaylistsFullView(context)
                }
                baseComposable<BrowserFullViewRoute> {
                    BrowserFullView(context)
                }
                baseComposable<FoldersFullViewRoute> {
                    FoldersFullView(context)
                }
                baseComposable<TreeFullViewRoute> {
                    TreeFullView(context)
                }
            }
        }
    }
}

private inline fun <reified T : Any> NavGraphBuilder.baseComposable(
    noinline content: @Composable (AnimatedContentScope.(NavBackStackEntry) -> Unit),
) {
    composable<T>(
        popEnterTransition = {
            when {
                isInitialRoute<NowPlayingViewRoute>() -> ScaleTransition.scaleUp.enterTransition()
                isInitialRoute<QueueViewRoute>() -> ScaleTransition.scaleUp.enterTransition()
                isInitialRoute<LyricsViewRoute>() -> ScaleTransition.scaleUp.enterTransition()
                else -> SlideTransition.slideRight.enterTransition()
            }
        },
        popExitTransition = {
            when {
                isInitialRoute<NowPlayingViewRoute>() -> SlideTransition.slideDown.exitTransition()
                isInitialRoute<QueueViewRoute>() -> SlideTransition.slideDown.exitTransition()
                isInitialRoute<LyricsViewRoute>() -> SlideTransition.slideDown.exitTransition()
                else -> SlideTransition.slideRight.exitTransition()
            }
        },
        enterTransition = {
            when {
                isTargetRoute<NowPlayingViewRoute>() -> SlideTransition.slideUp.enterTransition()
                isTargetRoute<QueueViewRoute>() -> SlideTransition.slideUp.enterTransition()
                isTargetRoute<LyricsViewRoute>() -> SlideTransition.slideUp.enterTransition()
                else -> SlideTransition.slideLeft.enterTransition()
            }
        },
        exitTransition = {
            when {
                isTargetRoute<NowPlayingViewRoute>() -> ScaleTransition.scaleDown.exitTransition()
                isTargetRoute<QueueViewRoute>() -> ScaleTransition.scaleDown.exitTransition()
                isTargetRoute<LyricsViewRoute>() -> ScaleTransition.scaleDown.exitTransition()
                else -> SlideTransition.slideLeft.exitTransition()
            }
        },
    ) {
        content(it)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private inline fun <reified T> NavDestination.isRoute() =
    route?.contains(serializer<T>().descriptor.serialName) == true

@OptIn(ExperimentalSerializationApi::class)
private inline fun <reified T> AnimatedContentTransitionScope<NavBackStackEntry>.isInitialRoute() =
    initialState.destination.isRoute<T>()

@OptIn(ExperimentalSerializationApi::class)
private inline fun <reified T> AnimatedContentTransitionScope<NavBackStackEntry>.isTargetRoute() =
    targetState.destination.isRoute<T>()
