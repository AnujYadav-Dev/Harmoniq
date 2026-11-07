package dev.anujyadav.harmoniq.widgets

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.util.Xml
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetSizingTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()

    private val receivers: List<GlanceAppWidgetReceiver> = listOf(
        NowPlayingBarReceiver(),
        FullPlayerReceiver(),
        AlbumArtReceiver(),
        QuickActionsReceiver(),
        QuickActionsSmallReceiver(),
        PlaylistShortcutsReceiver(),
        UpNextReceiver(),
        SuggestedReceiver(),
        LyricsReceiver(),
    )

    private fun providerXml(receiver: GlanceAppWidgetReceiver): XmlResourceParser {
        val info = application.packageManager.getReceiverInfo(
            ComponentName(application, receiver.javaClass),
            PackageManager.GET_META_DATA,
        )
        val parser = application.resources.getXml(info.metaData.getInt("android.appwidget.provider"))
        while (parser.next() != XmlPullParser.START_TAG) {
            // on to the root tag
        }
        return parser
    }

    private fun attribute(parser: XmlResourceParser, id: Int): Float {
        val values = application.resources.obtainAttributes(Xml.asAttributeSet(parser), intArrayOf(id))
        val size = values.getDimension(0, Float.NaN)
        values.recycle()
        return size / application.resources.displayMetrics.density
    }

    /** What the launcher can shrink it to. Android picks a layout from this, so it has to have one. */
    private fun smallestAllowed(receiver: GlanceAppWidgetReceiver): DpSize {
        val parser = providerXml(receiver)
        return DpSize(
            attribute(parser, android.R.attr.minResizeWidth).dp,
            attribute(parser, android.R.attr.minResizeHeight).dp,
        )
    }

    @Test
    fun aWidgetCanNotBeShrunkBelowItsSmallestLayout() {
        receivers.forEach { receiver ->
            val name = receiver.javaClass.simpleName
            val allowed = smallestAllowed(receiver)
            val mode = receiver.glanceAppWidget.sizeMode
            if (mode is SizeMode.Responsive) {
                assertTrue(
                    "$name can be smaller than all of its layouts",
                    mode.sizes.any { it.width <= allowed.width && it.height <= allowed.height },
                )
            }
        }
    }

    @Test
    fun theLyricsFitAtTheSmallestAllowedSize() {
        assertEquals(LYRICS_LINES_HEIGHT, smallestAllowed(LyricsReceiver()).height)
    }

    @Test
    fun everyWidgetOpensAtASizeItAllowsAndHasAPreview() {
        receivers.forEach { receiver ->
            val name = receiver.javaClass.simpleName
            val parser = providerXml(receiver)
            val minWidth = attribute(parser, android.R.attr.minWidth)
            val minHeight = attribute(parser, android.R.attr.minHeight)
            val allowed = smallestAllowed(receiver)
            assertTrue(name, allowed.width.value <= minWidth && allowed.height.value <= minHeight)
            val values = application.resources.obtainAttributes(
                Xml.asAttributeSet(providerXml(receiver)),
                intArrayOf(android.R.attr.previewLayout),
            )
            assertTrue(name, values.getResourceId(0, 0) != 0)
            values.recycle()
        }
    }

    private fun assertPreviewsInflateLikeALauncherWould() {
        receivers.forEach { receiver ->
            val values = application.resources.obtainAttributes(
                Xml.asAttributeSet(providerXml(receiver)),
                intArrayOf(android.R.attr.previewLayout),
            )
            val layout = values.getResourceId(0, 0)
            values.recycle()
            assertNotNull(
                receiver.javaClass.simpleName,
                RemoteViews(application.packageName, layout).apply(application, FrameLayout(application)),
            )
        }
    }

    @Test
    fun previewsInflateInLightMode() = assertPreviewsInflateLikeALauncherWould()

    @Test
    @Config(qualifiers = "night")
    fun previewsInflateInDarkMode() = assertPreviewsInflateLikeALauncherWould()

    // the same choice Android makes, between the layouts that fit
    private fun chosen(widget: DpSize, sizes: Set<DpSize>) = sizes
        .filter { it.width <= widget.width && it.height <= widget.height }
        .minBy {
            val width = widget.width.value - it.width.value
            val height = widget.height.value - it.height.value
            width * width + height * height
        }

    @Test
    fun aBigWidgetIsNeverGivenTheBar() {
        for (width in 280..800 step 20) {
            for (height in 110..600 step 10) {
                val size = chosen(DpSize(width.dp, height.dp), PlayerSizes.All)
                assertTrue("$width x $height got $size", size.height >= PlayerSizes.Full.height)
            }
        }
    }

    @Test
    fun aTallWidgetGetsTheLargeArtwork() {
        for (width in 280..800 step 20) {
            for (height in PlayerSizes.Tall.height.value.toInt()..600 step 10) {
                assertEquals(PlayerSizes.Tall, chosen(DpSize(width.dp, height.dp), PlayerSizes.All))
            }
        }
    }

    @Test
    fun whatsInsideFitsTheSizeItIsChosenFor() = with(WidgetDimens) {
        val control = ControlSize.value
        val play = PlayButtonSize.value
        val tight = TightPadding.value * 2
        val sides = ContentPadding.value * 2
        val belowTheSong = ProgressHeight.value + ProgressGap.value + play

        // heights
        assertTrue(play + tight <= PlayerSizes.Bar.height.value)
        assertTrue(ArtworkBarSize.value + tight <= PlayerSizes.Bar.height.value)
        assertTrue(ArtworkBarSize.value + SongToProgressGap.value + belowTheSong <= PlayerSizes.Full.height.value)
        assertTrue(control + SongToProgressGap.value + belowTheSong <= PlayerSizes.Full.height.value)
        assertTrue(
            ArtworkFullSize.value + TallSongToProgressGap.value + belowTheSong <= PlayerSizes.Tall.height.value,
        )
        assertTrue(play <= QuickActionSizes.Single.height.value)
        assertTrue(play <= AlbumArtSizes.Small.height.value - sides)

        // widths, with room left over for the song in the bars
        val narrowBarText = PlayerSizes.Bar.width.value - sides - play - control
        assertTrue(narrowBarText >= MIN_TEXT_WIDTH)
        val wideBarText =
            PlayerSizes.WideBar.width.value - sides - ArtworkBarSize.value - Spacing.value - play - control * 2
        assertTrue(wideBarText >= MIN_TEXT_WIDTH)
        assertTrue(play + control * 4 <= PlayerSizes.Full.width.value - sides)
        assertTrue(play <= QuickActionSizes.Single.width.value)
        assertTrue(control + Spacing.value + play <= QuickActionSizes.Both.width.value)
    }

    @Test
    fun everyTargetIsBigEnoughToTouch() = with(WidgetDimens) {
        assertTrue(ControlSize >= MIN_TOUCH_TARGET)
        assertTrue(PlayButtonSize >= MIN_TOUCH_TARGET)
    }

    @Test
    fun theLyricsCoverFitsEverySizeItIsChosenFor() {
        LyricsWidgetSizes.All.forEach { size ->
            lyricsLayoutOf(size).coverSize?.let { cover ->
                assertTrue("$size", cover.value + WidgetDimens.ContentPadding.value * 2 <= size.height.value)
                assertTrue("$size", cover.value < size.width.value / 2)
            }
        }
    }

    @Test
    fun aBigLyricsWidgetIsGivenTheBigLayout() {
        for (width in 320..800 step 20) {
            for (height in 200..600 step 10) {
                assertEquals(LyricsWidgetSizes.Huge, chosen(DpSize(width.dp, height.dp), LyricsWidgetSizes.All))
            }
        }
    }

    @Test
    fun aNarrowLyricsWidgetKeepsAllItsWidthForTheWords() {
        assertEquals(null, lyricsLayoutOf(LyricsWidgetSizes.Narrow).coverSize)
    }

    @Test
    fun aNarrowPlaylistWidgetHasOneColumn() {
        assertEquals(1, playlistColumns(LibraryWidgetDimens.PlaylistSizeOneColumn.width))
        assertEquals(2, playlistColumns(LibraryWidgetDimens.PlaylistSizeTwoColumns.width))
        assertFalse(
            LibraryWidgetDimens.PlaylistSizeOneColumn.width >= LibraryWidgetDimens.PlaylistSizeTwoColumns.width,
        )
    }

    private companion object {
        const val MIN_TEXT_WIDTH = 60f
        val MIN_TOUCH_TARGET = 48.dp
        val LYRICS_LINES_HEIGHT = 110.dp
    }
}
