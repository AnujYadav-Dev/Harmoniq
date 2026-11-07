package dev.anujyadav.harmoniq.widgets

import android.content.Context
import android.graphics.Bitmap
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.playback.PlaybackArtwork
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshot
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshotStore
import dev.anujyadav.harmoniq.services.playback.WidgetCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/** What a widget with a list needs loaded for its rows. */
internal class ListCovers(val sizePx: Int, val of: (PlaybackSnapshot) -> List<WidgetCover>)

/**
 * Keeps what's still on show, so a queue that moved on by one song only loads the new one.
 *
 * @param layouts how many layouts Android keeps of the widget, each with all the images.
 */
internal class CoverLoader(private val context: Context, private val sizePx: Int, private val layouts: Int) {
    private var loaded = emptyMap<WidgetCover, Bitmap>()

    fun load(covers: List<WidgetCover>): Map<WidgetCover, Bitmap> {
        val wanted = covers.distinct()
        loaded = wanted.mapNotNull { cover ->
            val bitmap = loaded[cover]
                ?: PlaybackArtwork.loadCover(context, cover.path, sizePx, wanted.size * layouts)
            bitmap?.let { cover to it }
        }.toMap()
        return loaded
    }
}

/** Everything a widget draws from. Nothing here needs Symphony to exist. */
internal data class PlaybackWidgetState(
    val snapshot: PlaybackSnapshot,
    val isPlaying: Boolean,
    /** Null for the widgets that show none. */
    val artwork: Bitmap?,
    val appearance: WidgetAppearance,
    /** The images of a widget's list, only those that could be loaded. */
    val covers: Map<WidgetCover, Bitmap> = emptyMap(),
) {
    companion object {
        /**
         * Starts with the current state, then follows changes. The artwork only reloads when
         * the song's artwork changes.
         */
        fun observe(
            context: Context,
            artworkSizePx: Int?,
            imagesInWidget: Int,
            listCovers: ListCovers? = null,
        ): Flow<PlaybackWidgetState> {
            val appContext = context.applicationContext
            val coverLoader = listCovers?.let { CoverLoader(appContext, it.sizePx, imagesInWidget) }
            var loadedPath: String? = null
            var loaded: Bitmap? = null
            return combine(
                PlaybackSnapshotStore(appContext).changes(),
                WidgetAppearance.changes(appContext),
            ) { snapshot, appearance ->
                val path = snapshot.song?.artworkPath
                val artwork = artworkSizePx?.let { size ->
                    loaded?.takeIf { path == loadedPath }
                        ?: PlaybackArtwork.load(appContext, path, size, imagesInWidget).also {
                            loaded = it
                            loadedPath = path
                        }
                }
                PlaybackWidgetState(
                    snapshot = snapshot,
                    isPlaying = isReallyPlaying(snapshot, appContext),
                    artwork = artwork,
                    appearance = appearance,
                    covers = listCovers?.let { coverLoader?.load(it.of(snapshot)) }.orEmpty(),
                )
            }.distinctUntilChanged().flowOn(Dispatchers.IO)
        }

        // a saved "playing" means nothing once the process that was playing is gone. Only whether
        // the player exists is looked at, a widget doesn't read anything from it
        private fun isReallyPlaying(snapshot: PlaybackSnapshot, context: Context) =
            snapshot.isPlaying && (context as? HarmoniqApplication)?.symphonyIfCreated != null
    }
}
