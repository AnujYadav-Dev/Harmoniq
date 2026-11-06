package dev.anujyadav.harmoniq.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplashPaletteTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun color(name: String): Int {
        val id = context.resources.getIdentifier(name, "color", context.packageName)
        assertTrue("missing color resource $name", id != 0)
        return ContextCompat.getColor(context, id)
    }

    @Test
    fun everyThemeColorHasAReadableLogo() {
        PrimaryThemeColor.entries.forEach { theme ->
            val key = theme.name.lowercase()
            val background = color("theme_$key")
            val shade = color("splash_logo_$key")
            val ink = color("splash_logo_ink_$key")
            assertContrast("$theme ink on background", ink, background, MIN_INK_CONTRAST)
            assertContrast("$theme ink on shade", ink, shade, MIN_INK_CONTRAST)
            assertContrast("$theme shade on background", shade, background, MIN_SHADE_CONTRAST)
        }
    }

    @Test
    fun logoColorsStayInTheBackgroundsColorFamily() {
        PrimaryThemeColor.entries.forEach { theme ->
            val key = theme.name.lowercase()
            val hue = hueOf(color("theme_$key"))
            assertSameFamily("$theme shade", hue, hueOf(color("splash_logo_$key")))
            assertSameFamily("$theme ink", hue, hueOf(color("splash_logo_ink_$key")))
        }
    }

    @Test
    fun themeColorResourcesMatchThemeColors() {
        PrimaryThemeColor.entries.forEach { theme ->
            assertEquals(
                theme.name,
                ThemeColors.resolvePrimaryColor(theme).toArgb(),
                color("theme_${theme.name.lowercase()}"),
            )
        }
    }

    private fun hueOf(color: Int) = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }[0]

    private fun assertSameFamily(message: String, expected: Float, actual: Float) {
        val distance = abs(expected - actual).let { min(it, 360f - it) }
        assertTrue("$message is $distance degrees away", distance <= MAX_HUE_DISTANCE)
    }

    private fun assertContrast(message: String, foreground: Int, background: Int, minimum: Double) {
        val contrast = ColorUtils.calculateContrast(foreground, background)
        assertTrue("$message is $contrast", contrast >= minimum)
    }

    companion object {
        private const val MIN_INK_CONTRAST = 3.0

        // a different shade of one color, not a different color
        private const val MIN_SHADE_CONTRAST = 1.8
        private const val MAX_HUE_DISTANCE = 6f
    }
}
