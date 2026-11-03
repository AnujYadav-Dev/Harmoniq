package dev.anujyadav.harmoniq.services.globalsearch.download

import android.content.Context
import androidx.core.content.edit
import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class DownloadRecord(
    val videoId: String,
    val format: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val state: State,
    // the server job of the current attempt, so a retried worker resumes it instead of starting over
    val jobId: String? = null,
    val fileName: String? = null,
    val savedUri: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val errorCode: String? = null,
    val updatedAt: Long = 0L,
) {
    val key get() = DownloadRecords.key(videoId, format)

    enum class State { Active, Done, Failed, Cancelled }
}

/**
 * Small persisted list of downloads, used for the "Downloaded" badge, deduplication and
 * resuming. Shared by the UI and the worker, which run in the same process.
 */
class DownloadRecords(
    private val load: () -> String?,
    private val save: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val serializer = ListSerializer(DownloadRecord.serializer())
    private val _records = MutableStateFlow(decode(load()))
    val records: StateFlow<Map<String, DownloadRecord>> = _records.asStateFlow()

    fun get(key: String) = _records.value[key]

    fun findDone(sha256: String) = _records.value.values.find {
        it.state == DownloadRecord.State.Done && it.sha256 == sha256 && it.savedUri != null
    }

    @Synchronized
    fun update(key: String, transform: (DownloadRecord?) -> DownloadRecord?) {
        val current = _records.value
        val next = current.toMutableMap()
        when (val updated = transform(current[key])) {
            null -> next.remove(key)
            else -> next[key] = updated.copy(updatedAt = clock())
        }
        val trimmed = trim(next)
        _records.value = trimmed
        save(ServerJson.encodeToString(serializer, trimmed.values.toList()))
    }

    // keeps the newest records; active ones are never dropped
    private fun trim(records: Map<String, DownloadRecord>): Map<String, DownloadRecord> {
        if (records.size <= ServerConfig.MAX_DOWNLOAD_RECORDS) return records
        val (active, rest) = records.values.partition { it.state == DownloadRecord.State.Active }
        val kept = active + rest.sortedByDescending { it.updatedAt }
            .take((ServerConfig.MAX_DOWNLOAD_RECORDS - active.size).coerceAtLeast(0))
        return kept.associateBy { it.key }
    }

    private fun decode(text: String?): Map<String, DownloadRecord> = text
        ?.let { runCatching { ServerJson.decodeFromString(serializer, it) }.getOrNull() }
        .orEmpty()
        .associateBy { it.key }

    companion object {
        private const val PREFERENCES_NAME = "global_search_downloads"
        private const val KEY_RECORDS = "records"

        @Volatile
        private var instance: DownloadRecords? = null

        fun key(videoId: String, format: String) = "$videoId:$format"

        fun get(context: Context): DownloadRecords = instance ?: synchronized(this) {
            instance ?: run {
                val preferences = context.applicationContext
                    .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                DownloadRecords(
                    load = { preferences.getString(KEY_RECORDS, null) },
                    save = { preferences.edit { putString(KEY_RECORDS, it) } },
                ).also { instance = it }
            }
        }
    }
}
