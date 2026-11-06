package dev.anujyadav.harmoniq.services.groove.repositories

import android.net.Uri
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.groove.Playlist
import dev.anujyadav.harmoniq.utils.ActivityUtils
import dev.anujyadav.harmoniq.utils.FuzzySearchOption
import dev.anujyadav.harmoniq.utils.FuzzySearcher
import dev.anujyadav.harmoniq.utils.KeyGenerator
import dev.anujyadav.harmoniq.utils.Logger
import dev.anujyadav.harmoniq.utils.mutate
import dev.anujyadav.harmoniq.utils.plusIfAbsent
import dev.anujyadav.harmoniq.utils.withCase
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.anujyadav.harmoniq.services.groove.PlaylistCoverStore

class PlaylistRepository(private val symphony: Symphony) {
    enum class SortBy {
        CUSTOM,
        TITLE,
        TRACKS_COUNT,
    }

    enum class HomeSortBy {
        CUSTOM,
        RECENTLY_PLAYED,
        RECENTLY_CREATED,
        TITLE,
        TRACKS_COUNT,
    }

    private val cache = ConcurrentHashMap<String, Playlist>()
    internal val idGenerator = KeyGenerator.TimeIncremental()
    val covers = PlaylistCoverStore(symphony)
    private val searcher = FuzzySearcher<String>(
        options = listOf(FuzzySearchOption({ v -> get(v)?.title?.let { compareString(it) } }))
    )

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating = _isUpdating.asStateFlow()
    private val _updateId = MutableStateFlow(0L)
    val updateId = _updateId.asStateFlow()
    private val _all = MutableStateFlow<List<String>>(emptyList())
    val all = _all.asStateFlow()
    private val _count = MutableStateFlow(0)
    val count = _count.asStateFlow()
    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites = _favorites.asStateFlow()

    private fun emitUpdate(value: Boolean) = _isUpdating.update {
        value
    }

    private fun emitUpdateId() = _updateId.update {
        System.currentTimeMillis()
    }

    private fun emitCount() = _count.update {
        cache.size
    }

    suspend fun fetch() {
        emitUpdate(true)
        try {
            val context = symphony.applicationContext
            val playlists = symphony.database.playlists.entries()
            playlists.values.map { x ->
                val playlist = when {
                    x.isLocal -> {
                        ActivityUtils.makePersistableReadableUri(context, x.uri!!)
                        Playlist.parse(symphony, x.id, x.uri)
                    }

                    else -> x
                }
                cache[playlist.id] = playlist
                // fetch also runs on rescans that keep what is already loaded
                _all.update {
                    it.plusIfAbsent(playlist.id)
                }
                emitUpdateId()
                emitCount()
            }
            if (!cache.containsKey(FAVORITE_PLAYLIST)) {
                add(getFavorites())
            }
        } catch (_: FileNotFoundException) {
        } catch (err: Exception) {
            Logger.error("PlaylistRepository", "fetch failed", err)
        }
        _favorites.update {
            getFavorites().getSongIds(symphony)
        }
        emitUpdateId()
        emitUpdate(false)
    }

    fun reset() {
        emitUpdate(true)
        cache.clear()
        _all.update {
            emptyList()
        }
        emitCount()
        _favorites.update {
            emptyList()
        }
        emitUpdateId()
        emitUpdate(false)
    }

    fun search(playlistIds: List<String>, terms: String, limit: Int = 7) = searcher
        .search(terms, playlistIds, maxLength = limit)

