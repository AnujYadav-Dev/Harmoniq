package dev.anujyadav.harmoniq.services.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.groove.Song
import dev.anujyadav.harmoniq.services.radio.Radio
import dev.anujyadav.harmoniq.services.radio.RadioPlayer
import dev.anujyadav.harmoniq.utils.Logger
import dev.anujyadav.harmoniq.utils.TimedContent
import dev.anujyadav.harmoniq.widgets.LyricsReceiver
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the lyrics widget shows right now. */
data class LyricsWidgetContent(
    val kind: Kind = Kind.Idle,
    val songTitle: String? = null,
    val previous: String? = null,
    val current: String? = null,
    val next: String? = null,
    val songArtist: String? = null,
    val artworkPath: String? = null,
) {
    val cover get() = artworkPath?.let { WidgetCover(it) }

    enum class Kind {
        /** Nothing is playing, or nothing has been followed yet. */
        Idle,
        Synced,
        Unsynced,
        Missing,
    }
}

/**
 * Holds [LyricsWidgetContent] in memory only. Lines change every few seconds, a save for each
 * would be a disk write for something that means nothing once the music has stopped anyway.
 */
object LyricsWidgetStore {
    private val mutableContent = MutableStateFlow(LyricsWidgetContent())
    val content = mutableContent.asStateFlow()

    internal fun set(content: LyricsWidgetContent) {
        mutableContent.value = content
    }
}

/**
 * Follows the lyrics for the widget. The most frequent thing any widget does, so it does nothing
 * at all unless all of these are true: the setting is on, a lyrics widget exists, music is
 * playing and the screen is on. It works with the position updates the player already sends,
 * which are one check per update until the line actually changes.
 */
