package com.scanrift.android.domain.model

/**
 * Collapses the three promo set ids into one and pins friendly labels.
 *
 * Ported from iOS `SetNormalizer`. The label overrides exist so the UI stays stable
 * even if the API varies a label between endpoints; without them the set filter
 * diverges between platforms.
 */
object SetNormalizer {

    /** Promo set ids merged under a single "Promos" set. */
    private val PROMO_SET_IDS = setOf("OPP", "JDG", "PR")

    private val LABEL_OVERRIDES = mapOf(
        "OGN" to "Origins",
        "OGS" to "Proving Grounds",
        "SFD" to "Spiritforged",
        "UNL" to "Unleashed",
    )

    const val PROMO_SET_ID = "PROMO"
    const val PROMO_LABEL = "Promos"

    data class NormalizedSet(val id: String, val label: String)

    fun normalize(setId: String, setLabel: String): NormalizedSet =
        if (setId in PROMO_SET_IDS) {
            NormalizedSet(PROMO_SET_ID, PROMO_LABEL)
        } else {
            NormalizedSet(setId, LABEL_OVERRIDES[setId] ?: setLabel)
        }

    fun isPromoSet(setId: String): Boolean = setId in PROMO_SET_IDS
}
