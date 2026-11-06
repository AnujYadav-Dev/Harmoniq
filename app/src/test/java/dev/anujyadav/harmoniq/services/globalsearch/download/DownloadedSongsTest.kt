package dev.anujyadav.harmoniq.services.globalsearch.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadedSongsTest {
    private fun record(
        id: String,
        fileName: String? = "$id.mp3",
        state: DownloadRecord.State = DownloadRecord.State.Done,
        updatedAt: Long = 0L,
        savedUri: String? = "content://media/$id",
    ) = DownloadRecord(
        videoId = id,
        format = "mp3",
        title = id,
        state = state,
        fileName = fileName,
        savedUri = savedUri,
        updatedAt = updatedAt,
    )

    private fun resolve(
        records: List<DownloadRecord>,
        paths: Map<String, String> = emptyMap(),
        exists: Boolean = true,
    ) = DownloadedSongs.resolve(records, paths) { exists }

    @Test
    fun matchesTheLibrarySongWhicheverMediaFolderFoundIt() {
        listOf("Harmoniq/a.mp3", "Download/Harmoniq/a.mp3", "storage/Download/harmoniq/A.MP3").forEach { path ->
            assertEquals("song", resolve(listOf(record("a")), mapOf(path to "song")).single().songId)
        }
    }

    @Test
    fun aSongFileFromElsewhereIsNotADownload() {
        val songs = resolve(listOf(record("a")), mapOf("Music/a.mp3" to "other"))
        assertEquals(null, songs.single().songId)
    }

    @Test
    fun filesOutsideTheRecordsAreIgnored() {
        assertEquals(emptyList<DownloadedSong>(), resolve(emptyList(), mapOf("Harmoniq/copied.mp3" to "song")))
    }

    @Test
    fun aFileNotScannedYetStaysListed() {
        assertEquals(null, resolve(listOf(record("a"))).single().songId)
    }

    @Test
    fun aDeletedFileIsLeftOut() {
        assertEquals(emptyList<DownloadedSong>(), resolve(listOf(record("a")), exists = false))
    }

    @Test
    fun onlyFinishedDownloadsCount() {
        val unfinished = DownloadRecord.State.entries
            .filter { it != DownloadRecord.State.Done }
            .map { record(it.name, state = it) }
        assertEquals(emptyList<DownloadedSong>(), resolve(unfinished))
    }

    @Test
    fun newestComesFirstAndAReusedFileOnce() {
        val songs = resolve(
            listOf(
                record("old", updatedAt = 1),
                record("new", updatedAt = 3),
                record("same", updatedAt = 2, savedUri = "content://media/old"),
            )
        )
        assertEquals(listOf("new", "same"), songs.map { it.record.videoId })
    }
}
