package dev.anujyadav.harmoniq.services.globalsearch

import dev.anujyadav.harmoniq.BuildConfig
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Everything Global Search can be tuned with, in one place. The server URL and app token
 * come from `local.properties` / environment variables via BuildConfig and are never committed.
 * Limits mirror the server defaults documented in harmoniq-server `docs/api.md`.
 */
object ServerConfig {
    val baseUrl: HttpUrl? = BuildConfig.HARMONIQ_SERVER_URL.trim().toHttpUrlOrNull()
    val appToken: String = BuildConfig.HARMONIQ_APP_TOKEN.trim()

    // builds without a server URL and token stay purely offline
    val isEnabled = baseUrl != null && appToken.isNotEmpty()

    // network
    val connectTimeout = 10.seconds
    val readTimeout = 30.seconds
    val writeTimeout = 30.seconds
    const val FILE_BUFFER_BYTES = 64 * 1024

    // search
    val searchDebounce = 400.milliseconds
    const val SEARCH_MIN_QUERY_LENGTH = 2
    const val SEARCH_MAX_QUERY_LENGTH = 200
    const val SEARCH_RESULT_LIMIT = 20

    // downloads
    val jobPollInterval = 2.seconds
    val retryBackoff = 30.seconds
    const val MAX_RUN_ATTEMPTS = 5
    const val MAX_VERIFY_ATTEMPTS = 2
    const val MAX_RESUBMITS_PER_RUN = 2
    const val MAX_QUEUED_DOWNLOADS = 20

    // waits up to this long inside the worker for "try again in N s" answers, longer waits reschedule
    val maxInlineRetryWait = 60.seconds
    const val MAX_INLINE_SUBMIT_WAITS = 3
    val maxJobDuration = 20.minutes

    // library
    val rescanStartTimeout = 5.seconds

    // storage
    const val DOWNLOAD_FOLDER_NAME = "Harmoniq"
    const val PART_FILES_FOLDER_NAME = "global_search_downloads"
    const val MAX_DOWNLOAD_RECORDS = 500
}
