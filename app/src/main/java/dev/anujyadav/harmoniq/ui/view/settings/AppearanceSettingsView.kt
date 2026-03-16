package dev.anujyadav.harmoniq.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.TextIncrease
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
import dev.anujyadav.harmoniq.ui.components.settings.SettingsFloatInputTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsOptionTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSideHeading
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSwitchTile
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.theme.PrimaryThemeColor
import dev.anujyadav.harmoniq.ui.theme.SymphonyTypography
import dev.anujyadav.harmoniq.ui.theme.ThemeColors
import dev.anujyadav.harmoniq.ui.theme.ThemeMode
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.serialization.Serializable

private val scalingPresets = listOf(
    0.25f, 0.5f, 0.75f, 0.9f, 1f,
    1.1f, 1.25f, 1.5f, 1.75f, 2f,
    2.25f, 2.5f, 2.75f, 3f,
)

@Serializable
object AppearanceSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()

    val fontFamily by context.symphony.settings.fontFamily.flow.collectAsState()
    val themeMode by context.symphony.settings.themeMode.flow.collectAsState()
    val useMaterialYou by context.symphony.settings.useMaterialYou.flow.collectAsState()
    val primaryColor by context.symphony.settings.primaryColor.flow.collectAsState()
    val fontScale by context.symphony.settings.fontScale.flow.collectAsState()
    val contentScale by context.symphony.settings.contentScale.flow.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${AppStrings.Settings} - ${AppStrings.Appearance}")
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
                    SettingsSideHeading(AppStrings.Appearance)
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.TextFormat, null)
                        },
                        title = {
                            Text(AppStrings.Font)
                        },
                        value = SymphonyTypography.resolveFont(fontFamily).fontName,
                        values = SymphonyTypography.all.keys.associateWith { it },
                        onChange = { value ->
                            context.symphony.settings.fontFamily.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsFloatInputTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.TextIncrease, null)
                        },
                        title = {
                            Text(AppStrings.FontScale)
                        },
                        value = fontScale,
                        presets = scalingPresets,
                        labelText = { "x$it" },
                        onReset = {
                            context.symphony.settings.fontScale.setValue(
                                context.symphony.settings.fontScale.defaultValue,
                            )
                        },
                        onChange = { value ->
                            context.symphony.settings.fontScale.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsFloatInputTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.PhotoSizeSelectLarge, null)
                        },
                        title = {
                            Text(AppStrings.ContentScale)
                        },
                        value = contentScale,
                        presets = scalingPresets,
                        labelText = { "x$it" },
                        onReset = {
                            context.symphony.settings.contentScale.setValue(
                                context.symphony.settings.contentScale.defaultValue,
                            )
                        },
                        onChange = { value ->
                            context.symphony.settings.contentScale.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Palette, null)
                        },
                        title = {
                            Text(AppStrings.Theme)
                        },
                        value = themeMode,
                        values = mapOf(
                            ThemeMode.SYSTEM to AppStrings.SystemLightDark,
                            ThemeMode.SYSTEM_BLACK to AppStrings.SystemLightBlack,
                            ThemeMode.LIGHT to AppStrings.Light,
                            ThemeMode.DARK to AppStrings.Dark,
                            ThemeMode.BLACK to AppStrings.Black,
                        ),
                        onChange = { value ->
                            context.symphony.settings.themeMode.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.Face, null)
                        },
                        title = {
                            Text(AppStrings.MaterialYou)
                        },
                        value = useMaterialYou,
                        onChange = { value ->
                            context.symphony.settings.useMaterialYou.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Colorize, null)
                        },
                        title = {
                            Text(AppStrings.PrimaryColor)
                        },
                        value = ThemeColors.resolvePrimaryColorKey(primaryColor),
                        values = PrimaryThemeColor.entries.associateWith { it.label(context) },
                        enabled = !useMaterialYou,
                        onChange = { value ->
                            context.symphony.settings.primaryColor.setValue(value.name)
                        }
                    )
                }
            }
        }
    )
}

fun PrimaryThemeColor.label(context: ViewContext) = when (this) {
    PrimaryThemeColor.Red -> AppStrings.Red
    PrimaryThemeColor.Orange -> AppStrings.Orange
    PrimaryThemeColor.Amber -> AppStrings.Amber
    PrimaryThemeColor.Yellow -> AppStrings.Yellow
    PrimaryThemeColor.Lime -> AppStrings.Lime
    PrimaryThemeColor.Green -> AppStrings.Green
    PrimaryThemeColor.Emerald -> AppStrings.Emerald
    PrimaryThemeColor.Teal -> AppStrings.Teal
    PrimaryThemeColor.Cyan -> AppStrings.Cyan
    PrimaryThemeColor.Sky -> AppStrings.Sky
    PrimaryThemeColor.Blue -> AppStrings.Blue
    PrimaryThemeColor.Indigo -> AppStrings.Indigo
    PrimaryThemeColor.Violet -> AppStrings.Violet
    PrimaryThemeColor.Purple -> AppStrings.Purple
    PrimaryThemeColor.Fuchsia -> AppStrings.Fuchsia
    PrimaryThemeColor.Pink -> AppStrings.Pink
    PrimaryThemeColor.Rose -> AppStrings.Rose
}
