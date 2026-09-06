package com.scanrift.android.data.remote.dto

import com.scanrift.android.core.time.Iso8601
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.domain.model.CardOrientation
import com.scanrift.android.domain.model.SetNormalizer
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaginatedCardsResponse(
    val items: List<CardDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 0,
    val pages: Int = 1,
)

@Serializable
data class CardDto(
    val id: String,
    val name: String,
    @SerialName("riftbound_id") val riftboundId: String,
    @SerialName("public_code") val publicCode: String? = null,
    @SerialName("collector_number") val collectorNumber: Int = 0,
    val attributes: CardAttributesDto = CardAttributesDto(),
    val classification: CardClassificationDto,
    val text: CardTextDto? = null,
    val set: CardSetDto,
    val media: CardMediaDto = CardMediaDto(),
    val tags: List<String> = emptyList(),
    val orientation: String = CardOrientation.PORTRAIT,
    val metadata: CardMetadataDto = CardMetadataDto(),
) {
    /**
     * `public_code` is absent from **every** card the live API currently returns, so
     * this derivation is the primary path, not a fallback. Card matching compares the
     * scanned code against `publicCode`, so getting this wrong means the scanner
     * silently matches nothing at all.
     *
     * `riftbound_id` "unl-060a-219" becomes "UNL-060a/219".
     */
    val resolvedPublicCode: String
        get() {
            publicCode?.takeIf { it.isNotBlank() }?.let { return it }
            val parts = riftboundId.split('-').filter { it.isNotEmpty() }
            if (parts.size < 3) {
                // Locale.ROOT matters: an Arabic-locale device would otherwise format
                // Arabic-Indic digits here and no scan would ever match.
                return "${set.setId}-${"%03d".format(Locale.ROOT, collectorNumber)}"
            }
            return "${parts[0].uppercase(Locale.ROOT)}-${parts[1]}/${parts[2]}"
        }

    /** Epoch millis, or null when the API omits or malforms `updated_on`. */
    val updatedOnMillis: Long?
        get() = Iso8601.parse(metadata.updatedOn)

    /** Newer cards return `clean_name: null`; the display name is the fallback. */
    val resolvedCleanName: String
        get() = metadata.cleanName?.takeIf { it.isNotBlank() } ?: name

    fun toEntity(): CardEntity {
        // Normalisation uses the raw set id, which is also kept as sourceSetId so
        // delta sync can compare local counts against /sets without the promo sets
        // reporting zero forever.
        val normalized = SetNormalizer.normalize(set.setId, set.label)
        return CardEntity(
            id = id,
            name = name,
            riftboundId = riftboundId,
            publicCode = resolvedPublicCode,
            collectorNumber = collectorNumber,
            energy = attributes.energy,
            might = attributes.might,
            power = attributes.power,
            type = classification.type,
            supertype = classification.supertype,
            rarity = classification.rarity,
            domains = classification.domain ?: emptyList(),
            richText = text?.rich,
            plainText = text?.plain,
            setId = normalized.id,
            setLabel = normalized.label,
            sourceSetId = set.setId,
            imageUrl = media.imageUrl,
            artist = media.artist,
            accessibilityText = media.accessibilityText,
            cleanName = resolvedCleanName,
            alternateArt = metadata.alternateArt,
            overnumbered = metadata.overnumbered,
            signature = metadata.signature,
            updatedOn = updatedOnMillis,
            orientation = orientation,
            tags = tags,
        )
    }
}

@Serializable
data class CardAttributesDto(
    val energy: Int? = null,
    val might: Int? = null,
    val power: Int? = null,
)

@Serializable
data class CardClassificationDto(
    val type: String = "",
    val supertype: String? = null,
    val rarity: String = "",
    val domain: List<String>? = null,
)

/** The live API also sends `flavour`; `ignoreUnknownKeys` handles it. */
@Serializable
data class CardTextDto(
    val rich: String? = null,
    val plain: String? = null,
)

@Serializable
data class CardSetDto(
    @SerialName("set_id") val setId: String = "",
    val label: String = "",
)

@Serializable
data class CardMediaDto(
    @SerialName("image_url") val imageUrl: String? = null,
    val artist: String? = null,
    @SerialName("accessibility_text") val accessibilityText: String? = null,
)

@Serializable
data class CardMetadataDto(
    @SerialName("clean_name") val cleanName: String? = null,
    @SerialName("updated_on") val updatedOn: String? = null,
    @SerialName("alternate_art") val alternateArt: Boolean = false,
    val overnumbered: Boolean = false,
    val signature: Boolean = false,
)
