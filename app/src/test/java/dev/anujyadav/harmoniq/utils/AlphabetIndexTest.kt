package dev.anujyadav.harmoniq.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlphabetIndexTest {
    private fun repeated(vararg texts: String) =
        texts.flatMap { text -> List(AlphabetIndex.MIN_ITEMS) { text } }

    @Test
    fun sectionsPointAtTheFirstItemOfEachLetter() {
        val sections = AlphabetIndex.build(repeated("apple", "banana", "cherry")) { it }
        assertEquals(
            listOf(
                AlphabetIndex.Section("A", 0),
                AlphabetIndex.Section("B", AlphabetIndex.MIN_ITEMS),
                AlphabetIndex.Section("C", AlphabetIndex.MIN_ITEMS * 2),
            ),
            sections,
        )
    }

    @Test
    fun followsAReversedList() {
        val sections = AlphabetIndex.build(repeated("cherry", "banana", "apple")) { it }
        assertEquals(listOf("C", "B", "A"), sections.map { it.label })
    }

    @Test
    fun aLetterThatReturnsKeepsItsFirstPosition() {
        val sections = AlphabetIndex.build(repeated("apple", "banana", "Apple", "cherry")) { it }
        assertEquals(listOf("A", "B", "C"), sections.map { it.label })
        assertEquals(0, sections.first().itemIndex)
    }

    @Test
    fun shortOrUniformListsGetNoIndex() {
        val tooShort = listOf("a", "b", "c", "d")
        assertTrue(AlphabetIndex.build(tooShort) { it }.isEmpty())
        assertTrue(AlphabetIndex.build(repeated("apple", "banana")) { it }.isEmpty())
    }

    @Test
    fun labels() {
        assertEquals("A", AlphabetIndex.labelOf("  apple"))
        assertEquals("É", AlphabetIndex.labelOf("école"))
        assertEquals("क", AlphabetIndex.labelOf("कल"))
        assertEquals(AlphabetIndex.OTHER_LABEL, AlphabetIndex.labelOf("99 problems"))
        assertEquals(AlphabetIndex.OTHER_LABEL, AlphabetIndex.labelOf("(intro)"))
        assertEquals(AlphabetIndex.OTHER_LABEL, AlphabetIndex.labelOf(""))
        assertEquals(AlphabetIndex.OTHER_LABEL, AlphabetIndex.labelOf(null))
    }
}
