package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anujyadav.harmoniq.services.groove.Groove
import dev.anujyadav.harmoniq.services.radio.Radio
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun SongUnavailableDialog(
    context: ViewContext,
    error: Radio.PlaybackError,
    onDismissRequest: () -> Unit,
) {
    val songTitle = context.symphony.groove.song.get(error.songId)?.title
        ?: AppStrings.UnknownSymbol
    val isUnavailable = error.reason == Radio.PlaybackError.Reason.SongUnavailable

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(if (isUnavailable) AppStrings.SongUnavailable else AppStrings.PlaybackFailed)
        },
        content = {
            Box(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                )
            ) {
                Text(
                    when {
                        isUnavailable -> AppStrings.SongXMayHaveBeenDeletedOrMoved(songTitle)
                        else -> AppStrings.SongXCouldNotBePlayed(songTitle)
                    }
                )
            }
        },
        actions = {
            if (isUnavailable) {
                TextButton(
                    onClick = {
                        context.symphony.groove.fetch(Groove.FetchOptions(resetInMemoryCache = true))
                        onDismissRequest()
                    }
                ) {
                    Text(AppStrings.RescanLibrary)
                }
            }
            TextButton(onClick = onDismissRequest) {
                Text(AppStrings.Ok)
            }
        }
    )
}
