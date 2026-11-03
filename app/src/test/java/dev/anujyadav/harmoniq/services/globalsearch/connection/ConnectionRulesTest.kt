package dev.anujyadav.harmoniq.services.globalsearch.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRulesTest {
    private fun default(
        remote: RemoteServerConfig?,
        builtInUrl: String = "",
        builtInToken: String = "",
        allowCleartext: Boolean = false,
    ) = ConnectionRules.resolve(ServerMode.Default, null, null, remote, builtInUrl, builtInToken, allowCleartext)

    private fun ready(state: ConnectionState) = (state as ConnectionState.Ready).connection

    @Test
    fun remoteConfigWinsOverBuiltInValues() {
        val state = default(
            RemoteServerConfig(serverUrl = "https://new.example.com", appToken = "hq_new", message = "  Hi  "),
            builtInUrl = "https://old.example.com",
            builtInToken = "hq_old",
        )
        val connection = ready(state)
        assertEquals("https://new.example.com/", connection.baseUrl.toString())
        assertEquals("hq_new", connection.appToken)
        assertEquals(ServerConnection.Source.Remote, connection.source)
        assertEquals("Hi", (state as ConnectionState.Ready).message)
    }

    @Test
    fun emptyOrUnreadableRemoteFallsBackToBuiltIn() {
        val builtIn = { remote: RemoteServerConfig? ->
            ready(default(remote, "https://built.example.com", "hq_built")).source
        }
        assertEquals(ServerConnection.Source.BuiltIn, builtIn(null))
        assertEquals(ServerConnection.Source.BuiltIn, builtIn(RemoteServerConfig(serverUrl = "", appToken = "")))
        assertEquals(ServerConnection.Source.BuiltIn, builtIn(RemoteServerConfig(serverUrl = "https://x.com", appToken = " ")))
        // a file format newer than this app is ignored instead of half-applied
        assertEquals(
            ServerConnection.Source.BuiltIn,
            builtIn(RemoteServerConfig(version = 2, serverUrl = "https://x.com", appToken = "hq")),
        )
    }

    @Test
    fun disabledRemotePausesTheDefaultServerEvenWithBuiltInValues() {
        val state = default(RemoteServerConfig(enabled = false, message = "Maintenance"), "https://b.com", "hq")
        assertEquals(ConnectionState.Disabled("Maintenance"), state)
    }

    @Test
    fun nothingKnownIsNotConfigured() {
        assertEquals(ConnectionState.NotConfigured, default(null))
        assertEquals(ConnectionState.NotConfigured, default(RemoteServerConfig()))
    }

    @Test
    fun plainHttpOnlyWhenCleartextIsAllowed() {
        val remote = RemoteServerConfig(serverUrl = "http://192.168.1.5:8000", appToken = "hq")
        assertEquals(ConnectionState.NotConfigured, default(remote))
        assertEquals("http://192.168.1.5:8000/", ready(default(remote, allowCleartext = true)).baseUrl.toString())
        assertNull(ConnectionRules.parseServerUrl("not a url", allowCleartext = true))
        assertNull(ConnectionRules.parseServerUrl("  ", allowCleartext = true))
    }

    @Test
    fun customModeUsesOnlyTheUsersServer() {
        val remote = RemoteServerConfig(serverUrl = "https://default.example.com", appToken = "hq_default")
        val custom = ConnectionRules.resolve(
            ServerMode.Custom, " https://mine.example.com ", " hq_mine ", remote, "", "", allowCleartext = false,
        )
        val connection = ready(custom)
        assertEquals("https://mine.example.com/", connection.baseUrl.toString())
        assertEquals("hq_mine", connection.appToken)
        assertEquals(ServerConnection.Source.Custom, connection.source)
        // the default server's pause doesn't affect someone using their own server
        val paused = ConnectionRules.resolve(
            ServerMode.Custom, "https://mine.example.com", "hq", RemoteServerConfig(enabled = false), "", "", false,
        )
        assertTrue(paused is ConnectionState.Ready)
        assertEquals(
            ConnectionState.NotConfigured,
            ConnectionRules.resolve(ServerMode.Custom, "https://mine.example.com", "", remote, "", "", false),
        )
    }

    @Test
    fun longMessagesAreCut() {
        val message = "x".repeat(ConnectionRules.MAX_MESSAGE_LENGTH + 50)
        val state = default(RemoteServerConfig(serverUrl = "https://a.com", appToken = "hq", message = message))
        assertEquals(ConnectionRules.MAX_MESSAGE_LENGTH, (state as ConnectionState.Ready).message?.length)
    }
}
