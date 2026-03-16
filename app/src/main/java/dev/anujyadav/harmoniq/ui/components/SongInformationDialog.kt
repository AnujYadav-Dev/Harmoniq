package dev.anujyadav.harmoniq.ui.components


import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import dev.anujyadav.harmoniq.services.groove.Song
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.AlbumArtistViewRoute
import dev.anujyadav.harmoniq.ui.view.AlbumViewRoute
import dev.anujyadav.harmoniq.ui.view.ArtistViewRoute
import dev.anujyadav.harmoniq.ui.view.GenreViewRoute
import dev.anujyadav.harmoniq.utils.ActivityUtils
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.DurationUtils
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.round

@Composable
fun SongInformationDialog(context: ViewContext, song: Song, onDismissRequest: () -> Unit) {
    InformationDialog(
        context,
        content = {
            InformationKeyValue(AppStrings.Id) {
                LongPressCopyableText(context, song.id)
            }
            InformationKeyValue(AppStrings.TrackName) {
                LongPressCopyableText(context, song.title)
            }
            if (song.artists.isNotEmpty()) {
                InformationKeyValue(AppStrings.Artist) {
                    LongPressCopyableAndTappableText(context, song.artists) {
                        onDismissRequest()
                        context.navController.navigate(ArtistViewRoute(it))
                    }
                }
            }
            if (song.albumArtists.isNotEmpty()) {
                InformationKeyValue(AppStrings.AlbumArtist) {
                    LongPressCopyableAndTappableText(context, song.albumArtists) {
                        onDismissRequest()
                        context.navController.navigate(AlbumArtistViewRoute(it))
                    }
                }
            }
            if (song.composers.isNotEmpty()) {
                InformationKeyValue(AppStrings.Composer) {
                    // TODO composers page maybe?
                    LongPressCopyableAndTappableText(context, song.composers) {
                        onDismissRequest()
                        context.navController.navigate(ArtistViewRoute(it))
                    }
                }
            }
            context.symphony.groove.album.getIdFromSong(song)?.let { albumId ->
                InformationKeyValue(AppStrings.Album) {
                    LongPressCopyableAndTappableText(context, setOf(song.album!!)) {
                        onDismissRequest()
                        context.navController.navigate(AlbumViewRoute(albumId))
                    }
                }
            }
            if (song.genres.isNotEmpty()) {
                InformationKeyValue(AppStrings.Genre) {
                    LongPressCopyableAndTappableText(context, song.genres) {
                        onDismissRequest()
                        context.navController.navigate(GenreViewRoute(it))
                    }
                }
            }
            song.date?.let {
                InformationKeyValue(AppStrings.Date) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.year?.let {
                InformationKeyValue(AppStrings.Year) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.trackNumber?.let {
                InformationKeyValue(AppStrings.TrackNumber) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.trackTotal?.let {
                InformationKeyValue(AppStrings.TrackCount) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.discNumber?.let {
                InformationKeyValue(AppStrings.DiscNumber) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.discTotal?.let {
                InformationKeyValue(AppStrings.DiscTotal) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            InformationKeyValue(AppStrings.Duration) {
                LongPressCopyableText(context, DurationUtils.formatMs(song.duration))
            }
            song.encoder?.let {
                InformationKeyValue(AppStrings.Encoder) {
                    LongPressCopyableText(context, it)
                }
            }
            song.channels?.let {
                InformationKeyValue(AppStrings.AudioChannels) {
                    LongPressCopyableText(context, it.toString())
                }
            }
            song.bitrateK?.let {
                InformationKeyValue(AppStrings.Bitrate) {
                    val text = buildString {
                        append(AppStrings.XKbps(it.toString()))
                    }
                    LongPressCopyableText(context, text)
                }
            }
            song.samplingRateK?.let {
                InformationKeyValue(AppStrings.SamplingRate) {
                    LongPressCopyableText(context, AppStrings.XKHz(it.toString()))
                }
            }
            InformationKeyValue(AppStrings.Filename) {
                LongPressCopyableText(context, song.filename)
            }
            InformationKeyValue(AppStrings.Path) {
                LongPressCopyableText(context, song.path)
            }
            InformationKeyValue(AppStrings.Size) {
                LongPressCopyableText(context, "${round((song.size / 1024 / 1024).toDouble())} MB")
            }
            InformationKeyValue(AppStrings.LastModified) {
                LongPressCopyableText(
                    context,
                    SimpleDateFormat.getInstance().format(Date(song.dateModified * 1000)),
                )
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LongPressCopyableAndTappableText(
    context: ViewContext,
    values: Set<String>,
    onTap: (String) -> Unit,
) {
    val textStyle = LocalTextStyle.current.copy(
        textDecoration = TextDecoration.Underline,
    )

    FlowRow {
        values.forEachIndexed { i, it ->
            Text(
                it,
                style = textStyle,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { _ ->
                            ActivityUtils.copyToClipboardAndNotify(context.symphony, it)
                        },
                        onTap = { _ ->
                            onTap(it)
                        },
                    )
                },
            )
            if (i != values.size - 1) {
                Text(", ")
            }
        }
    }
}
