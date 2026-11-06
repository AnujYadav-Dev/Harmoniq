package dev.anujyadav.harmoniq.services.radio

import android.media.MediaPlayer
import android.media.PlaybackParams
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaPlayer

/**
 * Robolectric's player has no playback params, which RadioPlayer needs once it's prepared.
 * Test discovery has to skip this (see app/build.gradle.kts): it would load this class outside
 * Robolectric's sandbox, where its parent's Android types don't exist.
 */
@Implements(MediaPlayer::class)
class ShadowPlayableMediaPlayer : ShadowMediaPlayer() {
    private var params = PlaybackParams()

    @Implementation
    fun getPlaybackParams(): PlaybackParams = params

    @Implementation
    fun setPlaybackParams(value: PlaybackParams) {
        params = value
    }
}
