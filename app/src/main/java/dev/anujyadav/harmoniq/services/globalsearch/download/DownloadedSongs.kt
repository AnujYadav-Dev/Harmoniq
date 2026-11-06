package dev.anujyadav.harmoniq.services.globalsearch.download

import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig

/** A finished download. [songId] is set once the library has scanned its file. */
data class DownloadedSong(val record: DownloadRecord, val songId: String?)

object DownloadedSongs {
    private const val PATH_SEPARATOR = '/'

    /**
     * Downloads are told apart from other files by the app's own records, not by their folder:
     * a file someone copied into `Download/Harmoniq` isn't one. A record is matched to its
     * library song by location, as the two know the same file under different URIs. One whose
     * file is gone and not in the library is left out.
     */
    fun resolve(
        records: Collection<DownloadRecord>,
        songIdsByPath: Map<String, String>,
        fileExists: (String) -> Boolean,
    ): List<DownloadedSong> {
        val done = records.filter { it.state == DownloadRecord.State.Done && it.fileName != null }
        if (done.isEmpty()) {
            return emptyList()
        }
        val songIdsByLocation = songIdsByPath.entries
            .associate { (path, songId) -> locationOf(path) to songId }
        return done
            .sortedByDescending { it.updatedAt }
            // the same file can be reused by several records, e.g. after a re-download
            .distinctBy { it.savedUri ?: it.key }
            .mapNotNull { record ->
                val songId = songIdsByLocation[downloadLocationOf(record.fileName!!)]
                when {
                    songId != null -> DownloadedSong(record, songId)
                    record.savedUri?.let(fileExists) == true -> DownloadedSong(record, null)
                    else -> null
                }
            }
    }

    // file and parent folder, which is all a library path and a record have in common:
    // the library path also depends on which media folder found the file
    private fun locationOf(path: String) =
        path.split(PATH_SEPARATOR).takeLast(2).joinToString(PATH_SEPARATOR.toString()).lowercase()

    private fun downloadLocationOf(fileName: String) =
        locationOf("${ServerConfig.DOWNLOAD_FOLDER_NAME}$PATH_SEPARATOR$fileName")
}
