package dev.anujyadav.harmoniq.services.groove

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SongSuggestionsPickTest {
    private val random = Random(42)

    @Test
    fun prefersScoredSongs() {
        val scored = (1..50).map { "s$it" to if (it <= 5) 10f else 0f }
        val picked = SongSuggestions.pick(scored, limit = 5, random = random)
        assertEquals((1..5).map { "s$it" }.toSet(), picked.toSet())
    }

    @Test
    fun fillsUpWhenNothingRelates() {
        val scored = (1..20).map { "s$it" to 0f }
        val picked = SongSuggestions.pick(scored, limit = 5, random = random)
        assertEquals(5, picked.size)
        assertEquals(5, picked.toSet().size)
    }

    @Test
    fun returnsWhatExistsWhenTooFew() {
        val scored = listOf("a" to 1f, "b" to 0f)
        val picked = SongSuggestions.pick(scored, limit = 5, random = random)
        assertEquals(setOf("a", "b"), picked.toSet())
    }

    @Test
    fun emptyCandidatesGiveNothing() {
        assertTrue(SongSuggestions.pick(emptyList(), limit = 5, random = random).isEmpty())
    }
}
