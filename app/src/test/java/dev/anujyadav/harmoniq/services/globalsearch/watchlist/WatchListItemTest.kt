package dev.anujyadav.harmoniq.services.globalsearch.watchlist

import dev.anujyadav.harmoniq.services.globalsearch.Fixtures
import dev.anujyadav.harmoniq.services.globalsearch.api.ChannelDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResponseDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WatchListItemTest {
    @Test
    fun songRoundTrip() {
        val song = ServerJson.decodeFromString<SearchResponseDto>(Fixtures.read("search_song.json")).results.first()
        val item = WatchListItem.from(song, addedAt = 42L, sourcePlaylistId = "PL0123456789")
        assertEquals(song.videoId, item.videoId)
        assertEquals(42L, item.addedAt)
        assertEquals("PL0123456789", item.sourcePlaylistId)

        val back = item.toSearchResult()
        assertEquals(song.videoId, back.videoId)
        assertEquals(song.resultType, back.resultType)
        assertEquals(song.title, back.title)
        assertEquals(song.displayArtists, back.displayArtists)
        assertEquals(song.album?.name, back.album?.name)
        assertEquals(song.duration, back.duration)
        assertEquals(song.isExplicit, back.isExplicit)
        assertEquals(song.bestThumbnail(), back.bestThumbnail())
        assertEquals(song.shareUrl(), back.shareUrl())
    }

    @Test
    fun videoKeepsItsChannelAsArtist() {
        val video = SearchResultDto(
            videoId = "jNQXAC9IVRw",
            resultType = "video",
            title = "Me at the zoo",
            channel = ChannelDto(name = "jawed"),
        )
        val back = WatchListItem.from(video, addedAt = 1L).toSearchResult()
        assertEquals(listOf("jawed"), back.displayArtists)
        assertEquals("https://www.youtube.com/watch?v=jNQXAC9IVRw", back.shareUrl())
    }

    @Test
    fun sorting() {
        fun item(id: String, title: String, artist: String, addedAt: Long) = WatchListItem(
            id, "song", title, listOf(artist), null, null, null, null, null, addedAt, null,
        )
        val a = item("a", "beta", "Zed", 1)
        val b = item("b", "Alpha", "yan", 3)
        val c = item("c", "gamma", "Xavier", 2)
        val items = listOf(a, b, c)
        assertEquals(listOf(b, c, a), WatchListSortBy.DateAdded.sort(items, reverse = false))
        assertEquals(listOf(a, c, b), WatchListSortBy.DateAdded.sort(items, reverse = true))
        assertEquals(listOf(b, a, c), WatchListSortBy.Title.sort(items, reverse = false))
        assertEquals(listOf(c, b, a), WatchListSortBy.Artist.sort(items, reverse = false))
    }
}
