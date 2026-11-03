package dev.anujyadav.harmoniq.ui.view.globalsearch

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearch
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearchMessages
import dev.anujyadav.harmoniq.services.globalsearch.OnlineDownloadState
import dev.anujyadav.harmoniq.services.globalsearch.api.DownloadFormat
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SongMetadataDto
import dev.anujyadav.harmoniq.ui.components.InformationDialog
import dev.anujyadav.harmoniq.ui.components.InformationKeyValue
import dev.anujyadav.harmoniq.ui.components.LocalOnlineSwipeScope
import dev.anujyadav.harmoniq.ui.components.LocalUndoHost
import dev.anujyadav.harmoniq.ui.components.OnlineTrackAction
import dev.anujyadav.harmoniq.ui.components.OnlineTrackActions
import dev.anujyadav.harmoniq.ui.components.SongRemoveAction
import dev.anujyadav.harmoniq.ui.components.SwipeActionsBox
import dev.anujyadav.harmoniq.ui.components.SwipeDirection
import dev.anujyadav.harmoniq.ui.components.toSwipeAction
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.ActivityUtils
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.Logger
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/**
 * Starts a download the way the preview's Download button does: asks for the storage
 * permission on Android 9 first, then enqueues. [onMessage] gets what to tell the user, or null.
 */
@Composable
fun rememberDownloadLauncher(
    context: ViewContext,
    onMessage: (String?) -> Unit,
): (SearchResultDto, DownloadFormat) -> Unit {
    val globalSearch = context.symphony.globalSearch
    val currentOnMessage by rememberUpdatedState(onMessage)
    var pending by remember { mutableStateOf<Pair<SearchResultDto, DownloadFormat>?>(null) }

    fun enqueue(result: SearchResultDto, format: DownloadFormat) {
        currentOnMessage(
            when (globalSearch.enqueueDownload(result, format)) {
                GlobalSearch.EnqueueResult.QueueFull -> AppStrings.DownloadQueueFull
                GlobalSearch.EnqueueResult.Unavailable -> AppStrings.GlobalSearchNotConfigured
                else -> null
            }
        )
    }

    // Android 9 writes a plain file into Download/, which needs the legacy storage permission
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = pending
        pending = null
        when {
            !granted -> currentOnMessage(AppStrings.StoragePermissionRequired)
            request != null -> enqueue(request.first, request.second)
        }
    }

    return { result, format ->
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                context.activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = result to format
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            enqueue(result, format)
        }
    }
}

/**
 * Provides [OnlineTrackActions] for [result] to [content] and hosts what the actions open
 * (details dialog, permission request), like [dev.anujyadav.harmoniq.ui.components.SongActionsHost].
 */
@Composable
fun OnlineTrackActionsHost(
    context: ViewContext,
    result: SearchResultDto,
    onPreview: (() -> Unit)? = null,
    removeAction: SongRemoveAction? = null,
    sourcePlaylistId: String? = null,
    content: @Composable (OnlineTrackActions) -> Unit,
) {
    val globalSearch = context.symphony.globalSearch
    val downloadStates by globalSearch.downloadStateByVideoId.collectAsState()
    val watchListIds by globalSearch.watchList.ids.collectAsState()
    val undoHost = LocalUndoHost.current
    var showDetails by remember { mutableStateOf(false) }
    val download = rememberDownloadLauncher(context) { message -> message?.let { showToast(context, it) } }
    val isInWatchList = watchListIds.contains(result.videoId)

    val actions = OnlineTrackActions(
        result = result,
        downloadState = downloadStates[result.videoId] ?: OnlineDownloadState.None,
        isInWatchList = isInWatchList,
        removeAction = removeAction,
        onPreview = onPreview,
        onDownload = { download(result, context.symphony.settings.globalSearchDownloadFormat.value) },
        onToggleWatchList = {
            when {
                isInWatchList -> globalSearch.watchList.remove(result.videoId)?.let { removed ->
                    undoHost?.show(AppStrings.RemovedXFromWatchList(result.title)) {
                        globalSearch.watchList.restore(removed)
                    } ?: showToast(context, AppStrings.RemovedXFromWatchList(result.title))
                }

                else -> {
                    globalSearch.watchList.add(result, sourcePlaylistId)
                    showToast(context, AppStrings.AddedToWatchList)
                }
            }
        },
        onShare = { shareLink(context, result.title, result.shareUrl()) },
        onOpenInYouTube = { openLink(context, result.youtubeUrl()) },
        onShowDetails = { showDetails = true },
        undoHost = undoHost,
    )

    content(actions)

    if (showDetails) {
        OnlineTrackDetailsDialog(context, result) { showDetails = false }
    }
}

