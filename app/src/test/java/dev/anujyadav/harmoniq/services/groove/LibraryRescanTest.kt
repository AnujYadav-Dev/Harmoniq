package dev.anujyadav.harmoniq.services.groove

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.utils.plusIfAbsent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A rescan without a reset goes over what is already loaded, none of the library's lists
 * or counts may change because of that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryRescanTest {
    private val symphony = Symphony(ApplicationProvider.getApplicationContext<Application>())
    private val groove = symphony.groove

    private fun song(id: String, path: String = "primary:Music/$id.mp3", album: String = "Album") = Song(
        id = id,
        title = id,
        album = album,
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
        duration = 1000,
        bitrate = null,
        samplingRate = null,
        channels = null,
        encoder = null,
        dateModified = 0,
        size = 0,
        coverFile = null,
        uri = Uri.EMPTY,
        path = path,
    )

    private fun albumTracks(name: String = "Album") =
        groove.album.values().single { it.name == name }.numberOfTracks

    @Test
    fun addingAnIdTwiceKeepsOneCopy() {
        assertEquals(listOf("a", "b"), listOf("a", "b").plusIfAbsent("a"))
        assertEquals(listOf("a", "b"), listOf("a").plusIfAbsent("b"))
    }

    @Test
    fun fetchingPlaylistsAgainDoesNotDuplicateThem() = runBlocking {
        groove.playlist.add(groove.playlist.create("Mix", emptyList()))
        groove.playlist.fetch()
        groove.playlist.fetch()

        val ids = groove.playlist.all.value
        assertEquals(ids.distinct(), ids)
        assertEquals(groove.playlist.count.value, ids.size)
    }

    @Test
    fun rescanningAKnownSongChangesNothing() {
        repeat(2) { groove.onSongScanned(song("a")) }

        assertEquals(listOf("a"), groove.song.all.value)
        assertEquals(1, albumTracks())
        assertEquals(1, groove.artist.values().single().numberOfTracks)
        assertEquals(1, groove.genre.values().single().numberOfTracks)
    }

    @Test
    fun aModifiedFileReplacesItsOldSong() {
        val path = "primary:Music/a.mp3"
        groove.onSongScanned(song("old", path, album = "Old Album"))
        groove.onSongScanned(song("new", path, album = "New Album"))

        assertEquals(listOf("new"), groove.song.all.value)
        assertEquals("new", groove.song.pathCache[path])
        assertNull(groove.song.get("old"))
        assertEquals(listOf("New Album"), groove.album.values().map { it.name })
        assertEquals(1, groove.artist.values().single().numberOfTracks)
    }

    @Test
    fun removingASongKeepsCollectionsOthersStillUse() {
        val removed = song("a")
        groove.onSongScanned(removed)
        groove.onSongScanned(song("b"))
        groove.removeSong(removed)

        assertEquals(listOf("b"), groove.song.all.value)
        assertEquals(1, albumTracks())
        groove.removeSong(song("b"))
        assertEquals(emptyList<String>(), groove.album.all.value)
        assertEquals(emptyList<String>(), groove.artist.all.value)
        assertEquals(emptyList<String>(), groove.albumArtist.all.value)
        assertEquals(emptyList<String>(), groove.genre.all.value)
    }

    @Test
    fun countsMatchTheListsAfterAScan() {
        groove.onSongScanned(song("a", album = "One"))
        groove.onSongScanned(song("b", album = "Two"))

        assertEquals(groove.album.all.value.size, groove.album.count.value)
        assertEquals(groove.artist.all.value.size, groove.artist.count.value)
        assertEquals(groove.albumArtist.all.value.size, groove.albumArtist.count.value)
        assertEquals(groove.genre.all.value.size, groove.genre.count.value)
    }
}
