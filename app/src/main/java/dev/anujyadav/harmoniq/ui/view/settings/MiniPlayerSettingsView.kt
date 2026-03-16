package dev.anujyadav.harmoniq.ui.view.settings


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.SkipNext
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
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSideHeading
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSwitchTile
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.serialization.Serializable

@Serializable
object MiniPlayerSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayerSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()
    val miniPlayerTrackControls by context.symphony.settings.miniPlayerTrackControls.flow.collectAsState()
    val miniPlayerSeekControls by context.symphony.settings.miniPlayerSeekControls.flow.collectAsState()
    val miniPlayerTextMarquee by context.symphony.settings.miniPlayerTextMarquee.flow.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${AppStrings.Settings} - ${AppStrings.MiniPlayer}")
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
                    SettingsSideHeading(AppStrings.MiniPlayer)
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.SkipNext, null)
                        },
                        title = {
                            Text(AppStrings.ShowTrackControls)
                        },
                        value = miniPlayerTrackControls,
                        onChange = { value ->
                            context.symphony.settings.miniPlayerTrackControls.setValue(value)
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
                        value = miniPlayerSeekControls,
                        onChange = { value ->
                            context.symphony.settings.miniPlayerSeekControls.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.KeyboardDoubleArrowRight, null)
                        },
                        title = {
                            Text(AppStrings.MiniPlayerTextMarquee)
                        },
                        value = miniPlayerTextMarquee,
                        onChange = { value ->
                            context.symphony.settings.miniPlayerTextMarquee.setValue(value)
                        }
                    )
                }
            }
        }
    )
}
