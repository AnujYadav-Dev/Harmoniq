package dev.anujyadav.harmoniq.metaphony

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioMetadataDatesTest {
    @Test
    fun fullDate() {
        assertEquals(LocalDate.of(2013, 5, 17), AudioMetadataDates.parse("2013-05-17"))
    }

    @Test
    fun yearMonth() {
        assertEquals(LocalDate.of(2013, 5, 1), AudioMetadataDates.parse("2013-05"))
    }

    @Test
    fun yearOnly() {
        // used to be dropped: LocalDate.parse("2013", "yyyy") always throws
        assertEquals(LocalDate.of(2013, 1, 1), AudioMetadataDates.parse("2013"))
        assertEquals(LocalDate.of(1999, 1, 1), AudioMetadataDates.parse(" 1999 "))
    }

    @Test
    fun timestampsFallBackToTheYear() {
        assertEquals(LocalDate.of(2001, 1, 1), AudioMetadataDates.parse("2001-03-12T00:00:00"))
        // an impossible month/day still has a usable year
        assertEquals(LocalDate.of(2013, 1, 1), AudioMetadataDates.parse("2013-13-45"))
    }

    @Test
    fun garbageIsNull() {
        assertNull(AudioMetadataDates.parse(""))
        assertNull(AudioMetadataDates.parse("unknown"))
        assertNull(AudioMetadataDates.parse("13"))
        assertNull(AudioMetadataDates.parse("20x3"))
    }
}
