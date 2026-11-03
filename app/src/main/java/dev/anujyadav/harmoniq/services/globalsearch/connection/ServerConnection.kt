package dev.anujyadav.harmoniq.services.globalsearch.connection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Which server Global Search talks to, chosen in Settings → Global search → Server. */
enum class ServerMode {
    // the Harmoniq server, whose address and app token come from a public config file on GitHub
    Default,

    // a server the user runs themselves, with the URL and token they typed in
    Custom,
}

/** A usable server: where it is and the app token to send. */
data class ServerConnection(
    val baseUrl: HttpUrl,
    val appToken: String,
    val source: Source,
) {
    enum class Source {
        // the GitHub config file (fresh or the last copy saved on the phone)
        Remote,

        // the values built into this APK, used until the config file has been read once
        BuiltIn,

        // the user's own server
        Custom,
    }
}

sealed interface ConnectionState {
    // no server address/token known for the selected mode
    data object NotConfigured : ConnectionState

    // the config file turned the default server off (e.g. maintenance); [message] says why
    data class Disabled(val message: String?) : ConnectionState

    data class Ready(val connection: ServerConnection, val message: String?) : ConnectionState
}

/**
 * The public config file for the default server (`config/global-search.json` in the app repo).
 * Anyone can read it, so the token in it is a public app key, not a secret.
 */
@Serializable
data class RemoteServerConfig(
    val version: Int = 1,
    @SerialName("server_url") val serverUrl: String? = null,
    @SerialName("app_token") val appToken: String? = null,
    val enabled: Boolean = true,
    val message: String? = null,
)

/** Pure resolution rules, shared by the UI process and the download worker. */
object ConnectionRules {
    // config files newer than this app understands are ignored rather than half-applied
    const val SUPPORTED_CONFIG_VERSION = 1
    const val MAX_MESSAGE_LENGTH = 300

    /** A server URL the app may use: parseable, and HTTPS unless [allowCleartext] (debug builds). */
    fun parseServerUrl(value: String?, allowCleartext: Boolean): HttpUrl? {
        val url = value?.trim()?.takeIf { it.isNotEmpty() }?.toHttpUrlOrNull() ?: return null
        return url.takeIf { it.isHttps || allowCleartext }
    }

    fun resolve(
        mode: ServerMode,
        customUrl: String?,
        customToken: String?,
        remote: RemoteServerConfig?,
        builtInUrl: String?,
        builtInToken: String?,
        allowCleartext: Boolean,
    ): ConnectionState = when (mode) {
        ServerMode.Custom -> {
            val url = parseServerUrl(customUrl, allowCleartext)
            val token = customToken?.trim().orEmpty()
            if (url != null && token.isNotEmpty()) {
                ConnectionState.Ready(ServerConnection(url, token, ServerConnection.Source.Custom), null)
            } else {
                ConnectionState.NotConfigured
            }
        }

        ServerMode.Default -> {
            val usable = remote?.takeIf { it.version <= SUPPORTED_CONFIG_VERSION }
            val message = usable?.message?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_MESSAGE_LENGTH)
            val remoteUrl = parseServerUrl(usable?.serverUrl, allowCleartext)
            val remoteToken = usable?.appToken?.trim().orEmpty()
            val builtIn = parseServerUrl(builtInUrl, allowCleartext)
            val builtInKey = builtInToken?.trim().orEmpty()
            when {
                usable?.enabled == false -> ConnectionState.Disabled(message)
                remoteUrl != null && remoteToken.isNotEmpty() -> ConnectionState.Ready(
                    ServerConnection(remoteUrl, remoteToken, ServerConnection.Source.Remote),
                    message,
                )

                builtIn != null && builtInKey.isNotEmpty() -> ConnectionState.Ready(
                    ServerConnection(builtIn, builtInKey, ServerConnection.Source.BuiltIn),
                    message,
                )

                else -> ConnectionState.NotConfigured
            }
        }
    }
}
