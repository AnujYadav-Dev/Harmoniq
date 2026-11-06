package dev.anujyadav.harmoniq.services.radio

import android.content.Context
import android.net.Uri
import android.os.Looper
import dev.anujyadav.harmoniq.services.groove.Song
import java.time.Duration
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

internal object RadioTestSupport {
    private const val STEP_MS = 50L
    private const val WAIT_LIMIT_MS = 20_000L
    const val SONG_DURATION_MS = 60_000

    // a player that never finishes preparing during a test
    const val NEVER_PREPARED_DELAY_MS = 3_600_000
    const val INSTANT_PREPARE_DELAY_MS = 0

    fun song(id: String) = Song(
        id = id,
        title = id,
        album = "Album",
        artists = setOf("Artist"),
        composers = emptySet(),
        albumArtists = setOf("Album Artist"),
        genres = setOf("Genre"),
        trackNumber = null,
        trackTotal = null,
        discNumber = null,
        discTotal = null,
        date = null,
        year = null,
        duration = SONG_DURATION_MS.toLong(),
        bitrate = null,
        samplingRate = null,
        channels = null,
        encoder = null,
        dateModified = 0,
        size = 0,
        coverFile = null,
        uri = Uri.parse("content://media/$id"),
        path = "primary:Music/$id.mp3",
    )

    /** Lets RadioPlayer open this song, as there's no real file behind it. */
    fun allowPlayback(context: Context, song: Song, prepareDelayMs: Int = INSTANT_PREPARE_DELAY_MS) {
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, song.uri),
            ShadowMediaPlayer.MediaInfo(SONG_DURATION_MS, prepareDelayMs),
        )
    }

    fun advance(duration: Duration) {
        shadowOf(Looper.getMainLooper()).idleFor(duration)
    }

    /** The main thread is the test's own here: what runs on it only moves when told to. */
    fun advanceUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_LIMIT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition never became true" }
            advance(Duration.ofMillis(STEP_MS))
            Thread.sleep(STEP_MS)
        }
    }
}
