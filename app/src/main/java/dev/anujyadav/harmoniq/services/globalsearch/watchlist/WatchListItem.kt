package dev.anujyadav.harmoniq.services.globalsearch.watchlist

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.anujyadav.harmoniq.services.globalsearch.api.AlbumRefDto
import dev.anujyadav.harmoniq.services.globalsearch.api.ArtistRefDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.ThumbnailDto

/**
 * A song or video saved from Global Search. It keeps what a search row needs, so the
 * Watch List shows (and opens) offline, and converts back to a [SearchResultDto].
 */
@Immutable
@Entity("watch_list")
data class WatchListItem(
    @PrimaryKey
    val videoId: String,
    // "song" | "video"
    val resultType: String?,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val watchUrl: String?,
    val isExplicit: Boolean?,
    val addedAt: Long,
    // the playlist it was saved from, if any
    val sourcePlaylistId: String?,
) {
    fun toSearchResult() = SearchResultDto(
        videoId = videoId,
        resultType = resultType,
        title = title,
        artists = artists.map { ArtistRefDto(it) }.ifEmpty { null },
        album = album?.let { AlbumRefDto(it) },
        duration = durationSeconds,
        isExplicit = isExplicit,
        thumbnails = listOfNotNull(thumbnailUrl?.let { ThumbnailDto(it) }),
        watchUrl = watchUrl,
    )

    companion object {
        fun from(result: SearchResultDto, addedAt: Long, sourcePlaylistId: String? = null) = WatchListItem(
            videoId = result.videoId,
            resultType = result.resultType,
            title = result.title,
            // videos only have their channel, which then reads like an artist
            artists = result.displayArtists,
            album = result.album?.name,
            durationSeconds = result.duration,
            thumbnailUrl = result.bestThumbnail(),
            watchUrl = result.watchUrl,
            isExplicit = result.isExplicit,
            addedAt = addedAt,
            sourcePlaylistId = sourcePlaylistId,
        )
    }
}
