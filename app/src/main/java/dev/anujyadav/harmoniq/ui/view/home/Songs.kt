package dev.anujyadav.harmoniq.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.anujyadav.harmoniq.ui.components.LoaderScaffold
import dev.anujyadav.harmoniq.ui.components.SongList
import dev.anujyadav.harmoniq.ui.helpers.ViewContext

@Composable
fun SongsView(context: ViewContext) {
    val isUpdating by context.symphony.groove.song.isUpdating.collectAsState()
    val songIds by context.symphony.groove.song.all.collectAsState()
    val songsCount by context.symphony.groove.song.count.collectAsState()

    LoaderScaffold(context, isLoading = isUpdating) {
        SongList(
            context,
            songIds = songIds,
            songsCount = songsCount,
            enableAddMediaFoldersHint = true,
        )
    }
}
