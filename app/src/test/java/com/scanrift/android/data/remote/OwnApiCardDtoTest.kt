package com.scanrift.android.data.remote

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.LocalizationsCodec
import com.scanrift.android.data.local.mapper.toDomain
import com.scanrift.android.data.remote.dto.CardDto
import com.scanrift.android.data.remote.dto.LegalityResponseDto
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.fake.AkaliFixture
import kotlinx.serialization.json.Json
import org.junit.Test

/** The own API's optional fields: decoded when present, harmless when absent. */
class OwnApiCardDtoTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private val radiance = """
    {
      "id": "0f1e2d3c4b5a69788796a5b4", "name": "Riven, Broken Blade", "riftbound_id": "rad-010-167",
      "collector_number": 10,
      "classification": {"type": "Unit", "supertype": "Champion", "rarity": "Rare", "domain": ["Fury"]},
      "set": {"set_id": "RAD", "label": "Radiance"},
      "media": {"image_url": "https://x/en.png"},
      "metadata": {"clean_name": "Riven Broken Blade"},
      "banned": true,
      "banned_formats": ["standard", "2v2"],
      "errata": "New text",
      "errata_date": "2026-10-09",
      "localizations": {
        "zh": {"name": "锐雯", "image_url": "https://x/zh.png", "text": "中文"},
        "ko": {"name": "리븐", "image_url": "https://x/ko.png"}
      }
    }
    """

    @Test
    fun `new fields decode and reach the domain card`() {
        val card = json.decodeFromString<CardDto>(radiance).toEntity().toDomain()

        assertThat(card.banned).isTrue()
        assertThat(card.bannedFormats).containsExactly("standard", "2v2").inOrder()
        assertThat(card.errata).isEqualTo("New text")
        assertThat(card.errataDate).isEqualTo("2026-10-09")
        assertThat(card.setLabel).isEqualTo("Radiance")
        assertThat(card.publicCode).isEqualTo("RAD-010/167")
        assertThat(card.localizations.getValue("zh").name).isEqualTo("锐雯")
        assertThat(card.imageUrl(CardLanguage.ZH)).isEqualTo("https://x/zh.png")
        assertThat(card.imageUrl(CardLanguage.KO)).isEqualTo("https://x/ko.png")
        // No French localisation: the English image is the fallback.
        assertThat(card.imageUrl(CardLanguage.FR)).isEqualTo("https://x/en.png")
    }

    @Test
    fun `a riftcodex card without the new fields decodes as legal with no errata`() {
        val card = AkaliFixture.decode().first().toEntity().toDomain()

        assertThat(card.banned).isFalse()
        assertThat(card.bannedFormats).isEmpty()
        assertThat(card.errata).isNull()
        assertThat(card.localizations).isEmpty()
        assertThat(card.imageUrl(CardLanguage.ZH)).isEqualTo(card.imageUrl)
    }

    @Test
    fun `explicit nulls decode like absence`() {
        val dto = json.decodeFromString<CardDto>(
            radiance.replace("\"banned_formats\": [\"standard\", \"2v2\"]", "\"banned_formats\": null")
                .replace("\"errata\": \"New text\"", "\"errata\": null"),
        )
        assertThat(dto.toEntity().bannedFormats).isEmpty()
        assertThat(dto.toEntity().errata).isNull()
    }

    @Test
    fun `malformed localisations json degrades to none`() {
        assertThat(LocalizationsCodec.decode("{not json")).isEmpty()
        assertThat(LocalizationsCodec.decode(null)).isEmpty()
    }

    @Test
    fun `legality payload decodes`() {
        val response = json.decodeFromString<LegalityResponseDto>(
            """
            { "region": "kr", "updated_at": "2026-11-14T10:00:00Z",
              "items": [ { "id": "69bc", "riftbound_id": "ogn-077-298", "banned": false,
                           "banned_formats": [], "errata": "Text", "errata_date": "2025-10-28" } ] }
            """,
        )
        assertThat(response.region).isEqualTo("kr")
        assertThat(response.items.single().errata).isEqualTo("Text")
        assertThat(response.items.single().banned).isFalse()
    }
}
