package com.scanrift.android.domain.model

import java.util.Locale

/**
 * A card in the catalogue.
 *
 * This is the domain type: a plain Kotlin class with no Room or Android imports, so
 * the derived properties below and everything built on them (deck validation, set
 * ordering, sync de-duplication) are testable with plain JUnit.
 *
 * @param setId normalised set id — promos collapse to [SetNormalizer.PROMO_SET_ID].
 * @param sourceSetId the raw `set_id` the API returned, before normalisation.
 *   Not present on iOS, and it fixes a live bug there: `syncDelta` counts local cards
 *   by the normalised id but compares against the raw id from `/sets`, so the count for
 *   OPP/JDG/PR is always zero and all three promo sets refetch on every single sync.
 * @param updatedOn epoch millis from `metadata.updated_on`; drives the delta sync's
 *   "unchanged, skip the write" check.
 */
data class Card(
    val id: String,
    val name: String,
    val riftboundId: String,
    val publicCode: String,
    val collectorNumber: Int,
    val energy: Int? = null,
    val might: Int? = null,
    val power: Int? = null,
    val type: String,
    val supertype: String? = null,
    val rarity: String,
    val domains: List<String> = emptyList(),
    val richText: String? = null,
    val plainText: String? = null,
    val setId: String,
    val setLabel: String,
    val sourceSetId: String,
    val imageUrl: String? = null,
    val artist: String? = null,
    val accessibilityText: String? = null,
    val cleanName: String,
    val alternateArt: Boolean = false,
    val overnumbered: Boolean = false,
    val signature: Boolean = false,
    val updatedOn: Long? = null,
    val orientation: String = CardOrientation.PORTRAIT,
    val tags: List<String> = emptyList(),
) {
    /** Rare, Epic and Showcase printings only exist as foils. */
    val isAlwaysFoil: Boolean
        get() = rarity in Rarity.alwaysFoil

    /** Battlefields are printed landscape and are rotated upright in the collection grid. */
    val isLandscape: Boolean
        get() = orientation == CardOrientation.LANDSCAPE

    /**
     * True for "SP" special-print variants, whose `riftboundId` number segment starts
     * with "sp" (e.g. `ven-sp2-006`). These reuse low collector numbers that collide
     * with base cards, so [setOrderRank] groups them at the end of their set.
     */
    val isSpecialPrint: Boolean
        get() {
            val parts = riftboundIdParts
            return parts.size >= 2 && parts[1].lowercase(Locale.ROOT).startsWith("sp")
        }

    /**
     * True for alternate-art printings, which carry a trailing letter in the
     * `riftboundId` number segment (`ven-042a-166`, or a rune's `ven-r01a`).
     *
     * The `alternateArt` metadata flag is unreliable in preliminary set data, so the id
     * suffix is the source of truth and the flag is only a fallback. Note the live API
     * also emits a `*` suffix for signature prints (`unl-229*-219`) — `*` is not a
     * letter, so those correctly do not count as alternate art.
     */
    val isAlternateArt: Boolean
        get() {
            val parts = riftboundIdParts
            if (parts.size >= 2 && !isSpecialPrint && parts[1].lastOrNull()?.isLetter() == true) {
                return true
            }
            return alternateArt
        }

    /**
     * Ordering bucket within a set for "Number" sorting: base cards, then
     * overnumbered, then special prints, then runes, then tokens.
     *
     * Tokens are detected by **supertype**, not type — a token's `type` is usually
     * "Unit" (e.g. "Sprite (274) // Buff").
     */
    val setOrderRank: Int
        get() = when {
            supertype == CardSupertype.TOKEN -> 4
            type == CardType.RUNE -> 3
            isSpecialPrint -> 2
            overnumbered -> 1
            else -> 0
        }

    /** The set-and-number portion of [publicCode], e.g. `OGN-021a` from `OGN-021a/298`. */
    val publicCodePrefix: String
        get() = publicCode.substringBefore('/')

    /** riftbound.gg's CardId format, e.g. `OGN-021`. */
    val dotGgId: String
        get() = "$setId-${"%03d".format(Locale.ROOT, collectorNumber)}"

    /** Swift's `split(separator:)` drops empty subsequences; `filter` reproduces that. */
    private val riftboundIdParts: List<String>
        get() = riftboundId.split('-').filter { it.isNotEmpty() }
}

/**
 * Comparator for the "Number" sort: set, then ordering bucket, then collector number,
 * then base art before alternate art. Ported from iOS `CollectionView`.
 */
val CardNumberComparator: Comparator<Card> = compareBy(
    { it.setId },
    { it.setOrderRank },
    { it.collectorNumber },
    { it.isAlternateArt },
)
