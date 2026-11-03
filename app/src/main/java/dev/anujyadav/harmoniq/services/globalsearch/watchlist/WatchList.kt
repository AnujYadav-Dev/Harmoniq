package dev.anujyadav.harmoniq.services.globalsearch.watchlist

import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Watch List, owned by [dev.anujyadav.harmoniq.services.globalsearch.GlobalSearch].
 * The in-memory list updates at once; Room is written in the background, like playlists.
 */
class WatchList(private val symphony: Symphony, private val coroutineScope: CoroutineScope) {
    private val store get() = symphony.database.watchList

    private val _items = MutableStateFlow<List<WatchListItem>>(emptyList())
    val items = _items.asStateFlow()

    val ids: StateFlow<Set<String>> = _items
        .map { all -> all.map { it.videoId }.toSet() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptySet())

    fun load() = coroutineScope.launch {
        try {
            val stored = store.entries()
            // anything added before the load finished stays
            _items.update { current -> current + stored.filter { s -> current.none { it.videoId == s.videoId } } }
        } catch (err: Exception) {
            Logger.error(TAG, "load failed", err)
        }
    }

    fun contains(videoId: String) = ids.value.contains(videoId)

    fun add(result: SearchResultDto, sourcePlaylistId: String? = null) =
        restore(WatchListItem.from(result, System.currentTimeMillis(), sourcePlaylistId))

    // puts a removed item back as it was (undo), keeping its original date
    fun restore(item: WatchListItem) {
        _items.update { current -> listOf(item) + current.filterNot { it.videoId == item.videoId } }
        persist { store.upsert(item) }
    }

    fun remove(videoId: String): WatchListItem? {
        val removed = _items.value.find { it.videoId == videoId } ?: return null
        _items.update { current -> current.filterNot { it.videoId == videoId } }
        persist { store.delete(videoId) }
        return removed
    }

    fun toggle(result: SearchResultDto, sourcePlaylistId: String? = null) {
        if (contains(result.videoId)) remove(result.videoId) else add(result, sourcePlaylistId)
    }

    private fun persist(block: suspend () -> Unit) = coroutineScope.launch {
        try {
            block()
        } catch (err: Exception) {
            Logger.error(TAG, "write failed", err)
        }
    }

    companion object {
        private const val TAG = "WatchList"
    }
}
