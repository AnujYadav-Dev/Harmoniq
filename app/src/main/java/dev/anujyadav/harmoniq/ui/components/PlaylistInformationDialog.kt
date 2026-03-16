package dev.anujyadav.harmoniq.ui.components

import androidx.compose.runtime.Composable


import dev.anujyadav.harmoniq.services.groove.Playlist


import dev.anujyadav.harmoniq.ui.helpers.ViewContext

import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun PlaylistInformationDialog(
    context: ViewContext,
    playlist: Playlist,
    onDismissRequest: () -> Unit,
) {
    InformationDialog(
        context,
        content = {
            InformationKeyValue(AppStrings.Id) {
                LongPressCopyableText(context, playlist.id)
            }
            InformationKeyValue(AppStrings.Title) {
                LongPressCopyableText(context, playlist.title)
            }
            InformationKeyValue(AppStrings.TrackCount) {
                LongPressCopyableText(context, playlist.numberOfTracks.toString())
            }
            InformationKeyValue(AppStrings.PlaylistStoreLocation) {
                LongPressCopyableText(
                    context,
                    when {
                        playlist.isLocal -> AppStrings.LocalStorage
                        else -> AppStrings.AppBuiltIn
                    }
                )
            }
            playlist.path?.let {
                InformationKeyValue(AppStrings.Path) {
                    LongPressCopyableText(context, it)
                }
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
