package dev.anujyadav.harmoniq.services.groove

import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.groove.repositories.AlbumArtistRepository
import dev.anujyadav.harmoniq.services.groove.repositories.AlbumRepository
import dev.anujyadav.harmoniq.services.groove.repositories.ArtistRepository
import dev.anujyadav.harmoniq.services.groove.repositories.GenreRepository
import dev.anujyadav.harmoniq.services.groove.repositories.PlaylistRepository
import dev.anujyadav.harmoniq.services.groove.repositories.SongRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class Groove(private val symphony: Symphony) : Symphony.Hooks {
    enum class Kind {
        SONG,
        ALBUM,
        ARTIST,
        ALBUM_ARTIST,
        GENRE,
        PLAYLIST,
    }

    val coroutineScope = CoroutineScope(Dispatchers.Default)
    var readyDeferred = CompletableDeferred<Boolean>()

    // a reset during another refresh's scan would mix the results of both
    private val refreshMutex = Mutex()

    val exposer = MediaExposer(symphony)
    val song = SongRepository(symphony)
    val album = AlbumRepository(symphony)
    val artist = ArtistRepository(symphony)
    val albumArtist = AlbumArtistRepository(symphony)
    val genre = GenreRepository(symphony)
    val playlist = PlaylistRepository(symphony)
    val suggestions = SongSuggestions(symphony)
    val uriGrants = UriGrants(symphony)

    private suspend fun fetch() {
        coroutineScope.launch {
            awaitAll(
                async { exposer.fetch() },
                async { playlist.fetch() },
            )
        }.join()
        uriGrants.releaseUnused()
    }

    private suspend fun reset() {
        coroutineScope.launch {
            awaitAll(
                async { exposer.reset() },
                async { albumArtist.reset() },
                async { album.reset() },
                async { artist.reset() },
                async { genre.reset() },
                async { playlist.reset() },
                async { song.reset() },
            )
        }.join()
    }

    private suspend fun clearCache() {
        symphony.database.songCache.clear()
        symphony.database.artworkCache.clear()
        symphony.database.lyricsCache.clear()
    }

    data class FetchOptions(
        val resetInMemoryCache: Boolean = false,
        val resetPersistentCache: Boolean = false,
    )

    fun fetch(options: FetchOptions) {
        coroutineScope.launch {
            refreshMutex.withLock {
                if (options.resetInMemoryCache) {
                    reset()
                }
                if (options.resetPersistentCache) {
                    clearCache()
                }
                fetch()
            }
        }
    }

    // stops playback and re-scans everything, used when the library sources change
    fun refreshLibrary(clearCache: Boolean = false) {
        symphony.radio.stop()
        fetch(
            FetchOptions(
                resetInMemoryCache = true,
                resetPersistentCache = clearCache,
            )
        )
    }

    /**
     * A scan reports every file it finds, only what changed may reach the repositories:
     * their track counts and lists would grow on each rescan otherwise.
     */
    internal fun onSongScanned(scanned: Song) {
        when (val knownId = song.pathCache[scanned.path]) {
            scanned.id -> return
            // the file was modified, which gives it a new id
            null -> {}
            else -> song.get(knownId)?.let { removeSong(it) }
        }
        albumArtist.onSong(scanned)
        album.onSong(scanned)
        artist.onSong(scanned)
        genre.onSong(scanned)
        song.onSong(scanned)
    }

    internal fun removeSong(removed: Song) {
        albumArtist.remove(removed)
        album.remove(removed)
        artist.remove(removed)
        genre.remove(removed)
        song.remove(removed)
    }

    /**
     * Makes these songs known ahead of the scan, from what the last scan saved. The scan then
     * treats them like any song it has seen before, see [onSongScanned], and takes them out if
     * their files are gone.
     */
    internal suspend fun loadFromSongCache(songIds: Set<String>) {
        val cached = symphony.database.songCache.entriesPathMapped().values
            .filter { songIds.contains(it.id) }
        withContext(Dispatchers.Main) {
            // whatever the scan already found is newer
            cached.filter { !song.pathCache.containsKey(it.path) }.forEach { onSongScanned(it) }
        }
    }

    // nothing is reset, whatever's playing or open isn't affected
    private fun rescanIfIdle() {
        if (readyDeferred.isCompleted && !exposer.isUpdating.value) {
            fetch(FetchOptions())
        }
    }

    override fun onSymphonyReady() {
        coroutineScope.launch {
            refreshMutex.withLock {
                fetch()
            }
            readyDeferred.complete(true)
        }
    }

    // the library may have changed while the app wasn't open, no scan notices that by itself
    override fun onSymphonyActivityReopen() {
        rescanIfIdle()
    }
}
