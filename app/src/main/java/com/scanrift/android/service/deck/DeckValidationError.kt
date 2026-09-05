package com.scanrift.android.service.deck

/**
 * Deck-construction rule violations.
 *
 * [id] and [description] are ported verbatim from iOS: `id` keys the validation
 * banner's list (so duplicates collapse), and the strings are user-visible on both
 * platforms.
 */
sealed interface DeckValidationError {
    val id: String
    val description: String

    data object NoLegend : DeckValidationError {
        override val id = "noLegend"
        override val description = "Deck must have a Legend"
    }

    data object NoChampion : DeckValidationError {
        override val id = "noChampion"
        override val description = "Deck must have a Champion"
    }

    data object ChampionTagMismatch : DeckValidationError {
        override val id = "championTagMismatch"
        override val description = "Champion must share a tag with Legend"
    }

    data object ChampionNotUnit : DeckValidationError {
        override val id = "championNotUnit"
        override val description = "Champion must be a Unit with supertype Champion"
    }

    data class MainDeckTooSmall(val current: Int, val required: Int) : DeckValidationError {
        override val id = "mainDeckTooSmall"
        override val description = "Main deck needs at least $required cards (currently $current)"
    }

    data class TooManyCopies(val cardName: String, val count: Int, val max: Int) : DeckValidationError {
        override val id = "tooManyCopies-$cardName"
        override val description = "$cardName: $count copies exceeds limit of $max"
    }

    data class DomainViolation(
        val cardName: String,
        val cardDomains: List<String>,
        val deckDomains: List<String>,
    ) : DeckValidationError {
        override val id = "domainViolation-$cardName"
        override val description =
            "$cardName (${cardDomains.joinToString(", ")}) outside deck domains (${deckDomains.joinToString(", ")})"
    }

    data class TooManySignatureCards(val count: Int, val max: Int) : DeckValidationError {
        override val id = "tooManySignatureCards"
        override val description = "Too many Signature cards: $count/$max"
    }

    data class SignatureTagMismatch(val cardName: String) : DeckValidationError {
        override val id = "signatureTagMismatch-$cardName"
        override val description = "$cardName: Signature card must share a tag with Legend"
    }

    data class RuneCountWrong(val current: Int, val required: Int) : DeckValidationError {
        override val id = "runeCountWrong"
        override val description = "Rune deck needs exactly $required cards (currently $current)"
    }

    data class BattlefieldCountWrong(val current: Int, val required: Int) : DeckValidationError {
        override val id = "battlefieldCountWrong"
        override val description = "Battlefield deck needs exactly $required cards (currently $current)"
    }

    data class DuplicateBattlefield(val name: String) : DeckValidationError {
        override val id = "duplicateBattlefield-$name"
        override val description = "Duplicate battlefield: $name"
    }

    data class SideboardTooLarge(val current: Int, val max: Int) : DeckValidationError {
        override val id = "sideboardTooLarge"
        override val description = "Sideboard has $current cards (max $max)"
    }
}
