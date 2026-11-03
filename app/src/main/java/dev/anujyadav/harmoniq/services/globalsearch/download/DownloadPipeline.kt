package dev.anujyadav.harmoniq.services.globalsearch.download

import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import dev.anujyadav.harmoniq.services.globalsearch.api.DownloadFormat
import dev.anujyadav.harmoniq.services.globalsearch.api.DownloadOverridesDto
import dev.anujyadav.harmoniq.services.globalsearch.api.DownloadRequestDto
import dev.anujyadav.harmoniq.services.globalsearch.api.ErrorCodes
import dev.anujyadav.harmoniq.services.globalsearch.api.HarmoniqServerApi
import dev.anujyadav.harmoniq.services.globalsearch.api.JobFileDto
import dev.anujyadav.harmoniq.services.globalsearch.api.JobStatus
import dev.anujyadav.harmoniq.services.globalsearch.api.JobStatusDto
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerError
import dev.anujyadav.harmoniq.utils.Logger
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class DownloadRequest(
    val videoId: String,
    val format: DownloadFormat,
    val title: String,
    val artists: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    // the square art of a song search result, the only metadata override (plan §9)
    val artworkUrl: String? = null,
    val saveLrc: Boolean = false,
) {
    val key get() = DownloadRecords.key(videoId, format.apiValue)
}

sealed interface DownloadProgress {
    data object Waiting : DownloadProgress
    data class OnServer(val status: JobStatus, val fraction: Float) : DownloadProgress
    data class Saving(val written: Long, val total: Long) : DownloadProgress
}

sealed interface DownloadOutcome {
    data class Saved(val record: DownloadRecord, val file: SavedFile) : DownloadOutcome
    data class Retry(val code: String) : DownloadOutcome
    data class Failed(val code: String) : DownloadOutcome
}

// codes produced on the phone, next to the server's ErrorCodes
object LocalErrorCodes {
    const val NETWORK = "network"
    const val STORAGE = "storage"
    const val INTEGRITY = "integrity_mismatch"
    const val MALFORMED = "malformed_response"
    const val TIMEOUT = "job_timeout"
}

/**
 * One download from tap to saved file (plan §9): submit → poll → fetch (resumable) → verify
 * size + SHA-256 → save → release the server copy. Free of Android framework types so it can be
 * unit tested; [ServerDownloadWorker] adapts it to WorkManager.
 */
