package dev.anujyadav.harmoniq.services.globalsearch.connection

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerJson

/**
 * Persisted connection choices, in their own preferences file so the download worker (which
 * runs without the UI's Settings object) reads exactly what the UI wrote.
 */
class ServerConnectionStore(
    private val preferences: SharedPreferences,
    private val cipher: SecretCipher,
) {
    var mode: ServerMode
        get() = preferences.getString(KEY_MODE, null)
            ?.let { name -> ServerMode.entries.find { it.name == name } }
            ?: ServerMode.Default
        set(value) = preferences.edit { putString(KEY_MODE, value.name) }

    var customUrl: String
        get() = preferences.getString(KEY_CUSTOM_URL, null).orEmpty()
        set(value) = preferences.edit { putString(KEY_CUSTOM_URL, value.trim()) }

    // stored encrypted; an empty value clears it
    var customToken: String
        get() = preferences.getString(KEY_CUSTOM_TOKEN, null)?.let { cipher.decrypt(it) }.orEmpty()
        set(value) = preferences.edit {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) remove(KEY_CUSTOM_TOKEN)
            else cipher.encrypt(trimmed)?.let { putString(KEY_CUSTOM_TOKEN, it) }
        }

    val hasCustomToken get() = preferences.contains(KEY_CUSTOM_TOKEN)

    // the last config file that parsed, so the default server keeps working offline
    var cachedRemote: RemoteServerConfig?
        get() = preferences.getString(KEY_REMOTE_JSON, null)
            ?.let { runCatching { ServerJson.decodeFromString(RemoteServerConfig.serializer(), it) }.getOrNull() }
        set(value) = preferences.edit {
            if (value == null) remove(KEY_REMOTE_JSON)
            else putString(KEY_REMOTE_JSON, ServerJson.encodeToString(RemoteServerConfig.serializer(), value))
        }

    var remoteFetchedAt: Long
        get() = preferences.getLong(KEY_REMOTE_FETCHED_AT, NEVER)
        set(value) = preferences.edit { putLong(KEY_REMOTE_FETCHED_AT, value) }

    companion object {
        private const val PREFERENCES_NAME = "global_search_connection"
        private const val KEY_MODE = "mode"
        private const val KEY_CUSTOM_URL = "custom_url"
        private const val KEY_CUSTOM_TOKEN = "custom_token"
        private const val KEY_REMOTE_JSON = "remote_config_json"
        private const val KEY_REMOTE_FETCHED_AT = "remote_config_fetched_at"
        private const val CIPHER_ALIAS = "harmoniq_global_search_token"
        const val NEVER = 0L

        fun create(context: Context) = ServerConnectionStore(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            KeystoreSecretCipher(CIPHER_ALIAS),
        )
    }
}
