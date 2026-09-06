package com.scanrift.android.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room type converters.
 *
 * Dates are stored as epoch millis and converted at the domain boundary rather than
 * here, which keeps the exported schema JSON free of platform date types.
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        JSON.encodeToString(LIST_SERIALIZER, value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { JSON.decodeFromString(LIST_SERIALIZER, value) }.getOrDefault(emptyList())
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val LIST_SERIALIZER = ListSerializer(String.serializer())
    }
}
