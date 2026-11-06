package dev.anujyadav.harmoniq.services.groove.repositories

import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.groove.Album
import dev.anujyadav.harmoniq.services.groove.Song
import dev.anujyadav.harmoniq.ui.helpers.Assets
import dev.anujyadav.harmoniq.ui.helpers.createHandyImageRequest
import dev.anujyadav.harmoniq.utils.ConcurrentSet
import dev.anujyadav.harmoniq.utils.FuzzySearchOption
import dev.anujyadav.harmoniq.utils.FuzzySearcher
import dev.anujyadav.harmoniq.utils.concurrentSetOf
import dev.anujyadav.harmoniq.utils.joinToStringIfNotEmpty
import dev.anujyadav.harmoniq.utils.withCase
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AlbumRepository(private val symphony: Symphony) {
    enum class SortBy {
        CUSTOM,
        ALBUM_NAME,
        ARTIST_NAME,
        TRACKS_COUNT,
        YEAR,
    }

    private val cache = ConcurrentHashMap<String, Album>()
    private val songIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()
    private val searcher = FuzzySearcher<String>(
        options = listOf(
            FuzzySearchOption({ v -> get(v)?.name?.let { compareString(it) } }, 3),
            FuzzySearchOption({ v -> get(v)?.artists?.let { compareCollection(it) } })
        )
    )

    val isUpdating get() = symphony.groove.exposer.isUpdating
    private val _all = MutableStateFlow<List<String>>(emptyList())
    val all = _all.asStateFlow()
    private val _count = MutableStateFlow(0)
    val count = _count.asStateFlow()

    private fun emitCount() = _count.update {
        cache.size
    }

    internal fun onSong(song: Song) {
        val albumId = getIdFromSong(song) ?: return
        songIdsCache.compute(albumId) { _, value ->
            value?.apply { add(song.id) } ?: concurrentSetOf(song.id)
        }
        cache.compute(albumId) { _, value ->
            value?.apply {
                artists.addAll(song.artists)
                song.year?.let {
                    startYear = startYear?.let { old -> min(old, it) } ?: it
                    endYear = endYear?.let { old -> max(old, it) } ?: it
                }
                numberOfTracks++
                duration += song.duration.milliseconds
            } ?: run {
                _all.update {
                    it + albumId
                }
                Album(
                    id = albumId,
                    name = song.album!!,
                    artists = mutableSetOf<String>().apply {
                        // ensure that album artists are first
                        addAll(song.albumArtists)
                        addAll(song.artists)
                    },
                    startYear = song.year,
                    endYear = song.year,
                    numberOfTracks = 1,
                    duration = song.duration.milliseconds,
                )
            }
        }
        // the cache only holds a new entry once compute is done
        emitCount()
    }

    internal fun remove(song: Song) {
        val albumId = getIdFromSong(song) ?: return
        songIdsCache[albumId]?.remove(song.id)
        val album = cache[albumId] ?: return
        album.numberOfTracks--
        album.duration -= song.duration.milliseconds
        if (album.numberOfTracks <= 0) {
            cache.remove(albumId)
            songIdsCache.remove(albumId)
            _all.update { it - albumId }
            emitCount()
            return
        }
        // the removed song may have been the only one with some artist or year
        val remaining = getSongIds(albumId).mapNotNull { symphony.groove.song.get(it) }
        album.artists.clear()
        remaining.forEach {
            album.artists.addAll(it.albumArtists)
            album.artists.addAll(it.artists)
        }
        val years = remaining.mapNotNull { it.year }
        album.startYear = years.minOrNull()
        album.endYear = years.maxOrNull()
    }

    fun reset() {
        cache.clear()
        songIdsCache.clear()
        _all.update {
            emptyList()
        }
        emitCount()
    }

    fun getIdFromSong(song: Song): String? {
        if (song.album == null) {
            return null
        }
        val artists = song.albumArtists.sorted().joinToString("-")
        return "${song.album}-${artists}"
    }

    fun getArtworkUri(albumId: String) = songIdsCache[albumId]?.firstOrNull()
        ?.let { symphony.groove.song.getArtworkUri(it) }
        ?: symphony.groove.song.getDefaultArtworkUri()

    fun createArtworkImageRequest(albumId: String) = createHandyImageRequest(
        symphony.applicationContext,
        image = getArtworkUri(albumId),
        fallback = Assets.placeholderDarkId,
    )

    fun search(albumIds: List<String>, terms: String, limit: Int = 7) = searcher
        .search(terms, albumIds, maxLength = limit)

    fun sort(albumIds: List<String>, by: SortBy, reverse: Boolean): List<String> {
        val sensitive = symphony.settings.caseSensitiveSorting.value
        val sorted = when (by) {
            SortBy.CUSTOM -> albumIds
            SortBy.ALBUM_NAME -> albumIds.sortedBy { get(it)?.name?.withCase(sensitive) }
            SortBy.ARTIST_NAME -> albumIds.sortedBy {
                get(it)?.artists?.joinToStringIfNotEmpty(sensitive)
            }

            SortBy.TRACKS_COUNT -> albumIds.sortedBy { get(it)?.numberOfTracks }
            SortBy.YEAR -> albumIds.sortedBy { get(it)?.startYear }
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun count() = cache.size
    fun ids() = cache.keys.toList()
    fun values() = cache.values.toList()

    fun get(albumId: String) = cache[albumId]
    fun get(albumIds: List<String>) = albumIds.mapNotNull { get(it) }.toList()
    fun getSongIds(albumId: String) = songIdsCache[albumId]?.toList() ?: emptyList()
}
