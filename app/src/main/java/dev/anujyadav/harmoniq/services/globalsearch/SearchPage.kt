package dev.anujyadav.harmoniq.services.globalsearch

import dev.anujyadav.harmoniq.services.globalsearch.api.PlaylistResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResponseDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchType

/**
 * One page of Global Search results. A single-type search fills one list, "All" fills
 * all three; [sectionErrors] names the sections the server couldn't load (section -> code).
 */
data class SearchPage(
    val songs: List<SearchResultDto> = emptyList(),
    val videos: List<SearchResultDto> = emptyList(),
    val playlists: List<PlaylistResultDto> = emptyList(),
    val sectionErrors: Map<Section, String> = emptyMap(),
) {
    enum class Section(val apiValue: String, val type: SearchType) {
        Songs("songs", SearchType.Song),
        Videos("videos", SearchType.Video),
        Playlists("playlists", SearchType.Playlist),
    }

    val isEmpty get() = songs.isEmpty() && videos.isEmpty() && playlists.isEmpty()

    // songs and videos as one list, for single-type tabs
    val tracks get() = songs + videos

    companion object {
        fun from(type: SearchType, response: SearchResponseDto) = when (type) {
            SearchType.All -> response.sections?.let { sections ->
                SearchPage(
                    songs = sections.songs,
                    videos = sections.videos,
                    playlists = sections.playlists,
                    sectionErrors = sections.errors.mapNotNull { (name, code) ->
                        Section.entries.find { it.apiValue == name }?.let { it to code }
                    }.toMap(),
                )
            } ?: SearchPage()

            // a song search the server answered from YouTube still belongs to the Songs tab
            SearchType.Song -> SearchPage(songs = response.results)
            SearchType.Video -> SearchPage(videos = response.results)
            SearchType.Playlist -> SearchPage(playlists = response.playlists.orEmpty())
        }
    }
}

/** How a video's downloads look in a row, whatever format was used. */
sealed interface OnlineDownloadState {
    data object None : OnlineDownloadState
    data object Queued : OnlineDownloadState
    data class Running(val fraction: Float?) : OnlineDownloadState
    data object Done : OnlineDownloadState
    data object Failed : OnlineDownloadState

    val isActive get() = this == Queued || this is Running

    companion object {
        // any finished format counts as downloaded, then the most advanced of the others
        fun merge(states: List<OnlineDownloadState>): OnlineDownloadState =
            states.maxByOrNull { it.rank } ?: None

        private val OnlineDownloadState.rank
            get() = when (this) {
                None -> 0
                Failed -> 1
                Queued -> 2
                is Running -> 3
                Done -> 4
            }
    }
}
