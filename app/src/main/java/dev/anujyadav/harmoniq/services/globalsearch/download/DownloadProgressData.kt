package dev.anujyadav.harmoniq.services.globalsearch.download

import androidx.work.Data
import androidx.work.workDataOf
import dev.anujyadav.harmoniq.services.globalsearch.api.JobStatus
import dev.anujyadav.harmoniq.utils.AppStrings

/** [DownloadProgress] through WorkManager's progress [Data], plus how it reads (plan §12). */
object DownloadProgressData {
    private const val KEY_KIND = "progress_kind"
    private const val KEY_STATUS = "progress_status"
    private const val KEY_FRACTION = "progress_fraction"
    private const val KEY_WRITTEN = "progress_written"
    private const val KEY_TOTAL = "progress_total"
    private const val KIND_WAITING = "waiting"
    private const val KIND_SERVER = "server"
    private const val KIND_SAVING = "saving"
    private const val PERCENT_MAX = 100

    fun toData(progress: DownloadProgress): Data = when (progress) {
        DownloadProgress.Waiting -> workDataOf(KEY_KIND to KIND_WAITING)
        is DownloadProgress.OnServer -> workDataOf(
            KEY_KIND to KIND_SERVER,
            KEY_STATUS to progress.status.apiValue,
            KEY_FRACTION to progress.fraction,
        )

        is DownloadProgress.Saving -> workDataOf(
            KEY_KIND to KIND_SAVING,
            KEY_WRITTEN to progress.written,
            KEY_TOTAL to progress.total,
        )
    }

    fun fromData(data: Data): DownloadProgress? = when (data.getString(KEY_KIND)) {
        KIND_WAITING -> DownloadProgress.Waiting
        KIND_SERVER -> DownloadProgress.OnServer(
            JobStatus.from(data.getString(KEY_STATUS).orEmpty()),
            data.getFloat(KEY_FRACTION, 0f),
        )

        KIND_SAVING -> DownloadProgress.Saving(data.getLong(KEY_WRITTEN, 0), data.getLong(KEY_TOTAL, 0))
        else -> null
    }

    fun text(progress: DownloadProgress?): String = when (progress) {
        null, DownloadProgress.Waiting -> AppStrings.DownloadWaiting
        is DownloadProgress.Saving -> AppStrings.DownloadSaving
        is DownloadProgress.OnServer -> when (progress.status) {
            JobStatus.Queued -> AppStrings.DownloadWaiting
            JobStatus.Resolving -> AppStrings.DownloadResolving
            JobStatus.Downloading -> AppStrings.DownloadOnServer
            else -> AppStrings.DownloadTagging
        }
    }

    /** 0..1, or null for an indeterminate bar. */
    fun fraction(progress: DownloadProgress?): Float? = when (progress) {
        null, DownloadProgress.Waiting -> null
        // the server's overall progress already spans resolve → download → tag
        is DownloadProgress.OnServer -> progress.fraction.coerceIn(0f, 1f)
        is DownloadProgress.Saving -> progress.total.takeIf { it > 0 }
            ?.let { (progress.written.toFloat() / it).coerceIn(0f, 1f) }
    }

    fun percent(progress: DownloadProgress?) = fraction(progress)?.let { (it * PERCENT_MAX).toInt() }
}
