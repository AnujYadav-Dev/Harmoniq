package dev.anujyadav.harmoniq.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Wysiwyg
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.anujyadav.harmoniq.ui.components.IconButtonPlaceholder
import dev.anujyadav.harmoniq.ui.components.TopAppBarMinimalTitle
import dev.anujyadav.harmoniq.ui.components.settings.SettingsOptionTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSideHeading
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSwitchTile
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.NowPlayingControlsLayout
import dev.anujyadav.harmoniq.ui.view.NowPlayingLyricsLayout
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.serialization.Serializable

@Serializable
object NowPlayingSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()
    val nowPlayingControlsLayout by context.symphony.settings.nowPlayingControlsLayout.flow.collectAsState()
    val nowPlayingAdditionalInfo by context.symphony.settings.nowPlayingAdditionalInfo.flow.collectAsState()
    val nowPlayingSeekControls by context.symphony.settings.nowPlayingSeekControls.flow.collectAsState()
    val nowPlayingLyricsLayout by context.symphony.settings.nowPlayingLyricsLayout.flow.collectAsState()
    val lyricsKeepScreenAwake by context.symphony.settings.lyricsKeepScreenAwake.flow.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${AppStrings.Settings} - ${AppStrings.NowPlaying}")
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
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    SettingsSideHeading(AppStrings.NowPlaying)
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Dashboard, null)
                        },
                        title = {
                            Text(AppStrings.ControlsLayout)
                        },
                        value = nowPlayingControlsLayout,
                        values = NowPlayingControlsLayout.entries
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            context.symphony.settings.nowPlayingControlsLayout.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.AutoMirrored.Outlined.Article, null)
                        },
                        title = {
                            Text(AppStrings.LyricsLayout)
                        },
                        value = nowPlayingLyricsLayout,
                        values = NowPlayingLyricsLayout.entries
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            context.symphony.settings.nowPlayingLyricsLayout.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.AutoMirrored.Filled.Wysiwyg, null)
                        },
                        title = {
                            Text(AppStrings.ShowAudioInformation)
                        },
                        value = nowPlayingAdditionalInfo,
                        onChange = { value ->
                            context.symphony.settings.nowPlayingAdditionalInfo.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.Forward30, null)
                        },
                        title = {
                            Text(AppStrings.ShowSeekControls)
                        },
                        value = nowPlayingSeekControls,
                        onChange = { value ->
                            context.symphony.settings.nowPlayingSeekControls.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.Lyrics, null)
                        },
                        title = {
                            Text(AppStrings.KeepScreenAwakeOnLyrics)
                        },
                        value = lyricsKeepScreenAwake,
                        onChange = { value ->
                            context.symphony.settings.lyricsKeepScreenAwake.setValue(value)
                        }
                    )
                }
            }
        }
    )
}

fun NowPlayingControlsLayout.label(context: ViewContext) = when (this) {
    NowPlayingControlsLayout.CompactLeft -> AppStrings.CompactLeft
    NowPlayingControlsLayout.CompactRight -> AppStrings.CompactRight
    NowPlayingControlsLayout.Traditional -> AppStrings.Traditional
}

fun NowPlayingLyricsLayout.label(context: ViewContext) = when (this) {
    NowPlayingLyricsLayout.ReplaceArtwork -> AppStrings.ReplaceArtwork
    NowPlayingLyricsLayout.SeparatePage -> AppStrings.SeparatePage
}
