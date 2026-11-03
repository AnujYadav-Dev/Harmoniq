package dev.anujyadav.harmoniq.services.database.store

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.anujyadav.harmoniq.services.globalsearch.watchlist.WatchListItem

@Dao
interface WatchListStore {
    @Upsert
    suspend fun upsert(vararg item: WatchListItem)

    @Query("DELETE FROM watch_list WHERE videoId = :videoId")
    suspend fun delete(videoId: String): Int

    @Query("SELECT * FROM watch_list ORDER BY addedAt DESC")
    suspend fun entries(): List<WatchListItem>
}
