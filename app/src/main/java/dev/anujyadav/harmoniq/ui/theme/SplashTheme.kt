package dev.anujyadav.harmoniq.ui.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.StyleRes
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.services.Settings
import dev.anujyadav.harmoniq.utils.Logger

enum class SplashAppearance {
    // the app's accent color, whether fixed, randomized or from Material You
    MatchAppTheme,

    // a different color on every start, even with a fixed accent or Material You
    RandomColor,

    // the neutral black and white splash
    Neutral,
}

/**
 * The system draws the splash screen before any app code runs, so its color cannot
 * be decided at launch. Instead, the theme for the next cold start is registered
 * ahead of time (Android 12+), and randomized colors are picked one launch early.
 * Older versions fall back to the neutral splash theme declared in the manifest.
 */
object SplashTheme {
    val isCustomizable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun startSession(activity: Activity, settings: Settings) {
        if (isAccentRandomized(settings)) {
            // use the color the splash screen was already shown with
            val sessionColor = settings.nextPrimaryColor.value
                ?.let { ThemeColors.resolvePrimaryColorKey(it) }
                ?: PrimaryThemeColor.entries.random()
            settings.primaryColor.setValue(sessionColor.name)
            settings.nextPrimaryColor.setValue(PrimaryThemeColor.entries.random().name)
        } else if (settings.splashAppearance.value == SplashAppearance.RandomColor) {
            settings.nextSplashColor.setValue(PrimaryThemeColor.entries.random().name)
        }
        registerForNextLaunch(activity, settings)
    }

    fun registerForNextLaunch(activity: Activity, settings: Settings) {
        if (!isCustomizable) {
            return
        }
        val themeId = when (settings.splashAppearance.value) {
            SplashAppearance.Neutral -> R.style.Theme_Harmoniq_SplashScreen
            SplashAppearance.RandomColor -> resolveTheme(resolveNextRandomColor(settings))
            SplashAppearance.MatchAppTheme -> when {
                settings.useMaterialYou.value -> R.style.Theme_Harmoniq_SplashScreen_MaterialYou
                else -> resolveTheme(resolveNextPrimaryColor(settings))
            }
        }
        try {
            activity.splashScreen.setSplashScreenTheme(themeId)
        } catch (err: Exception) {
            Logger.warn("SplashTheme", "registering splash screen theme failed", err)
        }
    }

    private fun isAccentRandomized(settings: Settings) =
        !settings.useMaterialYou.value && settings.randomizePrimaryColor.value

    private fun resolveNextPrimaryColor(settings: Settings) = when {
        settings.randomizePrimaryColor.value -> settings.nextPrimaryColor.value
            ?.let { ThemeColors.resolvePrimaryColorKey(it) }
            ?: PrimaryThemeColor.entries.random().also {
                settings.nextPrimaryColor.setValue(it.name)
            }

        else -> ThemeColors.resolvePrimaryColorKey(settings.primaryColor.value)
    }

    private fun resolveNextRandomColor(settings: Settings) = when {
        // a randomized accent is already picked ahead, sharing it keeps the splash and app matched
        isAccentRandomized(settings) -> resolveNextPrimaryColor(settings)
        else -> settings.nextSplashColor.value
            ?.let { ThemeColors.resolvePrimaryColorKey(it) }
            ?: PrimaryThemeColor.entries.random().also {
                settings.nextSplashColor.setValue(it.name)
            }
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
