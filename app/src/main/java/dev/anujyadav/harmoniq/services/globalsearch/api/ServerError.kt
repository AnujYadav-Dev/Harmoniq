package dev.anujyadav.harmoniq.services.globalsearch.api

import java.io.IOException

/**
 * Every way a server call can fail. Thrown by [HarmoniqServerApi]; callers branch on the
 * subtype (and on [Problem.code], which is part of the server contract).
 */
sealed class ServerError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connection, DNS failure, timeout, connection reset. */
    class Network(cause: IOException) : ServerError("network error: ${cause.message}", cause)

    /** An RFC 9457 error from the server, or an HTTP error without a problem body. */
    class Problem(
        val httpStatus: Int,
        val code: String,
        val detail: String?,
        val retryable: Boolean,
        val retryAfterSeconds: Long?,
        val requestId: String?,
    ) : ServerError("server error $httpStatus $code${detail?.let { ": $it" } ?: ""}")

    /** The response did not match the documented contract. */
    class Malformed(cause: Throwable) : ServerError("malformed response: ${cause.message}", cause)

    /** The downloaded file doesn't match the size or SHA-256 the server announced. */
    class IntegrityMismatch(expected: String, actual: String) :
        ServerError("integrity mismatch: expected $expected, got $actual")
}

// stable machine-readable error codes from docs/api.md
object ErrorCodes {
    const val UNAUTHORIZED = "unauthorized"
    const val FORBIDDEN = "forbidden"
    const val INVALID_QUERY = "invalid_query"
    const val JOB_NOT_FOUND = "job_not_found"
    const val JOB_NOT_READY = "job_not_ready"
    const val JOB_EXPIRED = "job_expired"
    const val VIDEO_NOT_FOUND = "video_not_found"
    const val PLAYLIST_NOT_FOUND = "playlist_not_found"
    const val INVALID_PLAYLIST_ID = "invalid_playlist_id"
    const val VIDEO_UNAVAILABLE = "video_unavailable"
    const val AGE_RESTRICTED = "age_restricted"
    const val LIVE_NOT_SUPPORTED = "live_not_supported"
    const val DURATION_LIMIT_EXCEEDED = "duration_limit_exceeded"
    const val LYRICS_NOT_AVAILABLE = "lyrics_not_available"
    const val RATE_LIMITED = "rate_limited"
    const val TOO_MANY_ACTIVE_JOBS = "too_many_active_jobs"
    const val QUEUE_FULL = "queue_full"
    const val UPSTREAM_BLOCKED = "upstream_blocked"
    const val UPSTREAM_ERROR = "upstream_error"
    const val UPSTREAM_TIMEOUT = "upstream_timeout"
    const val PROCESSING_FAILED = "processing_failed"
    const val SERVICE_UNAVAILABLE = "service_unavailable"
    const val INTERNAL_ERROR = "internal_error"

    // used when the server answered with an HTTP error but no problem+json body
    const val HTTP_ERROR = "http_error"
}
