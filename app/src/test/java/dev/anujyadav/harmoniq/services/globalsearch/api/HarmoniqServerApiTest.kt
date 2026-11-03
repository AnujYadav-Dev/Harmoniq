package dev.anujyadav.harmoniq.services.globalsearch.api

import dev.anujyadav.harmoniq.services.globalsearch.Fixtures
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HarmoniqServerApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HarmoniqServerApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HarmoniqServerApi(
            baseUrl = server.url("/"),
            appToken = TOKEN,
            deviceId = DEVICE_ID,
            userAgent = "Harmoniq/test",
            client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build(),
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun json(name: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", if (code < 400) "application/json" else "application/problem+json")
        .setBody(Fixtures.read(name))

    @Test
    fun sendsAuthHeadersAndQuery() = runBlocking {
        server.enqueue(json("search_song.json"))
        val result = api.search("daft punk get lucky", SearchType.Song, 20)
        assertEquals("4D7u5KF7SP8", result.results.first().videoId)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/search", request.requestUrl!!.encodedPath)
        assertEquals("daft punk get lucky", request.requestUrl!!.queryParameter("q"))
        assertEquals("song", request.requestUrl!!.queryParameter("type"))
        assertEquals("20", request.requestUrl!!.queryParameter("limit"))
        assertEquals(TOKEN, request.getHeader("X-App-Token"))
        assertEquals(DEVICE_ID, request.getHeader("X-Device-Id"))
        assertEquals("Harmoniq/test", request.getHeader("User-Agent"))
    }

    @Test
    fun pathSegmentsAreEscaped() = runBlocking {
        server.enqueue(json("track.json"))
        api.track("4D7u5KF7SP8")
        assertEquals("/v1/videos/4D7u5KF7SP8/track", server.takeRequest().path)
    }

    @Test
    fun submitSendsBodyAndIdempotencyKey() = runBlocking {
        server.enqueue(json("submit.json", 202))
        val submit = api.submitDownload(
            DownloadRequestDto("4D7u5KF7SP8", DownloadFormat.Opus.apiValue, DownloadOverridesDto("https://yt3.googleusercontent.com/x")),
            idempotencyKey = "key-1",
        )
        assertTrue(submit.jobId.startsWith("dl_"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("key-1", request.getHeader("Idempotency-Key"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = request.body.readUtf8()
        assertEquals(
            """{"video_id":"4D7u5KF7SP8","format":"opus","metadata":{"artwork_url":"https://yt3.googleusercontent.com/x"}}""",
            body,
        )
    }

    @Test
    fun problemJsonBecomesTypedError() = runBlocking {
        server.enqueue(json("error_unauthorized.json", 401))
        val error = assertThrows<ServerError.Problem> { runBlocking { api.meta() } }
        assertEquals(401, error.httpStatus)
        assertEquals(ErrorCodes.UNAUTHORIZED, error.code)
        assertEquals(false, error.retryable)
        assertEquals("097a5cba291e47a5", error.requestId)
    }

    @Test
    fun rateLimitReadsRetryAfter() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "41")
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type":"x","title":"Too many requests","status":429,"code":"rate_limited","detail":"Slow down.","request_id":"r1","retryable":true}""")
        )
        val error = assertThrows<ServerError.Problem> { runBlocking { api.search("x", SearchType.Song, 1) } }
        assertEquals(ErrorCodes.RATE_LIMITED, error.code)
        assertEquals(41L, error.retryAfterSeconds)
        assertTrue(error.retryable)
    }

    @Test
    fun httpErrorWithoutProblemBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))
        val error = assertThrows<ServerError.Problem> { runBlocking { api.meta() } }
        assertEquals(502, error.httpStatus)
        assertEquals(ErrorCodes.HTTP_ERROR, error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun malformedJson() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"not":"meta"}"""))
        assertThrows<ServerError.Malformed> { runBlocking { api.meta() } }
        Unit
    }

    @Test
    fun networkFailure() {
        server.shutdown()
        val error = assertThrows<ServerError> { runBlocking { api.meta() } }
        assertInstanceOf(ServerError.Network::class.java, error)
    }

    @Test
    fun deleteAcceptsNoContent() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.deleteJob("dl_1")
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/v1/downloads/dl_1", request.path)
    }

    @Test
    fun fileDownloadFromScratch() = runBlocking {
        val content = ByteArray(200_000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(content)))
        val destination = tempFile()
        var lastReported = 0L
        val length = api.downloadFile("dl_1", destination) { written, _ -> lastReported = written }

        assertEquals(content.size.toLong(), length)
        assertEquals(content.size.toLong(), lastReported)
        assertTrue(content.contentEquals(destination.readBytes()))
        assertNull(server.takeRequest().getHeader("Range"))
    }

    @Test
    fun fileDownloadResumesWithRange() = runBlocking {
        val content = ByteArray(10_000) { (it % 7).toByte() }
        val destination = tempFile().apply { writeBytes(content.copyOfRange(0, 4_000)) }
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 4000-9999/10000")
                .setBody(Buffer().write(content.copyOfRange(4_000, content.size)))
        )
        var reportedTotal: Long? = null
        val length = api.downloadFile("dl_1", destination) { _, total -> reportedTotal = total }

        assertEquals("bytes=4000-", server.takeRequest().getHeader("Range"))
        assertEquals(10_000L, length)
        assertEquals(10_000L, reportedTotal)
        assertTrue(content.contentEquals(destination.readBytes()))
    }

    @Test
    fun fileDownloadRestartsWhenRangeIgnored() = runBlocking {
        val content = ByteArray(5_000) { 1 }
        val destination = tempFile().apply { writeBytes(ByteArray(3_000) { 9 }) }
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(content)))
        val length = api.downloadFile("dl_1", destination) { _, _ -> }

        assertEquals(5_000L, length)
        assertTrue(content.contentEquals(destination.readBytes()))
    }

    @Test
    fun fileNotReadyIsAProblem() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(409).setHeader("Content-Type", "application/problem+json")
                .setBody("""{"code":"job_not_ready","status":409,"retryable":true}""")
        )
        val error = assertThrows<ServerError.Problem> {
            runBlocking { api.downloadFile("dl_1", tempFile()) { _, _ -> } }
        }
        assertEquals(ErrorCodes.JOB_NOT_READY, error.code)
    }

    private fun tempFile(): File = Files.createTempFile("hq", ".part").toFile().apply {
        delete()
        deleteOnExit()
    }

    companion object {
        private const val TOKEN = "test-token"
        private const val DEVICE_ID = "00000000-0000-4000-8000-000000000001"
    }
}