/**
 * Wraps an online row with the swipe actions configured for the current [LocalOnlineSwipeScope].
 */
@Composable
fun OnlineSwipeActionsBox(
    context: ViewContext,
    actions: OnlineTrackActions,
    content: @Composable () -> Unit,
) {
    val scope = LocalOnlineSwipeScope.current
    val settings = context.symphony.settings
    val swipeRightAction by settings.onlineSwipeAction(scope, SwipeDirection.Right).flow.collectAsState()
    val swipeLeftAction by settings.onlineSwipeAction(scope, SwipeDirection.Left).flow.collectAsState()

    SwipeActionsBox(
        swipeRight = actions.toSwipeAction(swipeRightAction),
        swipeLeft = actions.toSwipeAction(swipeLeftAction),
        content = content,
    )
}

@Composable
fun OnlineTrackDropdownMenu(
    actions: OnlineTrackActions,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    trailingContent: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        OnlineTrackAction.menuActions.forEach { action ->
            when {
                actions.isAvailable(action) -> DropdownMenuItem(
                    leadingIcon = { Icon(actions.icon(action), null) },
                    text = { Text(actions.label(action)) },
                    onClick = {
                        onDismissRequest()
                        actions.perform(action)
                    },
                )

                // "Downloaded" / progress instead of a second download
                action == OnlineTrackAction.Download -> actions.downloadStatusLabel()?.let { status ->
                    DropdownMenuItem(
                        leadingIcon = { Icon(actions.icon(action), null) },
                        text = { Text(status) },
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
        trailingContent?.invoke(this, onDismissRequest)
    }
}

@Composable
fun OnlineTrackDetailsDialog(context: ViewContext, result: SearchResultDto, onDismissRequest: () -> Unit) {
    val track by produceState<Loadable<SongMetadataDto>>(Loadable.Loading, result.videoId) {
        value = Loadable.of(context.symphony.globalSearch.track(result.videoId))
    }

    InformationDialog(
        context,
        content = {
            val metadata = (track as? Loadable.Ready)?.value
            InformationKeyValue(AppStrings.TrackName) { Text(metadata?.title ?: result.title) }
            val artists = metadata?.artists?.takeIf { it.isNotEmpty() } ?: result.displayArtists
            if (artists.isNotEmpty()) {
                InformationKeyValue(AppStrings.Artist) { Text(artists.joinToString()) }
            }
            when (val state = track) {
                Loadable.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is Loadable.Failed -> Text(
                    "${AppStrings.DetailsUnavailable}: ${GlobalSearchMessages.forError(state.error)}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )

                is Loadable.Ready -> TrackDetails(state.value, result)
            }
            resultSubtitleDuration(result)?.let {
                InformationKeyValue(AppStrings.Duration) { Text(it) }
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

fun shareLink(context: ViewContext, title: String, url: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = SHARE_MIME_TYPE
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.activity.startActivity(Intent.createChooser(intent, null))
    } catch (err: Exception) {
        Logger.error(TAG, "share failed", err)
        showToast(context, AppStrings.ShareFailedX(err.localizedMessage ?: err.toString()))
    }
}

// a YouTube link opens the YouTube app when it's installed, the browser otherwise
fun openLink(context: ViewContext, url: String) {
    try {
        ActivityUtils.startBrowserActivity(context.activity, Uri.parse(url))
    } catch (err: ActivityNotFoundException) {
        Logger.error(TAG, "no app for $url", err)
        showToast(context, AppStrings.NoAppForLink)
    }
}

private fun showToast(context: ViewContext, message: String) =
    Toast.makeText(context.activity, message, Toast.LENGTH_SHORT).show()

private const val TAG = "OnlineTrackActions"
private const val SHARE_MIME_TYPE = "text/plain"
