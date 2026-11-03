package dev.anujyadav.harmoniq.services.globalsearch.download

import dev.anujyadav.harmoniq.services.globalsearch.api.DownloadFormat
import dev.anujyadav.harmoniq.services.globalsearch.api.ErrorCodes
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.FILE_PATH
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.JOB_ID
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.JOB_PATH
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.VIDEO_ID
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.audio
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.audioSha
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.completed
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.fileBody
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.fixture
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.noContent
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.problem
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DownloadPipelineTest {
    private lateinit var server: TestServer
    private lateinit var saver: FakeSaver
    private lateinit var records: DownloadRecords
    private lateinit var partDirectory: File
    private val progress = mutableListOf<DownloadProgress>()
    private val sleeps = mutableListOf<Duration>()
    private val request = DownloadRequest(VIDEO_ID, DownloadFormat.Opus, "Get Lucky", listOf("Daft Punk"))

    @BeforeEach
    fun setUp() {
        server = TestServer()
        saver = FakeSaver()
        records = memoryRecords()
        partDirectory = Files.createTempDirectory("hq-parts").toFile()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        partDirectory.deleteRecursively()
    }

    private fun pipeline() = DownloadPipeline(
        api = server.api(),
        saver = saver,
        records = records,
        partDirectory = partDirectory,
        onProgress = { progress.add(it) },
        sleep = { sleeps.add(it) },
    )

    private fun run(seed: String = "work-1:0") = runBlocking { pipeline().run(request, seed) }

    private fun happyServer() {
        server.on("POST", "/v1/downloads", fixture("submit.json"))
        server.on("GET", JOB_PATH, fixture("status_running.json"), fixture("status_downloading.json"), completed())
        server.on("GET", FILE_PATH, fileBody())
        server.on("DELETE", JOB_PATH, noContent)
    }

    @Test
    fun submitPollFetchVerifySaveRelease() {
        happyServer()
        val outcome = run()

        val saved = assertInstanceOf(DownloadOutcome.Saved::class.java, outcome)
        val name = "Daft Punk - Get Lucky (feat. Pharrell Williams and Nile Rodgers).opus"
        assertEquals(name, saved.file.displayName)
        assertTrue(audio.contentEquals(saver.files[name]))

        val submit = server.requestsTo("POST", "/v1/downloads").single()
        assertTrue(submit.body.readUtf8().contains("\"format\":\"opus\""))
        assertTrue(submit.getHeader("Idempotency-Key")!!.isNotBlank())
        assertEquals(1, server.requestsTo("DELETE", JOB_PATH).size)

        val record = records.get(request.key)!!
        assertEquals(DownloadRecord.State.Done, record.state)
        assertEquals(audioSha, record.sha256)
        assertNull(record.jobId)
        // the .part file is gone after saving
        assertTrue(partDirectory.listFiles().isNullOrEmpty())
        // server stages then local saving were reported
        assertTrue(progress.any { it is DownloadProgress.OnServer })
        assertTrue(progress.last() is DownloadProgress.Saving)
    }

    @Test
    fun sameAttemptReusesIdempotencyKey() {
        happyServer()
        run(seed = "work-1:0")
        records.update(request.key) { it?.copy(state = DownloadRecord.State.Active, jobId = null, sha256 = null) }
        run(seed = "work-1:0")
        run(seed = "work-1:1")
        val keys = server.requestsTo("POST", "/v1/downloads").map { it.getHeader("Idempotency-Key") }
        assertEquals(keys[0], keys[1])
        assertNotEquals(keys[1], keys[2])
    }

    @Test
    fun nonRetryableJobFailureFails() {
        server.on("POST", "/v1/downloads", fixture("submit.json"))
        server.on("GET", JOB_PATH, fixture("status_failed.json"))
        val outcome = run()

        assertEquals(DownloadOutcome.Failed(ErrorCodes.DURATION_LIMIT_EXCEEDED), outcome)
        assertEquals(DownloadRecord.State.Failed, records.get(request.key)!!.state)
        assertTrue(server.requestsTo("GET", FILE_PATH).isEmpty())
    }

    @Test
    fun retryableJobFailureRetriesWithAFreshJob() {
        server.on("POST", "/v1/downloads", fixture("submit.json"))
        server.on("GET", JOB_PATH, TestServer.json(
            """{"job_id":"$JOB_ID","status":"failed","progress":0.1,"error":{"code":"upstream_blocked","detail":"bot check","retryable":true},"attempts":3}"""
        ))
        val outcome = run()

        assertEquals(DownloadOutcome.Retry(ErrorCodes.UPSTREAM_BLOCKED), outcome)
        val record = records.get(request.key)!!
        assertEquals(DownloadRecord.State.Active, record.state)
        assertNull(record.jobId)
    }

    @Test
    fun jobLostOnServerIsResubmitted() {
        server.on("POST", "/v1/downloads", fixture("submit.json"))
        server.on(
            "GET", JOB_PATH,
            problem(ErrorCodes.JOB_NOT_FOUND, 404, false),
            completed(),
        )
        server.on("GET", FILE_PATH, fileBody())
        server.on("DELETE", JOB_PATH, noContent)
        val outcome = run()

        assertInstanceOf(DownloadOutcome.Saved::class.java, outcome)
        val submits = server.requestsTo("POST", "/v1/downloads")
        assertEquals(2, submits.size)
        // a resubmit must not be swallowed by the first submit's idempotency key
        assertNotEquals(submits[0].getHeader("Idempotency-Key"), submits[1].getHeader("Idempotency-Key"))
    }

    @Test
    fun resumesAJobFromAnEarlierAttempt() {
        records.update(request.key) {
            DownloadRecord(VIDEO_ID, "opus", "Get Lucky", state = DownloadRecord.State.Active, jobId = JOB_ID)
        }
        server.on("GET", JOB_PATH, completed())
        server.on("GET", FILE_PATH, fileBody())
        server.on("DELETE", JOB_PATH, noContent)
        assertInstanceOf(DownloadOutcome.Saved::class.java, run())
        assertTrue(server.requestsTo("POST", "/v1/downloads").isEmpty())
    }

    @Test
    fun corruptFileIsFetchedAgainOnce() {
        val corrupt = audio.copyOf().also { it[1000] = (it[1000] + 1).toByte() }
        server.on("POST", "/v1/downloads", fixture("submit.json"))
        server.on("GET", JOB_PATH, completed())
        server.on("GET", FILE_PATH, fileBody(corrupt), fileBody())
        server.on("DELETE", JOB_PATH, noContent)

        assertInstanceOf(DownloadOutcome.Saved::class.java, run())
        assertEquals(2, server.requestsTo("GET", FILE_PATH).size)
    }

    @Test
    fun persistentlyCorruptFileRetriesLater() {
        val corrupt = audio.copyOf().also { it[0] = (it[0] + 1).toByte() }
        server.on("POST", "/v1/downloads", fixture("submit.json"))
        server.on("GET", JOB_PATH, completed())
        server.on("GET", FILE_PATH, fileBody(corrupt))

        assertEquals(DownloadOutcome.Retry(LocalErrorCodes.INTEGRITY), run())
        assertTrue(saver.files.isEmpty())
    }

    @Test
    fun interruptedFileResumesWithRange() {
        File(partDirectory, "$audioSha.part").writeBytes(audio.copyOfRange(0, 120_000))
        happyServer()

        assertInstanceOf(DownloadOutcome.Saved::class.java, run())
        assertEquals("bytes=120000-", server.requestsTo("GET", FILE_PATH).single().getHeader("Range"))
    }

    @Test
    fun tooManyActiveJobsWaitsInline() {
        server.on(
            "POST", "/v1/downloads",
            problem(ErrorCodes.TOO_MANY_ACTIVE_JOBS, 429, true, retryAfter = 5),
            fixture("submit.json"),
        )
        server.on("GET", JOB_PATH, completed())
        server.on("GET", FILE_PATH, fileBody())
        server.on("DELETE", JOB_PATH, noContent)

        assertInstanceOf(DownloadOutcome.Saved::class.java, run())
        assertEquals(listOf(5L), sleeps.map { it.inWholeSeconds })
    }

    @Test
    fun longRateLimitIsLeftToWorkManagerBackoff() {
        server.on("POST", "/v1/downloads", problem(ErrorCodes.RATE_LIMITED, 429, true, retryAfter = 900))
        assertEquals(DownloadOutcome.Retry(ErrorCodes.RATE_LIMITED), run())
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun unauthorizedFails() {
        server.on("POST", "/v1/downloads", fixture("error_unauthorized.json").let { f ->
            { r: okhttp3.mockwebserver.RecordedRequest -> f(r).setResponseCode(401) }
        })
        assertEquals(DownloadOutcome.Failed(ErrorCodes.UNAUTHORIZED), run())
    }

    @Test
    fun networkErrorRetries() {
        server.shutdown()
        assertEquals(DownloadOutcome.Retry(LocalErrorCodes.NETWORK), run())
    }

    @Test
    fun storageErrorFails() {
        happyServer()
        saver.failWith = IOException("ENOSPC (No space left on device)")
        assertEquals(DownloadOutcome.Failed(LocalErrorCodes.STORAGE), run())
    }

    @Test
    fun sameSongSavedBeforeIsNotCopiedAgain() {
        happyServer()
        run()
        // a second download of the same song in another "format slot" with identical bytes
        val again = request.copy(format = DownloadFormat.M4a)
        val outcome = runBlocking { pipeline().run(again, "work-2:0") }

        val saved = assertInstanceOf(DownloadOutcome.Saved::class.java, outcome)
        assertTrue(saved.file.reused)
        assertEquals(1, saver.files.size)
        assertEquals(1, server.requestsTo("GET", FILE_PATH).size)
        assertEquals(2, server.requestsTo("DELETE", JOB_PATH).size)
    }

    @Test
    fun differentFileWithSameNameGetsSuffix() {
        val name = "Daft Punk - Get Lucky (feat. Pharrell Williams and Nile Rodgers).opus"
        saver.files[name] = byteArrayOf(1, 2, 3)
        happyServer()
        val saved = assertInstanceOf(DownloadOutcome.Saved::class.java, run())
        assertEquals("Daft Punk - Get Lucky (feat. Pharrell Williams and Nile Rodgers) (2).opus", saved.file.displayName)
    }

    @Test
    fun lrcIsSavedWhenAsked() {
        happyServer()
        server.on("GET", "$JOB_PATH/lyrics", TestServer.json("[00:12.500]Paper lanterns drifting down the river"))
        runBlocking { pipeline().run(request.copy(saveLrc = true), "work-1:0") }
        assertTrue(saver.files.keys.any { it.endsWith(").lrc") })
    }

    @Test
    fun cancelReleasesTheServerJob() {
        server.on("DELETE", JOB_PATH, noContent)
        records.update(request.key) {
            DownloadRecord(VIDEO_ID, "opus", "Get Lucky", state = DownloadRecord.State.Active, jobId = JOB_ID)
        }
        runBlocking { pipeline().cancel(request.key) }

        assertEquals(1, server.requestsTo("DELETE", JOB_PATH).size)
        val record = records.get(request.key)!!
        assertEquals(DownloadRecord.State.Cancelled, record.state)
        assertNull(record.jobId)
    }

    @Test
    fun failedDeleteDoesNotFailTheDownload() {
        server.on("POST", "/v1/downloads", fixture("submit.json"))
        server.on("GET", JOB_PATH, completed())
        server.on("GET", FILE_PATH, fileBody())
        server.on("DELETE", JOB_PATH, problem(ErrorCodes.INTERNAL_ERROR, 500, true))
        val outcome = run()
        assertInstanceOf(DownloadOutcome.Saved::class.java, outcome)
        assertFalse((outcome as DownloadOutcome.Saved).file.reused)
    }
}
