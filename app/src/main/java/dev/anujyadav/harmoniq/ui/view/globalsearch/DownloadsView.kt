package dev.anujyadav.harmoniq.ui.view.globalsearch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.services.globalsearch.download.DownloadedSong
import dev.anujyadav.harmoniq.ui.components.ActionBanner
import dev.anujyadav.harmoniq.ui.components.IconTextBody
import dev.anujyadav.harmoniq.ui.components.MediaSortBarPlacement
import dev.anujyadav.harmoniq.ui.components.SongList
import dev.anujyadav.harmoniq.ui.components.SongListType
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings

/**
 * Library → Downloads: songs saved through Global Search, not other files on the device.
 * Saved files only play once the library has scanned them, which needs their folder to be
 * one of the media folders, so until then they're listed without playing.
 */
@Composable
fun DownloadsView(context: ViewContext) {
    val globalSearch = context.symphony.globalSearch
    val downloads by globalSearch.downloadedSongs.collectAsState()
    val isFolderCovered by globalSearch.isDownloadsFolderCovered.collectAsState()
    val pickFolder = rememberDownloadsFolderPicker(context)
    val songIds = remember(downloads) { downloads.mapNotNull { it.songId }.distinct() }
    val notInLibrary = remember(downloads) { downloads.filter { it.songId == null } }

    val header: LazyListScope.() -> Unit = {
        if (!isFolderCovered) {
            item(key = "folder-setup") { DownloadsFolderSetup(onPickFolder = pickFolder) }
        }
        items(notInLibrary, key = { "pending:${it.record.key}" }) { NotInLibraryRow(it) }
    }

    when {
        songIds.isNotEmpty() -> SongList(
            context,
            songIds = songIds,
            type = SongListType.Downloads,
            sortBarPlacement = MediaSortBarPlacement.Inline,
            leadingContent = header,
        )

        downloads.isEmpty() && isFolderCovered -> DownloadsEmpty()

        else -> Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) { header() }
            if (downloads.isEmpty()) {
                DownloadsEmpty()
            }
        }
    }
}

@Composable
private fun DownloadsEmpty() {
    IconTextBody(
        icon = { modifier -> Icon(Icons.Filled.Download, null, modifier = modifier) },
        content = { Text(AppStrings.DownloadsEmpty) },
    )
}

@Composable
private fun DownloadsFolderSetup(onPickFolder: () -> Unit) {
    ActionBanner(
        icon = Icons.Filled.CreateNewFolder,
        title = AppStrings.DownloadsFolderSetUpTitle,
        message = AppStrings.DownloadsFolderNotSetUp,
        actionLabel = AppStrings.SelectFolder,
        onAction = onPickFolder,
    )
}

@Composable
private fun NotInLibraryRow(download: DownloadedSong) {
    ListItem(
        headlineContent = {
            Text(download.record.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                listOf(download.record.artists.joinToString(), AppStrings.WaitingForLibrary)
                    .filter { it.isNotEmpty() }
                    .joinToString(SUBTITLE_SEPARATOR),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(modifier = Modifier.size(ROW_ARTWORK_SIZE)) {
                AsyncImage(
                    download.record.thumbnailUrl,
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
        },
    )
}

private val ROW_ARTWORK_SIZE = 48.dp