    fun sort(playlistIds: List<String>, by: SortBy, reverse: Boolean): List<String> {
        val sensitive = symphony.settings.caseSensitiveSorting.value
        val sorted = when (by) {
            SortBy.CUSTOM -> {
                val prefix = listOfNotNull(FAVORITE_PLAYLIST)
                val others = playlistIds.toMutableList()
                prefix.forEach { others.remove(it) }
                prefix + others
            }

            SortBy.TITLE -> playlistIds.sortedBy { get(it)?.title?.withCase(sensitive) }
            SortBy.TRACKS_COUNT -> playlistIds.sortedBy { get(it)?.numberOfTracks }
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun count() = cache.size
    fun ids() = cache.keys.toList()
    fun values() = cache.values.toList()

    fun get(id: String) = cache[id]
    fun get(ids: List<String>) = ids.mapNotNull { get(it) }

    fun getFavorites() = cache[FAVORITE_PLAYLIST]
        ?: create(FAVORITE_PLAYLIST, "Favorites", emptyList())

    fun create(title: String, songIds: List<String>) = create(idGenerator.next(), title, songIds)
    private fun create(id: String, title: String, songIds: List<String>) = Playlist(
        id = id,
        title = title,
        songPaths = songIds.mapNotNull { symphony.groove.song.get(it)?.path },
        uri = null,
        path = null,
    )

    fun add(playlist: Playlist) {
        cache[playlist.id] = playlist
        _all.update {
            it.plusIfAbsent(playlist.id)
        }
        emitUpdateId()
        emitCount()
        symphony.groove.coroutineScope.launch {
            symphony.database.playlists.insert(playlist)
        }
    }

    fun delete(id: String) {
        Logger.error(
            "PlaylistRepository",
            "cache ${cache.containsKey(id)}"
        )
        cache.remove(id)?.uri?.let {
            runCatching {
                ActivityUtils.makePersistableReadableUri(symphony.applicationContext, it)
            }
        }
        _all.update {
            it - id
        }
        forgetPlaylist(id)
        covers.delete(id)
        emitUpdateId()
        emitCount()
        symphony.groove.coroutineScope.launch {
            symphony.database.playlists.delete(id)
        }
    }

    fun update(id: String, songIds: List<String>) {
        val playlist = get(id) ?: return
        val updated = Playlist(
            id = id,
            title = playlist.title,
            songPaths = songIds.mapNotNull { symphony.groove.song.get(it)?.path },
            uri = playlist.uri,
            path = playlist.path,
        )
        cache[id] = updated
        emitUpdateId()
        emitCount()
        if (id == FAVORITE_PLAYLIST) {
            _favorites.update {
                songIds
            }
        }
        symphony.groove.coroutineScope.launch {
            symphony.database.playlists.update(updated)
        }
    }

    // NOTE: maybe we shouldn't use groove's coroutine scope?
    fun favorite(songId: String) {
        val favorites = getFavorites()
        val songIds = favorites.getSongIds(symphony)
        if (songIds.contains(songId)) {
            return
        }
        update(favorites.id, songIds.mutate { add(songId) })
    }

    fun unfavorite(songId: String) {
        val favorites = getFavorites()
        val songIds = favorites.getSongIds(symphony)
        if (!songIds.contains(songId)) {
            return
        }
        update(favorites.id, songIds.mutate { remove(songId) })
    }

    fun isFavoritesId(id: String) = id == FAVORITE_PLAYLIST
    fun isFavoritesPlaylist(playlist: Playlist) = isFavoritesId(playlist.id)
    fun isBuiltInPlaylist(playlist: Playlist) = isFavoritesPlaylist(playlist)

    fun savePlaylistToUri(playlist: Playlist, uri: Uri) {
        val outputStream = symphony.applicationContext.contentResolver.openOutputStream(uri, "w")
        outputStream?.use {
            val content = playlist.songPaths.joinToString("\n")
            it.write(content.toByteArray())
        }
    }

    fun renamePlaylist(playlist: Playlist, title: String) {
        val renamed = playlist.withTitle(title)
        cache[playlist.id] = renamed
        emitUpdateId()
        symphony.groove.coroutineScope.launch {
            symphony.database.playlists.update(renamed)
        }
    }

    /**
     * Uses the image at [uri] as the cover of playlist [id], returns whether it worked.
     */
    suspend fun setCover(id: String, uri: Uri) = try {
        covers.save(id, uri)
        emitUpdateId()
        true
    } catch (err: Exception) {
        Logger.error("PlaylistRepository", "setting cover failed", err)
        false
    }

    // goes back to the artwork of the first song
    fun removeCover(id: String) {
        covers.delete(id)
        emitUpdateId()
    }

    fun hasCustomCover(id: String) = covers.has(id)

    fun markPlayed(id: String) {
        val lastPlayed = symphony.settings.playlistLastPlayed.value.toMutableMap()
        lastPlayed[id] = System.currentTimeMillis()
        symphony.settings.playlistLastPlayed.setValue(lastPlayed)
    }

    /**
     * Playlists shown as shortcuts on the home screen and in the library's "for you"
     * section. Favorites never counts towards the limit.
     */
    fun getHomePlaylists(): List<Playlist> {
        val settings = symphony.settings
        val limit = settings.homePlaylistsLimit.value.coerceIn(0, HOME_PLAYLISTS_MAX)
        val showFavorites = settings.homeShowFavorites.value
        if (settings.homePlaylistsSortBy.value == HomeSortBy.CUSTOM) {
            var count = 0
            return get(settings.homePlaylistIds.value).filter {
                when {
                    isFavoritesPlaylist(it) -> showFavorites
                    else -> count++ < limit
                }
            }
        }
        val playlistIds = values().filterNot { isBuiltInPlaylist(it) }.map { it.id }
        val lastPlayed = settings.playlistLastPlayed.value
        val sortedIds = when (settings.homePlaylistsSortBy.value) {
            // never played playlists fall back to the newest ones
            HomeSortBy.RECENTLY_PLAYED -> playlistIds.sortedWith(
                compareByDescending<String> { lastPlayed[it] ?: 0L }.thenByDescending { createdAt(it) }
            )

            HomeSortBy.TITLE -> sort(playlistIds, SortBy.TITLE, false)
            HomeSortBy.TRACKS_COUNT -> sort(playlistIds, SortBy.TRACKS_COUNT, true)
            else -> playlistIds.sortedByDescending { createdAt(it) }
        }
        val favorites = getFavorites().takeIf { showFavorites }
        return listOfNotNull(favorites) + get(sortedIds.take(limit))
    }

    // generated ids start with their creation timestamp
    private fun createdAt(id: String) = id.substringBefore('.').toLongOrNull() ?: 0L

    private fun forgetPlaylist(id: String) {
        val settings = symphony.settings
        settings.playlistLastPlayed.value.let {
            if (it.containsKey(id)) settings.playlistLastPlayed.setValue(it - id)
        }
        settings.homePlaylistIds.value.let {
            if (it.contains(id)) settings.homePlaylistIds.setValue(it - id)
        }
    }

    internal fun onScanFinish() {
        _favorites.update {
            getFavorites().getSongIds(symphony)
        }
        emitUpdateId()
    }

    companion object {
        private const val FAVORITE_PLAYLIST = "favorites"
        const val HOME_PLAYLISTS_MAX = 10
        const val HOME_PLAYLISTS_DEFAULT_LIMIT = 4
    }
}
