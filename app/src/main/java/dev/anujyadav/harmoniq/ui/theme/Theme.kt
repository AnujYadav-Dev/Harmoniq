package dev.anujyadav.harmoniq.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.ui.helpers.ViewContext

enum class ThemeMode {
    SYSTEM,
    SYSTEM_BLACK,
    LIGHT,
    DARK,
    BLACK,
}

enum class ColorSchemeMode {
    LIGHT,
    DARK,
    BLACK
}

@Composable
fun SymphonyTheme(
    context: ViewContext,
    content: @Composable () -> Unit,
) {
    val themeMode by context.symphony.settings.themeMode.flow.collectAsState()
    val useMaterialYou by context.symphony.settings.useMaterialYou.flow.collectAsState()
    val primaryColorName by context.symphony.settings.primaryColor.flow.collectAsState()
    val fontName by context.symphony.settings.fontFamily.flow.collectAsState()
    val fontScale by context.symphony.settings.fontScale.flow.collectAsState()
    val contentScale by context.symphony.settings.contentScale.flow.collectAsState()

    val colorSchemeMode = themeMode.toColorSchemeMode(isSystemInDarkTheme())
    val colorScheme = resolveColorScheme(
        LocalContext.current,
        colorSchemeMode,
        useMaterialYou,
        primaryColorName,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as Activity
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view)
                .isAppearanceLightStatusBars = colorSchemeMode == ColorSchemeMode.LIGHT
        }
    }

    val textDirection = TextDirection.Ltr
    val typography = SymphonyTypography.toTypography(
        SymphonyTypography.resolveFont(fontName),
        textDirection,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    LocalDensity.current.density * contentScale,
                    LocalDensity.current.fontScale * fontScale,
                )
            ) {
                content()
            }
        }
    )
}

fun resolveColorScheme(
    context: Context,
    colorSchemeMode: ColorSchemeMode,
    useMaterialYou: Boolean,
    primaryColorName: String?,
) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useMaterialYou) {
    when (colorSchemeMode) {
        ColorSchemeMode.LIGHT -> dynamicLightColorScheme(context)
        ColorSchemeMode.DARK -> dynamicDarkColorScheme(context)
        ColorSchemeMode.BLACK -> ThemeColorSchemes.toBlackColorScheme(dynamicDarkColorScheme(context))
    }
} else {
    val primaryColor = ThemeColors.resolvePrimaryColor(primaryColorName)
    when (colorSchemeMode) {
        ColorSchemeMode.LIGHT -> ThemeColorSchemes.createLightColorScheme(primaryColor)
        ColorSchemeMode.DARK -> ThemeColorSchemes.createDarkColorScheme(primaryColor)
        ColorSchemeMode.BLACK -> ThemeColorSchemes.createBlackColorScheme(primaryColor)
    }
}

fun ThemeMode.toColorSchemeMode(symphony: Symphony): ColorSchemeMode {
    val isSystemInDarkTheme = symphony.applicationContext.resources.configuration.uiMode.let {
        (it and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    return toColorSchemeMode(isSystemInDarkTheme)
}

fun ThemeMode.toColorSchemeMode(isSystemInDarkTheme: Boolean) = when (this) {
    ThemeMode.SYSTEM -> if (isSystemInDarkTheme) ColorSchemeMode.DARK else ColorSchemeMode.LIGHT
    ThemeMode.SYSTEM_BLACK -> if (isSystemInDarkTheme) ColorSchemeMode.BLACK else ColorSchemeMode.LIGHT
    ThemeMode.LIGHT -> ColorSchemeMode.LIGHT
    ThemeMode.DARK -> ColorSchemeMode.DARK
    ThemeMode.BLACK -> ColorSchemeMode.BLACK
}

fun ColorSchemeMode.isLight() = this == ColorSchemeMode.LIGHT
