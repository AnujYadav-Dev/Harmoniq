package dev.anujyadav.harmoniq.ui.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.StyleRes
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.services.Settings
import dev.anujyadav.harmoniq.utils.Logger

/**
 * The system draws the splash screen before any app code runs, so its color cannot
 * be decided at launch. Instead, the theme for the next cold start is registered
 * ahead of time (Android 13+), and randomized colors are picked one launch early.
 * Older versions fall back to the neutral splash theme declared in the manifest.
 */
object SplashTheme {
    fun startSession(activity: Activity, settings: Settings) {
        if (!settings.useMaterialYou.value && settings.randomizePrimaryColor.value) {
            // use the color the splash screen was already shown with
            val sessionColor = settings.nextPrimaryColor.value
                ?.let { ThemeColors.resolvePrimaryColorKey(it) }
                ?: PrimaryThemeColor.entries.random()
            settings.primaryColor.setValue(sessionColor.name)
            settings.nextPrimaryColor.setValue(PrimaryThemeColor.entries.random().name)
        }
        registerForNextLaunch(activity, settings)
    }

    fun registerForNextLaunch(activity: Activity, settings: Settings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        val themeId = when {
            settings.useMaterialYou.value -> R.style.Theme_Harmoniq_SplashScreen_MaterialYou
            else -> resolveTheme(resolveNextPrimaryColor(settings))
        }
        try {
            activity.splashScreen.setSplashScreenTheme(themeId)
        } catch (err: Exception) {
            Logger.warn("SplashTheme", "registering splash screen theme failed", err)
        }
    }

    private fun resolveNextPrimaryColor(settings: Settings) = when {
        settings.randomizePrimaryColor.value -> settings.nextPrimaryColor.value
            ?.let { ThemeColors.resolvePrimaryColorKey(it) }
            ?: PrimaryThemeColor.entries.random().also {
                settings.nextPrimaryColor.setValue(it.name)
            }

        else -> ThemeColors.resolvePrimaryColorKey(settings.primaryColor.value)
    }

    @StyleRes
    private fun resolveTheme(color: PrimaryThemeColor) = when (color) {
        PrimaryThemeColor.Red -> R.style.Theme_Harmoniq_SplashScreen_Red
        PrimaryThemeColor.Orange -> R.style.Theme_Harmoniq_SplashScreen_Orange
        PrimaryThemeColor.Amber -> R.style.Theme_Harmoniq_SplashScreen_Amber
        PrimaryThemeColor.Yellow -> R.style.Theme_Harmoniq_SplashScreen_Yellow
        PrimaryThemeColor.Lime -> R.style.Theme_Harmoniq_SplashScreen_Lime
        PrimaryThemeColor.Green -> R.style.Theme_Harmoniq_SplashScreen_Green
        PrimaryThemeColor.Emerald -> R.style.Theme_Harmoniq_SplashScreen_Emerald
        PrimaryThemeColor.Teal -> R.style.Theme_Harmoniq_SplashScreen_Teal
        PrimaryThemeColor.Cyan -> R.style.Theme_Harmoniq_SplashScreen_Cyan
        PrimaryThemeColor.Sky -> R.style.Theme_Harmoniq_SplashScreen_Sky
        PrimaryThemeColor.Blue -> R.style.Theme_Harmoniq_SplashScreen_Blue
        PrimaryThemeColor.Indigo -> R.style.Theme_Harmoniq_SplashScreen_Indigo
        PrimaryThemeColor.Violet -> R.style.Theme_Harmoniq_SplashScreen_Violet
        PrimaryThemeColor.Purple -> R.style.Theme_Harmoniq_SplashScreen_Purple
        PrimaryThemeColor.Fuchsia -> R.style.Theme_Harmoniq_SplashScreen_Fuchsia
        PrimaryThemeColor.Pink -> R.style.Theme_Harmoniq_SplashScreen_Pink
        PrimaryThemeColor.Rose -> R.style.Theme_Harmoniq_SplashScreen_Rose
    }
}
