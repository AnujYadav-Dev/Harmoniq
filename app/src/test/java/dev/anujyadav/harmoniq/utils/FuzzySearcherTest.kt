package dev.anujyadav.harmoniq.utils

import dev.anujyadav.harmoniq.services.groove.repositories.SongRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FuzzySearcherTest {
    private data class Entry(val title: String, val artist: String)

    // mirrors how songs weigh their fields, weights must not affect matches()
    private val searcher = FuzzySearcher<Entry>(
        options = listOf(
            FuzzySearchOption({ compareString(it.title) }, 3),
            FuzzySearchOption({ compareString(it.artist) }),
        )
    )
    private val minScore = SongRepository.FILTER_MIN_SCORE

    @Test
    fun matchesTitleSubstringIgnoringCase() {
        assertTrue(searcher.matches("chal ke", Entry("Aa Chal Ke Tujhe", "Kishore Kumar"), minScore))
    }

    @Test
    fun matchesLowWeightField() {
        assertTrue(searcher.matches("kishore", Entry("Aa Chal Ke Tujhe", "Kishore Kumar"), minScore))
    }

    @Test
    fun rejectsUnrelatedTerms() {
        assertFalse(searcher.matches("metallica", Entry("Aa Chal Ke Tujhe", "Kishore Kumar"), minScore))
    }
}
