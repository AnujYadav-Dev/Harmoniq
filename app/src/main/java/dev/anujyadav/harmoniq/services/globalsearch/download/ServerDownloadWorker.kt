package dev.anujyadav.harmoniq.services.globalsearch.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearchMessages
import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import dev.anujyadav.harmoniq.services.globalsearch.api.DownloadFormat
import dev.anujyadav.harmoniq.services.globalsearch.api.ErrorCodes
import dev.anujyadav.harmoniq.services.globalsearch.api.HarmoniqServerApi
import dev.anujyadav.harmoniq.services.globalsearch.connection.ServerConnections
import dev.anujyadav.harmoniq.utils.Logger
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * WorkManager adapter around [DownloadPipeline]: runs as a dataSync foreground worker,
 * reports progress, and maps outcomes to retry / failure (plan §9, §12).
 */
class ServerDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val dependencies: Dependencies,
) : CoroutineWorker(context, params) {
    // used by WorkManager's default factory (its consumer R8 rules keep this constructor)
    constructor(context: Context, params: WorkerParameters) : this(context, params, Dependencies.create(context))

    class Dependencies(
        val api: HarmoniqServerApi?,
        val saver: DownloadSaver,
        val records: DownloadRecords,
        val partDirectory: File,
        val notifications: DownloadNotifications?,
        val sleep: suspend (Duration) -> Unit = { delay(it) },
        // re-reads the server config after a rejected token; true when the connection changed
        val refreshConnection: suspend () -> Boolean = { false },
    ) {
        companion object {
            fun create(context: Context) = ServerConnections.get(context).let { connections ->
                Dependencies(
                    api = connections.api(),
                    saver = DownloadSaver.create(context),
                    records = DownloadRecords.get(context),
                    partDirectory = File(context.cacheDir, ServerConfig.PART_FILES_FOLDER_NAME),
                    notifications = DownloadNotifications(context.applicationContext),
                    refreshConnection = { connections.refreshRemote(force = true) },
                )
            }
        }
    }

    private val request by lazy { requestFrom(inputData) }

    override suspend fun getForegroundInfo() = dependencies.notifications?.let {
        it.ensureChannel()
        it.foregroundInfo(id, request?.title.orEmpty(), DownloadProgressData.text(null), null)
    } ?: super.getForegroundInfo()

    override suspend fun doWork(): Result {
        val request = request ?: return Result.failure()
        val api = dependencies.api ?: return when {
            // no server known yet: the config file may simply not have been read
            dependencies.refreshConnection() -> Result.retry()
            else -> Result.failure(errorData(ErrorCodes.UNAUTHORIZED))
        }
        dependencies.notifications?.ensureChannel()
        showProgress(request, null)

        val pipeline = DownloadPipeline(
            api = api,
            saver = dependencies.saver,
            records = dependencies.records,
            partDirectory = dependencies.partDirectory,
            onProgress = { progress ->
                setProgress(DownloadProgressData.toData(progress))
                showProgress(request, progress)
            },
            sleep = dependencies.sleep,
        )
        val outcome = try {
            pipeline.run(request, attemptSeed = "$id:$runAttemptCount")
        } catch (err: CancellationException) {
            // only a user cancel releases the server job; a lost network keeps it for the next run
            if (stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP) {
                withContext(NonCancellable) { pipeline.cancel(request.key) }
            }
            throw err
        }

        return when (outcome) {
            is DownloadOutcome.Saved -> {
                dependencies.notifications?.showSaved(id, request.title)
                Result.success(workDataOf(KEY_SAVED_URI to outcome.file.uri))
            }

            is DownloadOutcome.Failed -> fail(pipeline, request, outcome.code)

            is DownloadOutcome.Retry -> when {
                runAttemptCount + 1 >= ServerConfig.MAX_RUN_ATTEMPTS -> fail(pipeline, request, outcome.code)
                else -> {
                    Logger.warn(TAG, "${request.videoId} will retry: ${outcome.code}")
                    Result.retry()
                }
            }
        }
    }

    private suspend fun fail(pipeline: DownloadPipeline, request: DownloadRequest, code: String): Result {
        // a rotated token or a moved server: the next run uses the new connection
        if (code == ErrorCodes.UNAUTHORIZED && dependencies.refreshConnection()) {
            Logger.warn(TAG, "${request.videoId}: server connection changed, retrying")
            // the pipeline already marked it failed; it's waiting for the next run instead
            dependencies.records.update(request.key) {
                it?.copy(state = DownloadRecord.State.Active, errorCode = null, jobId = null)
            }
            return Result.retry()
        }
        pipeline.markFailed(request.key, code)
        dependencies.notifications?.showFailed(id, request.title, GlobalSearchMessages.forCode(code))
        return Result.failure(errorData(code))
    }

    private suspend fun showProgress(request: DownloadRequest, progress: DownloadProgress?) {
        val notifications = dependencies.notifications ?: return
        val info = notifications.foregroundInfo(
            id,
            request.title,
            DownloadProgressData.text(progress),
            DownloadProgressData.percent(progress),
        )
        try {
            setForeground(info)
        } catch (err: IllegalStateException) {
            // Android 12+ refuses foreground starts from the background; the work still runs
            Logger.warn(TAG, "foreground not allowed: ${err.message}")
        }
    }

    companion object {
        private const val TAG = "ServerDownloadWorker"
        const val KEY_SAVED_URI = "saved_uri"
        const val KEY_ERROR_CODE = "error_code"
        private const val KEY_VIDEO_ID = "video_id"
        private const val KEY_FORMAT = "format"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTISTS = "artists"
        private const val KEY_THUMBNAIL_URL = "thumbnail_url"
        private const val KEY_ARTWORK_URL = "artwork_url"
        private const val KEY_SAVE_LRC = "save_lrc"

        fun inputData(request: DownloadRequest): Data = workDataOf(
            KEY_VIDEO_ID to request.videoId,
            KEY_FORMAT to request.format.apiValue,
            KEY_TITLE to request.title,
            KEY_ARTISTS to request.artists.toTypedArray(),
            KEY_THUMBNAIL_URL to request.thumbnailUrl,
            KEY_ARTWORK_URL to request.artworkUrl,
            KEY_SAVE_LRC to request.saveLrc,
        )

        fun requestFrom(data: Data): DownloadRequest? {
            val videoId = data.getString(KEY_VIDEO_ID) ?: return null
            val format = DownloadFormat.entries.find { it.apiValue == data.getString(KEY_FORMAT) } ?: return null
            return DownloadRequest(
                videoId = videoId,
                format = format,
                title = data.getString(KEY_TITLE).orEmpty(),
                artists = data.getStringArray(KEY_ARTISTS)?.toList().orEmpty(),
                thumbnailUrl = data.getString(KEY_THUMBNAIL_URL),
                artworkUrl = data.getString(KEY_ARTWORK_URL),
                saveLrc = data.getBoolean(KEY_SAVE_LRC, false),
            )
        }

        private fun errorData(code: String) = workDataOf(KEY_ERROR_CODE to code)
    }
}
