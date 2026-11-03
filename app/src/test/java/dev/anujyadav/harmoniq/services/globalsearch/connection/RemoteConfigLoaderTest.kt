package dev.anujyadav.harmoniq.services.globalsearch.connection

import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteConfigLoaderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun loader() = HttpRemoteConfigLoader(server.url("/config/global-search.json"), OkHttpClient(), "test")

    @Test
    fun parsesTheConfigFileAndIgnoresUnknownFields() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"version":1,"server_url":"https://music.example.com","app_token":"hq_x",
                   "enabled":true,"message":"Hello","future_field":42}"""
            )
        )
        val config = loader().load().getOrThrow()
        assertEquals(RemoteServerConfig(1, "https://music.example.com", "hq_x", true, "Hello"), config)
        assertEquals("test", server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun missingFieldsTakeDefaults() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        assertEquals(RemoteServerConfig(), loader().load().getOrThrow())
    }

    @Test
    fun httpErrorsAndBrokenJsonFail() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(loader().load().isFailure)
        server.enqueue(MockResponse().setBody("<html>not json</html>"))
        assertTrue(loader().load().isFailure)
    }

    @Test
    fun oversizedFilesAreRejected() = runBlocking {
        val padding = " ".repeat(ServerConfig.REMOTE_CONFIG_MAX_BYTES + 10)
        server.enqueue(MockResponse().setBody("{$padding}"))
        assertTrue(loader().load().isFailure)
    }
}
