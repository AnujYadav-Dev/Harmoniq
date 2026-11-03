package dev.anujyadav.harmoniq.services.globalsearch.api

import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Mirrors harmoniq-server docs/api.md (v1). Adding fields is non-breaking on the server,
// so unknown keys are ignored and every non-essential field has a default.
val ServerJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

// declaration order is the order of the chips in the Search tab
enum class SearchType(val apiValue: String) {
    All("all"),
    Song("song"),
    Video("video"),
    Playlist("playlist");

    companion object {
        // what a server without `features.search_types` (before v0.3.0) understands
        val legacy = listOf(Song, Video)

        fun supported(apiValues: List<String>?) =
            apiValues?.let { values -> entries.filter { it.apiValue in values } }?.ifEmpty { null } ?: legacy
    }
}

enum class DownloadFormat(val apiValue: String, val minSdk: Int) {
    Opus("opus", 29),
    M4a("m4a", 28),
    Mp3("mp3", 28),
}

enum class JobStatus(val apiValue: String, val isTerminal: Boolean = false) {
    Queued("queued"),
    Resolving("resolving"),
    Downloading("downloading"),
    Processing("processing"),
    Tagging("tagging"),
    Verifying("verifying"),
    Completed("completed", true),
    Failed("failed", true),
    Cancelled("cancelled", true),
    Expired("expired", true),
    Unknown("unknown");

    companion object {
        fun from(value: String) = entries.find { it.apiValue == value } ?: Unknown
    }
}

enum class YoutubeStatus(val apiValue: String) {
    Ok("ok"),
    Degraded("degraded"),
    Blocked("blocked"),
    Unknown("unknown");

    companion object {
        fun from(value: String?) = entries.find { it.apiValue == value } ?: Unknown
    }
}

@Serializable
data class MetaDto(
    @SerialName("api_version") val apiVersion: String,
    @SerialName("server_version") val serverVersion: String,
    val formats: List<String> = emptyList(),
    @SerialName("default_format") val defaultFormat: String? = null,
    @SerialName("max_duration_seconds") val maxDurationSeconds: Int? = null,
    val limits: LimitsDto? = null,
    val features: FeaturesDto? = null,
    val status: ServerStatusDto? = null,
) {
    val youtubeStatus get() = YoutubeStatus.from(status?.youtube)

    val supportedSearchTypes get() = SearchType.supported(features?.searchTypes)
}

@Serializable
data class FeaturesDto(
    // missing on servers before v0.3.0, see [SearchType.legacy]
    @SerialName("search_types") val searchTypes: List<String>? = null,
    @SerialName("max_playlist_tracks") val maxPlaylistTracks: Int? = null,
)

@Serializable
data class LimitsDto(
    @SerialName("search_per_minute") val searchPerMinute: Int? = null,
    @SerialName("downloads_per_hour") val downloadsPerHour: Int? = null,
    @SerialName("max_active_jobs") val maxActiveJobs: Int? = null,
)

@Serializable
data class ServerStatusDto(
    val youtube: String? = null,
    @SerialName("last_canary_at") val lastCanaryAt: String? = null,
    @SerialName("last_canary_ok") val lastCanaryOk: Boolean? = null,
)

@Serializable
data class SearchResponseDto(
    val query: String,
    val type: String,
    val source: String? = null,
    val cached: Boolean = false,
    val results: List<SearchResultDto> = emptyList(),
    // type=playlist
    val playlists: List<PlaylistResultDto>? = null,
    // type=all
    val sections: SearchSectionsDto? = null,
)

@Serializable
data class SearchSectionsDto(
    val songs: List<SearchResultDto> = emptyList(),
    val videos: List<SearchResultDto> = emptyList(),
    val playlists: List<PlaylistResultDto> = emptyList(),
    // section name ("songs", "videos", "playlists") -> error code, for sections that failed
    val errors: Map<String, String> = emptyMap(),
)

@Serializable
data class PlaylistResultDto(
    @SerialName("playlist_id") val playlistId: String,
    val title: String,
    val author: String? = null,
    @SerialName("item_count") val itemCount: Int? = null,
    val thumbnails: List<ThumbnailDto> = emptyList(),
    @SerialName("watch_url") val watchUrl: String? = null,
) {
    fun bestThumbnail() = thumbnails.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url

    fun shareUrl() = watchUrl ?: ServerConfig.youtubePlaylistUrl(playlistId)
}

/** `GET /playlists/{id}`: tracks come in the search result shape, so rows and actions are shared. */
@Serializable
data class PlaylistDto(
    @SerialName("playlist_id") val playlistId: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    @SerialName("track_count") val trackCount: Int? = null,
    val duration: Int? = null,
    val thumbnails: List<ThumbnailDto> = emptyList(),
    @SerialName("watch_url") val watchUrl: String? = null,
    val tracks: List<SearchResultDto> = emptyList(),
    val truncated: Boolean = false,
) {
    fun bestThumbnail() = thumbnails.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url

    fun shareUrl() = watchUrl ?: ServerConfig.youtubePlaylistUrl(playlistId)
}

