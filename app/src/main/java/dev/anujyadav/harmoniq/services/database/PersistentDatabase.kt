package dev.anujyadav.harmoniq.services.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.database.store.PlaylistStore
import dev.anujyadav.harmoniq.services.database.store.WatchListStore
import dev.anujyadav.harmoniq.services.globalsearch.watchlist.WatchListItem
import dev.anujyadav.harmoniq.services.groove.Playlist
import dev.anujyadav.harmoniq.utils.RoomConvertors

// v2 adds the Global Search Watch List; a new table needs no migration spec
@Database(
    entities = [Playlist::class, WatchListItem::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(RoomConvertors::class)
abstract class PersistentDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistStore
    abstract fun watchList(): WatchListStore

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
