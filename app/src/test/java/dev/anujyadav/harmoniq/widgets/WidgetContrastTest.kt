package dev.anujyadav.harmoniq.widgets

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.anujyadav.harmoniq.ui.theme.PrimaryThemeColor
import dev.anujyadav.harmoniq.ui.theme.ThemeColorSchemes
import dev.anujyadav.harmoniq.ui.theme.ThemeColors
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Widget text against what's behind it, for every theme and accent color the app offers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetContrastTest {
    private fun schemes(): List<Pair<String, ColorScheme>> = PrimaryThemeColor.entries.flatMap { key ->
        val color = ThemeColors.resolvePrimaryColor(key)
        listOf(
            "$key light" to ThemeColorSchemes.createLightColorScheme(color).forWidgets(),
            "$key dark" to ThemeColorSchemes.createDarkColorScheme(color).forWidgets(),
            "$key black" to ThemeColorSchemes.createBlackColorScheme(color).forWidgets(),
        )
    }

    private fun failures(minimum: Double, pair: (ColorScheme) -> Pair<Color, Color>) = schemes()
        .map { (name, scheme) ->
            val (front, back) = pair(scheme)
            name to contrastOf(front, back)
        }
        .filter { it.second < minimum }
        .map { (name, value) -> "$name %.2f".format(value) }

    @Test
    fun titlesAndLyricsAreReadable() {
        assertEquals(emptyList<String>(), failures(MIN_TEXT) { it.onSurface to it.surface })
    }

    @Test
    fun subtitlesAreReadable() {
        assertEquals(emptyList<String>(), failures(MIN_TEXT) { it.onSurfaceVariant to it.surface })
    }

    @Test
    fun rowTextIsReadable() {
        assertEquals(emptyList<String>(), failures(MIN_TEXT) { it.onSurface to it.surfaceVariant })
        assertEquals(emptyList<String>(), failures(MIN_TEXT) { it.onSurfaceVariant to it.surfaceVariant })
    }

    @Test
    fun theAccentOnASurfaceIsVisible() {
        assertEquals(emptyList<String>(), failures(MIN_ICON) { it.primary to it.surface })
    }

    @Test
    fun thePlayIconIsVisibleOnItsButton() {
        assertEquals(emptyList<String>(), failures(MIN_ICON) { it.onPrimary to it.primary })
    }

    @Test
    fun anAccentThatCanBeSeenStaysAsItIs() {
        val color = ThemeColors.resolvePrimaryColor(PrimaryThemeColor.Blue)
        val scheme = ThemeColorSchemes.createDarkColorScheme(color)
        assertEquals(scheme.primary, scheme.forWidgets().primary)
    }

    private companion object {
        const val MIN_TEXT = 4.5
        const val MIN_ICON = 3.0
    }
}
