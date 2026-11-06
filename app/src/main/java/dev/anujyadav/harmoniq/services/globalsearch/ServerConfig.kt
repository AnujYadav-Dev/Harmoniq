package dev.anujyadav.harmoniq.services.globalsearch

import dev.anujyadav.harmoniq.BuildConfig
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Everything Global Search can be tuned with, in one place. Which server is used, and with
 * which token, is decided at runtime by [dev.anujyadav.harmoniq.services.globalsearch.connection.ServerConnections].
 * Limits mirror the server defaults documented in harmoniq-server `docs/api.md`.
 */
object ServerConfig {
    // public config file describing the default server (BuildConfig, overridable per build type)
    val remoteConfigUrl: String = BuildConfig.HARMONIQ_REMOTE_CONFIG_URL

    // re-read the config file after this long, or sooner after an auth/connection error
    val remoteConfigMaxAge = 6.hours
    val remoteConfigMinRefresh = 1.minutes
    const val REMOTE_CONFIG_MAX_BYTES = 16 * 1024

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

    // results per section of the "All" tab (server: SEARCH_ALL_SECTION_LIMIT)
    const val SEARCH_ALL_SECTION_LIMIT = 10

    // how long a search for a v0.3.0 type waits for /meta to know whether the server supports it
    val metaWaitTimeout = 5.seconds

    // playlists kept in memory, so reopening one from the list is instant
    const val PLAYLIST_CACHE_SIZE = 16

    // links built when the server didn't send one
    private const val YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v="
    private const val YOUTUBE_PLAYLIST_URL = "https://www.youtube.com/playlist?list="

    fun youtubeWatchUrl(videoId: String) = YOUTUBE_WATCH_URL + videoId
    fun youtubePlaylistUrl(playlistId: String) = YOUTUBE_PLAYLIST_URL + playlistId

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
    val libraryChangeDebounce = 1.seconds
    val sharingStopTimeout = 5.seconds

    // how often an unavailable server is checked again while its state is on screen
    val serverRecheckInterval = 30.seconds

    // storage
    const val DOWNLOAD_FOLDER_NAME = "Harmoniq"
    const val PART_FILES_FOLDER_NAME = "global_search_downloads"
    const val MAX_DOWNLOAD_RECORDS = 500
}
