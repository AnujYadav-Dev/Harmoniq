package dev.anujyadav.harmoniq.services.globalsearch

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/**
 * Random UUID generated once per install and sent as `X-Device-Id`. Not a secret: the server
 * uses it for rate limits and job ownership. Read through a plain [Context] so the download
 * worker can use it without the UI-scoped [dev.anujyadav.harmoniq.Symphony].
 */
object DeviceId {
    private const val PREFERENCES_NAME = "global_search"
    private const val KEY = "device_id"

    @Volatile
    private var cached: String? = null

    fun get(context: Context): String = cached ?: synchronized(this) {
        cached ?: run {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val id = preferences.getString(KEY, null) ?: UUID.randomUUID().toString().also {
                preferences.edit(commit = true) { putString(KEY, it) }
            }
            cached = id
            id
        }
    }
}
