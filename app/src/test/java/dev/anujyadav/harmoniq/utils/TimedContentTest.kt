package dev.anujyadav.harmoniq.utils

import dev.anujyadav.harmoniq.services.globalsearch.Fixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimedContentTest {
    private fun timeOf(line: String) = TimedContent.fromLyrics(line).pairs.single().first

    @Test
    fun hundredthsAreScaled() {
        // the standard LRC form: 12.34 s, not 12.034 s
        assertEquals(12_340L, timeOf("[00:12.34]line"))
        assertEquals(65_500L, timeOf("[01:05.5]line"))
    }

    @Test
    fun millisecondsStayMilliseconds() {
        assertEquals(33_210L, timeOf("[00:33.210]line"))
        assertEquals(7_005L, timeOf("[00:07.005]line"))
    }

    @Test
    fun extraPrecisionIsTruncated() {
        assertEquals(1_234L, timeOf("[00:01.2345]line"))
    }

    @Test
    fun emptyFractionIsZero() {
        assertEquals(42_000L, timeOf("[00:42.]line"))
    }

    @Test
    fun serverLyricsFixture() {
        val content = TimedContent.fromLyrics(Fixtures.read("lyrics_synced.lrc"))
        assertTrue(content.isSynced)
        assertEquals(0L to "♪", content.pairs[0])
        assertEquals(12_500L to "Paper lanterns drifting down the river", content.pairs[1])
        assertEquals(16_040L to "Every light a wish we never said", content.pairs[2])
    }

    @Test
    fun plainLyricsAreNotSynced() {
        assertFalse(TimedContent.fromLyrics("first line\nsecond line").isSynced)
    }
}
