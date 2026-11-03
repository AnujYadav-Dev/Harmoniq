package dev.anujyadav.harmoniq.ui.view.globalsearch

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.loadOrCueVideo
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import androidx.compose.runtime.getValue

/**
 * The official YouTube IFrame player (android-youtube-player). It plays straight from
 * YouTube, never through harmoniq-server, because stream URLs are bound to the requesting IP.
 */
@Composable
fun YouTubePreviewPlayer(
    videoId: String,
    autoPlay: Boolean,
    onPlaying: () -> Unit,
    onError: (PlayerConstants.PlayerError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPlaying by rememberUpdatedState(onPlaying)
    val currentOnError by rememberUpdatedState(onError)

    val playerView = remember(videoId) {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            initialize(
                object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        // onReady comes once the IFrame API has loaded. loadOrCueVideo only starts
                        // playback while the screen is resumed (it cues otherwise), so a preview
                        // never plays while the app is in the background
                        if (autoPlay) {
                            youTubePlayer.loadOrCueVideo(lifecycleOwner.lifecycle, videoId, 0f)
                        } else {
                            youTubePlayer.cueVideo(videoId, 0f)
                        }
                    }

                    override fun onStateChange(
                        youTubePlayer: YouTubePlayer,
                        state: PlayerConstants.PlayerState,
                    ) {
                        if (state == PlayerConstants.PlayerState.PLAYING) currentOnPlaying()
                    }

                    override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                        currentOnError(error)
                    }
                },
                IFramePlayerOptions.Builder(context)
                    .controls(PLAYER_CONTROLS_VISIBLE)
                    .rel(RELATED_VIDEOS_SAME_CHANNEL_ONLY)
                    .build(),
            )
        }
    }

    // pauses with the activity; released when the sheet closes
    DisposableEffect(lifecycleOwner, playerView) {
        lifecycleOwner.lifecycle.addObserver(playerView)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
        }
    }

    AndroidView(
        factory = { playerView },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(VIDEO_ASPECT_RATIO),
    )
}

private const val PLAYER_CONTROLS_VISIBLE = 1
private const val RELATED_VIDEOS_SAME_CHANNEL_ONLY = 0
private const val VIDEO_ASPECT_RATIO = 16f / 9f
