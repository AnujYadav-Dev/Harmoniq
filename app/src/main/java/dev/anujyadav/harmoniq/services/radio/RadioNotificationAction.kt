package dev.anujyadav.harmoniq.services.radio

import android.os.Build
import androidx.annotation.DrawableRes
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.utils.AppStrings

/**
 * Optional buttons of the media notification, next to the permanent play/pause, previous
 * and next. Declared once here, for the notification (before Android 13), the session's
 * custom actions (Android 13+) and Settings.
 */
enum class RadioNotificationAction(val label: String) {
    Favorite(AppStrings.Favorite),
    Shuffle(AppStrings.Shuffle),
    Repeat(AppStrings.Repeat),
    SeekBack(AppStrings.SeekBack),
    SeekForward(AppStrings.SeekForward),
    Stop(AppStrings.Stop);

    data class Presentation(@DrawableRes val icon: Int, val title: String)

    val actionId = "harmoniq_notification_${name.lowercase()}"

    fun present(symphony: Symphony, songId: String) = when (this) {
        Favorite -> when {
            symphony.groove.playlist.favorites.value.contains(songId) -> Presentation(
                R.drawable.material_icon_favorite,
                AppStrings.Unfavorite,
            )

            else -> Presentation(R.drawable.material_icon_favorite_border, AppStrings.Favorite)
        }

        Shuffle -> Presentation(
            when {
                symphony.radio.queue.currentShuffleMode -> R.drawable.material_icon_shuffle
                else -> R.drawable.material_icon_shuffle_off
            },
            label,
        )

        Repeat -> Presentation(
            when (symphony.radio.queue.currentLoopMode) {
                RadioQueue.LoopMode.None -> R.drawable.material_icon_repeat_off
                RadioQueue.LoopMode.Queue -> R.drawable.material_icon_repeat
                RadioQueue.LoopMode.Song -> R.drawable.material_icon_repeat_one
            },
            label,
        )

        SeekBack -> Presentation(
            R.drawable.material_icon_fast_rewind,
            seekTitle(symphony.settings.seekBackDuration.value),
        )

        SeekForward -> Presentation(
            R.drawable.material_icon_fast_forward,
            seekTitle(symphony.settings.seekForwardDuration.value),
        )

        Stop -> Presentation(R.drawable.material_icon_stop, label)
    }

    fun perform(symphony: Symphony) {
        when (this) {
            Favorite -> toggleFavorite(symphony)
            Shuffle -> symphony.radio.queue.toggleShuffleMode()
            Repeat -> symphony.radio.queue.toggleLoopMode()
            SeekBack -> symphony.radio.shorty
                .seekFromCurrent(-symphony.settings.seekBackDuration.value)

            SeekForward -> symphony.radio.shorty
                .seekFromCurrent(symphony.settings.seekForwardDuration.value)

            Stop -> symphony.radio.stop()
        }
    }

    private fun seekTitle(seconds: Int) = "$label ${AppStrings.XSecs(seconds.toString())}"

    private fun toggleFavorite(symphony: Symphony) {
        val songId = symphony.radio.queue.currentSongId ?: return
        symphony.groove.playlist.run {
            // favorites aren't known yet while playlists load, a toggle could overwrite them
            if (isUpdating.value) {
                return
            }
            when {
                favorites.value.contains(songId) -> unfavorite(songId)
                else -> favorite(songId)
            }
        }
    }

    companion object {
        /**
         * Android shows 5 media buttons (3 are permanent) on every supported version:
         * https://developer.android.com/media/implement/surfaces/mobile
         */
        const val MAX_SELECTED = 2

        // stays as it was for each version until the user opts in: only older ones show Stop
        val defaultSelection = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> setOf(Stop)
            else -> emptySet()
        }

        fun fromActionId(actionId: String?) = entries.find { it.actionId == actionId }
    }
}
