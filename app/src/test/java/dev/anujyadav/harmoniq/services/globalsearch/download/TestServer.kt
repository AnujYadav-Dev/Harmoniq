package dev.anujyadav.harmoniq.services.globalsearch.download

import dev.anujyadav.harmoniq.services.globalsearch.Fixtures
import dev.anujyadav.harmoniq.services.globalsearch.api.HarmoniqServerApi
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/** MockWebServer with per-route response queues; the last response of a route repeats. */
class TestServer {
    val server = MockWebServer()
    private val routes = mutableMapOf<String, ArrayDeque<(RecordedRequest) -> MockResponse>>()
    val requests = ConcurrentLinkedQueue<RecordedRequest>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val path = request.requestUrl!!.encodedPath
                val route = "${request.method} $path"
                val queue = synchronized(routes) { routes[route] } ?: return MockResponse().setResponseCode(404)
                val next = synchronized(queue) { if (queue.size > 1) queue.removeFirst() else queue.first() }
                return next(request)
            }
        }
        server.start()
    }

    fun on(method: String, path: String, vararg responses: (RecordedRequest) -> MockResponse) {
        synchronized(routes) { routes.getOrPut("$method $path") { ArrayDeque() }.addAll(responses) }
    }

    fun api() = HarmoniqServerApi(
        baseUrl = server.url("/"),
        appToken = "test-token",
        deviceId = "00000000-0000-4000-8000-000000000001",
        userAgent = "Harmoniq/test",
        client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
    )

    fun requestsTo(method: String, path: String) =
        requests.filter { it.method == method && it.requestUrl!!.encodedPath == path }

    fun shutdown() = server.shutdown()

    companion object {
        const val JOB_ID = "dl_01M6FVTTC1NVDK81GAY0VMDM99"
        const val VIDEO_ID = "4D7u5KF7SP8"
        const val JOB_PATH = "/v1/downloads/$JOB_ID"
        const val FILE_PATH = "/v1/downloads/$JOB_ID/file"

        val audio: ByteArray = ByteArray(300_000) { (it * 31 % 251).toByte() }
        val audioSha: String = sha256(audio)

        fun sha256(bytes: ByteArray) =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun json(body: String, code: Int = 200): (RecordedRequest) -> MockResponse = {
            MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
        }

        fun problem(code: String, status: Int, retryable: Boolean, retryAfter: Long? = null): (RecordedRequest) -> MockResponse = {
            MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/problem+json")
                .apply { retryAfter?.let { setHeader("Retry-After", it.toString()) } }
                .setBody("""{"type":"x","title":"t","status":$status,"code":"$code","detail":"d","request_id":"r","retryable":$retryable}""")
        }

        fun fixture(name: String) = json(Fixtures.read(name))

        // the real completed-job fixture, re-pointed at our test bytes
        fun completed(bytes: ByteArray = audio): (RecordedRequest) -> MockResponse = json(
            Fixtures.read("status_completed.json")
                .replace("\"size_bytes\":6286631", "\"size_bytes\":${bytes.size}")
                .replace("d9215c111228d22d9eaf9f9491570530371c95a412a7d9a089ef3ef8e4756e49", sha256(bytes))
        )

        fun fileBody(bytes: ByteArray = audio): (RecordedRequest) -> MockResponse = { request ->
            val range = request.getHeader("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull()
            when (range) {
                null -> MockResponse().setBody(Buffer().write(bytes))
                else -> MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $range-${bytes.size - 1}/${bytes.size}")
                    .setBody(Buffer().write(bytes.copyOfRange(range, bytes.size)))
            }
        }

        val noContent: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(204) }
    }
}

/** In-memory [DownloadSaver] that applies the same reuse-or-rename rule as the real ones. */
class FakeSaver : DownloadSaver {
    val files = linkedMapOf<String, ByteArray>()
    var failWith: Exception? = null

    override suspend fun save(source: File, fileName: String, mimeType: String, sha256: String): SavedFile {
        failWith?.let { throw it }
        val bytes = source.readBytes()
        for (attempt in 1..DownloadSaver.MAX_NAME_ATTEMPTS) {
            val name = FileNames.withCollisionSuffix(fileName, attempt)
            val existing = files[name]
            if (existing == null) {
                files[name] = bytes
                return SavedFile(uriOf(name), name, reused = false)
            }
            if (existing.contentEquals(bytes)) return SavedFile(uriOf(name), name, reused = true)
        }
        error("no name")
    }

    override suspend fun saveCompanion(bytes: ByteArray, fileName: String): SavedFile {
        files[fileName] = bytes
        return SavedFile(uriOf(fileName), fileName, reused = false)
    }

    override fun exists(uri: String) = files.containsKey(uri.removePrefix(URI_PREFIX))

    private fun uriOf(name: String) = "$URI_PREFIX$name"

    companion object {
        const val URI_PREFIX = "fake://"
    }
}

fun memoryRecords() = DownloadRecords(load = { null }, save = {})
