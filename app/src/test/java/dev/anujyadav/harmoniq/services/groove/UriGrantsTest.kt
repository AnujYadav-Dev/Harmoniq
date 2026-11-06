package dev.anujyadav.harmoniq.services.groove

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.Symphony
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UriGrantsTest {
    private val folder = Uri.parse("content://docs/tree/music")
    private val removedFolder = Uri.parse("content://docs/tree/old")
    private val playlistFile = Uri.parse("content://docs/document/mix.m3u")
    private val deletedFile = Uri.parse("content://docs/document/gone.m3u")

    private fun localPlaylist(id: String, uri: Uri) = Playlist(id, id, emptyList(), uri, null)

    @Test
    fun onlyGrantsNothingUsesAreUnused() {
        assertEquals(
            listOf(removedFolder, deletedFile),
            UriGrants.findUnused(
                granted = listOf(folder, removedFolder, playlistFile, deletedFile),
                inUse = setOf(folder, playlistFile),
            ),
        )
    }

    @Test
    fun nothingGrantedLeavesNothingToRelease() {
        assertEquals(emptyList<Uri>(), UriGrants.findUnused(emptyList(), setOf(folder)))
    }

    @Test
    fun onlyUnusedGrantsAreReleased() = runBlocking {
        val symphony = Symphony(ApplicationProvider.getApplicationContext<Application>())
        val resolver = symphony.applicationContext.contentResolver
        listOf(folder, removedFolder, playlistFile, deletedFile).forEach {
            resolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        symphony.settings.mediaFolders.setValue(setOf(folder))
        symphony.database.playlists.insert(
            localPlaylist("first-copy", playlistFile),
            localPlaylist("second-copy", playlistFile),
        )

        symphony.groove.uriGrants.releaseUnused()

        assertEquals(
            setOf(folder, playlistFile),
            resolver.persistedUriPermissions.map { it.uri }.toSet(),
        )
    }
}
