package dev.anujyadav.harmoniq.services.globalsearch.api

import dev.anujyadav.harmoniq.services.globalsearch.Fixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// harmoniq-server v0.3.0 shapes, captured from a local server run (playlist search, type=all, /playlists)
class PlaylistDtoParsingTest {
    private inline fun <reified T> parse(name: String): T =
        ServerJson.decodeFromString<T>(Fixtures.read(name))

    @Test
    fun metaListsSearchTypes() {
        val meta = parse<MetaDto>("meta_v030.json")
        assertEquals("0.3.0", meta.serverVersion)
        assertEquals(SearchType.entries, meta.supportedSearchTypes)
        assertEquals(200, meta.features?.maxPlaylistTracks)
    }

    @Test
    fun metaWithoutFeaturesMeansSongsAndVideosOnly() {
        // v0.2.0 sends no search_types
        val meta = parse<MetaDto>("meta.json")
        assertEquals(listOf(SearchType.Song, SearchType.Video), meta.supportedSearchTypes)
    }

    @Test
    fun supportedIgnoresUnknownTypesAndFallsBack() {
        assertEquals(listOf(SearchType.Song, SearchType.Playlist), SearchType.supported(listOf("playlist", "song", "album")))
        assertEquals(SearchType.legacy, SearchType.supported(emptyList()))
        assertEquals(SearchType.legacy, SearchType.supported(listOf("album")))
    }

    @Test
    fun savedChipNamesStayValid() {
        // Settings stores the enum name; values saved before v0.3.0 must still resolve
        assertEquals(SearchType.Song, SearchType.valueOf("Song"))
        assertEquals(SearchType.Video, SearchType.valueOf("Video"))
        assertEquals(listOf(SearchType.All, SearchType.Song, SearchType.Video, SearchType.Playlist), SearchType.entries)
    }

    @Test
    fun playlistSearch() {
        val search = parse<SearchResponseDto>("search_playlist.json")
        assertEquals("playlist", search.type)
        assertTrue(search.results.isEmpty())
        val first = search.playlists!!.first()
        assertEquals("PL0BKTsqcDLNFhlmyCUzRyYEF6ox9PI1z5", first.playlistId)
        assertEquals("https://music.youtube.com/playlist?list=PL0BKTsqcDLNFhlmyCUzRyYEF6ox9PI1z5", first.shareUrl())
        assertTrue(first.bestThumbnail()!!.startsWith("https://"))
    }

    @Test
    fun allSections() {
        val search = parse<SearchResponseDto>("search_all.json")
        assertEquals("all", search.type)
        val sections = search.sections!!
        assertEquals("khnokW3Mw24", sections.songs.first().videoId)
        assertTrue(sections.songs.first().isSong)
        assertEquals("video", sections.videos.first().resultType)
        assertEquals("Daft Punk (Instrumental)", sections.playlists.first().title)
        assertNull(sections.playlists.first().itemCount)
        assertTrue(sections.errors.isEmpty())
    }

    @Test
    fun oldResponsesHaveNoNewFields() {
        val search = parse<SearchResponseDto>("search_song.json")
        assertNull(search.playlists)
        assertNull(search.sections)
    }

    @Test
    fun playlistDetails() {
        val playlist = parse<PlaylistDto>("playlist.json")
        assertEquals("Daft Punk (Instrumental)", playlist.title)
        assertEquals("Rahmadia Kushardiani", playlist.author)
        assertEquals(19, playlist.trackCount)
        assertEquals(5478, playlist.duration)
        assertTrue(playlist.truncated)
        assertEquals(listOf("qe8Q7mjxjig", "Can7kOYrGpI", "iRHo9cz2wFc"), playlist.tracks.map { it.videoId })
        assertEquals("https://music.youtube.com/watch?v=qe8Q7mjxjig", playlist.tracks.first().shareUrl())
    }

    @Test
    fun linksFallBackToYoutube() {
        val result = SearchResultDto(videoId = "FGBhQbmPwH8", title = "x")
        assertEquals("https://www.youtube.com/watch?v=FGBhQbmPwH8", result.shareUrl())
        assertEquals("https://www.youtube.com/watch?v=FGBhQbmPwH8", result.youtubeUrl())
        val song = result.copy(watchUrl = "https://music.youtube.com/watch?v=FGBhQbmPwH8")
        assertEquals("https://music.youtube.com/watch?v=FGBhQbmPwH8", song.shareUrl())
        // "Open in YouTube" always targets the YouTube app
        assertEquals("https://www.youtube.com/watch?v=FGBhQbmPwH8", song.youtubeUrl())
    }
}