class DownloadPipeline(
    private val api: HarmoniqServerApi,
    private val saver: DownloadSaver,
    private val records: DownloadRecords,
    private val partDirectory: File,
    private val onProgress: suspend (DownloadProgress) -> Unit,
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) {
    /** [attemptSeed] must be stable within one attempt and differ between attempts (idempotency). */
    suspend fun run(request: DownloadRequest, attemptSeed: String): DownloadOutcome {
        records.update(request.key) {
            (it ?: newRecord(request)).copy(state = DownloadRecord.State.Active, errorCode = null)
        }
        val outcome = try {
            process(request, attemptSeed)
        } catch (err: ServerError) {
            outcomeFor(err)
        } catch (err: IOException) {
            Logger.error(TAG, "saving ${request.videoId} failed", err)
            DownloadOutcome.Failed(LocalErrorCodes.STORAGE)
        }
        when (outcome) {
            is DownloadOutcome.Failed -> markFailed(request.key, outcome.code)
            is DownloadOutcome.Retry -> records.update(request.key) { it?.copy(errorCode = outcome.code) }
            is DownloadOutcome.Saved -> {}
        }
        return outcome
    }

    fun markFailed(key: String, code: String) = records.update(key) {
        it?.copy(state = DownloadRecord.State.Failed, errorCode = code, jobId = null)
    }

    /** User cancelled: stop the server job (or release its file) and remember the cancel. */
    suspend fun cancel(key: String) {
        records.get(key)?.jobId?.let { deleteQuietly(it) }
        records.update(key) { it?.copy(state = DownloadRecord.State.Cancelled, jobId = null) }
    }

    private suspend fun process(request: DownloadRequest, attemptSeed: String): DownloadOutcome {
        var resubmits = 0
        while (true) {
            val jobId = records.get(request.key)?.jobId ?: submit(request, "$attemptSeed:$resubmits")
            val status = try {
                awaitTerminal(jobId) ?: return DownloadOutcome.Retry(LocalErrorCodes.TIMEOUT)
            } catch (err: ServerError.Problem) {
                if (DownloadErrorPolicy.classify(err.code, err.retryable) != DownloadErrorPolicy.Action.Resubmit) throw err
                null
            }
            when (status?.jobStatus) {
                JobStatus.Completed -> {
                    val file = status.file ?: throw ServerError.Malformed(IOException("completed job without file"))
                    return finish(request, jobId, status, file)
                }

                JobStatus.Failed -> {
                    // a retry must create a fresh job, the failed one is final
                    clearJob(request.key)
                    val error = status.error
                    return outcomeFor(error?.code ?: ErrorCodes.INTERNAL_ERROR, error?.retryable ?: true)
                }

                // gone (server restart, TTL) or cancelled elsewhere: submit again
                else -> {
                    clearJob(request.key)
                    if (++resubmits > ServerConfig.MAX_RESUBMITS_PER_RUN) {
                        return DownloadOutcome.Retry(ErrorCodes.JOB_NOT_FOUND)
                    }
                }
            }
        }
    }

    private suspend fun submit(request: DownloadRequest, seed: String): String {
        val body = DownloadRequestDto(
            videoId = request.videoId,
            format = request.format.apiValue,
            metadata = request.artworkUrl?.let { DownloadOverridesDto(artworkUrl = it) },
        )
        val idempotencyKey = UUID.nameUUIDFromBytes("${request.key}:$seed".toByteArray()).toString()
        var waits = 0
        while (true) {
            try {
                val response = api.submitDownload(body, idempotencyKey)
                records.update(request.key) { it?.copy(jobId = response.jobId) }
                return response.jobId
            } catch (err: ServerError.Problem) {
                val wait = (err.retryAfterSeconds?.seconds ?: ServerConfig.jobPollInterval)
                val canWait = DownloadErrorPolicy.isInlineWaitable(err.code) &&
                        wait <= ServerConfig.maxInlineRetryWait &&
                        waits < ServerConfig.MAX_INLINE_SUBMIT_WAITS
                if (!canWait) throw err
                waits++
                onProgress(DownloadProgress.Waiting)
                sleep(wait)
            }
        }
    }

    private suspend fun awaitTerminal(jobId: String): JobStatusDto? = withTimeoutOrNull(ServerConfig.maxJobDuration) {
        var status = api.job(jobId)
        while (!status.jobStatus.isTerminal) {
            onProgress(
                when (status.jobStatus) {
                    JobStatus.Queued -> DownloadProgress.Waiting
                    else -> DownloadProgress.OnServer(status.jobStatus, status.progress.toFloat())
                }
            )
            sleep(ServerConfig.jobPollInterval)
            status = api.job(jobId)
        }
        status
    }

    private suspend fun finish(
        request: DownloadRequest,
        jobId: String,
        status: JobStatusDto,
        file: JobFileDto,
    ): DownloadOutcome {
        // the same song saved before: keep that copy instead of making "(2)"
        records.findDone(file.sha256)
            ?.takeIf { previous -> previous.savedUri?.let { saver.exists(it) } == true }
            ?.let { previous ->
                deleteQuietly(jobId)
                val saved = SavedFile(previous.savedUri!!, previous.fileName ?: file.filename, reused = true)
                return DownloadOutcome.Saved(markDone(request.key, saved, file), saved)
            }

        val part = File(partDirectory, "${file.sha256}$PART_SUFFIX")
        fetchVerified(jobId, file, part)
        val saved = saver.save(part, file.filename, file.mimeType, file.sha256)
        if (request.saveLrc && status.metadata?.lyrics?.available == true) {
            saveLyrics(jobId, saved)
        }
        deleteQuietly(jobId)
        part.delete()
        return DownloadOutcome.Saved(markDone(request.key, saved, file), saved)
    }

    private suspend fun fetchVerified(jobId: String, file: JobFileDto, part: File) {
        var lastPercent = -1L
        for (attempt in 1..ServerConfig.MAX_VERIFY_ATTEMPTS) {
            // a leftover .part (same content hash) resumes with a Range request
            if (part.exists() && part.length() > file.sizeBytes) part.delete()
            api.downloadFile(jobId, part) { written, total ->
                val expected = total ?: file.sizeBytes
                val percent = if (expected > 0) written * PERCENT / expected else 0
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(DownloadProgress.Saving(written, expected))
                }
            }
            val actualSize = part.length()
            val actualSha = if (actualSize == file.sizeBytes) withContext(Dispatchers.IO) { Sha256.of(part) } else null
            if (actualSha == file.sha256) return
            part.delete()
            if (attempt == ServerConfig.MAX_VERIFY_ATTEMPTS) {
                throw ServerError.IntegrityMismatch(
                    "${file.sizeBytes} bytes / ${file.sha256}",
                    "$actualSize bytes / ${actualSha ?: "-"}",
                )
            }
        }
    }

    private suspend fun saveLyrics(jobId: String, song: SavedFile) {
        try {
            val lyrics = api.lyrics(jobId)
            saver.saveCompanion(lyrics.toByteArray(Charsets.UTF_8), FileNames.withExtension(song.displayName, LRC_EXTENSION))
        } catch (err: ServerError) {
            // optional extra, the lyrics are embedded anyway
            Logger.warn(TAG, "lyrics file skipped: ${err.message}")
        } catch (err: IOException) {
            Logger.warn(TAG, "lyrics file skipped", err)
        }
    }

    private fun markDone(key: String, saved: SavedFile, file: JobFileDto): DownloadRecord {
        records.update(key) {
            it?.copy(
                state = DownloadRecord.State.Done,
                jobId = null,
                fileName = saved.displayName,
                savedUri = saved.uri,
                sizeBytes = file.sizeBytes,
                sha256 = file.sha256,
                errorCode = null,
            )
        }
        return records.get(key)!!
    }

    private fun clearJob(key: String) = records.update(key) { it?.copy(jobId = null) }

    private suspend fun deleteQuietly(jobId: String) {
        try {
            api.deleteJob(jobId)
        } catch (err: ServerError) {
            // the server drops it after its TTL anyway
            Logger.warn(TAG, "releasing job $jobId failed: ${err.message}")
        }
    }

    private fun outcomeFor(err: ServerError): DownloadOutcome = when (err) {
        is ServerError.Network -> DownloadOutcome.Retry(LocalErrorCodes.NETWORK)
        is ServerError.IntegrityMismatch -> DownloadOutcome.Retry(LocalErrorCodes.INTEGRITY)
        is ServerError.Malformed -> DownloadOutcome.Retry(LocalErrorCodes.MALFORMED)
        is ServerError.Problem -> outcomeFor(err.code, err.retryable)
    }

    private fun outcomeFor(code: String, retryable: Boolean): DownloadOutcome =
        when (DownloadErrorPolicy.classify(code, retryable)) {
            DownloadErrorPolicy.Action.Fail -> DownloadOutcome.Failed(code)
            else -> DownloadOutcome.Retry(code)
        }

    private fun newRecord(request: DownloadRequest) = DownloadRecord(
        videoId = request.videoId,
        format = request.format.apiValue,
        title = request.title,
        artists = request.artists,
        thumbnailUrl = request.thumbnailUrl,
        state = DownloadRecord.State.Active,
    )

    companion object {
        private const val TAG = "DownloadPipeline"
        private const val PART_SUFFIX = ".part"
        private const val LRC_EXTENSION = "lrc"
        private const val PERCENT = 100L
    }
}
