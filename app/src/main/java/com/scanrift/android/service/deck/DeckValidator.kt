package com.scanrift.android.service.deck

import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardSupertype
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckSection

/**
 * The 13 Riftbound deck-construction rules.
 *
 * Pure in, pure out — no Room, no coroutines, no Android — so the whole rule set is
 * testable on plain JUnit and can be shared verbatim with the UI's live validation.
 *
 * Several carve-outs look arbitrary and are not; they are called out at each rule.
 * Rules are emitted in iOS's order because the validation banner shows the first few.
 */
object DeckValidator {

    fun validate(deck: Deck): List<DeckValidationError> {
        val errors = mutableListOf<DeckValidationError>()
        val legend = deck.legend
        val champion = deck.champion

        // 1 & 2: both slots must be filled.
        if (legend == null) errors += DeckValidationError.NoLegend
        if (champion == null) errors += DeckValidationError.NoChampion

        // 3: the champion has to belong to the legend's faction.
        if (legend != null && champion != null &&
            legend.tags.toSet().intersect(champion.tags.toSet()).isEmpty()
        ) {
            errors += DeckValidationError.ChampionTagMismatch
        }

        // 4: and actually be a champion unit.
        if (champion != null &&
            (champion.type != CardType.UNIT || champion.supertype != CardSupertype.CHAMPION)
        ) {
            errors += DeckValidationError.ChampionNotUnit
        }

        val main = deck.entries.filter { it.section == DeckSection.MAIN_DECK }
        val runes = deck.entries.filter { it.section == DeckSection.RUNE }
        val battlefields = deck.entries.filter { it.section == DeckSection.BATTLEFIELD }
        val sideboard = deck.entries.filter { it.section == DeckSection.SIDEBOARD }

        // 5: main deck is a minimum, not an exact count.
        //
        // Note the asymmetry that runs through rules 5, 10, 11 and 13: an entry whose
        // card is missing still counts toward section totals, but is skipped by the
        // name-based rules below, which can't do anything without a card.
        val mainCount = main.sumOf { it.quantity }
        if (mainCount < Constants.Deck.MAIN_DECK_MINIMUM) {
            errors += DeckValidationError.MainDeckTooSmall(mainCount, Constants.Deck.MAIN_DECK_MINIMUM)
        }

        // 6: at most 3 copies per *cleanName* across main + sideboard, plus the
        // champion counted once. Keying on cleanName rather than id is what stops an
        // alternate-art printing being used as a fourth copy. Runes and battlefields
        // are deliberately outside this count — they have their own rules.
        val copiesByName = mutableMapOf<String, Int>()
        (main + sideboard).forEach { entry ->
            val card = entry.card ?: return@forEach
            copiesByName[card.cleanName] = (copiesByName[card.cleanName] ?: 0) + entry.quantity
        }
        champion?.let { copiesByName[it.cleanName] = (copiesByName[it.cleanName] ?: 0) + 1 }
        copiesByName.forEach { (name, count) ->
            if (count > Constants.Deck.MAX_COPIES_PER_NAME) {
                errors += DeckValidationError.TooManyCopies(name, count, Constants.Deck.MAX_COPIES_PER_NAME)
            }
        }

        // 7: domain identity across main + sideboard.
        //
        // A card with *no* domains is exempt rather than universally illegal — that is
        // how colourless cards and most gear stay playable in any deck.
        if (legend != null) {
            val deckDomains = legend.domains.toSet()
            (main + sideboard).forEach { entry ->
                val card = entry.card ?: return@forEach
                if (card.domains.isNotEmpty() && !deckDomains.containsAll(card.domains)) {
                    errors += DeckValidationError.DomainViolation(
                        cardName = card.name,
                        cardDomains = card.domains,
                        deckDomains = deckDomains.toList(),
                    )
                }
            }
        }

        // 8: at most 3 signature cards — **main deck only**. Sideboard signatures are
        // not counted, which is why this uses `main` and not `main + sideboard`.
        val signatureEntries = main.filter { it.card?.signature == true }
        val signatureCount = signatureEntries.sumOf { it.quantity }
        if (signatureCount > Constants.Deck.MAX_SIGNATURE_CARDS) {
            errors += DeckValidationError.TooManySignatureCards(
                signatureCount, Constants.Deck.MAX_SIGNATURE_CARDS,
            )
        }

        // 9: every signature card in the main deck must belong to the legend.
        if (legend != null) {
            val legendTags = legend.tags.toSet()
            signatureEntries.forEach { entry ->
                val card = entry.card ?: return@forEach
                if (legendTags.intersect(card.tags.toSet()).isEmpty()) {
                    errors += DeckValidationError.SignatureTagMismatch(card.name)
                }
            }
        }

        // 10 & 11: runes and battlefields are exact counts, not minimums.
        val runeCount = runes.sumOf { it.quantity }
        if (runeCount != Constants.Deck.RUNE_COUNT) {
            errors += DeckValidationError.RuneCountWrong(runeCount, Constants.Deck.RUNE_COUNT)
        }

        val battlefieldCount = battlefields.sumOf { it.quantity }
        if (battlefieldCount != Constants.Deck.BATTLEFIELD_COUNT) {
            errors += DeckValidationError.BattlefieldCountWrong(
                battlefieldCount, Constants.Deck.BATTLEFIELD_COUNT,
            )
        }

        // 12: battlefields are singleton by name.
        val seenBattlefields = mutableSetOf<String>()
        battlefields.forEach { entry ->
            val card = entry.card ?: return@forEach
            if (!seenBattlefields.add(card.cleanName)) {
                errors += DeckValidationError.DuplicateBattlefield(card.cleanName)
            }
        }

        // 13: sideboard is a maximum.
        val sideboardCount = sideboard.sumOf { it.quantity }
        if (sideboardCount > Constants.Deck.SIDEBOARD_MAXIMUM) {
            errors += DeckValidationError.SideboardTooLarge(
                sideboardCount, Constants.Deck.SIDEBOARD_MAXIMUM,
            )
        }

        return errors
    }

    /**
     * Copy limit for a single card.
     *
     * Two exceptions to the flat 3: runes, because a legal deck needs 12 of them, and
     * battlefields, which are singleton — a deck needs three *different* ones, never
     * three copies of one. Enforced by the builder at add time; [validate] covers the
     * same ground through its own exact-count and duplicate-name rules.
     */
    fun maxCopies(card: Card): Int = when (card.type) {
        CardType.RUNE -> Constants.Deck.MAX_RUNE_COPIES
        CardType.BATTLEFIELD -> 1
        else -> Constants.Deck.MAX_COPIES_PER_NAME
    }

    /** Copies of a card already in the deck, counted by `cleanName` across all sections. */
    fun copiesInDeck(deck: Deck, card: Card): Int =
        deck.entries.filter { it.card?.cleanName == card.cleanName }.sumOf { it.quantity }

    /** Where a card lands when added without an explicit section. */
    fun inferSection(card: Card): DeckSection = when (card.type) {
        CardType.RUNE -> DeckSection.RUNE
        CardType.BATTLEFIELD -> DeckSection.BATTLEFIELD
        else -> DeckSection.MAIN_DECK
    }

    fun isEligibleChampion(card: Card, legend: Card?): Boolean {
        if (card.type != CardType.UNIT || card.supertype != CardSupertype.CHAMPION) return false
        val legendTags = legend?.tags?.toSet() ?: return false
        return legendTags.intersect(card.tags.toSet()).isNotEmpty()
    }
}
