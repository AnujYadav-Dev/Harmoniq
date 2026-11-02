package dev.anujyadav.harmoniq.services.groove

import dev.anujyadav.harmoniq.Symphony
import kotlin.random.Random
import dev.anujyadav.harmoniq.services.groove.repositories.SongRepository

/**
 * Offline song suggestions, built from what the library already knows: favorites,
 * playlists (weighted by how recently they were played) and recently added songs.
 * Every other song is scored by how much it shares with those (genres, artists,
 * album, era), and a random pick among the best scored ones keeps it varied.
 */
class SongSuggestions(private val symphony: Symphony) {
    sealed interface Category {
        val key: String

        // songs similar to what the user already listens to
        data object ForYou : Category {
            override val key = "for_you"
        }

        // songs that are not in any playlist yet
        data object Rediscover : Category {
            override val key = "rediscover"
        }

        data class Genre(val name: String) : Category {
            override val key = "$GENRE_KEY_PREFIX$name"
        }

        companion object {
            private const val GENRE_KEY_PREFIX = "genre:"

            fun fromKey(key: String?) = when {
                key == null -> null
                key == ForYou.key -> ForYou
                key == Rediscover.key -> Rediscover
                key.startsWith(GENRE_KEY_PREFIX) -> Genre(key.removePrefix(GENRE_KEY_PREFIX))
                else -> null
            }
        }
    }

    private class Affinity(
        val genres: Map<String, Float>,
        val artists: Map<String, Float>,
        val albums: Map<String, Float>,
        val decades: Map<Int, Float>,
        val seedSongIds: Set<String>,
        val playlistSongIds: Set<String>,
        val favoriteSongIds: Set<String>,
        // favorites, recently played playlists and recently added songs
        val recentSongIds: Set<String>,
    )

    /**
     * Genres to offer as categories: the ones the user listens to first, then the
     * largest ones in the library.
     */
    fun topGenres(limit: Int = TOP_GENRES_LIMIT): List<String> {
        val affinity = buildAffinity()
        return symphony.groove.genre.values()
            .filter { it.numberOfTracks > 0 }
            .sortedWith(
                compareByDescending<Genre> { affinity.genres[it.name] ?: 0f }
                    .thenByDescending { it.numberOfTracks }
            )
            .take(limit)
            .map { it.name }
    }

    fun suggest(
        category: Category,
        limit: Int = SUGGESTIONS_LIMIT,
        random: Random = Random.Default,
    ): List<String> {
        val affinity = buildAffinity()
        val allSongIds = symphony.groove.song.ids()
        val candidates = when (category) {
            // songs already in playlists stay eligible (ranked lower), otherwise a
            // library kept in playlists would leave nothing to suggest
            Category.ForYou -> allSongIds
                .filterNot { affinity.favoriteSongIds.contains(it) }
                .ifEmpty { allSongIds }

            Category.Rediscover -> rediscoverCandidates(
                allSongIds,
                affinity.playlistSongIds,
                affinity.recentSongIds,
            )

            is Category.Genre -> symphony.groove.genre.getSongIds(category.name)
        }
        val scored = candidates.mapNotNull { id ->
            symphony.groove.song.get(id)?.let { song ->
                val knownFactor = if (affinity.seedSongIds.contains(id)) KNOWN_SONG_FACTOR else 1f
                id to score(song, affinity) * knownFactor
            }
        }
        return pick(scored, limit, random)
    }

