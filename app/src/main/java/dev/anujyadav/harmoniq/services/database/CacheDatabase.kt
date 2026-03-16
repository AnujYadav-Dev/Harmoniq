package dev.anujyadav.harmoniq.services.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.database.store.SongCacheStore
import dev.anujyadav.harmoniq.services.groove.Song
import dev.anujyadav.harmoniq.utils.RoomConvertors

@Database(
    entities = [Song::class],
    version = 2,
    autoMigrations = [AutoMigration(1, 2, CacheDatabase.Migration1To2::class)]
)
@TypeConverters(RoomConvertors::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun songs(): SongCacheStore

    companion object {
        fun create(symphony: Symphony) = Room
            .databaseBuilder(
                symphony.applicationContext,
                CacheDatabase::class.java,
                "cache"
            )
            .build()
    }

    @DeleteColumn("songs", "minBitrate")
    @DeleteColumn("songs", "maxBitrate")
    @DeleteColumn("songs", "bitsPerSample")
    @DeleteColumn("songs", "samples")
    @DeleteColumn("songs", "codec")
    class Migration1To2 : AutoMigrationSpec
}
