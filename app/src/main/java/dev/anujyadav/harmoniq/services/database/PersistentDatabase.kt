package dev.anujyadav.harmoniq.services.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.database.store.PlaylistStore
import dev.anujyadav.harmoniq.services.groove.Playlist
import dev.anujyadav.harmoniq.utils.RoomConvertors

@Database(entities = [Playlist::class], version = 1)
@TypeConverters(RoomConvertors::class)
abstract class PersistentDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistStore

    companion object {
        fun create(symphony: Symphony) = Room
            .databaseBuilder(
                symphony.applicationContext,
                PersistentDatabase::class.java,
                "persistent"
            )
            .build()
    }
}
