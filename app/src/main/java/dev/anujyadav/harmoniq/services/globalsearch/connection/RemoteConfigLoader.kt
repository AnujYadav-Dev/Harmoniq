package dev.anujyadav.harmoniq.services.globalsearch.connection

import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerJson
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads and parses the public config file that describes the default server. */
fun interface RemoteConfigLoader {
    suspend fun load(): Result<RemoteServerConfig>
}

class HttpRemoteConfigLoader(
    private val url: HttpUrl,
    private val client: OkHttpClient,
    private val userAgent: String,
) : RemoteConfigLoader {
    override suspend fun load(): Result<RemoteServerConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header(HEADER_USER_AGENT, userAgent)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("config file: HTTP ${response.code}")
                val body = response.body ?: throw IOException("config file: empty body")
                if (body.contentLength() > ServerConfig.REMOTE_CONFIG_MAX_BYTES) throw IOException("config file too large")
                // read at most the limit, even when the length isn't announced
                val bytes = body.byteStream().use { it.readNBytesCompat(ServerConfig.REMOTE_CONFIG_MAX_BYTES + 1) }
                if (bytes.size > ServerConfig.REMOTE_CONFIG_MAX_BYTES) throw IOException("config file too large")
                ServerJson.decodeFromString(RemoteServerConfig.serializer(), bytes.toString(Charsets.UTF_8))
            }
        }
    }

    private companion object {
        const val HEADER_USER_AGENT = "User-Agent"
    }
}

// InputStream.readNBytes is API 33+; minSdk is 28
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private const val BUFFER_BYTES = 8 * 1024
