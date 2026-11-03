package dev.anujyadav.harmoniq.services.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// JUnit 4 + Robolectric: the auto-migration 1 -> 2 adds the Watch List and keeps playlists
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistentDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PersistentDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsPlaylists() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO playlists (id, title, songPaths, uri, path) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>("p1", "Road trip", "[\"/music/a.mp3\",\"/music/b.mp3\"]", null, null),
            )
            close()
        }

        // validates the migrated schema against room-schemas/.../2.json
        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true)

        db.query("SELECT title, songPaths FROM playlists WHERE id = 'p1'").use {
            it.moveToFirst()
            assertEquals("Road trip", it.getString(0))
            assertEquals("[\"/music/a.mp3\",\"/music/b.mp3\"]", it.getString(1))
        }
        db.execSQL(
            "INSERT INTO watch_list (videoId, resultType, title, artists, album, durationSeconds, thumbnailUrl, " +
                    "watchUrl, isExplicit, addedAt, sourcePlaylistId) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("FGBhQbmPwH8", "song", "One More Time", "[\"Daft Punk\"]", null, 320, null, null, 0, 1L, null),
        )
        db.query("SELECT COUNT(*) FROM watch_list").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB_NAME = "persistent-migration-test"
    }
}