class LyricsWidgetPublisher(
    private val symphony: Symphony,
    private val host: PlaybackWidgetHost = AppWidgetPlaybackHost(
        symphony.applicationContext,
        listOf(LyricsReceiver::class.java),
    ),
) : Symphony.Hooks {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val powerManager = symphony.applicationContext.getSystemService(PowerManager::class.java)
    private val evaluate = Runnable { evaluateNow() }
    private val publishLine = Runnable { publishLineNow() }

    // a save and its widget update stay in order, without holding up anything else
    @OptIn(ExperimentalCoroutinesApi::class)
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var loadJob: Job? = null
    private var loadedSongId: String? = null
    private var loadedSong: PlaybackSnapshot.Item? = null
    private var lastPublishAt = NEVER
    private var isLinePublishScheduled = false
    private var isScreenReceiverRegistered = false

    // read on the player's timer thread, for each position update
    @Volatile
    private var isFollowing = false

    @Volatile
    private var syncedLines: List<Pair<Long, String>> = emptyList()

    @Volatile
    private var lineIndex = NO_LINE

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    fun start() {
        symphony.radio.onUpdate.subscribe { event ->
            when (event) {
                is Radio.Events.Player, Radio.Events.Queue.IndexChanged -> refresh()
                else -> {}
            }
        }
        symphony.radio.onPlaybackPositionUpdate.subscribe(this::onPosition)
        backgroundScope.launch {
            symphony.settings.lyricsWidgetEnabled.flow.drop(1).collect { refresh() }
        }
        // an .lrc next to the song isn't known until the scan has seen it
        backgroundScope.launch {
            symphony.groove.exposer.isUpdating.drop(1).filter { !it }.collect {
                mainHandler.post {
                    if (LyricsWidgetStore.content.value.kind == LyricsWidgetContent.Kind.Missing) {
                        loadedSongId = null
                        evaluateNow()
                    }
                }
            }
        }
        refresh()
    }

    fun stop() {
        isFollowing = false
        mainHandler.removeCallbacks(evaluate)
        mainHandler.removeCallbacks(publishLine)
        setScreenReceiverRegistered(false)
        backgroundScope.cancel()
    }

    /** For when something changed that this can't hear about, like a widget being added. */
    fun refresh() {
        mainHandler.removeCallbacks(evaluate)
        mainHandler.post(evaluate)
    }

    private fun evaluateNow() {
        val hasWidget = symphony.settings.lyricsWidgetEnabled.value && host.hasWidgets()
        setScreenReceiverRegistered(hasWidget)
        if (!hasWidget) {
            isFollowing = false
            return
        }
        val radio = symphony.radio
        val song = radio.queue.currentSongId?.let { symphony.groove.song.get(it) }
        if (song == null) {
            isFollowing = false
            forgetSong()
            publish(LyricsWidgetContent())
            return
        }
        val canFollow = radio.isPlaying && powerManager.isInteractive
        when {
            !canFollow -> isFollowing = false
            song.id != loadedSongId -> load(song)
            else -> {
                isFollowing = syncedLines.isNotEmpty()
                // caught up at once, as nothing moved it on while the screen was off
                radio.currentPlaybackPosition?.let { onPosition(it) }
                if (isFollowing) {
                    requestLinePublish()
                }
            }
        }
    }

    private fun forgetSong() {
        loadJob?.cancel()
        loadedSongId = null
        loadedSong = null
        syncedLines = emptyList()
        lineIndex = NO_LINE
    }

    private fun load(song: Song) {
        forgetSong()
        isFollowing = false
        loadedSongId = song.id
        loadedSong = song.toWidgetItem(symphony)
        loadJob = backgroundScope.launch {
            val content = try {
                symphony.groove.song.getLyrics(song)?.let { TimedContent.fromLyrics(it) }
            } catch (err: Exception) {
                Logger.warn(TAG, "unable to load lyrics of ${song.id}", err)
                null
            }
            withContext(Dispatchers.Main) {
                if (loadedSongId == song.id) {
                    onLoaded(content)
                }
            }
        }
    }

    private fun onLoaded(content: TimedContent?) {
        val kind = kindOf(content)
        if (kind == LyricsWidgetContent.Kind.Synced) {
            syncedLines = content!!.pairs
            evaluateNow()
        } else {
            publish(LyricsWidgetContent(kind = kind).withLoadedSong())
        }
    }

    // on the player's timer thread, up to ten times a second: this has to stay this small
    private fun onPosition(position: RadioPlayer.PlaybackPosition) {
        if (!isFollowing) {
            return
        }
        val lines = syncedLines
        val index = lineIndex
        val start = lines.getOrNull(index)?.first ?: Long.MIN_VALUE
        val end = lines.getOrNull(index + 1)?.first ?: Long.MAX_VALUE
        if (position.played in start until end) {
            return
        }
        lineIndex = lineIndexAt(lines, position.played)
        mainHandler.post { requestLinePublish() }
    }

    // many short lines in a row don't each get to be drawn, the last one goes out together
    private fun requestLinePublish() {
        if (isLinePublishScheduled) {
            return
        }
        val wait = lastPublishAt + LINE_UPDATE_INTERVAL.inWholeMilliseconds - SystemClock.uptimeMillis()
        if (lastPublishAt == NEVER || wait <= 0) {
            publishLineNow()
        } else {
            isLinePublishScheduled = true
            mainHandler.postDelayed(publishLine, wait)
        }
    }

    private fun publishLineNow() {
        isLinePublishScheduled = false
        lastPublishAt = SystemClock.uptimeMillis()
        // paused or the screen went off since it was scheduled: this picks up again from evaluateNow
        if (!isFollowing) {
            return
        }
        publish(contentAt(syncedLines, lineIndex, loadedSong?.title).withLoadedSong())
    }

    private fun LyricsWidgetContent.withLoadedSong() = copy(
        songTitle = loadedSong?.title,
        songArtist = loadedSong?.artist,
        artworkPath = loadedSong?.artworkPath,
    )

    private fun publish(content: LyricsWidgetContent) {
        if (LyricsWidgetStore.content.value == content) {
            return
        }
        LyricsWidgetStore.set(content)
        updateScope.launch { host.update() }
    }

    private fun setScreenReceiverRegistered(register: Boolean) {
        if (register == isScreenReceiverRegistered) {
            return
        }
        isScreenReceiverRegistered = register
        val context = symphony.applicationContext
        if (register) {
            ContextCompat.registerReceiver(
                context,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            context.unregisterReceiver(screenReceiver)
        }
    }

    override fun onSymphonyReady() {
        start()
    }

    override fun onSymphonyDestroy() {
        stop()
    }

    internal companion object {
        private const val TAG = "LyricsWidgetPublisher"
        private const val NEVER = -1L
        const val NO_LINE = -1
        val LINE_UPDATE_INTERVAL = 1.seconds

        fun kindOf(content: TimedContent?) = when {
            content == null || content.pairs.all { it.second.isBlank() } -> LyricsWidgetContent.Kind.Missing
            content.isSynced -> LyricsWidgetContent.Kind.Synced
            else -> LyricsWidgetContent.Kind.Unsynced
        }

        /** The line being sung at [position], or [NO_LINE] before the first one. */
        fun lineIndexAt(lines: List<Pair<Long, String>>, position: Long) =
            lines.indexOfLast { it.first <= position }

        /** The neighbours skip blank lines, which are only pauses in the song. */
        fun contentAt(lines: List<Pair<Long, String>>, index: Int, songTitle: String?): LyricsWidgetContent {
            fun text(i: Int) = lines.getOrNull(i)?.second?.takeIf { it.isNotBlank() }
            return LyricsWidgetContent(
                kind = LyricsWidgetContent.Kind.Synced,
                songTitle = songTitle,
                previous = (index - 1 downTo 0).firstNotNullOfOrNull { text(it) },
                current = text(index),
                next = (index + 1..lines.lastIndex).firstNotNullOfOrNull { text(it) },
            )
        }
    }
}
