package dev.anujyadav.harmoniq.services.groove

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SongSuggestionsRediscoverTest {
    private val all = listOf("a", "b", "c", "d")

    @Test
    fun prefersSongsOutsidePlaylists() {
        val candidates = SongSuggestions.rediscoverCandidates(all, setOf("a", "b"), setOf("c"))
        assertEquals(listOf("c", "d"), candidates)
    }

    @Test
    fun fallsBackToSongsNotPlayedLatelyWhenEverythingIsInPlaylists() {
        val candidates = SongSuggestions.rediscoverCandidates(all, all.toSet(), setOf("a", "b"))
        assertEquals(listOf("c", "d"), candidates)
    }

    @Test
    fun fallsBackToEverythingAsLastResort() {
        val candidates = SongSuggestions.rediscoverCandidates(all, all.toSet(), all.toSet())
        assertEquals(all, candidates)
    }
}
