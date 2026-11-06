package dev.anujyadav.harmoniq.services.globalsearch

import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearch.MetaState
import dev.anujyadav.harmoniq.services.globalsearch.connection.ConnectionState
import dev.anujyadav.harmoniq.services.globalsearch.connection.ServerConnection
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlobalSearchAvailabilityTest {
    private val ready = ConnectionState.Ready(
        ServerConnection("https://example.com".toHttpUrl(), "token", ServerConnection.Source.Custom),
        message = null,
    )

    @Test
    fun missingServerIsNotConfigured() {
        assertEquals(
            GlobalSearchAvailability.NotConfigured,
            GlobalSearchAvailability.from(ConnectionState.NotConfigured, MetaState.Unknown),
        )
    }

    @Test
    fun pausedServerKeepsItsMessage() {
        assertEquals(
            GlobalSearchAvailability.Paused("maintenance"),
            GlobalSearchAvailability.from(ConnectionState.Disabled("maintenance"), MetaState.Unknown),
        )
    }

    @Test
    fun aServerNotCheckedYetCountsAsAvailable() {
        listOf(MetaState.Unknown, MetaState.Loading).forEach {
            assertEquals(GlobalSearchAvailability.Available, GlobalSearchAvailability.from(ready, it))
        }
    }

    @Test
    fun onlyProblemsThatCanPassAreCheckedAgain() {
        assertTrue(GlobalSearchAvailability.Unreachable.canRecover)
        assertTrue(GlobalSearchAvailability.Rejected.canRecover)
        assertFalse(GlobalSearchAvailability.NotConfigured.canRecover)
        assertFalse(GlobalSearchAvailability.Available.canRecover)
    }
}
