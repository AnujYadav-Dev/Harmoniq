package dev.anujyadav.harmoniq.ui.view.globalsearch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearch
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearchMessages
import dev.anujyadav.harmoniq.services.globalsearch.api.DownloadFormat
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.services.globalsearch.download.DownloadProgressData
import dev.anujyadav.harmoniq.services.globalsearch.download.DownloadRecords
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun GlobalDownloadControls(context: ViewContext, result: SearchResultDto) {
    val globalSearch = context.symphony.globalSearch
    val downloads by globalSearch.downloads.collectAsState()
    var format by rememberSaveable { mutableStateOf(context.symphony.settings.globalSearchDownloadFormat.value) }
    var message by remember { mutableStateOf<String?>(null) }
    val download = downloads[DownloadRecords.key(result.videoId, format.apiValue)]

    fun enqueue() {
        message = when (globalSearch.enqueueDownload(result, format)) {
            GlobalSearch.EnqueueResult.QueueFull -> AppStrings.DownloadQueueFull
            GlobalSearch.EnqueueResult.Unavailable -> AppStrings.GlobalSearchAppVersionUnsupported
            else -> null
        }
    }

    // Android 9 writes a plain file into Download/, which needs the legacy storage permission
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enqueue() else message = AppStrings.StoragePermissionRequired
    }

    fun onDownloadClick() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                context.activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else enqueue()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        when (download?.state) {
            GlobalSearch.DownloadView.State.Queued, GlobalSearch.DownloadView.State.Running ->
                DownloadProgressRow(download) { globalSearch.cancelDownload(download.key) }

            GlobalSearch.DownloadView.State.Done -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.DownloadSaved, modifier = Modifier.weight(1f))
                }
                NotInLibraryHint(context)
            }

            else -> {
                download?.takeIf { it.state == GlobalSearch.DownloadView.State.Failed }?.let {
                    Text(
                        it.errorCode?.let { code -> GlobalSearchMessages.forCode(code) } ?: AppStrings.DownloadFailedTapToRetry,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                DownloadButtonRow(
                    format = format,
                    onFormatChange = { format = it },
                    onDownload = { onDownloadClick() },
                )
            }
        }
        message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DownloadButtonRow(
    format: DownloadFormat,
    onFormatChange: (DownloadFormat) -> Unit,
    onDownload: () -> Unit,
) {
    var showFormats by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Download, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(AppStrings.Download)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box {
            OutlinedButton(onClick = { showFormats = true }) {
                Text(format.label())
                Icon(Icons.Filled.ArrowDropDown, contentDescription = AppStrings.DownloadFormat)
            }
            DropdownMenu(expanded = showFormats, onDismissRequest = { showFormats = false }) {
                supportedDownloadFormats().forEach {
                    DropdownMenuItem(
                        text = { Text(it.label()) },
                        onClick = {
                            onFormatChange(it)
                            showFormats = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressRow(download: GlobalSearch.DownloadView, onCancel: () -> Unit) {
    val fraction = DownloadProgressData.fraction(download.progress)
    Text(DownloadProgressData.text(download.progress), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            when (fraction) {
                null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else -> LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onCancel) { Text(AppStrings.Cancel) }
    }
}

fun DownloadFormat.label() = when (this) {
    DownloadFormat.Opus -> AppStrings.DownloadFormatOpus
    DownloadFormat.M4a -> AppStrings.DownloadFormatM4a
    DownloadFormat.Mp3 -> AppStrings.DownloadFormatMp3
}

fun supportedDownloadFormats() = DownloadFormat.entries.filter { Build.VERSION.SDK_INT >= it.minSdk }
