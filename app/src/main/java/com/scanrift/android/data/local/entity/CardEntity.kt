package com.scanrift.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.scanrift.android.domain.model.CardOrientation

/**
 * The card catalogue.
 *
 * `id` is a real primary key here. iOS had to drop uniqueness on it because CloudKit
 * forbids unique constraints; Android has no CloudKit, so the constraint is free — and
 * it makes iOS's "collapse exact-id duplicates" dedup pass structurally impossible,
 * which is why that pass is not ported.
 *
 * There is deliberately **no** unique index on [riftboundId]. The API legitimately
 * returns a preliminary and a finalised record sharing one (16 such pairs are in the
 * bundled catalogue today), so a hard constraint would throw during upsert, before the
 * de-duplication that resolves them ever gets to run.
 */
@Entity(
    tableName = "cards",
    indices = [
        Index("riftboundId"),
        Index("setId"),
        Index("sourceSetId"),
        Index(value = ["setId", "collectorNumber"]),
        Index("cleanName"),
        Index("type"),
        Index("rarity"),
        Index("publicCode"),
    ],
)
data class CardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val riftboundId: String,
    val publicCode: String,
    val collectorNumber: Int,
    val energy: Int?,
    val might: Int?,
    val power: Int?,
    val type: String,
    val supertype: String?,
    val rarity: String,
    val domains: List<String>,
    val richText: String?,
    val plainText: String?,

    /** Normalised set id — the three promo sets collapse to "PROMO". */
    val setId: String,
    val setLabel: String,

    /**
     * The raw `set_id` from the API, before promo normalisation.
     *
     * Keeps the promo sets (OPP, JDG, PR) distinguishable after normalisation folds
     * them into one.
     */
    val sourceSetId: String,

    val imageUrl: String?,
    val artist: String?,
    val accessibilityText: String?,
    val cleanName: String,
    val alternateArt: Boolean = false,
    val overnumbered: Boolean = false,
    val signature: Boolean = false,

    /** Epoch millis from `metadata.updated_on`; de-duplication prefers the newest record. */
    val updatedOn: Long?,

    @ColumnInfo(defaultValue = CardOrientation.PORTRAIT)
    val orientation: String = CardOrientation.PORTRAIT,
    val tags: List<String> = emptyList(),

    /**
     * Legality for the user's competitive region. Written by card sync and then
     * overwritten by the `/legality` refresh, which only ever UPDATEs these columns.
     */
    @ColumnInfo(defaultValue = "0")
    val banned: Boolean = false,
    @ColumnInfo(defaultValue = "[]")
    val bannedFormats: List<String> = emptyList(),
    val errata: String? = null,
    val errataDate: String? = null,

    /** `localizations` JSON in the API's own shape; see `LocalizationsCodec`. */
    val localizations: String? = null,
)

/** One card's legality columns, as applied by the `/legality` refresh. */
data class CardLegality(
    val banned: Boolean,
    val bannedFormats: List<String>,
    val errata: String?,
    val errataDate: String?,
)

/** Lightweight projection for dedup and legality, so they never materialise every full row. */
data class CardIdentity(
    val id: String,
    val riftboundId: String,
    val sourceSetId: String,
    val updatedOn: Long?,
    val cleanName: String,
)
