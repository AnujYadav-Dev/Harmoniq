package dev.anujyadav.harmoniq.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.anujyadav.harmoniq.ui.components.LoaderScaffold
import dev.anujyadav.harmoniq.ui.components.SongExplorerList
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.SimplePath

@Composable
fun BrowserView(context: ViewContext) {
    val isUpdating by context.symphony.groove.song.isUpdating.collectAsState()
    val id by context.symphony.groove.song.id.collectAsState()
    val explorer = context.symphony.groove.song.explorer
    val lastUsedFolderPath by context.symphony.settings.lastUsedBrowserPath.flow.collectAsState()

    LoaderScaffold(context, isLoading = isUpdating) {
        SongExplorerList(
            context,
            initialPath = lastUsedFolderPath?.let { SimplePath(it) },
            key = id,
            explorer = explorer,
            onPathChange = { path ->
                context.symphony.settings.lastUsedBrowserPath.setValue(path.pathString)
            }
        )
    }
}
