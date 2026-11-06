package dev.anujyadav.harmoniq.utils

import java.util.Locale

object AlphabetIndex {
    data class Section(val label: String, val itemIndex: Int)

    const val OTHER_LABEL = "#"

    // shorter lists are quick to scroll, and a couple of letters isn't worth the space
    const val MIN_ITEMS = 30

    // tiles and collections take much more room per item than a row of songs
    const val MIN_COLLECTION_ITEMS = 12
    const val MIN_SECTIONS = 3

    /**
     * Sections in the order of [items], so it holds for reversed and filtered lists, and
     * only letters that have items are listed. A letter that shows up again further down
     * (sorting is by code point, so `É` follows `Z`) keeps its first position.
     */
    fun <T> build(items: List<T>, minItems: Int = MIN_ITEMS, textOf: (T) -> String?): List<Section> {
        if (items.size < minItems) {
            return emptyList()
        }
        val seen = mutableSetOf<String>()
        val sections = mutableListOf<Section>()
        items.forEachIndexed { index, item ->
            val label = labelOf(textOf(item))
            if (seen.add(label)) {
                sections.add(Section(label, index))
            }
        }
        return if (sections.size < MIN_SECTIONS) emptyList() else sections
    }

    fun labelOf(text: String?): String {
        val trimmed = text?.trimStart()
        if (trimmed.isNullOrEmpty()) {
            return OTHER_LABEL
        }
        val codePoint = trimmed.codePointAt(0)
        return when {
            Character.isLetter(codePoint) -> String(Character.toChars(codePoint))
                .uppercase(Locale.ROOT)

            else -> OTHER_LABEL
        }
    }
}
