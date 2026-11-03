package dev.anujyadav.harmoniq.services.globalsearch

import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.globalsearch.api.ErrorCodes
import dev.anujyadav.harmoniq.services.globalsearch.api.HarmoniqServerApi
import dev.anujyadav.harmoniq.services.globalsearch.api.MetaDto
import dev.anujyadav.harmoniq.services.globalsearch.api.PlaylistDto
import dev.anujyadav.harmoniq.services.globalsearch.api.PreviewDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SongMetadataDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchType
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerError
import dev.anujyadav.harmoniq.utils.Logger
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.anujyadav.harmoniq.services.globalsearch.api.DownloadFormat
import dev.anujyadav.harmoniq.services.globalsearch.download.DownloadProgress
import dev.anujyadav.harmoniq.services.globalsearch.download.DownloadProgressData
import dev.anujyadav.harmoniq.services.globalsearch.download.DownloadRecord
import dev.anujyadav.harmoniq.services.globalsearch.download.DownloadRecords
import dev.anujyadav.harmoniq.services.globalsearch.download.DownloadRequest
import dev.anujyadav.harmoniq.services.globalsearch.download.ServerDownloadWorker
import kotlin.time.toJavaDuration
import android.net.Uri
import dev.anujyadav.harmoniq.services.globalsearch.download.LibraryLink
import dev.anujyadav.harmoniq.services.globalsearch.watchlist.WatchList
import dev.anujyadav.harmoniq.services.groove.Groove
import dev.anujyadav.harmoniq.utils.ActivityUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.drop
import dev.anujyadav.harmoniq.services.globalsearch.connection.ConnectionState
import dev.anujyadav.harmoniq.services.globalsearch.connection.ServerConnections
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Global Search facade owned by [Symphony], like `groove` and `radio`. The UI reads state
 * from the exposed [StateFlow]s and never talks to the server directly.
 */
class GlobalSearch(private val symphony: Symphony) : Symphony.Hooks {
    sealed interface SearchState {
        data object Idle : SearchState
        data class Loading(val query: String) : SearchState
        data class Results(val query: String, val type: SearchType, val page: SearchPage) : SearchState
        data class Empty(val query: String) : SearchState
        data class Failed(val query: String, val error: ServerError) : SearchState
    }

    sealed interface MetaState {
        data object Unknown : MetaState
        data object Loading : MetaState
        data class Ready(val meta: MetaDto) : MetaState
        data class Failed(val error: ServerError) : MetaState
    }

    private data class SearchRequest(val query: String, val type: SearchType, val attempt: Int = 0)

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context get() = symphony.applicationContext
    // which server and token to use; changes at runtime (Settings, or the GitHub config file)
    val connections by lazy { ServerConnections.get(context) }
    val connectionState: StateFlow<ConnectionState> get() = connections.state

    // a client for the current connection, or null when no server is configured
    val api: HarmoniqServerApi? get() = connections.api()

    // the user hasn't turned it off in Settings and a server is known (a paused one still shows its notice)
    val isEnabled: StateFlow<Boolean> by lazy {
        combine(symphony.settings.globalSearchEnabled.flow, connections.state) { enabled, state ->
            enabled && state !is ConnectionState.NotConfigured
        }.stateIn(
            coroutineScope,
            SharingStarted.Eagerly,
            symphony.settings.globalSearchEnabled.value && connections.state.value !is ConnectionState.NotConfigured,
        )
    }

    // notice from the config file, e.g. "Server maintenance tonight"
    val connectionMessage: StateFlow<String?> by lazy {
        connections.state.map {
            when (it) {
                is ConnectionState.Ready -> it.message
                is ConnectionState.Disabled -> it.message
                ConnectionState.NotConfigured -> null
            }
        }.stateIn(coroutineScope, SharingStarted.Eagerly, null)
    }

    private val _meta = MutableStateFlow<MetaState>(MetaState.Unknown)
    val meta = _meta.asStateFlow()

    // until /meta answers every type is offered; a server before v0.3.0 then hides Playlists and All
    val supportedSearchTypes: StateFlow<List<SearchType>> = _meta
        .map { (it as? MetaState.Ready)?.meta?.supportedSearchTypes ?: SearchType.entries }
        .stateIn(coroutineScope, SharingStarted.Eagerly, SearchType.entries)

    // songs saved from Global Search to listen to or download later; local, works offline
    val watchList = WatchList(symphony, coroutineScope)

    /** One download as the UI shows it: WorkManager's live state over the persisted record. */
    data class DownloadView(
        val key: String,
        val videoId: String,
        val format: DownloadFormat,
        val title: String,
        val state: State,
        val progress: DownloadProgress? = null,
        val errorCode: String? = null,
        val fileName: String? = null,
    ) {
        enum class State { Queued, Running, Done, Failed, Cancelled }

        val isActive get() = state == State.Queued || state == State.Running
    }

