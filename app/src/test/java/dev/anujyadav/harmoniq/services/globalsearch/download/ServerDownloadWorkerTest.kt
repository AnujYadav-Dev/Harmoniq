package dev.anujyadav.harmoniq.services.globalsearch.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import dev.anujyadav.harmoniq.services.globalsearch.api.DownloadFormat
import dev.anujyadav.harmoniq.services.globalsearch.api.ErrorCodes
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.FILE_PATH
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.JOB_PATH
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.VIDEO_ID
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.completed
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.fileBody
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.fixture
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.noContent
import dev.anujyadav.harmoniq.services.globalsearch.download.TestServer.Companion.problem
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// JUnit 4 + Robolectric (run on the JUnit 5 platform by the vintage engine)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerDownloadWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: TestServer
    private lateinit var saver: FakeSaver
    private lateinit var records: DownloadRecords
    private lateinit var partDirectory: File
    private val request = DownloadRequest(VIDEO_ID, DownloadFormat.Opus, "Get Lucky", listOf("Daft Punk"))

    @Before
    fun setUp() {
        // the progress notification's Cancel action needs a WorkManager instance
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        server = TestServer()
        saver = FakeSaver()
        records = memoryRecords()
        partDirectory = Files.createTempDirectory("hq-worker").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        partDirectory.deleteRecursively()
    }

    private fun worker(runAttemptCount: Int = 0, withApi: Boolean = true): ServerDownloadWorker {
        val dependencies = ServerDownloadWorker.Dependencies(
            api = if (withApi) server.api() else null,
            saver = saver,
            records = records,
            partDirectory = partDirectory,
            notifications = DownloadNotifications(context),
            sleep = {},
        )
        return TestListenableWorkerBuilder<ServerDownloadWorker>(context)
            .setInputData(ServerDownloadWorker.inputData(request))
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = ServerDownloadWorker(appContext, workerParameters, dependencies)
            })
            .build()
    }

    @Test
    fun inputDataRoundTrips() {
        val full = request.copy(artworkUrl = "https://yt3.googleusercontent.com/a", thumbnailUrl = "t", saveLrc = true)
        assertEquals(full, ServerDownloadWorker.requestFrom(ServerDownloadWorker.inputData(full)))
    }

    @Test
    fun successSavesAndReturnsTheUri() = runBlocking {
        server.on("POST", "/v1/downloads", fixture("submit.json"))
        server.on("GET", JOB_PATH, fixture("status_downloading.json"), completed())
        server.on("GET", FILE_PATH, fileBody())
        server.on("DELETE", JOB_PATH, noContent)

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, saver.files.size)
        assertTrue(result.outputData.getString(ServerDownloadWorker.KEY_SAVED_URI)!!.startsWith(FakeSaver.URI_PREFIX))
        assertEquals(DownloadRecord.State.Done, records.get(request.key)!!.state)
    }

    @Test
    fun serverBusyRetries() = runBlocking {
        server.on("POST", "/v1/downloads", problem(ErrorCodes.QUEUE_FULL, 503, true, retryAfter = 600))
        val result = worker().doWork()
        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(DownloadRecord.State.Active, records.get(request.key)!!.state)
    }

    @Test
    fun lastAttemptGivesUp() = runBlocking {
        server.on("POST", "/v1/downloads", problem(ErrorCodes.UPSTREAM_ERROR, 502, true))
        val result = worker(runAttemptCount = ServerConfig.MAX_RUN_ATTEMPTS - 1).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(ErrorCodes.UPSTREAM_ERROR, result.outputData.getString(ServerDownloadWorker.KEY_ERROR_CODE))
        assertEquals(DownloadRecord.State.Failed, records.get(request.key)!!.state)
    }

    @Test
    fun clientErrorFailsImmediately() = runBlocking {
        server.on("POST", "/v1/downloads", fixture("submit.json"))
        server.on("GET", JOB_PATH, fixture("status_failed.json"))
        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(ErrorCodes.DURATION_LIMIT_EXCEEDED, result.outputData.getString(ServerDownloadWorker.KEY_ERROR_CODE))
    }

    @Test
    fun networkLossRetries() = runBlocking {
        server.shutdown()
        assertTrue(worker().doWork() is ListenableWorker.Result.Retry)
    }

    @Test
    fun jobLostAfterServerRestartIsResubmitted() = runBlocking {
        server.on("POST", "/v1/downloads", fixture("submit.json"))
        server.on("GET", JOB_PATH, problem(ErrorCodes.JOB_NOT_FOUND, 404, false), completed())
        server.on("GET", FILE_PATH, fileBody())
        server.on("DELETE", JOB_PATH, noContent)

        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        assertEquals(2, server.requestsTo("POST", "/v1/downloads").size)
    }

    @Test
    fun unconfiguredBuildFails() = runBlocking {
        val result = worker(withApi = false).doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(ErrorCodes.UNAUTHORIZED, result.outputData.getString(ServerDownloadWorker.KEY_ERROR_CODE))
    }
}
