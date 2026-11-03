package dev.anujyadav.harmoniq.services.globalsearch.connection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import dev.anujyadav.harmoniq.services.globalsearch.api.HarmoniqServerApi
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// JUnit 4 + Robolectric (run on the JUnit 5 platform by the vintage engine)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerConnectionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // the Android Keystore isn't available on the JVM; the cipher is tested on devices
    private class ReversingCipher : SecretCipher {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(encoded: String) = encoded.removePrefix("enc:").reversed()
    }

    private class FakeLoader(var next: Result<RemoteServerConfig>) : RemoteConfigLoader {
        var calls = 0
        override suspend fun load(): Result<RemoteServerConfig> {
            calls++
            return next
        }
    }

    private var now = 1_000_000_000L
    private lateinit var store: ServerConnectionStore
    private lateinit var loader: FakeLoader

    private val remoteA = RemoteServerConfig(serverUrl = "https://a.example.com", appToken = "hq_a")
    private val remoteB = RemoteServerConfig(serverUrl = "https://b.example.com", appToken = "hq_b")

    @Before
    fun setUp() {
        val preferences = context.getSharedPreferences("connection-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = ServerConnectionStore(preferences, ReversingCipher())
        loader = FakeLoader(Result.success(remoteA))
    }

    private fun connections(builtInUrl: String = "", builtInToken: String = "") = ServerConnections(
        store = store,
        loader = loader,
        builtInUrl = builtInUrl,
        builtInToken = builtInToken,
        allowCleartext = false,
        apiFactory = { HarmoniqServerApi(it.baseUrl, it.appToken, "device", "test") },
        clock = { now },
    )

    private fun ServerConnections.token() = connection?.appToken

    @Test
    fun startsFromBuiltInValuesAndSwitchesToTheConfigFile() = runBlocking {
        val connections = connections("https://built.example.com", "hq_built")
        assertEquals("hq_built", connections.token())
        assertTrue(connections.refreshRemote())
        assertEquals("hq_a", connections.token())
        assertEquals(ServerConnection.Source.Remote, connections.connection?.source)
    }

    @Test
    fun freshConfigIsNotReloadedUntilItIsOld() = runBlocking {
        val connections = connections()
        connections.refreshRemote()
        now += ServerConfig.remoteConfigMinRefresh.inWholeMilliseconds * 2
        assertFalse(connections.refreshRemote())
        assertEquals(1, loader.calls)

        now += ServerConfig.remoteConfigMaxAge.inWholeMilliseconds
        loader.next = Result.success(remoteB)
        assertTrue(connections.refreshRemote())
        assertEquals("hq_b", connections.token())
    }

    @Test
    fun forcedRefreshesAreSpacedOut() = runBlocking {
        val connections = connections()
        connections.refreshRemote()
        loader.next = Result.success(remoteB)
        assertFalse("too soon after the last attempt", connections.refreshRemote(force = true))
        assertEquals(1, loader.calls)

        now += ServerConfig.remoteConfigMinRefresh.inWholeMilliseconds + 1
        assertTrue(connections.refreshRemote(force = true))
        assertEquals("hq_b", connections.token())
    }

    @Test
    fun failedDownloadKeepsTheLastGoodConfig() = runBlocking {
        val first = connections()
        first.refreshRemote()
        now += ServerConfig.remoteConfigMaxAge.inWholeMilliseconds + 1
        loader.next = Result.failure(IOException("offline"))
        assertFalse(first.refreshRemote())
        assertEquals("hq_a", first.token())
        assertEquals("offline", first.remoteStatus.value.lastError)
        // and it survives a restart (a new instance reads the saved copy)
        assertEquals("hq_a", connections().token())
    }

    @Test
    fun customServerIgnoresTheConfigFileAndStoresTheTokenEncrypted() = runBlocking {
        val connections = connections()
        connections.refreshRemote()
        connections.setMode(ServerMode.Custom)
        assertNull(connections.connection)

        connections.saveCustom("https://mine.example.com", "hq_mine")
        assertEquals("hq_mine", connections.token())
        assertTrue(connections.hasCustomToken.value)
        val raw = context.getSharedPreferences("connection-test", Context.MODE_PRIVATE).all.values
        assertFalse("token must not be stored in plain text", raw.any { it == "hq_mine" })

        // saving only the URL keeps the token
        connections.saveCustom("https://mine2.example.com", token = null)
        assertEquals("hq_mine", connections.token())
        assertEquals("https://mine2.example.com/", connections.connection?.baseUrl.toString())

        val calls = loader.calls
        assertFalse(connections.refreshRemote(force = true))
        assertEquals(calls, loader.calls)

        connections.setMode(ServerMode.Default)
        assertEquals("hq_a", connections.token())
    }

    @Test
    fun apiClientIsReusedUntilTheConnectionChanges() = runBlocking {
        val connections = connections("https://built.example.com", "hq_built")
        val first = connections.api()
        assertSame(first, connections.api())
        connections.refreshRemote()
        assertNotSame(first, connections.api())
    }

    @Test
    fun testRejectsInvalidUrls() = runBlocking {
        val connections = connections()
        assertTrue(connections.test("not a url", "hq").isFailure)
        assertFalse(connections.isValidServerUrl("http://insecure.example.com"))
        assertTrue(connections.isValidServerUrl("https://ok.example.com"))
    }
}
