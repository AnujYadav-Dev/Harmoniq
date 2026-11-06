package dev.anujyadav.harmoniq.services.playback

import android.content.Context
import dev.anujyadav.harmoniq.services.radio.RadioQueue
import dev.anujyadav.harmoniq.utils.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What widgets show, kept on disk so they can draw with the app closed and without creating
 * Symphony. It's only a copy of the last known state, the player itself is the truth.
 */
@Serializable
data class PlaybackSnapshot(
    /** Null until something has been played. */
    val song: Item? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** When the position was read, there are no updates while it moves on from there. */
    val capturedAtMs: Long = 0L,
    val shuffle: Boolean = false,
    val loopMode: RadioQueue.LoopMode = RadioQueue.LoopMode.None,
    val isFavorite: Boolean = false,
    val sleepTimerEndsAtMs: Long? = null,
    /** What comes after [song], at most [QUEUE_PREVIEW_LIMIT]. */
    val queue: List<Item> = emptyList(),
    /** At most [SUGGESTIONS_PREVIEW_LIMIT]. */
    val suggestions: List<Item> = emptyList(),
) {
    /**
     * @param path identifies the song, ids change when the library is reset.
     * @param artworkPath a file of the artwork cache. Null or gone means the placeholder.
     * @param queueIndex where it was in the queue, for a song of the queue.
     */
    @Serializable
    data class Item(
        val path: String,
        val title: String,
        val artist: String,
        val album: String? = null,
        val artworkPath: String? = null,
        val queueIndex: Int? = null,
    )

    /** The time alone doesn't make a change worth redrawing widgets for. */
    fun hasSameContentAs(other: PlaybackSnapshot) = copy(capturedAtMs = 0L) == other.copy(capturedAtMs = 0L)

    companion object {
        const val QUEUE_PREVIEW_LIMIT = 20
        const val SUGGESTIONS_PREVIEW_LIMIT = 6
    }
}

/** Reads and writes [PlaybackSnapshot], needs nothing but a context. */
class PlaybackSnapshotStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** An empty snapshot if there's none, or what's there can't be read anymore. */
    fun read(): PlaybackSnapshot {
        val saved = preferences.getString(KEY_SNAPSHOT, null) ?: return PlaybackSnapshot()
        return try {
            json.decodeFromString<PlaybackSnapshot>(saved)
        } catch (err: IllegalArgumentException) {
            Logger.warn(TAG, "unreadable snapshot", err)
            PlaybackSnapshot()
        }
    }

    fun write(snapshot: PlaybackSnapshot) {
        preferences.edit().putString(KEY_SNAPSHOT, json.encodeToString(snapshot)).apply()
    }

    private companion object {
        const val TAG = "PlaybackSnapshotStore"
        const val PREFERENCES_NAME = "playback_snapshot"
        const val KEY_SNAPSHOT = "snapshot"

        // an update that added or dropped a field must still read what the last version saved
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
