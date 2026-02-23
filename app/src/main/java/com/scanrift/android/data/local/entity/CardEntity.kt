package com.scanrift.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val riftboundId: String,
    val publicCode: String,
    val collectorNumber: Int,

    // Attributes
    val energy: Int?,
    val might: Int?,
    val power: Int?,

    // Classification
    val type: String,
    val supertype: String?,
    val rarity: String,
    val domains: List<String>,

    // Text
    val richText: String?,
    val plainText: String?,

    // Set
    val setId: String,
    val setLabel: String,

    // Media
    val imageUrl: String?,
    val artist: String?,
    val accessibilityText: String?,

    // Metadata
    val cleanName: String,
    val alternateArt: Boolean = false,
    val overnumbered: Boolean = false,
    val signature: Boolean = false,

    val orientation: String = "portrait",
    val tags: List<String> = emptyList()
) {
    val isAlwaysFoil: Boolean
        get() = rarity == "Rare" || rarity == "Epic" || rarity == "Showcase"
}
