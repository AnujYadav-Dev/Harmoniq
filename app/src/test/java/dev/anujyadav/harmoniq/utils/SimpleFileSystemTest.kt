package dev.anujyadav.harmoniq.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleFileSystemTest {
    private val root = SimpleFileSystem.Folder()
    private val song = SimplePath("primary:Music/Album/song.mp3")

    @Test
    fun ensuringAKnownFileReturnsTheSameOne() {
        val file = root.ensureChildFile(song).apply { data = "id" }
        assertSame(file, root.ensureChildFile(song))
        assertEquals("id", root.ensureChildFile(song).data)
    }

    @Test
    fun removingTheLastFileAlsoRemovesItsEmptyFolders() {
        root.ensureChildFile(song)
        root.removeChildFile(song)
        assertTrue(root.isEmpty)
    }

    @Test
    fun foldersWithOtherFilesAreKept() {
        root.ensureChildFile(song)
        root.ensureChildFile(SimplePath("primary:Music/Album/other.mp3"))
        root.removeChildFile(song)
        assertFalse(root.isEmpty)
        assertEquals(1, (root.children.values.single() as SimpleFileSystem.Folder).children.size)
    }

    @Test
    fun aFileThePredicateRejectsIsKept() {
        root.ensureChildFile(song).data = "new-id"
        root.removeChildFile(song) { it.data == "old-id" }
        assertEquals("new-id", root.ensureChildFile(song).data)
    }

    @Test
    fun removingAnUnknownFileDoesNothing() {
        root.ensureChildFile(song)
        root.removeChildFile(SimplePath("primary:Elsewhere/song.mp3"))
        assertFalse(root.isEmpty)
    }
}
