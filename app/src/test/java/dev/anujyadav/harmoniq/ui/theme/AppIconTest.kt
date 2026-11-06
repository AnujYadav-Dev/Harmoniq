package dev.anujyadav.harmoniq.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppIconTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun component(icon: AppIcon) = ComponentName(context.packageName, icon.componentClassName)

    private fun isEnabled(icon: AppIcon) = context.packageManager
        .getActivityInfo(component(icon), PackageManager.MATCH_DISABLED_COMPONENTS)
        .let { info ->
            when (context.packageManager.getComponentEnabledSetting(component(icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> info.enabled
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                else -> false
            }
        }

    @Test
    fun everyIconIsDeclaredInTheManifest() {
        AppIcon.entries.forEach { icon ->
            assertNotNull(
                icon.name,
                context.packageManager.getActivityInfo(component(icon), PackageManager.MATCH_DISABLED_COMPONENTS),
            )
        }
    }

    @Test
    fun everyThemeColorHasAnIcon() {
        PrimaryThemeColor.entries.forEach { color ->
            assertNotNull(color.name, AppIcon.entries.find { it.name == color.name })
        }
    }

    @Test
    fun onlyTheClassicIconIsEnabledAtFirst() {
        assertEquals(listOf(AppIcon.Classic), AppIcon.entries.filter { isEnabled(it) })
    }

    // anything in between gives a launch without the system's splash screen and open animation
    @Test
    fun everyIconOpensThePlayerDirectly() {
        AppIcon.entries.forEach { icon ->
            val info = context.packageManager
                .getActivityInfo(component(icon), PackageManager.MATCH_DISABLED_COMPONENTS)
            assertEquals(icon.name, HarmoniqActivity::class.java.name, info.targetActivity)
        }
    }

    @Test
    fun applyIfNeededSwitchesOnlyWhenTheIconChanged() {
        AppIcon.Classic.applyIfNeeded(context)
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            context.packageManager.getComponentEnabledSetting(component(AppIcon.Classic)),
        )
        AppIcon.Rose.applyIfNeeded(context)
        assertEquals(listOf(AppIcon.Rose), AppIcon.entries.filter { isEnabled(it) })
        AppIcon.Classic.applyIfNeeded(context)
        assertEquals(listOf(AppIcon.Classic), AppIcon.entries.filter { isEnabled(it) })
    }

    @Test
    fun applyingLeavesExactlyOneEnabled() {
        AppIcon.Teal.apply(context)
        assertEquals(listOf(AppIcon.Teal), AppIcon.entries.filter { isEnabled(it) })
        AppIcon.Classic.apply(context)
        assertEquals(listOf(AppIcon.Classic), AppIcon.entries.filter { isEnabled(it) })
    }
}