    private fun buildAffinity(): Affinity {
        val genres = mutableMapOf<String, Float>()
        val artists = mutableMapOf<String, Float>()
        val albums = mutableMapOf<String, Float>()
        val decades = mutableMapOf<Int, Float>()
        val seedSongIds = mutableSetOf<String>()
        val playlistSongIds = mutableSetOf<String>()
        val recentSongIds = mutableSetOf<String>()

        fun addSeed(songId: String, weight: Float) {
            val song = symphony.groove.song.get(songId) ?: return
            seedSongIds.add(songId)
            song.genres.forEach { genres.merge(it, weight, Float::plus) }
            (song.artists + song.albumArtists).forEach { artists.merge(it, weight, Float::plus) }
            song.album?.let { albums.merge(it, weight, Float::plus) }
            song.decade()?.let { decades.merge(it, weight, Float::plus) }
        }

        val playlists = symphony.groove.playlist
        val lastPlayed = symphony.settings.playlistLastPlayed.value
        val recentlyPlayedIds = lastPlayed.entries
            .sortedByDescending { it.value }
            .take(RECENT_PLAYLISTS_COUNT)
            .map { it.key }
            .toSet()
        playlists.values().forEach { playlist ->
            val weight = when {
                playlists.isFavoritesPlaylist(playlist) -> FAVORITES_WEIGHT
                recentlyPlayedIds.contains(playlist.id) -> RECENT_PLAYLIST_WEIGHT
                else -> PLAYLIST_WEIGHT
            }
            val isRecent = playlists.isFavoritesPlaylist(playlist) || recentlyPlayedIds.contains(playlist.id)
            playlist.getSongIds(symphony).forEach {
                playlistSongIds.add(it)
                if (isRecent) recentSongIds.add(it)
                addSeed(it, weight)
            }
        }
        symphony.groove.song
            .sort(symphony.groove.song.ids(), SongRepository.SortBy.DATE_MODIFIED, true)
            .take(RECENTLY_ADDED_SEEDS)
            .forEach {
                recentSongIds.add(it)
                addSeed(it, RECENTLY_ADDED_WEIGHT)
            }

        val favoriteSongIds = playlists.getFavorites().getSongIds(symphony).toSet()
        return Affinity(
            genres,
            artists,
            albums,
            decades,
            seedSongIds,
            playlistSongIds,
            favoriteSongIds,
            recentSongIds,
        )
    }

    private fun score(song: Song, affinity: Affinity): Float {
        var score = 0f
        song.genres.forEach { score += (affinity.genres[it] ?: 0f) * GENRE_SCORE }
        (song.artists + song.albumArtists).forEach { score += (affinity.artists[it] ?: 0f) * ARTIST_SCORE }
        song.album?.let { score += (affinity.albums[it] ?: 0f) * ALBUM_SCORE }
        song.decade()?.let { score += (affinity.decades[it] ?: 0f) * DECADE_SCORE }
        return score
    }

    private fun Song.decade() = year?.let { it / DECADE_YEARS * DECADE_YEARS }

    companion object {
        /**
         * Songs outside every playlist first. When the whole library is in playlists,
         * the ones the user has not been listening to lately, then anything.
         */
        internal fun rediscoverCandidates(
            allSongIds: List<String>,
            playlistSongIds: Set<String>,
            recentSongIds: Set<String>,
        ) = allSongIds
            .filterNot { playlistSongIds.contains(it) }
            .ifEmpty { allSongIds.filterNot { recentSongIds.contains(it) } }
            .ifEmpty { allSongIds }

        /**
         * Picks [limit] songs at random among the best scored ones, filling up with
         * random others when too few of them relate to the user's taste.
         */
        internal fun pick(
            scored: List<Pair<String, Float>>,
            limit: Int,
            random: Random,
        ): List<String> {
            val ranked = scored.sortedByDescending { it.second }
            val picked = ranked
                .take(limit * CANDIDATE_POOL_MULTIPLIER)
                .filter { it.second > 0f }
                .map { it.first }
                .shuffled(random)
                .take(limit)
            if (picked.size >= limit) {
                return picked
            }
            val fill = ranked.map { it.first }
                .filterNot { picked.contains(it) }
                .shuffled(random)
                .take(limit - picked.size)
            return picked + fill
        }

        const val SUGGESTIONS_LIMIT = 10
        const val TOP_GENRES_LIMIT = 6

        // the random pick happens among this many times [SUGGESTIONS_LIMIT] best songs
        private const val CANDIDATE_POOL_MULTIPLIER = 4
        private const val RECENT_PLAYLISTS_COUNT = 3
        private const val RECENTLY_ADDED_SEEDS = 20
        private const val DECADE_YEARS = 10

        // songs already in playlists or recently added still count, but less
        private const val KNOWN_SONG_FACTOR = 0.3f

        // how much each source counts towards the user's taste
        private const val FAVORITES_WEIGHT = 3f
        private const val RECENT_PLAYLIST_WEIGHT = 2f
        private const val PLAYLIST_WEIGHT = 1f
        private const val RECENTLY_ADDED_WEIGHT = 0.5f

        // how much each shared attribute counts towards a song's score
        private const val GENRE_SCORE = 3f
        private const val ARTIST_SCORE = 2f
        private const val ALBUM_SCORE = 1f
        private const val DECADE_SCORE = 0.5f
    }
}
