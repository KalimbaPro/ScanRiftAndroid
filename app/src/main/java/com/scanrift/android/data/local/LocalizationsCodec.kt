package com.scanrift.android.data.local

import com.scanrift.android.data.remote.dto.CardLocalizationDto
import com.scanrift.android.domain.model.CardLocalization
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Stores `localizations` as the API's own JSON shape in one TEXT column.
 *
 * It is read on every catalogue emission but only ever looked up by language, so a
 * child table would buy nothing. Malformed JSON degrades to "no localisations".
 */
object LocalizationsCodec {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val serializer = MapSerializer(String.serializer(), CardLocalizationDto.serializer())

    fun encode(value: Map<String, CardLocalizationDto>?): String? =
        value?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(serializer, it) }

    fun decode(value: String?): Map<String, CardLocalization> {
        if (value.isNullOrBlank()) return emptyMap()
        val decoded = runCatching { json.decodeFromString(serializer, value) }.getOrNull() ?: return emptyMap()
        return decoded.mapValues { (_, dto) -> CardLocalization(dto.name, dto.imageUrl, dto.text) }
    }

    fun encodeDomain(value: Map<String, CardLocalization>): String? =
        encode(value.mapValues { (_, l) -> CardLocalizationDto(l.name, l.imageUrl, l.text) })
}
