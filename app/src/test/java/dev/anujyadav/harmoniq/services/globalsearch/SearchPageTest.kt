package dev.anujyadav.harmoniq.services.globalsearch

import dev.anujyadav.harmoniq.services.globalsearch.api.PlaylistResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResponseDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchSectionsDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchType
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchPageTest {
    private val song = SearchResultDto(videoId = "aaaaaaaaaaa", resultType = "song", title = "Song")
    private val video = SearchResultDto(videoId = "bbbbbbbbbbb", resultType = "video", title = "Video")
    private val playlist = PlaylistResultDto(playlistId = "PL0123456789", title = "Mix")

    private fun response(type: String, results: List<SearchResultDto> = emptyList()) =
        SearchResponseDto(query = "q", type = type, results = results)

    @Test
    fun singleTypesFillOneList() {
        assertEquals(listOf(song), SearchPage.from(SearchType.Song, response("song", listOf(song))).songs)
        assertEquals(listOf(video), SearchPage.from(SearchType.Video, response("video", listOf(video))).videos)
        val playlists = SearchPage.from(SearchType.Playlist, response("playlist").copy(playlists = listOf(playlist)))
        assertEquals(listOf(playlist), playlists.playlists)
        assertTrue(playlists.tracks.isEmpty())
    }

    @Test
    fun allMapsSectionsAndErrors() {
        val page = SearchPage.from(
            SearchType.All,
            response("all").copy(
                sections = SearchSectionsDto(
                    songs = listOf(song),
                    videos = emptyList(),
                    playlists = listOf(playlist),
                    // a section name a newer server might add is ignored
                    errors = mapOf("videos" to "upstream_blocked", "albums" to "upstream_error"),
                ),
            ),
        )
        assertEquals(listOf(song), page.songs)
        assertEquals(listOf(playlist), page.playlists)
        assertEquals(mapOf(SearchPage.Section.Videos to "upstream_blocked"), page.sectionErrors)
        assertFalse(page.isEmpty)
    }

    @Test
    fun allWithoutSectionsIsEmpty() {
        assertTrue(SearchPage.from(SearchType.All, response("all")).isEmpty)
    }

    @Test
    fun allFromRealServerResponse() {
        val page = SearchPage.from(SearchType.All, ServerJson.decodeFromString(Fixtures.read("search_all.json")))
        assertEquals(2, page.songs.size)
        assertEquals(2, page.videos.size)
        assertEquals(2, page.playlists.size)
        assertTrue(page.sectionErrors.isEmpty())
    }

    @Test
    fun sectionsSeeAllTheirOwnType() {
        assertEquals(SearchType.Song, SearchPage.Section.Songs.type)
        assertEquals(SearchType.Video, SearchPage.Section.Videos.type)
        assertEquals(SearchType.Playlist, SearchPage.Section.Playlists.type)
    }

    @Test
    fun downloadStateMergeOverFormats() {
        val running = OnlineDownloadState.Running(0.5f)
        assertEquals(OnlineDownloadState.None, OnlineDownloadState.merge(emptyList()))
        // any finished format counts as downloaded
        assertEquals(OnlineDownloadState.Done, OnlineDownloadState.merge(listOf(running, OnlineDownloadState.Done)))
        assertEquals(running, OnlineDownloadState.merge(listOf(OnlineDownloadState.Failed, running)))
        assertEquals(OnlineDownloadState.Queued, OnlineDownloadState.merge(listOf(OnlineDownloadState.None, OnlineDownloadState.Queued)))
        assertEquals(OnlineDownloadState.Failed, OnlineDownloadState.merge(listOf(OnlineDownloadState.Failed, OnlineDownloadState.None)))
        assertTrue(running.isActive)
        assertFalse(OnlineDownloadState.Done.isActive)
    }
}
