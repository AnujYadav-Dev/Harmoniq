package dev.anujyadav.harmoniq.services.globalsearch

import dev.anujyadav.harmoniq.services.globalsearch.api.ErrorCodes
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerError
import dev.anujyadav.harmoniq.services.globalsearch.download.LocalErrorCodes
import dev.anujyadav.harmoniq.utils.AppStrings

// maps server error codes (docs/api.md §Errors) to what the user reads
object GlobalSearchMessages {
    fun forError(error: Throwable): String = when (error) {
        is ServerError.Network -> AppStrings.ServerUnreachable
        is ServerError.Problem -> forCode(error.code, error.retryAfterSeconds)
        is ServerError.IntegrityMismatch -> AppStrings.GlobalSearchServerBusy
        is ServerError.Malformed -> AppStrings.GlobalSearchUnexpectedResponse
        else -> AppStrings.GlobalSearchUnexpectedResponse
    }

    fun forCode(code: String, retryAfterSeconds: Long? = null): String = when (code) {
        ErrorCodes.UNAUTHORIZED -> AppStrings.GlobalSearchAccessRejected
        ErrorCodes.RATE_LIMITED -> retryAfterSeconds
            ?.let { AppStrings.RateLimitedTryAgainInX(it.toString()) }
            ?: AppStrings.RateLimitedTryAgainLater
        ErrorCodes.INVALID_QUERY -> AppStrings.GlobalSearchInvalidQuery
        ErrorCodes.VIDEO_UNAVAILABLE, ErrorCodes.VIDEO_NOT_FOUND -> AppStrings.VideoUnavailable
        ErrorCodes.PLAYLIST_NOT_FOUND, ErrorCodes.INVALID_PLAYLIST_ID -> AppStrings.PlaylistUnavailable
        ErrorCodes.AGE_RESTRICTED -> AppStrings.VideoAgeRestricted
        ErrorCodes.LIVE_NOT_SUPPORTED -> AppStrings.VideoIsLive
        ErrorCodes.DURATION_LIMIT_EXCEEDED -> AppStrings.VideoTooLong
        ErrorCodes.UPSTREAM_BLOCKED -> AppStrings.ServerBlockedByYoutube
        LocalErrorCodes.STORAGE -> AppStrings.NotEnoughStorage
        LocalErrorCodes.NETWORK -> AppStrings.DownloadWaitingForConnection
        else -> AppStrings.GlobalSearchServerBusy
    }
}
