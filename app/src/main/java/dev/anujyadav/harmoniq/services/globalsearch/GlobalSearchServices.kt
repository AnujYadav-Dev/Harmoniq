package dev.anujyadav.harmoniq.services.globalsearch

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import dev.anujyadav.harmoniq.BuildConfig
import dev.anujyadav.harmoniq.services.AppMeta
import dev.anujyadav.harmoniq.services.globalsearch.api.HarmoniqServerApi
import dev.anujyadav.harmoniq.services.globalsearch.connection.ServerConnection

/**
 * Builds the pieces both the UI ([GlobalSearch]) and the download worker need, from a plain
 * [Context] (the worker runs without [dev.anujyadav.harmoniq.Symphony]).
 */
object GlobalSearchServices {
    val userAgent = "${AppMeta.appName}/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})"

    fun createApi(context: Context, connection: ServerConnection) = HarmoniqServerApi(
        baseUrl = connection.baseUrl,
        appToken = connection.appToken,
        deviceId = DeviceId.get(context.applicationContext),
        userAgent = userAgent,
    )
}

/** Small persisted flags that must survive the UI process and be readable by the worker. */
object GlobalSearchPreferences {
    private const val PREFERENCES_NAME = "global_search"
    private const val KEY_LIBRARY_PROMPT_DECLINED = "library_prompt_declined"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isLibraryPromptDeclined(context: Context) =
        preferences(context).getBoolean(KEY_LIBRARY_PROMPT_DECLINED, false)

    fun setLibraryPromptDeclined(context: Context, value: Boolean) = preferences(context).edit {
        putBoolean(KEY_LIBRARY_PROMPT_DECLINED, value)
    }
}
