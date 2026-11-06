package dev.anujyadav.harmoniq.widgets

import android.content.Context
import android.graphics.Bitmap
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.playback.PlaybackArtwork
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshot
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/** Everything a widget draws from. Nothing here needs Symphony to exist. */
internal data class PlaybackWidgetState(
    val snapshot: PlaybackSnapshot,
    val isPlaying: Boolean,
    /** Null for the widgets that show none. */
    val artwork: Bitmap?,
    val appearance: WidgetAppearance,
) {
    companion object {
        /**
         * Starts with the current state, then follows changes. The artwork only reloads when
         * the song's artwork changes.
         */
        fun observe(context: Context, artworkSizePx: Int?, imagesInWidget: Int): Flow<PlaybackWidgetState> {
            val appContext = context.applicationContext
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
                )
            }.distinctUntilChanged().flowOn(Dispatchers.IO)
        }

        // a saved "playing" means nothing once the process that was playing is gone. Only whether
        // the player exists is looked at, a widget doesn't read anything from it
        private fun isReallyPlaying(snapshot: PlaybackSnapshot, context: Context) =
            snapshot.isPlaying && (context as? HarmoniqApplication)?.symphonyIfCreated != null
    }
}
