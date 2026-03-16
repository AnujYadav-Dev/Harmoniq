package dev.anujyadav.harmoniq.utils

import android.net.Uri
import androidx.room.TypeConverter
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomConvertors {
    @TypeConverter
    fun serializeUri(value: Uri) = value.toString()

    @TypeConverter
    fun deserializeUri(value: String) = Uri.parse(value)

    @TypeConverter
    fun serializeStringSet(value: Set<String>) = Json.encodeToString(value)

    @TypeConverter
    fun deserializeStringSet(value: String) = Json.decodeFromString<Set<String>>(value)

    @TypeConverter
    fun serializeStringList(value: List<String>) = Json.encodeToString(value)

    @TypeConverter
    fun deserializeStringList(value: String) = Json.decodeFromString<List<String>>(value)

    @TypeConverter
    fun serializeLocalDate(value: LocalDate) = value.toString()

    @TypeConverter
    fun deserializeLocalDate(value: String): LocalDate = LocalDate.parse(value)
}
