package dev.anujyadav.harmoniq.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import dev.anujyadav.harmoniq.HarmoniqActivity
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.utils.AppStrings

/**
 * The launcher icons to choose from. Android only offers a fixed set declared ahead of time:
 * each one is an activity-alias in the manifest, and enabling one is what changes the icon.
 */
enum class AppIcon(@DrawableRes val icon: Int, val label: String) {
    Classic(R.mipmap.ic_launcher, AppStrings.AppIconClassic),
    Light(R.mipmap.ic_launcher_light, AppStrings.Light),
    Dark(R.mipmap.ic_launcher_dark, AppStrings.Dark),
    Red(R.mipmap.ic_launcher_red, AppStrings.Red),
    Orange(R.mipmap.ic_launcher_orange, AppStrings.Orange),
    Amber(R.mipmap.ic_launcher_amber, AppStrings.Amber),
    Yellow(R.mipmap.ic_launcher_yellow, AppStrings.Yellow),
    Lime(R.mipmap.ic_launcher_lime, AppStrings.Lime),
    Green(R.mipmap.ic_launcher_green, AppStrings.Green),
    Emerald(R.mipmap.ic_launcher_emerald, AppStrings.Emerald),
    Teal(R.mipmap.ic_launcher_teal, AppStrings.Teal),
    Cyan(R.mipmap.ic_launcher_cyan, AppStrings.Cyan),
    Sky(R.mipmap.ic_launcher_sky, AppStrings.Sky),
    Blue(R.mipmap.ic_launcher_blue, AppStrings.Blue),
    Indigo(R.mipmap.ic_launcher_indigo, AppStrings.Indigo),
    Violet(R.mipmap.ic_launcher_violet, AppStrings.Violet),
    Purple(R.mipmap.ic_launcher_purple, AppStrings.Purple),
    Fuchsia(R.mipmap.ic_launcher_fuchsia, AppStrings.Fuchsia),
    Pink(R.mipmap.ic_launcher_pink, AppStrings.Pink),
    Rose(R.mipmap.ic_launcher_rose, AppStrings.Rose);

    val componentClassName
        get() = when (this) {
            Classic -> "$PACKAGE_NAME.MainActivity"
            else -> "$PACKAGE_NAME.AppIcon$name"
        }

    /**
     * Android closes the task opened through a launcher entry when that entry is disabled, which
     * stops playback with it, and some phones kill the app too. It can't be avoided without
     * something in between the launcher and the activity, which costs the system's splash screen
     * and open animation on every launch. So call this only when nothing is playing and the app
     * isn't in use.
     *
     * @return whether the icon changed.
     */
    fun applyIfNeeded(context: Context): Boolean {
        if (isEnabled(context)) {
            return false
        }
        apply(context)
        return true
    }

    private fun isEnabled(context: Context) = context.packageManager
        .getComponentEnabledSetting(ComponentName(context.packageName, componentClassName))
        .let { state ->
            when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                // as declared in the manifest
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> this == Classic
                else -> false
            }
        }

    internal fun apply(context: Context) {
        // the new one first, so there's never a moment without a launcher entry
        setEnabled(context, true)
        entries.filter { it != this }.forEach { it.setEnabled(context, false) }
    }

    private fun setEnabled(context: Context, enabled: Boolean) {
        val state = when {
            enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val component = ComponentName(context.packageName, componentClassName)
        if (context.packageManager.getComponentEnabledSetting(component) != state) {
            context.packageManager.setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private companion object {
        val PACKAGE_NAME: String = HarmoniqActivity::class.java.`package`!!.name
    }
}
