package dev.anujyadav.harmoniq.ui.view

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.anujyadav.harmoniq.MainActivity
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.radio.Radio
import dev.anujyadav.harmoniq.ui.components.SongUnavailableDialog
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
import dev.anujyadav.harmoniq.ui.view.settings.SwipeActionsSettingsView
import dev.anujyadav.harmoniq.ui.view.settings.SwipeActionsSettingsViewRoute
import dev.anujyadav.harmoniq.ui.components.ProvideSwipeScope
import dev.anujyadav.harmoniq.ui.components.SongSwipeScope
import dev.anujyadav.harmoniq.ui.view.navigation.LibraryPill

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
    var playbackError by remember { mutableStateOf<Radio.PlaybackError?>(null) }

    DisposableEffect(symphony) {
        val unsubscribe = symphony.radio.onPlaybackError.subscribe { error ->
            // keep the first error visible when several queued songs fail in a row
            if (playbackError == null) {
                playbackError = error
            }
        }
        onDispose { unsubscribe() }
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
                    ProvideSwipeScope(songScope = SongSwipeScope.Collections) {
                        ArtistView(context, it.toRoute())
                    }
                }
                baseComposable<AlbumViewRoute> {
                    ProvideSwipeScope(songScope = SongSwipeScope.Collections) {
                        AlbumView(context, it.toRoute())
                    }
                }
                baseComposable<AlbumArtistViewRoute> {
                    ProvideSwipeScope(songScope = SongSwipeScope.Collections) {
                        AlbumArtistView(context, it.toRoute())
                    }
                }
                baseComposable<GenreViewRoute> {
                    ProvideSwipeScope(songScope = SongSwipeScope.Collections) {
                        GenreView(context, it.toRoute())
                    }
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
                baseComposable<SwipeActionsSettingsViewRoute> {
                    SwipeActionsSettingsView(context)
                }
                // Full-screen library category routes
                baseComposable<SongsFullViewRoute> {
                    ProvideSwipeScope(songScope = LibraryPill.Songs.songSwipeScope) {
                        SongsFullView(context)
                    }
                }
                baseComposable<AlbumsFullViewRoute> {
                    ProvideSwipeScope(songScope = LibraryPill.Albums.songSwipeScope) {
                        AlbumsFullView(context)
                    }
                }
                baseComposable<ArtistsFullViewRoute> {
                    ProvideSwipeScope(songScope = LibraryPill.Artists.songSwipeScope) {
                        ArtistsFullView(context)
                    }
                }
                baseComposable<AlbumArtistsFullViewRoute> {
                    ProvideSwipeScope(songScope = LibraryPill.AlbumArtists.songSwipeScope) {
                        AlbumArtistsFullView(context)
                    }
                }
                baseComposable<GenresFullViewRoute> {
                    ProvideSwipeScope(songScope = LibraryPill.Genres.songSwipeScope) {
                        GenresFullView(context)
                    }
                }
                baseComposable<PlaylistsFullViewRoute> {
                    PlaylistsFullView(context)
                }
                baseComposable<BrowserFullViewRoute> {
                    ProvideSwipeScope(songScope = LibraryPill.Browser.songSwipeScope) {
                        BrowserFullView(context)
                    }
                }
                baseComposable<FoldersFullViewRoute> {
                    ProvideSwipeScope(songScope = LibraryPill.Folders.songSwipeScope) {
                        FoldersFullView(context)
                    }
                }
                baseComposable<TreeFullViewRoute> {
                    ProvideSwipeScope(songScope = LibraryPill.Tree.songSwipeScope) {
                        TreeFullView(context)
                    }
                }
            }
        }

        playbackError?.let { error ->
            SongUnavailableDialog(
                context = context,
                error = error,
                onDismissRequest = { playbackError = null },
            )
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