    enum class EnqueueResult { Enqueued, AlreadyQueued, QueueFull, Unavailable }

    private val rescanMutex = Mutex()
    private val workManager by lazy { WorkManager.getInstance(context) }
    val records by lazy { DownloadRecords.get(context) }

    val downloads: StateFlow<Map<String, DownloadView>> by lazy {
        combine(records.records, workManager.getWorkInfosByTagFlow(WORK_TAG)) { records, infos ->
            mergeDownloads(records, infos)
        }.stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())
    }

    val downloadedVideoIds: StateFlow<Set<String>> by lazy {
        records.records
            .map { all -> all.values.filter { it.state == DownloadRecord.State.Done }.map { it.videoId }.toSet() }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptySet())
    }

    /** Per video, merged over formats: what the row badge and the actions show. */
    val downloadStateByVideoId: StateFlow<Map<String, OnlineDownloadState>> by lazy {
        downloads
            .map { all ->
                all.values.groupBy { it.videoId }.mapValues { (_, views) ->
                    OnlineDownloadState.merge(views.map { it.toOnlineState() })
                }
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())
    }

    private val searchRequests = MutableStateFlow(SearchRequest("", SearchType.All))

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchState: StateFlow<SearchState> = searchRequests
        .debounce { if (it.query.length < ServerConfig.SEARCH_MIN_QUERY_LENGTH) 0L else ServerConfig.searchDebounce.inWholeMilliseconds }
        .distinctUntilChanged()
        // a newer query cancels the request still in flight
        .flatMapLatest { request -> flow { emit(SearchState.Loading(request.query)); emit(runSearch(request)) } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, SearchState.Idle)

    fun search(query: String, type: SearchType) {
        val trimmed = query.trim().take(ServerConfig.SEARCH_MAX_QUERY_LENGTH)
        searchRequests.update { if (it.query == trimmed && it.type == type) it else SearchRequest(trimmed, type) }
    }

    fun retrySearch() = searchRequests.update { it.copy(attempt = it.attempt + 1) }

    fun refreshMeta() {
        val api = api ?: return
        if (_meta.value == MetaState.Loading) return
        _meta.value = MetaState.Loading
        coroutineScope.launch {
            _meta.value = try {
                MetaState.Ready(api.meta())
            } catch (err: ServerError) {
                onServerError(err)
                MetaState.Failed(err)
            }
        }
    }

    private var metaRequested = false

    // called once Global mode is first shown, so offline-only use never touches the network
    fun ensureMeta() {
        metaRequested = true
        if (_meta.value != MetaState.Unknown) return
        _meta.value = MetaState.Loading
        coroutineScope.launch {
            // the config file is re-read at most every few hours (connections decides)
            connections.refreshRemote()
            _meta.value = MetaState.Unknown
            refreshMeta()
        }
    }

    // a new server or token: forget the old server's answers and run the current search again
    private fun watchConnection() = coroutineScope.launch {
        connections.state.drop(1).distinctUntilChanged().collect {
            _meta.value = MetaState.Unknown
            if (metaRequested) refreshMeta()
            retrySearch()
        }
    }

    // plan §11: a saved song only reaches the library through a media folder covering Download/Harmoniq
    val isDownloadsFolderCovered: StateFlow<Boolean> by lazy {
        symphony.settings.mediaFolders.flow
            .map { LibraryLink.isCovered(it) }
            .stateIn(coroutineScope, SharingStarted.Eagerly, LibraryLink.isCovered(symphony.settings.mediaFolders.value))
    }

    private val _showLibraryPrompt = MutableStateFlow(false)
    val showLibraryPrompt = _showLibraryPrompt.asStateFlow()

    fun onLibraryFolderPicked(uri: Uri) {
        ActivityUtils.makePersistableReadableUri(context, uri)
        symphony.settings.mediaFolders.setValue(symphony.settings.mediaFolders.value + uri)
        _showLibraryPrompt.value = false
        requestRescan()
    }

    fun dismissLibraryPrompt() {
        // asked once; afterwards downloaded items show a small "add folder" hint instead
        GlobalSearchPreferences.setLibraryPromptDeclined(context, true)
        _showLibraryPrompt.value = false
    }

    private fun watchSavedSongs() = coroutineScope.launch {
        var known: Set<String>? = null
        records.records.collect { all ->
            val saved = all.values.filter { it.state == DownloadRecord.State.Done }.mapNotNull { it.savedUri }.toSet()
            val previous = known
            known = saved
            val canPrompt = !isDownloadsFolderCovered.value && !GlobalSearchPreferences.isLibraryPromptDeclined(context)
            when {
                // first value at launch: groove scans on its own, only a pending prompt is left to show
                previous == null -> if (saved.isNotEmpty() && canPrompt) _showLibraryPrompt.value = true
                (saved - previous).isEmpty() -> {}
                isDownloadsFolderCovered.value -> requestRescan()
                canPrompt -> _showLibraryPrompt.value = true
            }
        }
    }

    // the scan cache skips unchanged files, so a plain fetch is cheap; never overlap two scans
    private fun requestRescan() = coroutineScope.launch {
        rescanMutex.withLock {
            symphony.groove.exposer.isUpdating.first { !it }
            symphony.groove.fetch(Groove.FetchOptions())
            // fetch() is fire-and-forget: wait for that scan to start and finish before the next one
            withTimeoutOrNull(ServerConfig.rescanStartTimeout) { symphony.groove.exposer.isUpdating.first { it } }
            symphony.groove.exposer.isUpdating.first { !it }
        }
    }

    override fun onSymphonyReady() {
        watchSavedSongs()
        watchConnection()
        watchList.load()
    }

    fun enqueueDownload(result: SearchResultDto, format: DownloadFormat): EnqueueResult {
        if (api == null) return EnqueueResult.Unavailable
        val key = DownloadRecords.key(result.videoId, format.apiValue)
        val current = downloads.value
        if (current[key]?.isActive == true) return EnqueueResult.AlreadyQueued
        if (current.values.count { it.isActive } >= ServerConfig.MAX_QUEUED_DOWNLOADS) return EnqueueResult.QueueFull

        val download = DownloadRequest(
            videoId = result.videoId,
            format = format,
            title = result.title,
            artists = result.displayArtists,
            thumbnailUrl = result.bestThumbnail(),
            // song results carry the square album art; video thumbnails are 16:9 frames
            artworkUrl = result.bestThumbnail()?.takeIf { result.resultType == SearchType.Song.apiValue },
            saveLrc = symphony.settings.globalSearchSaveLrcFiles.value,
        )
        // visible as "Waiting..." before the worker gets to run
        records.update(key) {
            (it ?: DownloadRecord(download.videoId, format.apiValue, download.title, state = DownloadRecord.State.Active))
                .copy(
                    title = download.title,
                    artists = download.artists,
                    thumbnailUrl = download.thumbnailUrl,
                    state = DownloadRecord.State.Active,
                    errorCode = null,
                )
        }
        val request = OneTimeWorkRequestBuilder<ServerDownloadWorker>()
            .setInputData(ServerDownloadWorker.inputData(download))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ServerConfig.retryBackoff.toJavaDuration())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_TAG)
            .addTag(KEY_TAG_PREFIX + key)
            .build()
        workManager.enqueueUniqueWork(uniqueWorkName(key), ExistingWorkPolicy.KEEP, request)
        return EnqueueResult.Enqueued
    }

    fun cancelDownload(key: String) {
        workManager.cancelUniqueWork(uniqueWorkName(key))
        // a worker waiting for its retry isn't running, so release the server job here too
        coroutineScope.launch {
            records.get(key)?.jobId?.let { jobId -> call { it.deleteJob(jobId) } }
            records.update(key) { it?.copy(state = DownloadRecord.State.Cancelled, jobId = null) }
        }
    }

    private fun mergeDownloads(
        records: Map<String, DownloadRecord>,
        infos: List<WorkInfo>,
    ): Map<String, DownloadView> {
        val result = records.mapNotNull { (key, record) ->
            val format = DownloadFormat.entries.find { it.apiValue == record.format } ?: return@mapNotNull null
            key to DownloadView(
                key = key,
                videoId = record.videoId,
                format = format,
                title = record.title,
                state = when (record.state) {
                    DownloadRecord.State.Active -> DownloadView.State.Queued
                    DownloadRecord.State.Done -> DownloadView.State.Done
                    DownloadRecord.State.Failed -> DownloadView.State.Failed
                    DownloadRecord.State.Cancelled -> DownloadView.State.Cancelled
                },
                errorCode = record.errorCode,
                fileName = record.fileName,
            )
        }.toMap().toMutableMap()
        // live work wins over the record, and an unfinished run over older finished ones
        infos.sortedBy { !it.state.isFinished }.forEach { info ->
            val key = info.tags.firstOrNull { it.startsWith(KEY_TAG_PREFIX) }?.removePrefix(KEY_TAG_PREFIX)
                ?: return@forEach
            val base = result[key] ?: return@forEach
            // a finished run older than the record (e.g. an earlier download) must not override it
            if (info.state.isFinished && base.state != DownloadView.State.Queued) return@forEach
            result[key] = base.copy(
                state = when (info.state) {
                    WorkInfo.State.RUNNING -> DownloadView.State.Running
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadView.State.Queued
                    WorkInfo.State.SUCCEEDED -> DownloadView.State.Done
                    WorkInfo.State.FAILED -> DownloadView.State.Failed
                    WorkInfo.State.CANCELLED -> DownloadView.State.Cancelled
                },
                progress = DownloadProgressData.fromData(info.progress),
                errorCode = info.outputData.getString(ServerDownloadWorker.KEY_ERROR_CODE) ?: base.errorCode,
            )
        }
        return result
    }

    // "what the file will be tagged with", shown in the preview sheet
    suspend fun track(videoId: String): Result<SongMetadataDto> = call { it.track(videoId) }

    suspend fun preview(videoId: String): Result<PreviewDto> = call { it.preview(videoId) }

    // reopening a playlist from the list shouldn't cost another request
    private val playlistCache = object : LinkedHashMap<String, PlaylistDto>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PlaylistDto>?) =
            size > ServerConfig.PLAYLIST_CACHE_SIZE
    }

    suspend fun playlist(playlistId: String): Result<PlaylistDto> {
        synchronized(playlistCache) { playlistCache[playlistId] }?.let { return Result.success(it) }
        return call { it.playlist(playlistId) }.onSuccess {
            synchronized(playlistCache) { playlistCache[playlistId] = it }
        }
    }

    private suspend fun <T> call(block: suspend (HarmoniqServerApi) -> T): Result<T> {
        val api = api ?: return Result.failure(IllegalStateException("Global Search is not configured"))
        return try {
            Result.success(block(api))
        } catch (err: ServerError) {
            onServerError(err)
            Result.failure(err)
        }
    }

    private suspend fun runSearch(request: SearchRequest): SearchState {
        if (request.query.length < ServerConfig.SEARCH_MIN_QUERY_LENGTH) return SearchState.Idle
        val api = api ?: return SearchState.Idle
        return try {
            val type = resolveSearchType(request.type)
            val limit = when (type) {
                SearchType.All -> ServerConfig.SEARCH_ALL_SECTION_LIMIT
                else -> ServerConfig.SEARCH_RESULT_LIMIT
            }
            val page = SearchPage.from(type, api.search(request.query, type, limit))
            when {
                page.isEmpty && page.sectionErrors.isEmpty() -> SearchState.Empty(request.query)
                else -> SearchState.Results(request.query, type, page)
            }
        } catch (err: ServerError) {
            Logger.warn(TAG, "search failed: ${err.message}")
            onServerError(err)
            SearchState.Failed(request.query, err)
        }
    }

    // a type the server doesn't know (older server) falls back to the first one it supports
    private suspend fun resolveSearchType(type: SearchType): SearchType {
        if (type in SearchType.legacy) return type
        ensureMeta()
        val state = withTimeoutOrNull(ServerConfig.metaWaitTimeout) {
            meta.first { it is MetaState.Ready || it is MetaState.Failed }
        }
        val supported = (state as? MetaState.Ready)?.meta?.supportedSearchTypes ?: return type
        return if (type in supported) type else supported.first()
    }

    // a rejected token or an unreachable server may mean the config file changed: re-read it
    // (spaced out by ServerConnections); a changed connection re-runs the search via watchConnection
    internal fun onServerError(err: ServerError) {
        val maybeMoved = err is ServerError.Network ||
                (err is ServerError.Problem && err.code == ErrorCodes.UNAUTHORIZED)
        if (maybeMoved) coroutineScope.launch { connections.refreshRemote(force = true) }
    }

    override fun onSymphonyDestroy() {
        coroutineScope.cancel()
    }

    companion object {
        private const val TAG = "GlobalSearch"

        fun DownloadView.toOnlineState(): OnlineDownloadState = when (state) {
            DownloadView.State.Queued -> OnlineDownloadState.Queued
            DownloadView.State.Running -> OnlineDownloadState.Running(DownloadProgressData.fraction(progress))
            DownloadView.State.Done -> OnlineDownloadState.Done
            DownloadView.State.Failed -> OnlineDownloadState.Failed
            DownloadView.State.Cancelled -> OnlineDownloadState.None
        }
        private const val WORK_TAG = "global_search_download"
        private const val KEY_TAG_PREFIX = "download_key:"

        // plan §9: one unique work per song and format
        private fun uniqueWorkName(key: String) = "dl-${key.replace(':', '-')}"
    }
}
