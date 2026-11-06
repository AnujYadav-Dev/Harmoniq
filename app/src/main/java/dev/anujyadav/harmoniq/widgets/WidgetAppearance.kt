package dev.anujyadav.harmoniq.widgets

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import dev.anujyadav.harmoniq.services.AppearanceSettings
import dev.anujyadav.harmoniq.ui.theme.ThemeMode
import dev.anujyadav.harmoniq.ui.theme.resolveColorScheme
import dev.anujyadav.harmoniq.ui.theme.toColorSchemeMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The app's look, for drawing widgets with the app closed: read straight from where Settings
 * keeps it, as creating Symphony for a widget is what this avoids.
 */
internal data class WidgetAppearance(
    val themeMode: ThemeMode,
    val useMaterialYou: Boolean,
    val primaryColorName: String?,
) {
    /** The system's own colors follow the wallpaper and dark mode, with nothing to redraw. */
    val followsSystemColors: Boolean
        get() = useMaterialYou &&
                themeMode == ThemeMode.SYSTEM &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun colorProviders(context: Context): ColorProviders {
        fun scheme(isSystemDark: Boolean) = resolveColorScheme(
            context,
            themeMode.toColorSchemeMode(isSystemDark),
            useMaterialYou,
            primaryColorName,
        )
        return ColorProviders(light = scheme(false), dark = scheme(true))
    }

    companion object {
        private fun preferences(context: Context) = context.applicationContext
            .getSharedPreferences(AppearanceSettings.PREFERENCES_NAME, Context.MODE_PRIVATE)

        fun read(context: Context) = read(preferences(context))

        private fun read(preferences: SharedPreferences) = WidgetAppearance(
            themeMode = preferences.getString(AppearanceSettings.KEY_THEME_MODE, null)
                ?.let { name -> ThemeMode.entries.find { it.name == name } }
                ?: AppearanceSettings.DEFAULT_THEME_MODE,
            useMaterialYou = preferences.getBoolean(
                AppearanceSettings.KEY_MATERIAL_YOU,
                AppearanceSettings.DEFAULT_MATERIAL_YOU,
            ),
            primaryColorName = preferences.getString(AppearanceSettings.KEY_PRIMARY_COLOR, null),
        )

        fun changes(context: Context): Flow<WidgetAppearance> = callbackFlow {
            val preferences = preferences(context)
            trySend(read(preferences))
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                trySend(read(preferences))
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()
    }
}

@Composable
internal fun WidgetTheme(
    appearance: WidgetAppearance,
    context: Context,
    content: @Composable () -> Unit,
) {
    GlanceTheme(
        colors = when {
            appearance.followsSystemColors -> GlanceTheme.colors
            else -> appearance.colorProviders(context)
        },
        content = content,
    )
}
