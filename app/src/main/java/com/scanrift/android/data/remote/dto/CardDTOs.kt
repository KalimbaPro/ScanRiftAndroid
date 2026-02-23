package com.scanrift.android.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.scanrift.android.data.local.entity.CardEntity

data class PaginatedResponse(
    val items: List<CardDTO>,
    val total: Int,
    val page: Int,
    val size: Int,
    val pages: Int
)

data class CardDTO(
    val id: String,
    val name: String,
    @SerializedName("riftbound_id") val riftboundId: String,
    @SerializedName("public_code") val publicCode: String,
    @SerializedName("collector_number") val collectorNumber: Int,
    val attributes: CardAttributesDTO,
    val classification: CardClassificationDTO,
    val text: CardTextDTO?,
    val set: CardSetDTO,
    val media: CardMediaDTO,
    val tags: List<String>,
    val orientation: String,
    val metadata: CardMetadataDTO
) {
    fun toEntity(): CardEntity = CardEntity(
        id = id,
        name = name,
        riftboundId = riftboundId,
        publicCode = publicCode,
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
        setId = set.setId,
        setLabel = set.label,
        imageUrl = media.imageUrl,
        artist = media.artist,
        accessibilityText = media.accessibilityText,
        cleanName = metadata.cleanName,
        alternateArt = metadata.alternateArt,
        overnumbered = metadata.overnumbered,
        signature = metadata.signature,
        orientation = orientation,
        tags = tags
    )
}

data class CardAttributesDTO(
    val energy: Int?,
    val might: Int?,
    val power: Int?
)

data class CardClassificationDTO(
    val type: String,
    val supertype: String?,
    val rarity: String,
    val domain: List<String>?
)

data class CardTextDTO(
    val rich: String?,
    val plain: String?
)

data class CardSetDTO(
    @SerializedName("set_id") val setId: String,
    val label: String
)

data class CardMediaDTO(
    @SerializedName("image_url") val imageUrl: String?,
    val artist: String?,
    @SerializedName("accessibility_text") val accessibilityText: String?
)

data class CardMetadataDTO(
    @SerializedName("clean_name") val cleanName: String,
    @SerializedName("alternate_art") val alternateArt: Boolean,
    val overnumbered: Boolean,
    val signature: Boolean
)
