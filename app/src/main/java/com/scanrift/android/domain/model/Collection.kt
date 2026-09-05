package com.scanrift.android.domain.model

/**
 * One owned stack of a card.
 *
 * The identity triple `(cardId, isFoil, condition)` is the upsert key everywhere —
 * scanning, importing and restoring a backup all merge on it. Room enforces it with a
 * unique index so the invariant survives a bug rather than depending on discipline.
 *
 * [card] is nullable because the underlying FK is `ON DELETE SET NULL`: that mirrors
 * iOS's optional SwiftData relationship and, more importantly, means a catalogue
 * refresh can never cascade away the user's collection.
 */
data class CollectionEntry(
    val id: Long = 0,
    val cardId: String?,
    val card: Card? = null,
    val quantity: Int = 1,
    val isFoil: Boolean = false,
    val dateAdded: Long,
    val condition: CardCondition = CardCondition.NEAR_MINT,
    val notes: String? = null,
    val folder: String? = null,
) {
    val key: EntryKey? get() = cardId?.let { EntryKey(it, isFoil, condition.value) }
}

/** The upsert key shared by scan-add, import and snapshot restore. */
data class EntryKey(val cardId: String, val isFoil: Boolean, val condition: String)

/**
 * A wishlist or user-created list.
 *
 * Exactly one system list exists per install: the Wishlist, reconciled at launch.
 */
data class CardList(
    val id: String,
    val name: String,
    val colorHex: String = "#FF9500",
    val isSystem: Boolean = false,
    val systemType: String? = null,
    val createdDate: Long,
    val cards: List<Card> = emptyList(),
) {
    val isWishlist: Boolean get() = systemType == "wishlist"
}

data class Deck(
    val id: String,
    val name: String = "New Deck",
    val createdDate: Long,
    val lastModifiedDate: Long,
    val legendCardId: String? = null,
    val championCardId: String? = null,
    val legend: Card? = null,
    val champion: Card? = null,
    val entries: List<DeckEntry> = emptyList(),
)

data class DeckEntry(
    val id: Long = 0,
    val deckId: String,
    val cardId: String?,
    val card: Card? = null,
    val quantity: Int = 1,
    val section: DeckSection = DeckSection.MAIN_DECK,
)

/**
 * A recorded game.
 *
 * [deckId] is optional: a legend-only game has no deck and lives in global history
 * only. [legendId] and [opponentLegendId] are plain card-id strings rather than
 * relations — matching iOS — so a matchup record survives a card the catalogue no
 * longer contains.
 */
data class GameRecord(
    val id: String,
    val date: Long,
    val name: String? = null,
    val result: GameResult = GameResult.DRAW,
    val playerName: String? = null,
    val opponentName: String? = null,
    val pointsScored: Int? = null,
    val pointsAllowed: Int? = null,
    val ties: Int? = null,
    val conquerCount: Int? = null,
    val holdCount: Int? = null,
    val abilityCount: Int? = null,
    val notes: String? = null,
    val deckId: String? = null,
    val legendId: String? = null,
    val opponentLegendId: String? = null,
) {
    val hasCategoryBreakdown: Boolean
        get() = conquerCount != null || holdCount != null || abilityCount != null
}
