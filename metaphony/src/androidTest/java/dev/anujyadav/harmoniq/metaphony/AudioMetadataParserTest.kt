package dev.anujyadav.harmoniq.metaphony

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AudioMetadataParserTest {
    @Test
    fun testFlac() {
        assertMetadata("flac", parseAsset("audio.flac"))
    }

    @Test
    fun testOgg() {
        assertMetadata("ogg", parseAsset("audio.ogg"))
    }

    @Test
    fun testMp3() {
        assertMetadata("mp3", parseAsset("audio.mp3"))
    }

    @Test
    fun testMp3Id23() {
        assertMetadata("mp3id23", parseAsset("audio-id3v2.3.mp3"))
    }

    @Test
    fun testMp3Id24() {
        assertMetadata("mp3id24", parseAsset("audio-id3v2.4.mp3"))
    }

    // The descriptor from assets.openFd() is for the whole test APK, with the asset somewhere inside
    // it, and the parser reads from the start. So hand it a real file, as the app does.
    private fun parseAsset(filename: String): AudioMetadata? {
        val context = InstrumentationRegistry.getInstrumentation().context
        val file = File.createTempFile("metadata", filename, context.cacheDir)
        try {
            context.assets.open(filename).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).detachFd()
            return AudioMetadataParser.parse(filename, fd)
        } finally {
            file.delete()
        }
    }

    fun assertMetadata(source: String, metadata: AudioMetadata?) {
        Assert.assertNotNull(metadata)
        metadata!!
        Assert.assertEquals("Demo Audio", metadata.title)
        val expectedArtists = when (source) {
            // ID3v2.4 is the only one of these files that stores two separate values
            "mp3id24" -> arrayOf("Demo Artist 1", "Demo Artist 2")
            else -> arrayOf("Demo Artist 1; Demo Artist 2")
        }
        Assert.assertArrayEquals(expectedArtists, metadata.artists.toTypedArray())
        Assert.assertArrayEquals(
            arrayOf("Demo Artist 2"),
            metadata.albumArtists.toTypedArray(),
        )
        Assert.assertArrayEquals(emptyArray(), metadata.composers.toTypedArray())
        Assert.assertArrayEquals(
            arrayOf("Rap", "Rock"),
            metadata.genres.toTypedArray(),
        )
        Assert.assertEquals(null, metadata.discNumber)
        Assert.assertEquals(null, metadata.discTotal)
        Assert.assertEquals(1, metadata.trackNumber)
        Assert.assertEquals(2, metadata.trackTotal)
        Assert.assertEquals(null, metadata.date)
        Assert.assertEquals(null, metadata.lyrics)
        val expectedBitrate = when (source) {
            // these files hold one second of near silence: 279 and 162 bytes of audio
            "ogg" -> 2
            "flac" -> 1
            else -> 130
        }
        Assert.assertEquals(expectedBitrate, metadata.bitrate)
        Assert.assertEquals(1, metadata.lengthInSeconds)
        Assert.assertEquals(44100, metadata.sampleRate)
        Assert.assertEquals(2, metadata.channels)
        Assert.assertEquals(1, metadata.pictures.size)
        Assert.assertEquals("Front Cover", metadata.pictures[0].pictureType)
        Assert.assertEquals("image/png", metadata.pictures[0].mimeType)
        Assert.assertNotEquals(0, metadata.pictures[0].data.size)
    }
}
