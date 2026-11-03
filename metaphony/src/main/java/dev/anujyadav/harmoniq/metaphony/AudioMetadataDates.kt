package dev.anujyadav.harmoniq.metaphony

import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

// kept apart from AudioMetadataParser, whose companion loads the native library
internal object AudioMetadataDates {
    private const val YEAR_LENGTH = 4

    /**
     * Parses a `DATE` tag: `2013-05-17`, `2013-05` or `2013` (a year-only date is the first of
     * January). Anything after the year that isn't a valid date, e.g. `2013-05-17T00:00:00`,
     * falls back to the year.
     */
    fun parse(text: String): LocalDate? {
        val value = text.trim()
        return runCatching { LocalDate.parse(value) }.getOrNull()
            ?: runCatching { YearMonth.parse(value).atDay(1) }.getOrNull()
            ?: value.take(YEAR_LENGTH)
                .takeIf { it.length == YEAR_LENGTH && it.all(Char::isDigit) }
                ?.let { runCatching { Year.parse(it).atDay(1) }.getOrNull() }
    }
}
