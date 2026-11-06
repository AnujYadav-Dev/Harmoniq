package dev.anujyadav.harmoniq.services.playback

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackArtworkTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()

    private fun coverFile(width: Int, height: Int): File {
        val file = File(application.cacheDir, "cover.png")
        file.outputStream().use {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return file
    }

    @Test
    fun aSizeThatFitsIsKept() {
        assertEquals(200, PlaybackArtwork.maxSide(200, 1080, 2400, 1))
    }

    @Test
    fun anyAskedSizeStaysWithinTheCap() {
        assertEquals(512, PlaybackArtwork.maxSide(4000, 1440, 3200, 1))
    }

    @Test
    fun manyImagesAndASmallScreenShareTheSystemLimit() {
        val side = PlaybackArtwork.maxSide(512, 240, 320, 12)
        assertTrue(side < 512)
        val bytes = side.toLong() * side * 4 * 12
        assertTrue(bytes <= (1.5 * 240 * 320 * 4).toLong())
    }

    @Test
    fun theSizeIsNeverZero() {
        assertEquals(1, PlaybackArtwork.maxSide(0, 1080, 2400, 1))
    }

    @Test
    fun decodingShrinksWithoutGoingBelowTheSize() {
        assertEquals(1, PlaybackArtwork.sampleSize(500, 500, 512))
        assertEquals(1, PlaybackArtwork.sampleSize(1023, 1023, 512))
        assertEquals(2, PlaybackArtwork.sampleSize(1024, 1024, 512))
        assertEquals(8, PlaybackArtwork.sampleSize(4096, 4096, 512))
        assertEquals(1, PlaybackArtwork.sampleSize(4096, 100, 512))
    }

    @Test
    fun aLargeCoverComesOutAtTheAskedSize() {
        val bitmap = PlaybackArtwork.load(application, coverFile(2000, 1000).path, 300)
        assertEquals(300, bitmap.width)
        assertEquals(150, bitmap.height)
    }

    @Test
    fun aSmallCoverIsNotBlownUp() {
        val bitmap = PlaybackArtwork.load(application, coverFile(100, 100).path, 300)
        assertEquals(100, bitmap.width)
    }

    @Test
    fun noCoverOrAMissingOneGivesThePlaceholder() {
        listOf(null, "/no/such/file.jpg").forEach {
            val bitmap = PlaybackArtwork.load(application, it, 300)
            assertTrue(bitmap.width in 1..300)
        }
    }

    @Test
    fun aDamagedCoverGivesThePlaceholder() {
        val file = File(application.cacheDir, "bad.jpg").apply { writeText("not an image") }
        assertTrue(PlaybackArtwork.load(application, file.path, 300).width in 1..300)
    }
}
