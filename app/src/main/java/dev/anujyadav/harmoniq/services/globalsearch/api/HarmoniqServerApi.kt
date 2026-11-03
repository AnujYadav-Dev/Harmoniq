package dev.anujyadav.harmoniq.services.globalsearch.api

import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Thin client for harmoniq-server `/v1` (contract: harmoniq-server `docs/api.md`).
 * Every call is main-safe and cancellable; failures are thrown as [ServerError].
 */
class HarmoniqServerApi(
    private val baseUrl: HttpUrl,
    private val appToken: String,
    private val deviceId: String,
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient,
    private val json: Json = ServerJson,
) {
    suspend fun meta(): MetaDto = getJson(url("meta"), MetaDto.serializer())

    suspend fun search(query: String, type: SearchType, limit: Int): SearchResponseDto {
        val url = url("search").newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", type.apiValue)
            .addQueryParameter("limit", limit.toString())
            .build()
        return getJson(url, SearchResponseDto.serializer())
    }

    suspend fun track(videoId: String): SongMetadataDto =
        getJson(url("videos", videoId, "track"), SongMetadataDto.serializer())

    suspend fun preview(videoId: String): PreviewDto =
        getJson(url("videos", videoId, "preview"), PreviewDto.serializer())

    suspend fun submitDownload(body: DownloadRequestDto, idempotencyKey: String): SubmitResponseDto {
        val request = baseRequest(url("downloads"))
            .header(HEADER_IDEMPOTENCY_KEY, idempotencyKey)
            .post(json.encodeToString(DownloadRequestDto.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return send(request).use { decode(it, SubmitResponseDto.serializer()) }
    }

    suspend fun job(jobId: String): JobStatusDto =
        getJson(url("downloads", jobId), JobStatusDto.serializer())

    suspend fun deleteJob(jobId: String) {
        val request = baseRequest(url("downloads", jobId)).delete().build()
        send(request).use { ensureSuccess(it) }
    }

    suspend fun lyrics(jobId: String): String {
        val request = baseRequest(url("downloads", jobId, "lyrics")).build()
        return send(request).use { response ->
            ensureSuccess(response)
            withContext(Dispatchers.IO) { readBody(response) }
        }
    }

    /**
     * Streams the finished file into [destination], resuming from its current length with a
     * `Range` request. Returns the final length; the caller verifies size and SHA-256.
     */
    suspend fun downloadFile(
        jobId: String,
        destination: File,
        onProgress: suspend (written: Long, total: Long?) -> Unit,
    ): Long {
        val offset = if (destination.exists()) destination.length() else 0L
        val request = baseRequest(url("downloads", jobId, "file"))
            .apply { if (offset > 0) header(HEADER_RANGE, "bytes=$offset-") }
            .build()
        return send(request).use { response ->
            when (response.code) {
                HTTP_RANGE_NOT_SATISFIABLE -> return@use offset
                HTTP_PARTIAL_CONTENT -> writeBody(response, destination, offset, onProgress)
                else -> {
                    ensureSuccess(response)
                    // the server ignored the range, start over
                    writeBody(response, destination, 0L, onProgress)
                }
            }
        }
    }

    private suspend fun writeBody(
        response: Response,
        destination: File,
        offset: Long,
        onProgress: suspend (Long, Long?) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        val body = response.body ?: throw ServerError.Malformed(IOException("empty body"))
        val total = body.contentLength().takeIf { it >= 0 }?.let { it + offset }
        var written = offset
        // write failures (disk full) stay plain IOExceptions, only reads are network errors
        destination.parentFile?.mkdirs()
        FileOutputStream(destination, offset > 0).use { out ->
            body.byteStream().use { input ->
                val buffer = ByteArray(ServerConfig.FILE_BUFFER_BYTES)
                while (true) {
                    ensureActive()
                    val read = try {
                        input.read(buffer)
                    } catch (err: IOException) {
                        throw ServerError.Network(err)
                    }
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    written += read
                    onProgress(written, total)
                }
            }
        }
        written
    }

    private suspend fun <T> getJson(url: HttpUrl, serializer: KSerializer<T>): T =
        send(baseRequest(url).build()).use { decode(it, serializer) }

    private fun url(vararg segments: String) = baseUrl.newBuilder()
        .addPathSegment(API_VERSION)
        .apply { segments.forEach { addPathSegment(it) } }
        .build()

    private fun baseRequest(url: HttpUrl) = Request.Builder()
        .url(url)
        .header(HEADER_APP_TOKEN, appToken)
        .header(HEADER_DEVICE_ID, deviceId)
        .header(HEADER_USER_AGENT, userAgent)

    private suspend fun send(request: Request): Response = try {
        client.newCall(request).await()
    } catch (err: IOException) {
        throw ServerError.Network(err)
    }

    private suspend fun <T> decode(response: Response, serializer: KSerializer<T>): T {
        ensureSuccess(response)
        val text = withContext(Dispatchers.IO) { readBody(response) }
        return try {
            json.decodeFromString(serializer, text)
        } catch (err: SerializationException) {
            throw ServerError.Malformed(err)
        } catch (err: IllegalArgumentException) {
            throw ServerError.Malformed(err)
        }
    }

    private suspend fun ensureSuccess(response: Response) {
        if (response.isSuccessful) return
        val text = withContext(Dispatchers.IO) {
            runCatching { response.body?.string() }.getOrNull()
        }
        throw toProblem(response, text)
    }

    private fun toProblem(response: Response, body: String?): ServerError.Problem {
        val problem = body
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(ProblemDto.serializer(), it) }.getOrNull() }
        val retryAfter = response.header(HEADER_RETRY_AFTER)?.trim()?.toLongOrNull()
            ?: response.header(HEADER_RATE_LIMIT_RESET)?.trim()?.toLongOrNull()
        return ServerError.Problem(
            httpStatus = response.code,
            code = problem?.code ?: ErrorCodes.HTTP_ERROR,
            detail = problem?.detail ?: response.message.ifBlank { null },
            retryable = problem?.retryable ?: (response.code >= 500 || response.code == HTTP_TOO_MANY_REQUESTS),
            retryAfterSeconds = retryAfter,
            requestId = problem?.requestId ?: response.header(HEADER_REQUEST_ID),
        )
    }

    private fun readBody(response: Response) = try {
        response.body?.string().orEmpty()
    } catch (err: IOException) {
        throw ServerError.Network(err)
    }

    companion object {
        private const val API_VERSION = "v1"
        const val HEADER_APP_TOKEN = "X-App-Token"
        const val HEADER_DEVICE_ID = "X-Device-Id"
        const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"
        const val HEADER_REQUEST_ID = "X-Request-ID"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_RANGE = "Range"
        private const val HEADER_RETRY_AFTER = "Retry-After"
        private const val HEADER_RATE_LIMIT_RESET = "RateLimit-Reset"
        private const val HTTP_PARTIAL_CONTENT = 206
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .cache(null)
                .connectTimeout(ServerConfig.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .readTimeout(ServerConfig.readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .writeTimeout(ServerConfig.writeTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .build()
        }
    }
}

// suspends on OkHttp's dispatcher instead of blocking a thread, and cancels the call with the coroutine
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { response.close() }
        }
    })
}
