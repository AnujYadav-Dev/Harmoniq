package dev.anujyadav.harmoniq.widgets

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.AppearanceSettings
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshot
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshotStore
import dev.anujyadav.harmoniq.ui.theme.PrimaryThemeColor
import dev.anujyadav.harmoniq.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackWidgetSetupTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()
    private val preferences = application
        .getSharedPreferences(AppearanceSettings.PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val receivers = listOf(
        NowPlayingBarReceiver::class.java,
        FullPlayerReceiver::class.java,
        AlbumArtReceiver::class.java,
        QuickActionsReceiver::class.java,
        QuickActionsSmallReceiver::class.java,
    )

    @Test
    fun everyWidgetIsDeclaredWithItsOwnPickerEntry() {
        val labels = receivers.map {
            val info = application.packageManager.getReceiverInfo(
                ComponentName(application, it),
                PackageManager.GET_META_DATA,
            )
            assertFalse(it.name, info.exported)
            assertNotNull(it.name, application.resources.getXml(info.metaData.getInt("android.appwidget.provider")))
            application.getString(info.labelRes)
        }
        assertEquals(receivers.size, labels.toSet().size)
    }

    @Test
    fun aFirstWidgetDoesNotCreateSymphony() {
        NowPlayingBarReceiver().onEnabled(application)
        assertNull(application.symphonyIfCreated)
    }

    @Test
    fun theStateNeedsNoSymphony() = runBlocking {
        PlaybackWidgetState.observe(application, 100, 1).first()
        assertNull(application.symphonyIfCreated)
    }

    @Test
    fun savedPlayingOnlyCountsWithAPlayerProcess() = runBlocking {
        val store = PlaybackSnapshotStore(application)
        store.write(PlaybackSnapshot(isPlaying = true))
        assertFalse(PlaybackWidgetState.observe(application, 100, 1).first().isPlaying)
        application.symphony
        assertTrue(PlaybackWidgetState.observe(application, 100, 1).first().isPlaying)
    }

    @Test
    fun appearanceFollowsSettings() {
        assertEquals(
            WidgetAppearance(ThemeMode.SYSTEM, true, null),
            WidgetAppearance.read(application),
        )
        application.symphony.settings.run {
            themeMode.setValue(ThemeMode.BLACK)
            useMaterialYou.setValue(false)
            primaryColor.setValue(PrimaryThemeColor.entries.first().name)
        }
        assertEquals(
            WidgetAppearance(ThemeMode.BLACK, false, PrimaryThemeColor.entries.first().name),
            WidgetAppearance.read(application),
        )
    }

    @Test
    fun anUnknownThemeFallsBack() {
        preferences.edit().putString(AppearanceSettings.KEY_THEME_MODE, "gone").commit()
        assertEquals(ThemeMode.SYSTEM, WidgetAppearance.read(application).themeMode)
    }

    @Test
    fun onlyMaterialYouInSystemModeLeavesColorsToTheSystem() {
        assertTrue(WidgetAppearance(ThemeMode.SYSTEM, true, null).followsSystemColors)
        assertFalse(WidgetAppearance(ThemeMode.DARK, true, null).followsSystemColors)
        assertFalse(WidgetAppearance(ThemeMode.SYSTEM, false, null).followsSystemColors)
    }

    @Test
    @Config(sdk = [30])
    fun beforeAndroid12TheAppsAccentIsUsed() {
        assertFalse(WidgetAppearance(ThemeMode.SYSTEM, true, null).followsSystemColors)
    }

    @Test
    fun theAccentReachesTheColors() {
        val names = PrimaryThemeColor.entries.map { it.name }
        val first = WidgetAppearance(ThemeMode.LIGHT, false, names.first()).colorProviders(application)
        val last = WidgetAppearance(ThemeMode.LIGHT, false, names.last()).colorProviders(application)
        assertNotEquals(first.primary.getColor(application), last.primary.getColor(application))
    }

    @Test
    fun aForcedModeIgnoresTheSystem() {
        val light = WidgetAppearance(ThemeMode.LIGHT, false, null).colorProviders(application)
        val dark = WidgetAppearance(ThemeMode.DARK, false, null).colorProviders(application)
        assertNotEquals(light.surface.getColor(application), dark.surface.getColor(application))
    }

    @Test
    fun progressStaysInRange() {
        assertEquals(0.5f, progressOf(5, 10))
        assertEquals(1f, progressOf(50, 10))
        assertEquals(0f, progressOf(5, 0))
        assertEquals(0f, progressOf(-5, 10))
    }
}
