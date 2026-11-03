package dev.anujyadav.harmoniq.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.ui.graphics.vector.ImageVector
import dev.anujyadav.harmoniq.services.globalsearch.OnlineDownloadState
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.utils.AppStrings

/**
 * Every action that can be run on a Global Search song or video. Like [SongAction], the
 * three-dot menu, the swipe actions and the preview drawer all run through [OnlineTrackActions].
 */
enum class OnlineTrackAction {
    Preview,
    Download,
    AddToWatchList,
    Share,
    OpenInYouTube,
    Details,

    // removes the item from the list being viewed (the Watch List), when supported there
    Remove;

    companion object {
        // order of the items in the three-dot menu; Preview is the row tap
        val menuActions = listOf(
            Download,
            AddToWatchList,
            Share,
            OpenInYouTube,
            Details,
        )
    }
}

fun OnlineTrackAction.settingsLabel() = when (this) {
    OnlineTrackAction.Preview -> AppStrings.Preview
    OnlineTrackAction.Download -> AppStrings.Download
    OnlineTrackAction.AddToWatchList -> "${AppStrings.AddToWatchList} / ${AppStrings.RemoveFromWatchList}"
    OnlineTrackAction.Share -> AppStrings.Share
    OnlineTrackAction.OpenInYouTube -> AppStrings.OpenInYoutube
    OnlineTrackAction.Details -> AppStrings.Details
    OnlineTrackAction.Remove -> AppStrings.RemoveFromList
}

/**
 * What can be done with one online [result]. Availability and labels only depend on the
 * constructor values; [perform] runs the callbacks the host wired up.
 */
class OnlineTrackActions(
    val result: SearchResultDto,
    val downloadState: OnlineDownloadState,
    val isInWatchList: Boolean,
    private val removeAction: SongRemoveAction? = null,
    private val onPreview: (() -> Unit)? = null,
    private val onDownload: () -> Unit = {},
    private val onToggleWatchList: () -> Unit = {},
    private val onShare: () -> Unit = {},
    private val onOpenInYouTube: () -> Unit = {},
    private val onShowDetails: () -> Unit = {},
    private val undoHost: UndoHost? = null,
) {
    fun isAvailable(action: OnlineTrackAction) = when (action) {
        OnlineTrackAction.Preview -> onPreview != null
        // already downloaded or on its way: the menu shows the state instead
        OnlineTrackAction.Download -> !downloadState.isActive && downloadState != OnlineDownloadState.Done
        OnlineTrackAction.Remove -> removeAction != null
        else -> true
    }

    fun label(action: OnlineTrackAction) = when (action) {
        OnlineTrackAction.AddToWatchList -> if (isInWatchList) AppStrings.RemoveFromWatchList else AppStrings.AddToWatchList
        OnlineTrackAction.Download -> if (downloadState == OnlineDownloadState.Failed) AppStrings.DownloadAgain else AppStrings.Download
        OnlineTrackAction.Remove -> removeAction?.label ?: action.settingsLabel()
        else -> action.settingsLabel()
    }

    fun icon(action: OnlineTrackAction): ImageVector = when (action) {
        OnlineTrackAction.Preview -> Icons.Filled.PlayCircle
        OnlineTrackAction.Download -> Icons.Filled.Download
        OnlineTrackAction.AddToWatchList -> if (isInWatchList) Icons.Filled.WatchLater else Icons.Outlined.WatchLater
        OnlineTrackAction.Share -> Icons.Filled.Share
        OnlineTrackAction.OpenInYouTube -> Icons.AutoMirrored.Filled.OpenInNew
        OnlineTrackAction.Details -> Icons.Filled.Info
        OnlineTrackAction.Remove -> Icons.Filled.RemoveCircleOutline
    }

    // shown in place of Download while it isn't available
    fun downloadStatusLabel() = when (downloadState) {
        OnlineDownloadState.Done -> AppStrings.Downloaded
        is OnlineDownloadState.Running -> downloadState.fraction
            ?.let { AppStrings.DownloadingXPercent((it * PERCENT).toInt().toString()) }
            ?: AppStrings.Downloading
        OnlineDownloadState.Queued -> AppStrings.DownloadWaiting
        else -> null
    }

    fun perform(action: OnlineTrackAction) {
        if (!isAvailable(action)) {
            return
        }
        when (action) {
            OnlineTrackAction.Preview -> onPreview?.invoke()
            OnlineTrackAction.Download -> onDownload()
            OnlineTrackAction.AddToWatchList -> onToggleWatchList()
            OnlineTrackAction.Share -> onShare()
            OnlineTrackAction.OpenInYouTube -> onOpenInYouTube()
            OnlineTrackAction.Details -> onShowDetails()
            OnlineTrackAction.Remove -> remove()
        }
    }

    fun remove() {
        val action = removeAction ?: return
        action.onRemove()
        action.onUndo?.let { onUndo ->
            undoHost?.show(AppStrings.RemovedX(result.title), onUndo)
        }
    }

    private companion object {
        const val PERCENT = 100
    }
}

fun OnlineTrackActions.toSwipeAction(action: OnlineTrackAction?) = action
    ?.takeIf { isAvailable(it) }
    ?.let { SwipeAction(label(it), icon(it)) { perform(it) } }
