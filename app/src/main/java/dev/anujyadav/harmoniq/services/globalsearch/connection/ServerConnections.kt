package dev.anujyadav.harmoniq.services.globalsearch.connection

import android.content.Context
import dev.anujyadav.harmoniq.BuildConfig
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearchServices
import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import dev.anujyadav.harmoniq.services.globalsearch.api.HarmoniqServerApi
import dev.anujyadav.harmoniq.services.globalsearch.api.MetaDto
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The one place that knows which server Global Search uses and with which app token.
 * Shared by the UI ([dev.anujyadav.harmoniq.services.globalsearch.GlobalSearch]) and the
 * download worker, which run in the same process.
 */
class ServerConnections(
    private val store: ServerConnectionStore,
    private val loader: RemoteConfigLoader?,
    private val builtInUrl: String,
    private val builtInToken: String,
    private val allowCleartext: Boolean,
    private val apiFactory: (ServerConnection) -> HarmoniqServerApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class RemoteStatus(val fetchedAt: Long, val lastError: String?, val isLoading: Boolean)

    private val lock = Any()
    private val refreshMutex = Mutex()
    private var lastAttemptAt = 0L
    private var cachedApi: Pair<ServerConnection, HarmoniqServerApi>? = null

    private val _state = MutableStateFlow(compute())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _mode = MutableStateFlow(store.mode)
    val mode: StateFlow<ServerMode> = _mode.asStateFlow()

    private val _customUrl = MutableStateFlow(store.customUrl)
    val customUrl: StateFlow<String> = _customUrl.asStateFlow()

    private val _hasCustomToken = MutableStateFlow(store.hasCustomToken)
    val hasCustomToken: StateFlow<Boolean> = _hasCustomToken.asStateFlow()

    private val _remoteStatus = MutableStateFlow(RemoteStatus(store.remoteFetchedAt, null, false))
    val remoteStatus: StateFlow<RemoteStatus> = _remoteStatus.asStateFlow()

    val hasRemoteConfig get() = loader != null

    val connection: ServerConnection? get() = (state.value as? ConnectionState.Ready)?.connection

    private fun compute(): ConnectionState {
        val mode = store.mode
        return ConnectionRules.resolve(
            mode = mode,
            customUrl = store.customUrl,
            // only decrypted when it's actually used
            customToken = if (mode == ServerMode.Custom) store.customToken else null,
            remote = store.cachedRemote,
            builtInUrl = builtInUrl,
            builtInToken = builtInToken,
            allowCleartext = allowCleartext,
        )
    }

    private fun publish() {
        _state.value = compute()
        _mode.value = store.mode
        _customUrl.value = store.customUrl
        _hasCustomToken.value = store.hasCustomToken
    }

    fun setMode(mode: ServerMode) {
        store.mode = mode
        publish()
    }

    /** Saves the user's own server. A `null` [token] keeps the one already stored. */
    fun saveCustom(url: String, token: String?) {
        store.customUrl = url
        if (token != null) store.customToken = token
        publish()
    }

    fun isValidServerUrl(url: String) = ConnectionRules.parseServerUrl(url, allowCleartext) != null

    // one client per connection, rebuilt only when the URL or token changes
    fun api(): HarmoniqServerApi? {
        val current = connection ?: return null
        return synchronized(lock) {
            cachedApi?.takeIf { it.first == current }?.second
                ?: apiFactory(current).also { cachedApi = current to it }
        }
    }

    /**
     * Re-reads the default server's config file: when it's older than the refresh interval, or
     * right away with [force] (after an auth or connection error). Attempts are spaced at least
     * [ServerConfig.remoteConfigMinRefresh] apart so failures can't loop. Returns true when the
     * usable connection changed.
     */
    suspend fun refreshRemote(force: Boolean = false): Boolean {
        val loader = loader ?: return false
        if (store.mode != ServerMode.Default) return false
        return refreshMutex.withLock {
            val now = clock()
            val fetchedAt = store.remoteFetchedAt
            val isFresh = fetchedAt != ServerConnectionStore.NEVER &&
                    now - fetchedAt < ServerConfig.remoteConfigMaxAge.inWholeMilliseconds
            val triedRecently = now - lastAttemptAt < ServerConfig.remoteConfigMinRefresh.inWholeMilliseconds
            if ((!force && isFresh) || triedRecently) return@withLock false
            lastAttemptAt = now

            val before = state.value
            _remoteStatus.update { it.copy(isLoading = true) }
            val result = loader.load()
            result.onSuccess {
                store.cachedRemote = it
                store.remoteFetchedAt = now
            }
            _remoteStatus.value = RemoteStatus(store.remoteFetchedAt, result.exceptionOrNull()?.message, false)
            publish()
            state.value != before
        }
    }

    /** Checks a server before saving it: calls `/v1/meta` with that URL and token. */
    suspend fun test(url: String, token: String): Result<MetaDto> {
        val parsed = ConnectionRules.parseServerUrl(url, allowCleartext)
            ?: return Result.failure(IllegalArgumentException("invalid server URL"))
        return try {
            Result.success(apiFactory(ServerConnection(parsed, token.trim(), ServerConnection.Source.Custom)).meta())
        } catch (err: ServerError) {
            Result.failure(err)
        }
    }

    companion object {
        @Volatile
        private var instance: ServerConnections? = null

        fun get(context: Context): ServerConnections = instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                ServerConnections(
                    store = ServerConnectionStore.create(appContext),
                    loader = ServerConfig.remoteConfigUrl.trim().toHttpUrlOrNull()?.let {
                        HttpRemoteConfigLoader(it, HarmoniqServerApi.defaultClient, GlobalSearchServices.userAgent)
                    },
                    builtInUrl = BuildConfig.HARMONIQ_SERVER_URL,
                    builtInToken = BuildConfig.HARMONIQ_APP_TOKEN,
                    // matches the network security config: plain http only in debug builds
                    allowCleartext = BuildConfig.DEBUG,
                    apiFactory = { GlobalSearchServices.createApi(appContext, it) },
                ).also { instance = it }
            }
        }
    }
}
