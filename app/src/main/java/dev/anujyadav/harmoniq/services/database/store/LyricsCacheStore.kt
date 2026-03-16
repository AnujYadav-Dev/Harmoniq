package dev.anujyadav.harmoniq.services.database.store

import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.database.adapters.SQLiteKeyValueDatabaseAdapter

class LyricsCacheStore(val symphony: Symphony) {
    private val adapter = SQLiteKeyValueDatabaseAdapter(
        SQLiteKeyValueDatabaseAdapter.Transformer.AsString(),
        SQLiteKeyValueDatabaseAdapter.CacheOpenHelper(symphony.applicationContext, "lyrics", 1)
    )

    fun get(key: String) = adapter.get(key)
    fun put(key: String, value: String) = adapter.put(key, value)
    fun delete(key: String) = adapter.delete(key)
    fun delete(keys: Collection<String>) = adapter.delete(keys)
    fun keys() = adapter.keys()
    fun all() = adapter.all()
    fun clear() = adapter.clear()
}
