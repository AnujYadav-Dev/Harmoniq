package dev.anujyadav.harmoniq.services.groove

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.ui.helpers.Assets
import dev.anujyadav.harmoniq.utils.DocumentFileX
import dev.anujyadav.harmoniq.utils.SimplePath
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import coil.request.ImageRequest

@Immutable
@Entity("playlists")
data class Playlist(
    @PrimaryKey
    val id: String,
    val title: String,
    val songPaths: List<String>,
    val uri: Uri?,
    val path: String?,
) {
    val numberOfTracks: Int get() = songPaths.size
    val isLocal get() = uri != null
    val isNotLocal get() = uri == null

    fun createArtworkImageRequest(symphony: Symphony) =
        symphony.groove.playlist.covers.get(id)
            ?.let { cover ->
                // keyed by modification time, so a replaced cover is not served from cache
                val cacheKey = "playlist-cover:$id:${cover.lastModified()}"
                ImageRequest.Builder(symphony.applicationContext)
                    .data(cover)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .crossfade(true)
            }
            ?: getSongIds(symphony).firstOrNull()
                ?.let { symphony.groove.song.get(it)?.createArtworkImageRequest(symphony) }
            ?: Assets.createPlaceholderImageRequest(symphony)

    fun getSongIds(symphony: Symphony) = songPaths.mapNotNull(songIdFinder(symphony))

    /** The first of [getSongIds], without going through a long playlist to get it. */
    fun firstSongId(symphony: Symphony) = songPaths.firstNotNullOfOrNull(songIdFinder(symphony))

    private fun songIdFinder(symphony: Symphony): (String) -> String? {
        val parentPath = path?.let { SimplePath(it) }?.parent
        val primaryPath = SimplePath(PRIMARY_STORAGE)
        return { x ->
            symphony.groove.song.pathCache[x]
                ?: x.takeIf { x[0] == '/' }?.let {
                    symphony.groove.song.pathCache[it.substring(1).replaceFirst("/", ":")]
                }
                ?: parentPath?.let { symphony.groove.song.pathCache[it.join(x).pathString] }
                ?: symphony.groove.song.pathCache[primaryPath.join(x).pathString]
        }
    }

    fun getSortedSongIds(symphony: Symphony) = symphony.groove.song.sort(
        getSongIds(symphony),
        symphony.settings.lastUsedPlaylistSongsSortBy.value,
        symphony.settings.lastUsedPlaylistSongsSortReverse.value,
    )

    fun withTitle(title: String) = Playlist(
        id = id,
        title = title,
        songPaths = songPaths,
        uri = uri,
        path = path,
    )

    companion object {
        private const val PRIMARY_STORAGE = "primary:"

        fun parse(symphony: Symphony, playlistId: String?, uri: Uri): Playlist {
            val file = DocumentFileX.fromSingleUri(symphony.applicationContext, uri)!!
            val content = symphony.applicationContext.contentResolver.openInputStream(uri)
                ?.use { String(it.readBytes()) } ?: ""
            val songPaths = content.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it[0] != '#' }
                .toList()
            val id = playlistId ?: symphony.groove.playlist.idGenerator.next()
            val path = DocumentFileX.getParentPathOfSingleUri(file.uri) ?: file.name
            return Playlist(
                id = id,
                title = Path(path).nameWithoutExtension,
                songPaths = songPaths,
                uri = uri,
                path = path,
            )
        }
    }
}
