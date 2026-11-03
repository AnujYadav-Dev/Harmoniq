package dev.anujyadav.harmoniq.services.globalsearch.download

import dev.anujyadav.harmoniq.services.globalsearch.api.ErrorCodes

// what the worker does with an error code (plan §12)
object DownloadErrorPolicy {
    enum class Action {
        // try again later with backoff (network, server busy, YouTube trouble)
        Retry,

        // the job is gone on the server (restart, TTL): submit it again right away
        Resubmit,

        // will never work for this video / app build
        Fail,
    }

    private val resubmitCodes = setOf(ErrorCodes.JOB_NOT_FOUND, ErrorCodes.JOB_EXPIRED)
    private val failCodes = setOf(
        ErrorCodes.UNAUTHORIZED,
        ErrorCodes.FORBIDDEN,
        ErrorCodes.VIDEO_NOT_FOUND,
        ErrorCodes.VIDEO_UNAVAILABLE,
        ErrorCodes.AGE_RESTRICTED,
        ErrorCodes.LIVE_NOT_SUPPORTED,
        ErrorCodes.DURATION_LIMIT_EXCEEDED,
    )
    private val retryCodes = setOf(
        ErrorCodes.UPSTREAM_BLOCKED,
        ErrorCodes.UPSTREAM_ERROR,
        ErrorCodes.UPSTREAM_TIMEOUT,
        ErrorCodes.PROCESSING_FAILED,
        ErrorCodes.QUEUE_FULL,
        ErrorCodes.RATE_LIMITED,
        ErrorCodes.TOO_MANY_ACTIVE_JOBS,
        ErrorCodes.SERVICE_UNAVAILABLE,
        ErrorCodes.INTERNAL_ERROR,
        ErrorCodes.JOB_NOT_READY,
    )

    fun classify(code: String, retryable: Boolean): Action = when (code) {
        in resubmitCodes -> Action.Resubmit
        in failCodes -> Action.Fail
        in retryCodes -> Action.Retry
        // unknown codes: trust the server's flag (forward compatible)
        else -> if (retryable) Action.Retry else Action.Fail
    }

    // short waits are absorbed inside the worker instead of a full backoff cycle
    fun isInlineWaitable(code: String) = code == ErrorCodes.TOO_MANY_ACTIVE_JOBS ||
            code == ErrorCodes.RATE_LIMITED ||
            code == ErrorCodes.QUEUE_FULL
}
