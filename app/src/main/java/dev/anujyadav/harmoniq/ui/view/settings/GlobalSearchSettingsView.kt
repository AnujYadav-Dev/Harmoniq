package dev.anujyadav.harmoniq.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearch
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearchMessages
import dev.anujyadav.harmoniq.ui.components.IconButtonPlaceholder
import dev.anujyadav.harmoniq.ui.components.TopAppBarMinimalTitle
import dev.anujyadav.harmoniq.ui.components.settings.SettingsOptionTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSideHeading
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSimpleTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSwitchTile
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.globalsearch.label
import dev.anujyadav.harmoniq.ui.view.globalsearch.rememberDownloadsFolderPicker
import dev.anujyadav.harmoniq.ui.view.globalsearch.supportedDownloadFormats
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.serialization.Serializable
import androidx.compose.runtime.getValue

@Serializable
object GlobalSearchSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()
    val globalSearch = context.symphony.globalSearch
    val downloadFormat by context.symphony.settings.globalSearchDownloadFormat.flow.collectAsState()
    val saveLrcFiles by context.symphony.settings.globalSearchSaveLrcFiles.flow.collectAsState()
    // the page stays reachable while it's off, so it can be turned back on here
    val isEnabled by context.symphony.settings.globalSearchEnabled.flow.collectAsState()
    val autoplayPreviews by context.symphony.settings.globalSearchAutoplayPreviews.flow.collectAsState()
    val meta by globalSearch.meta.collectAsState()
    val isFolderCovered by globalSearch.isDownloadsFolderCovered.collectAsState()
    val pickFolder = rememberDownloadsFolderPicker(context)

    LaunchedEffect(isEnabled) {
        if (isEnabled) globalSearch.ensureMeta()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${AppStrings.Settings} - ${AppStrings.GlobalSearch}")
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
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.Public, null)
                        },
                        title = {
                            Text(AppStrings.EnableGlobalSearch)
                        },
                        subtitle = {
                            Text(AppStrings.EnableGlobalSearchHint)
                        },
                        value = isEnabled,
                        onChange = { value ->
                            context.symphony.settings.globalSearchEnabled.setValue(value)
                        }
                    )
                    GlobalSearchServerSettings(context, enabled = isEnabled)
                    SettingsSideHeading(AppStrings.Preferences)
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.PlayCircle, null)
                        },
                        title = {
                            Text(AppStrings.AutoplayPreviews)
                        },
                        subtitle = {
                            Text(AppStrings.AutoplayPreviewsHint)
                        },
                        value = autoplayPreviews,
                        enabled = isEnabled,
                        onChange = { value ->
                            context.symphony.settings.globalSearchAutoplayPreviews.setValue(value)
                        }
                    )
                    SettingsSideHeading(AppStrings.Downloads)
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.AudioFile, null)
                        },
                        title = {
                            Text(AppStrings.DownloadFormat)
                        },
                        value = downloadFormat,
                        values = supportedDownloadFormats().associateWith { it.label() },
                        enabled = isEnabled,
                        onChange = { value ->
                            context.symphony.settings.globalSearchDownloadFormat.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.Lyrics, null)
                        },
                        title = {
                            Text(AppStrings.AlsoSaveLrcFiles)
                        },
                        subtitle = {
                            Text(AppStrings.AlsoSaveLrcFilesHint)
                        },
                        value = saveLrcFiles,
                        enabled = isEnabled,
                        onChange = { value ->
                            context.symphony.settings.globalSearchSaveLrcFiles.setValue(value)
                        }
                    )
                    if (!isFolderCovered) {
                        HorizontalDivider()
                        SettingsSimpleTile(
                            icon = {
                                Icon(Icons.Filled.CreateNewFolder, null)
                            },
                            title = {
                                Text(AppStrings.NotInLibraryAddFolder)
                            },
                            enabled = isEnabled,
                            onClick = pickFolder,
                        )
                    }
                    HorizontalDivider()
                    SettingsSimpleTile(
                        icon = {
                            Icon(Icons.Filled.Dns, null)
                        },
                        title = {
                            Text(AppStrings.ServerStatus)
                        },
                        subtitle = {
                            Text(serverStatusText(meta))
                        },
                        enabled = isEnabled,
                        onClick = { globalSearch.refreshMeta() },
                    )
                }
            }
        }
    )
}

private fun serverStatusText(meta: GlobalSearch.MetaState) = when (meta) {
    GlobalSearch.MetaState.Unknown, GlobalSearch.MetaState.Loading -> AppStrings.ServerStatusChecking
    is GlobalSearch.MetaState.Ready -> AppStrings.ServerVersionXYoutubeY(
        meta.meta.serverVersion,
        meta.meta.youtubeStatus.apiValue,
    )

    is GlobalSearch.MetaState.Failed -> "${GlobalSearchMessages.forError(meta.error)} · ${AppStrings.TapToCheckAgain}"
}
