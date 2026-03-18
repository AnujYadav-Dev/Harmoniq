package dev.anujyadav.harmoniq.services.groove.repositories

import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.groove.Genre
import dev.anujyadav.harmoniq.services.groove.Song
import dev.anujyadav.harmoniq.utils.ConcurrentSet
import dev.anujyadav.harmoniq.utils.FuzzySearchOption
import dev.anujyadav.harmoniq.utils.FuzzySearcher
import dev.anujyadav.harmoniq.utils.concurrentSetOf
import dev.anujyadav.harmoniq.utils.withCase
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GenreRepository(private val symphony: Symphony) {
    enum class SortBy {
        CUSTOM,
        GENRE,
        TRACKS_COUNT,
    }

    private val cache = ConcurrentHashMap<String, Genre>()
    private val songIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()
    private val searcher = FuzzySearcher<String>(
        options = listOf(FuzzySearchOption({ v -> get(v)?.name?.let { compareString(it) } }))
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
        song.genres.forEach { genre ->
            songIdsCache.compute(genre) { _, value ->
                value?.apply { add(song.id) } ?: concurrentSetOf(song.id)
            }
            cache.compute(genre) { _, value ->
                value?.apply {
                    numberOfTracks++
                } ?: run {
                    _all.update {
                        it + genre
                    }
                    emitCount()
                    Genre(
                        name = genre,
                        numberOfTracks = 1,
                    )
                }
            }
        }
    }

    internal fun remove(song: Song) {
        song.genres.forEach { genre ->
            songIdsCache[genre]?.remove(song.id)
            val genreObj = cache[genre] ?: return@forEach
            genreObj.numberOfTracks--
            if (genreObj.numberOfTracks <= 0) {
                cache.remove(genre)
                songIdsCache.remove(genre)
                _all.update { it - genre }
                emitCount()
            }
        }
    }

    fun reset() {
        cache.clear()
        songIdsCache.clear()
        _all.update {
            emptyList()
        }
        emitCount()
    }

    fun search(genreNames: List<String>, terms: String, limit: Int = 7) = searcher
        .search(terms, genreNames, maxLength = limit)

    fun sort(genreNames: List<String>, by: SortBy, reverse: Boolean): List<String> {
        val sensitive = symphony.settings.caseSensitiveSorting.value
        val sorted = when (by) {
            SortBy.CUSTOM -> genreNames
            SortBy.GENRE -> genreNames.sortedBy { get(it)?.name?.withCase(sensitive) }
            SortBy.TRACKS_COUNT -> genreNames.sortedBy { get(it)?.numberOfTracks }
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun count() = cache.size
    fun ids() = cache.keys.toList()
    fun values() = cache.values.toList()

    fun get(id: String) = cache[id]
    fun getSongIds(genre: String) = songIdsCache[genre]?.toList() ?: emptyList()
}
