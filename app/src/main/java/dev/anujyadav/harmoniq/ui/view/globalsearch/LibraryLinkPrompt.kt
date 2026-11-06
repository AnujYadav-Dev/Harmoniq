package dev.anujyadav.harmoniq.ui.view.globalsearch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anujyadav.harmoniq.services.globalsearch.download.LibraryLink
import dev.anujyadav.harmoniq.ui.components.ScaffoldDialog
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings

/** Opens the folder picker on Download/Harmoniq and adds the picked tree to the media folders. */
@Composable
fun rememberDownloadsFolderPicker(context: ViewContext): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { context.symphony.globalSearch.onLibraryFolderPicked(it) }
    }
    return {
        context.symphony.globalSearch.prepareDownloadsFolder()
        launcher.launch(LibraryLink.downloadsInitialUri)
    }
}

/** The one-time "add downloads to your library?" question after the first saved song (plan §11). */
@Composable
fun LibraryLinkPrompt(context: ViewContext) {
    val globalSearch = context.symphony.globalSearch
    val show by globalSearch.showLibraryPrompt.collectAsState()
    val pickFolder = rememberDownloadsFolderPicker(context)
    if (!show) return

    ScaffoldDialog(
        onDismissRequest = { globalSearch.dismissLibraryPrompt() },
        title = { Text(AppStrings.AddDownloadsFolderTitle) },
        content = {
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text(AppStrings.AddDownloadsFolderMessage)
            }
        },
        actions = {
            TextButton(onClick = { globalSearch.dismissLibraryPrompt() }) {
                Text(AppStrings.NotNow)
            }
            TextButton(onClick = pickFolder) {
                Text(AppStrings.PickDownloadsFolder)
            }
        },
    )
}

/** Shown on a downloaded item while Download/Harmoniq isn't part of the library. */
@Composable
fun NotInLibraryHint(context: ViewContext) {
    val covered by context.symphony.globalSearch.isDownloadsFolderCovered.collectAsState()
    val pickFolder = rememberDownloadsFolderPicker(context)
    if (covered) return
    TextButton(onClick = pickFolder) {
        Icon(Icons.Filled.CreateNewFolder, null)
        Text(AppStrings.NotInLibraryAddFolder, modifier = Modifier.padding(start = 8.dp))
    }
}
