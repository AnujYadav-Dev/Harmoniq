package dev.anujyadav.harmoniq.services.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.groove.Song
import dev.anujyadav.harmoniq.services.groove.SongSuggestions
import dev.anujyadav.harmoniq.services.radio.Radio
import dev.anujyadav.harmoniq.utils.EventUnsubscribeFn
import dev.anujyadav.harmoniq.utils.Logger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps [PlaybackSnapshot] up to date for widgets. Runs on events only, never on the position
 * moving on, and with no widget on the home screen it does nothing at all.
 */
class PlaybackPublisher(
    private val symphony: Symphony,
    private val host: PlaybackWidgetHost = AppWidgetPlaybackHost(symphony.applicationContext),
) : Symphony.Hooks {
    private val store = PlaybackSnapshotStore(symphony.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val publish = Runnable { publishNow() }
    private var isPublishScheduled = false
    private var lastPublishAt = NEVER
    private var lastSnapshot: PlaybackSnapshot? = null
    private var suggestions: List<PlaybackSnapshot.Item>? = null
    private var isLoadingSuggestions = false
    private var unsubscribeFromRadio: EventUnsubscribeFn? = null

    // a save and its widget update stay in order, without holding up anything else
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        unsubscribeFromRadio = symphony.radio.onUpdate.subscribe { event ->
            when (event) {
                is Radio.Events.Player,
                is Radio.Events.Queue,
                Radio.Events.QueueOption.LoopModeChanged,
                Radio.Events.QueueOption.ShuffleModeChanged,
                Radio.Events.QueueOption.SleepTimerChanged,
                    -> requestPublish()

                else -> {}
            }
        }
        backgroundScope.launch {
            symphony.groove.playlist.favorites.drop(1).collect { requestPublish() }
        }
        backgroundScope.launch {
            symphony.groove.exposer.isUpdating.drop(1).filter { !it }.collect { onScanFinish() }
        }
        backgroundScope.launch {
            symphony.groove.readyDeferred.await()
            onScanFinish()
        }
        backgroundScope.launch {
            val settings = symphony.settings
            merge(
                settings.themeMode.flow.drop(1),
                settings.useMaterialYou.flow.drop(1),
                settings.primaryColor.flow.drop(1),
            ).collect { redrawWidgets() }
        }
        backgroundScope.launch {
            val settings = symphony.settings
            merge(
                symphony.groove.playlist.updateId.drop(1),
                settings.playlistLastPlayed.flow.drop(1),
                settings.homePlaylistsSortBy.flow.drop(1),
                settings.homePlaylistsLimit.flow.drop(1),
                settings.homeShowFavorites.flow.drop(1),
                settings.homePlaylistIds.flow.drop(1),
            ).collect { requestPublish() }
        }
    }

    fun stop() {
        unsubscribeFromRadio?.invoke()
        unsubscribeFromRadio = null
        mainHandler.removeCallbacks(publish)
        backgroundScope.cancel()
    }

    /** For when a widget was just added. Ignores the time since the last one. */
    fun publishSoon() {
        mainHandler.post {
            lastPublishAt = NEVER
            requestPublishOnMain()
            if (currentSuggestions().isEmpty() && symphony.groove.readyDeferred.isCompleted) {
                loadSuggestions()
            }
        }
    }

    /** New suggestions, which otherwise stay the same as long as their songs exist. */
    suspend fun refreshSuggestionsNow() {
        val loaded = withContext(Dispatchers.Default) { computeSuggestions() } ?: return
        withContext(Dispatchers.Main) {
            suggestions = loaded
            lastPublishAt = NEVER
            requestPublishOnMain()
        }
    }

    // the look isn't part of the snapshot, widgets read it from the settings themselves
    private fun redrawWidgets() {
        if (host.hasWidgets()) {
            scope.launch { host.update() }
        }
    }

    private fun onScanFinish() {
        mainHandler.post {
            if (host.hasWidgets()) {
                val known = currentSuggestions().filter { isInLibrary(it.path) }
                suggestions = known
                if (known.isEmpty()) {
                    loadSuggestions()
                }
            }
            requestPublishOnMain()
        }
    }

    private fun loadSuggestions() {
        if (isLoadingSuggestions || !host.hasWidgets()) {
            return
        }
        isLoadingSuggestions = true
        // scores every song of the library
        backgroundScope.launch {
            val loaded = computeSuggestions()
            mainHandler.post {
                isLoadingSuggestions = false
                loaded?.let { suggestions = it }
                requestPublishOnMain()
            }
        }
    }

    private fun computeSuggestions() = try {
        symphony.groove.suggestions
            .suggest(SongSuggestions.Category.ForYou, PlaybackSnapshot.SUGGESTIONS_PREVIEW_LIMIT)
            .mapNotNull { id -> symphony.groove.song.get(id)?.let { toItem(it) } }
    } catch (err: Exception) {
        Logger.warn(TAG, "unable to load suggestions", err)
        null
    }

    private fun requestPublish() {
        mainHandler.post { requestPublishOnMain() }
    }

    // the first of a burst goes out right away, for a play button that responds at once. What
    // follows waits, and goes out together
    private fun requestPublishOnMain() {
        if (isPublishScheduled) {
            return
        }
        val wait = lastPublishAt + PUBLISH_INTERVAL.inWholeMilliseconds - SystemClock.uptimeMillis()
        if (lastPublishAt == NEVER || wait <= 0) {
            publishNow()
        } else {
            isPublishScheduled = true
            mainHandler.postDelayed(publish, wait)
        }
    }

    private fun publishNow() {
        isPublishScheduled = false
        lastPublishAt = SystemClock.uptimeMillis()
        if (!host.hasWidgets()) {
            return
        }
        val previous = lastSnapshot ?: store.read()
        val snapshot = buildSnapshot(previous)
        lastSnapshot = snapshot
        if (snapshot.hasSameContentAs(previous)) {
            return
        }
        scope.launch {
            store.write(snapshot)
            host.update()
        }
    }

    private fun currentSuggestions() = suggestions ?: (lastSnapshot ?: store.read()).suggestions

    private fun buildSnapshot(previous: PlaybackSnapshot): PlaybackSnapshot {
        val radio = symphony.radio
        val shared = previous.copy(
            shuffle = radio.queue.currentShuffleMode,
            loopMode = radio.queue.currentLoopMode,
            sleepTimerEndsAtMs = radio.sleepTimer?.endsAt,
            suggestions = currentSuggestions(),
            playlists = currentPlaylists(previous),
            isLibraryLoaded = symphony.groove.readyDeferred.isCompleted,
        )
        val song = radio.queue.currentSongId?.let { symphony.groove.song.get(it) }
            ?: return afterMusicEnded(shared)
        val position = radio.currentPlaybackPosition
        return shared.copy(
            song = toItem(song),
            isPlaying = radio.isPlaying,
            positionMs = position?.played ?: 0L,
            durationMs = position?.total?.takeIf { it > 0L } ?: song.duration,
            capturedAtMs = System.currentTimeMillis(),
            isFavorite = symphony.groove.playlist.favorites.value.contains(song.id),
            queue = upcomingQueue(),
        )
    }

    // stopped or not restored yet: the last song stays up, as one tap on play brings it back
    private fun afterMusicEnded(previous: PlaybackSnapshot): PlaybackSnapshot {
        val song = previous.song
        val shouldClear = song != null && shouldClearSong(
            isLibraryLoaded = symphony.groove.readyDeferred.isCompleted,
            isSongInLibrary = isInLibrary(song.path),
            librarySize = symphony.groove.song.count.value,
            hasMediaFolders = symphony.settings.mediaFolders.value.isNotEmpty(),
        )
        return when {
            shouldClear -> PlaybackSnapshot(suggestions = previous.suggestions)
            else -> previous.copy(
                isPlaying = false,
                queue = previous.queue.filter {
                    !symphony.groove.readyDeferred.isCompleted || isInLibrary(it.path)
                },
            )
        }
    }

    // keeps what's known while there's nothing to read, which also happens during a refresh
    private fun currentPlaylists(previous: PlaybackSnapshot): List<PlaybackSnapshot.PlaylistItem> {
        val playlists = symphony.groove.playlist
        if (!symphony.groove.readyDeferred.isCompleted || playlists.isUpdating.value) {
            return previous.playlists
        }
        return playlists.getHomePlaylists()
            .take(PlaybackSnapshot.PLAYLISTS_PREVIEW_LIMIT)
            .map { PlaybackSnapshot.PlaylistItem(it.id, it.title, it.numberOfTracks) }
    }

    private fun upcomingQueue(): List<PlaybackSnapshot.Item> {
        val queue = symphony.radio.queue
        return queue.currentQueue.toList()
            .asSequence()
            .mapIndexed { index, id -> index to id }
            .drop(queue.currentSongIndex + 1)
            .mapNotNull { (index, id) -> symphony.groove.song.get(id)?.let { toItem(it, index) } }
            .take(PlaybackSnapshot.QUEUE_PREVIEW_LIMIT)
            .toList()
    }

    private fun isInLibrary(path: String) = symphony.groove.song.pathCache.containsKey(path)

    private fun toItem(song: Song, queueIndex: Int? = null) = PlaybackSnapshot.Item(
        path = song.path,
        title = song.title,
        artist = song.artists.joinToString(", "),
        album = song.album,
        artworkPath = song.coverFile?.let { symphony.database.artworkCache.get(it).absolutePath },
        queueIndex = queueIndex,
    )

    override fun onSymphonyReady() {
        start()
    }

    override fun onSymphonyDestroy() {
        stop()
    }

    internal companion object {
        private const val TAG = "PlaybackPublisher"
        private const val NEVER = -1L
        val PUBLISH_INTERVAL = 1.seconds

        /**
         * A song the library doesn't have can't be played, so it shouldn't be offered. But with
         * nothing at all loaded, its folder is more likely unavailable for now than the song
         * gone, unless the user removed all their folders.
         */
        fun shouldClearSong(
            isLibraryLoaded: Boolean,
            isSongInLibrary: Boolean,
            librarySize: Int,
            hasMediaFolders: Boolean,
        ) = isLibraryLoaded && !isSongInLibrary && (librarySize > 0 || !hasMediaFolders)
    }
}
