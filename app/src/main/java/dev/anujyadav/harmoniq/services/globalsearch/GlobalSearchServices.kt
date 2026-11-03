package dev.anujyadav.harmoniq.services.globalsearch

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import dev.anujyadav.harmoniq.BuildConfig
import dev.anujyadav.harmoniq.services.AppMeta
import dev.anujyadav.harmoniq.services.globalsearch.api.HarmoniqServerApi

/**
 * Builds the pieces both the UI ([GlobalSearch]) and the download worker need, from a plain
 * [Context] (the worker runs without [dev.anujyadav.harmoniq.Symphony]).
 */
object GlobalSearchServices {
    private val userAgent = "${AppMeta.appName}/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})"

    fun isAvailable(context: Context) = ServerConfig.isEnabled && !GlobalSearchPreferences.isRevoked(context)

    fun createApi(context: Context): HarmoniqServerApi? {
        val baseUrl = ServerConfig.baseUrl ?: return null
        if (!ServerConfig.isEnabled) return null
        return HarmoniqServerApi(
            baseUrl = baseUrl,
            appToken = ServerConfig.appToken,
            deviceId = DeviceId.get(context.applicationContext),
            userAgent = userAgent,
        )
    }
}

/** Small persisted flags that must survive the UI process and be readable by the worker. */
object GlobalSearchPreferences {
    private const val PREFERENCES_NAME = "global_search"
    private const val KEY_REVOKED_VERSION_CODE = "revoked_version_code"
    private const val KEY_LIBRARY_PROMPT_DECLINED = "library_prompt_declined"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    // a 401 means this APK's token was rotated out; hide Global Search until the app is updated
    fun isRevoked(context: Context) =
        preferences(context).getInt(KEY_REVOKED_VERSION_CODE, NO_VERSION) == BuildConfig.VERSION_CODE

    fun markRevoked(context: Context) = preferences(context).edit {
        putInt(KEY_REVOKED_VERSION_CODE, BuildConfig.VERSION_CODE)
    }

    fun isLibraryPromptDeclined(context: Context) =
        preferences(context).getBoolean(KEY_LIBRARY_PROMPT_DECLINED, false)

    fun setLibraryPromptDeclined(context: Context, value: Boolean) = preferences(context).edit {
        putBoolean(KEY_LIBRARY_PROMPT_DECLINED, value)
    }

    private const val NO_VERSION = -1
}
