package dev.anujyadav.harmoniq.ui.view.settings


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.repeatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.RuleFolder
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import dev.anujyadav.harmoniq.ui.components.AdaptiveSnackbar
import dev.anujyadav.harmoniq.ui.components.IconButtonPlaceholder
import dev.anujyadav.harmoniq.ui.components.TopAppBarMinimalTitle
import dev.anujyadav.harmoniq.ui.components.settings.SettingsMultiGrooveFolderTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsMultiSystemFolderTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsMultiTextOptionTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsOptionTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSideHeading
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSimpleTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSliderTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSwitchTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsTextInputTile
import dev.anujyadav.harmoniq.ui.helpers.TransitionDurations
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.SettingsViewRoute
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.ImagePreserver
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class GrooveSettingsViewRoute(val initialElement: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrooveSettingsView(context: ViewContext, route: GrooveSettingsViewRoute) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val songsFilterPattern by context.symphony.settings.songsFilterPattern.flow.collectAsState()
    val minSongDuration by context.symphony.settings.minSongDuration.flow.collectAsState()
    val blacklistFolders by context.symphony.settings.blacklistFolders.flow.collectAsState()
    val whitelistFolders by context.symphony.settings.whitelistFolders.flow.collectAsState()
    val artistTagSeparators by context.symphony.settings.artistTagSeparators.flow.collectAsState()
    val genreTagSeparators by context.symphony.settings.genreTagSeparators.flow.collectAsState()
    val mediaFolders by context.symphony.settings.mediaFolders.flow.collectAsState()
    val artworkQuality by context.symphony.settings.artworkQuality.flow.collectAsState()
    val caseSensitiveSorting by context.symphony.settings.caseSensitiveSorting.flow.collectAsState()
    val useMetaphony by context.symphony.settings.useMetaphony.flow.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHostState) {
                AdaptiveSnackbar(it)
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${AppStrings.Settings} - ${AppStrings.Groove}")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
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
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    val defaultSongsFilterPattern = ".*"
                    val minSongDurationRange = 0f..60f

                    SettingsSideHeading(AppStrings.Groove)
                    SpotlightTile(route.initialElement == SettingsViewRoute.ELEMENT_MEDIA_FOLDERS) {
                        SettingsMultiSystemFolderTile(
                            context,
                            icon = {
                                Icon(Icons.Filled.LibraryMusic, null)
                            },
                            title = {
                                Text(AppStrings.MediaFolders)
                            },
                            initialValues = mediaFolders,
                            onChange = { values ->
                                context.symphony.settings.mediaFolders.setValue(values)
                                context.symphony.groove.refreshLibrary()
                            }
                        )
                    }
                    HorizontalDivider()
                    SettingsTextInputTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.FilterAlt, null)
                        },
                        title = {
                            Text(AppStrings.SongsFilterPattern)
                        },
                        value = songsFilterPattern ?: defaultSongsFilterPattern,
                        onReset = {
                            context.symphony.settings.songsFilterPattern.setValue(null)
                        },
                        onChange = { value ->
                            context.symphony.settings.songsFilterPattern.setValue(
                                when (value) {
                                    defaultSongsFilterPattern -> null
                                    else -> value
                                }
                            )
                            context.symphony.groove.refreshLibrary()
                        }
                    )
                    HorizontalDivider()
                    SettingsSliderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.FilterAlt, null)
                        },
                        title = {
                            Text(AppStrings.MinSongDurationFilter)
                        },
                        label = { value ->
                            Text(AppStrings.XSecs(value.toString()))
                        },
                        range = minSongDurationRange,
                        initialValue = minSongDuration.toFloat(),
                        onValue = { value ->
                            value.roundToInt().toFloat()
                        },
                        onChange = { value ->
                            context.symphony.settings.minSongDuration.setValue(value.toInt())
                        },
                        onReset = {
                            context.symphony.settings.minSongDuration.setValue(
                                context.symphony.settings.minSongDuration.defaultValue,
                            )
                        },
                    )
                    HorizontalDivider()
                    SettingsMultiGrooveFolderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.RuleFolder, null)
                        },
                        title = {
                            Text(AppStrings.BlacklistFolders)
                        },
                        explorer = context.symphony.groove.exposer.explorer,
                        initialValues = blacklistFolders,
                        onChange = { values ->
                            context.symphony.settings.blacklistFolders.setValue(values)
                            context.symphony.groove.refreshLibrary()
                        }
                    )
                    HorizontalDivider()
                    SettingsMultiGrooveFolderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.RuleFolder, null)
                        },
                        title = {
                            Text(AppStrings.WhitelistFolders)
                        },
                        explorer = context.symphony.groove.exposer.explorer,
                        initialValues = whitelistFolders,
                        onChange = { values ->
                            context.symphony.settings.whitelistFolders.setValue(values)
                            context.symphony.groove.refreshLibrary()
                        }
                    )
                    HorizontalDivider()
                    SettingsMultiTextOptionTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.SpaceBar, null)
                        },
                        title = {
                            Text(AppStrings.ArtistTagValueSeparators)
                        },
                        values = artistTagSeparators.toList(),
                        onChange = {
                            context.symphony.settings.artistTagSeparators.setValue(it.toSet())
                            context.symphony.groove.refreshLibrary()
                        },
                    )
                    HorizontalDivider()
                    SettingsMultiTextOptionTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.SpaceBar, null)
                        },
                        title = {
                            Text(AppStrings.GenreTagValueSeparators)
                        },
                        values = genreTagSeparators.toList(),
                        onChange = {
                            context.symphony.settings.genreTagSeparators.setValue(it.toSet())
                            context.symphony.groove.refreshLibrary()
                        },
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Image, null)
                        },
                        title = {
                            Text(AppStrings.ArtworkQuality)
                        },
                        value = artworkQuality,
                        values = ImagePreserver.Quality.entries
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            context.symphony.settings.artworkQuality.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.TextFields, null)
                        },
                        title = {
                            Text(AppStrings.CaseSensitiveSorting)
                        },
                        value = caseSensitiveSorting,
                        onChange = { value ->
                            context.symphony.settings.caseSensitiveSorting.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.FindInPage, null)
                        },
                        title = {
                            Text(AppStrings.UseMetaphonyMetadataDecoder)
                        },
                        value = useMetaphony,
                        onChange = { value ->
                            context.symphony.settings.useMetaphony.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSimpleTile(
                        icon = {
                            Icon(Icons.Filled.Storage, null)
                        },
                        title = {
                            Text(AppStrings.ClearSongCache)
                        },
                        onClick = {
                            context.symphony.groove.refreshLibrary(clearCache = true)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    AppStrings.SongCacheCleared,
                                    withDismissAction = true,
                                )
                            }
                        }
                    )
                }
            }
        }
    )
}

fun ImagePreserver.Quality.label(context: ViewContext) = when (this) {
    ImagePreserver.Quality.Low -> AppStrings.Low
    ImagePreserver.Quality.Medium -> AppStrings.Medium
    ImagePreserver.Quality.High -> AppStrings.High
    ImagePreserver.Quality.Loseless -> AppStrings.Loseless
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotlightTile(isInSpotlight: Boolean, content: @Composable (() -> Unit)) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val highlightAlphaAnimated = remember { Animatable(0f) }
    val highlightColor = MaterialTheme.colorScheme.surfaceTint

    LaunchedEffect(isInSpotlight) {
        if (isInSpotlight) {
            bringIntoViewRequester.bringIntoView()
            delay(100)
            highlightAlphaAnimated.animateTo(
                targetValue = 0.3f,
                animationSpec = repeatable(
                    2,
                    TransitionDurations.Fast.asTween(easing = LinearEasing)
                ),
            )
            highlightAlphaAnimated.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .drawWithContent {
                drawContent()
                drawRect(color = highlightColor, alpha = highlightAlphaAnimated.value)
            }
    ) {
        content()
    }
}