@Serializable
data class SearchResultDto(
    @SerialName("video_id") val videoId: String,
    @SerialName("result_type") val resultType: String? = null,
    val title: String,
    val artists: List<ArtistRefDto>? = null,
    val album: AlbumRefDto? = null,
    val duration: Int? = null,
    @SerialName("duration_text") val durationText: String? = null,
    @SerialName("is_explicit") val isExplicit: Boolean? = null,
    val thumbnails: List<ThumbnailDto> = emptyList(),
    val channel: ChannelDto? = null,
    @SerialName("view_count") val viewCount: Long? = null,
    @SerialName("watch_url") val watchUrl: String? = null,
) {
    val artistNames get() = artists.orEmpty().map { it.name }.filter { it.isNotBlank() }

    // songs carry artists, videos only have the uploading channel
    val displayArtists get() = artistNames.ifEmpty { listOfNotNull(channel?.name) }

    fun bestThumbnail() = thumbnails.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url

    val isSong get() = resultType == SearchType.Song.apiValue

    // the server's link (music.youtube.com for songs), what people expect to receive
    fun shareUrl() = watchUrl ?: youtubeUrl()

    // always www.youtube.com, which the YouTube app handles
    fun youtubeUrl() = ServerConfig.youtubeWatchUrl(videoId)
}

@Serializable
data class ArtistRefDto(
    val name: String,
    @SerialName("channel_id") val channelId: String? = null,
)

@Serializable
data class AlbumRefDto(
    val name: String,
    @SerialName("browse_id") val browseId: String? = null,
)

@Serializable
data class ThumbnailDto(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class ChannelDto(
    val name: String? = null,
    val id: String? = null,
    val url: String? = null,
)

@Serializable
data class PreviewDto(
    @SerialName("video_id") val videoId: String,
    val embeddable: Boolean = false,
    @SerialName("age_restricted") val ageRestricted: Boolean = false,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("embed_url") val embedUrl: String? = null,
    @SerialName("watch_url") val watchUrl: String? = null,
    val thumbnail: String? = null,
    val title: String? = null,
    val duration: Int? = null,
)

/**
 * Song metadata as resolved by the server. `GET /videos/{id}/track` returns it before
 * downloading, and a completed job carries the same shape under `metadata` (minus the
 * top-level `video_id` and `suggested_filename`).
 */
@Serializable
data class SongMetadataDto(
    @SerialName("video_id") val videoId: String? = null,
    val title: String? = null,
    val artists: List<String> = emptyList(),
    @SerialName("album_artist") val albumArtist: String? = null,
    val album: String? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    @SerialName("track_total") val trackTotal: Int? = null,
    @SerialName("disc_number") val discNumber: Int? = null,
    @SerialName("disc_total") val discTotal: Int? = null,
    val date: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val explicit: Boolean? = null,
    val duration: Int? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    val lyrics: LyricsInfoDto? = null,
    @SerialName("video_type") val videoType: String? = null,
    @SerialName("suggested_filename") val suggestedFilename: Map<String, String> = emptyMap(),
)

@Serializable
data class LyricsInfoDto(
    val available: Boolean = false,
    val synced: Boolean = false,
    val source: String? = null,
    val lines: Int = 0,
)

@Serializable
data class DownloadRequestDto(
    @SerialName("video_id") val videoId: String,
    val format: String,
    val metadata: DownloadOverridesDto? = null,
)

// only the artwork is overridden, the server resolves everything else better than a search row
@Serializable
data class DownloadOverridesDto(
    @SerialName("artwork_url") val artworkUrl: String? = null,
)

@Serializable
data class SubmitResponseDto(
    @SerialName("job_id") val jobId: String,
    val status: String,
    @SerialName("cache_hit") val cacheHit: Boolean = false,
)

@Serializable
data class JobStatusDto(
    @SerialName("job_id") val jobId: String,
    val status: String,
    val progress: Double = 0.0,
    val stage: JobStageDto? = null,
    @SerialName("video_id") val videoId: String? = null,
    val format: String? = null,
    @SerialName("cache_hit") val cacheHit: Boolean = false,
    val file: JobFileDto? = null,
    val metadata: SongMetadataDto? = null,
    val error: JobErrorDto? = null,
) {
    val jobStatus get() = JobStatus.from(status)
}

@Serializable
data class JobStageDto(
    val name: String? = null,
    val progress: Double? = null,
    @SerialName("downloaded_bytes") val downloadedBytes: Long? = null,
    @SerialName("total_bytes") val totalBytes: Long? = null,
)

@Serializable
data class JobFileDto(
    val filename: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val sha256: String,
    val codec: String? = null,
    @SerialName("bitrate_kbps") val bitrateKbps: Int? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class JobErrorDto(
    val code: String,
    val detail: String? = null,
    val retryable: Boolean = false,
)

// RFC 9457 application/problem+json
@Serializable
data class ProblemDto(
    val code: String,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    val retryable: Boolean = false,
)
