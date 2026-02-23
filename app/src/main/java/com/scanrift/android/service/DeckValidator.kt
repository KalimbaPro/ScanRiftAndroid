package com.scanrift.android.service

import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.entity.DeckSection
import com.scanrift.android.util.Constants

/**
 * Deck validation error — direct port of iOS DeckValidationError.
 */
sealed class DeckValidationError(val description: String) {
    data object NoLegend : DeckValidationError("Deck must have a Legend")
    data object NoChampion : DeckValidationError("Deck must have a Champion")
    data object ChampionTagMismatch : DeckValidationError("Champion must share a tag with Legend")
    data object ChampionNotUnit : DeckValidationError("Champion must be a Unit with supertype Champion")

    data class MainDeckTooSmall(val current: Int, val required: Int) :
        DeckValidationError("Main deck needs at least $required cards (currently $current)")

    data class TooManyCopies(val cardName: String, val count: Int, val max: Int) :
        DeckValidationError("$cardName: $count copies exceeds limit of $max")

    data class DomainViolation(val cardName: String, val cardDomains: List<String>, val deckDomains: List<String>) :
        DeckValidationError("$cardName (${cardDomains.joinToString(", ")}) outside deck domains (${deckDomains.joinToString(", ")})")

    data class TooManySignatureCards(val count: Int, val max: Int) :
        DeckValidationError("Too many Signature cards: $count/$max")

    data class SignatureTagMismatch(val cardName: String) :
        DeckValidationError("$cardName: Signature card must share a tag with Legend")

    data class RuneCountWrong(val current: Int, val required: Int) :
        DeckValidationError("Rune deck needs exactly $required cards (currently $current)")

    data class BattlefieldCountWrong(val current: Int, val required: Int) :
        DeckValidationError("Battlefield deck needs exactly $required cards (currently $current)")

    data class DuplicateBattlefield(val name: String) :
        DeckValidationError("Duplicate battlefield: $name")

    data class SideboardTooLarge(val current: Int, val max: Int) :
        DeckValidationError("Sideboard has $current cards (max $max)")
}

/**
 * Deck validator — direct port of all 13 iOS validation rules.
 * Pure logic, no platform dependencies.
 */
object DeckValidator {

    data class DeckData(
        val legend: CardEntity?,
        val champion: CardEntity?,
        val entries: List<Pair<DeckEntryEntity, CardEntity>>
    )

    fun validate(data: DeckData): List<DeckValidationError> {
        val errors = mutableListOf<DeckValidationError>()

        // Rule 1: Legend required
        if (data.legend == null) {
            errors.add(DeckValidationError.NoLegend)
        }

        // Rule 2: Champion required
        if (data.champion == null) {
            errors.add(DeckValidationError.NoChampion)
        }

        // Rule 3: Champion tag matches Legend tag
        val legend = data.legend
        val champion = data.champion
        if (legend != null && champion != null) {
            val legendTags = legend.tags.toSet()
            val championTags = champion.tags.toSet()
            if (legendTags.intersect(championTags).isEmpty()) {
                errors.add(DeckValidationError.ChampionTagMismatch)
            }
        }

        // Rule 4: Champion is Unit with supertype Champion
        if (champion != null) {
            if (champion.type != "Unit" || champion.supertype != "Champion") {
                errors.add(DeckValidationError.ChampionNotUnit)
            }
        }

        val mainEntries = data.entries.filter { it.first.section == DeckSection.MAIN_DECK.value }
        val runeEntries = data.entries.filter { it.first.section == DeckSection.RUNE.value }
        val battlefieldEntries = data.entries.filter { it.first.section == DeckSection.BATTLEFIELD.value }
        val sideboardEntries = data.entries.filter { it.first.section == DeckSection.SIDEBOARD.value }

        // Rule 5: Main deck >= 40
        val mainDeckCount = mainEntries.sumOf { it.first.quantity }
        if (mainDeckCount < Constants.Deck.MAIN_DECK_MINIMUM) {
            errors.add(DeckValidationError.MainDeckTooSmall(mainDeckCount, Constants.Deck.MAIN_DECK_MINIMUM))
        }

        // Rule 6: Max 3 copies per card name across main + sideboard + champion
        val nameCountMap = mutableMapOf<String, Int>()
        for ((entry, card) in mainEntries + sideboardEntries) {
            nameCountMap[card.cleanName] = (nameCountMap[card.cleanName] ?: 0) + entry.quantity
        }
        if (champion != null) {
            nameCountMap[champion.cleanName] = (nameCountMap[champion.cleanName] ?: 0) + 1
        }
        for ((name, count) in nameCountMap) {
            if (count > Constants.Deck.MAX_COPIES_PER_NAME) {
                errors.add(DeckValidationError.TooManyCopies(name, count, Constants.Deck.MAX_COPIES_PER_NAME))
            }
        }

        // Rule 7: Domain identity — cards must be subset of legend's domains
        if (legend != null) {
            val deckDomains = legend.domains.toSet()
            for ((_, card) in mainEntries + sideboardEntries) {
                if (card.domains.isNotEmpty() && !card.domains.toSet().all { it in deckDomains }) {
                    errors.add(DeckValidationError.DomainViolation(
                        card.name, card.domains, deckDomains.toList()
                    ))
                }
            }
        }

        // Rule 8: Signature cards <= 3
        val signatureEntries = mainEntries.filter { it.second.signature }
        val signatureCount = signatureEntries.sumOf { it.first.quantity }
        if (signatureCount > Constants.Deck.MAX_SIGNATURE_CARDS) {
            errors.add(DeckValidationError.TooManySignatureCards(signatureCount, Constants.Deck.MAX_SIGNATURE_CARDS))
        }

        // Rule 9: Signature cards share tag with legend
        if (legend != null) {
            val legendTags = legend.tags.toSet()
            for ((_, card) in signatureEntries) {
                if (legendTags.intersect(card.tags.toSet()).isEmpty()) {
                    errors.add(DeckValidationError.SignatureTagMismatch(card.name))
                }
            }
        }

        // Rule 10: Rune count == 12
        val runeCount = runeEntries.sumOf { it.first.quantity }
        if (runeCount != Constants.Deck.RUNE_COUNT) {
            errors.add(DeckValidationError.RuneCountWrong(runeCount, Constants.Deck.RUNE_COUNT))
        }

        // Rule 11: Battlefield count == 3
        val battlefieldCount = battlefieldEntries.sumOf { it.first.quantity }
        if (battlefieldCount != Constants.Deck.BATTLEFIELD_COUNT) {
            errors.add(DeckValidationError.BattlefieldCountWrong(battlefieldCount, Constants.Deck.BATTLEFIELD_COUNT))
        }

        // Rule 12: No duplicate battlefield names
        val seenBattlefields = mutableSetOf<String>()
        for ((_, card) in battlefieldEntries) {
            if (!seenBattlefields.add(card.cleanName)) {
                errors.add(DeckValidationError.DuplicateBattlefield(card.cleanName))
            }
        }

        // Rule 13: Sideboard <= 8
        val sideboardCount = sideboardEntries.sumOf { it.first.quantity }
        if (sideboardCount > Constants.Deck.SIDEBOARD_MAXIMUM) {
            errors.add(DeckValidationError.SideboardTooLarge(sideboardCount, Constants.Deck.SIDEBOARD_MAXIMUM))
        }

        return errors
    }
}
