package dev.anujyadav.harmoniq.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.anujyadav.harmoniq.ui.components.settings.SettingsMultiSystemFolderDialog
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun MediaFoldersDialog(
    context: ViewContext,
    onDismissRequest: () -> Unit,
) {
    val mediaFolders by context.symphony.settings.mediaFolders.flow.collectAsState()

    SettingsMultiSystemFolderDialog(
        context,
        title = {
            Text(AppStrings.MediaFolders)
        },
        initialValues = mediaFolders,
        onChange = { values ->
            // re-scanning stops playback, so skip it when nothing changed
            if (values != mediaFolders) {
                context.symphony.settings.mediaFolders.setValue(values)
                context.symphony.groove.refreshLibrary()
            }
        },
        onDismissRequest = onDismissRequest,
        pickFolderIfEmpty = true,
    )
}
