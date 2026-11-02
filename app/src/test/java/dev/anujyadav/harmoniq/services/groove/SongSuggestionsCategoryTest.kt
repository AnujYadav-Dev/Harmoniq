package dev.anujyadav.harmoniq.services.groove

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SongSuggestionsCategoryTest {
    @Test
    fun keysRoundTrip() {
        listOf(
            SongSuggestions.Category.ForYou,
            SongSuggestions.Category.Rediscover,
            SongSuggestions.Category.Genre("Classical: Hindustani"),
        ).forEach {
            assertEquals(it, SongSuggestions.Category.fromKey(it.key))
        }
    }

    @Test
    fun unknownKeysAreIgnored() {
        assertNull(SongSuggestions.Category.fromKey(null))
        assertNull(SongSuggestions.Category.fromKey("something_else"))
    }
}
