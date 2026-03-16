package dev.anujyadav.harmoniq.services.database

import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.database.store.ArtworkCacheStore
import dev.anujyadav.harmoniq.services.database.store.LyricsCacheStore

class Database(symphony: Symphony) {
    private val cache = CacheDatabase.create(symphony)
    private val persistent = PersistentDatabase.create(symphony)

    val artworkCache = ArtworkCacheStore(symphony)
    val lyricsCache = LyricsCacheStore(symphony)
    val songCache get() = cache.songs()
    val playlists get() = persistent.playlists()
}
