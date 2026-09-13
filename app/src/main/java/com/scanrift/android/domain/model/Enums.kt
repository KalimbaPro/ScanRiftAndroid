package com.scanrift.android.domain.model

/**
 * Enum raw values are part of the cross-platform contract.
 *
 * They are persisted verbatim in Room and serialised verbatim into the backup
 * snapshot and every export format, so an Android backup restores on iOS and vice
 * versa. Store [value], never `name` and never the ordinal.
 */

enum class CardCondition(val value: String, val shortName: String) {
    MINT("Mint", "M"),
    NEAR_MINT("Near Mint", "NM"),
    LIGHTLY_PLAYED("Lightly Played", "LP"),
    MODERATELY_PLAYED("Moderately Played", "MP"),
    HEAVILY_PLAYED("Heavily Played", "HP"),
    DAMAGED("Damaged", "DMG");

    companion object {
        /** Unknown values fall back to Near Mint, matching iOS. */
        fun fromValue(value: String?): CardCondition =
            entries.firstOrNull { it.value == value } ?: NEAR_MINT

        /** Case-insensitive lookup, used when importing user-authored CSV. */
        fun fromValueIgnoringCase(value: String?): CardCondition =
            entries.firstOrNull { it.value.equals(value?.trim(), ignoreCase = true) } ?: NEAR_MINT
    }
}

enum class DeckSection(val value: String, val displayName: String) {
    MAIN_DECK("mainDeck", "Main Deck"),
    RUNE("rune", "Runes"),
    BATTLEFIELD("battlefield", "Battlefields"),
    SIDEBOARD("sideboard", "Sideboard");

    companion object {
        fun fromValue(value: String?): DeckSection =
            entries.firstOrNull { it.value == value } ?: MAIN_DECK
    }
}

enum class GameResult(val value: String, val displayName: String) {
    WIN("win", "Win"),
    LOSS("loss", "Loss"),
    DRAW("draw", "Draw");

    companion object {
        /** Unknown values decode to draw, matching iOS `GameResult(rawValue:) ?? .draw`. */
        fun fromValue(value: String?): GameResult =
            entries.firstOrNull { it.value == value } ?: DRAW
    }
}

enum class ScoreCategory(val value: String, val displayName: String) {
    CONQUER("conquer", "Conquer"),
    HOLD("hold", "Hold"),
    ABILITY("ability", "Ability"),
    ;

    companion object {
        fun fromValue(value: String?): ScoreCategory =
            entries.firstOrNull { it.value == value } ?: CONQUER
    }
}

/**
 * How a seat in the point tracker takes a score.
 *
 * [TAP_ZONES] is the default: tap the player's own left half to take a point back, the
 * right half to pick which category the next point belongs to. [CATEGORY_BUTTONS] is
 * the older three-circle layout, kept because some players prefer a permanent readout
 * of each category over a breakdown bar.
 *
 * Stored as [value] in preferences, never as `name` or the ordinal.
 */
enum class ScoreInputMode(val value: String, val displayName: String, val description: String) {
    TAP_ZONES(
        "tapZones",
        "Tap zones",
        "Tap the left of your tile to undo a point, the right to score one",
    ),
    CATEGORY_BUTTONS(
        "categoryButtons",
        "Category buttons",
        "Three circles per tile: tap to score, long-press to take a point back",
    ),
    ;

    companion object {
        fun fromValue(value: String?): ScoreInputMode =
            entries.firstOrNull { it.value == value } ?: TAP_ZONES
    }
}

/**
 * Card vocabulary. These are plain strings in the API and the database — the app
 * never rejects an unknown value — but these constants pin the known set so filter
 * ordering and icon lookup stay stable.
 */
object CardType {
    const val UNIT = "Unit"
    const val SPELL = "Spell"
    const val GEAR = "Gear"
    const val LEGEND = "Legend"
    const val BATTLEFIELD = "Battlefield"
    const val RUNE = "Rune"

    val all = listOf(UNIT, SPELL, GEAR, LEGEND, BATTLEFIELD, RUNE)
}

object CardSupertype {
    const val CHAMPION = "Champion"
    const val SIGNATURE = "Signature"
    const val BASIC = "Basic"
    const val TOKEN = "Token"
}

object Rarity {
    const val COMMON = "Common"
    const val UNCOMMON = "Uncommon"
    const val RARE = "Rare"
    const val EPIC = "Epic"
    const val SHOWCASE = "Showcase"
    const val PROMO = "Promo"

    /** Filter-sheet display order. `Promo` is deliberately absent, as on iOS. */
    val displayOrder = listOf(COMMON, UNCOMMON, RARE, EPIC, SHOWCASE)

    /** Rarities whose printings are always foil. */
    val alwaysFoil = setOf(RARE, EPIC, SHOWCASE)

    fun sortRank(rarity: String): Int =
        displayOrder.indexOf(rarity).takeIf { it >= 0 } ?: displayOrder.size
}

object Domain {
    const val BODY = "Body"
    const val CALM = "Calm"
    const val CHAOS = "Chaos"
    const val COLORLESS = "Colorless"
    const val FURY = "Fury"
    const val MIND = "Mind"
    const val ORDER = "Order"

    /**
     * Filter-sheet order, matching iOS. `Colorless` is excluded from the filter
     * because it is an absence of domain rather than a choice — but note the old
     * Android build excluded it from the data model too, which hid 44 cards.
     */
    val filterOrder = listOf(FURY, CALM, MIND, CHAOS, BODY, ORDER)

    val all = listOf(BODY, CALM, CHAOS, COLORLESS, FURY, MIND, ORDER)
}

object CardOrientation {
    const val PORTRAIT = "portrait"
    const val LANDSCAPE = "landscape"
}
