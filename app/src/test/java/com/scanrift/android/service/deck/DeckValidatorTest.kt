package com.scanrift.android.service.deck

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardSupertype
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckEntry
import com.scanrift.android.domain.model.DeckSection
import org.junit.Test

/**
 * One test per rule, plus one per carve-out.
 *
 * The carve-outs are the interesting part: they look like inconsistencies and are load
 * bearing, so each has a test explaining why it exists.
 */
class DeckValidatorTest {

    private var nextId = 0

    private fun card(
        name: String = "Card ${nextId++}",
        type: String = CardType.UNIT,
        supertype: String? = null,
        domains: List<String> = listOf("Fury"),
        tags: List<String> = listOf("Akali"),
        signature: Boolean = false,
        cleanName: String = name,
    ) = Card(
        id = "id-${nextId++}",
        name = name,
        riftboundId = "ven-${"%03d".format(nextId)}-166",
        publicCode = "VEN-001/166",
        collectorNumber = nextId,
        type = type,
        supertype = supertype,
        rarity = "Common",
        domains = domains,
        setId = "VEN",
        setLabel = "Vendetta",
        sourceSetId = "VEN",
        cleanName = cleanName,
        signature = signature,
        tags = tags,
    )

    private fun legend(domains: List<String> = listOf("Fury"), tags: List<String> = listOf("Akali")) =
        card(name = "Akali Legend", type = CardType.LEGEND, domains = domains, tags = tags)

    private fun champion(tags: List<String> = listOf("Akali")) =
        card(name = "Akali Champion", type = CardType.UNIT, supertype = CardSupertype.CHAMPION, tags = tags)

    private fun entry(card: Card?, quantity: Int = 1, section: DeckSection = DeckSection.MAIN_DECK) =
        DeckEntry(deckId = "deck-1", cardId = card?.id, card = card, quantity = quantity, section = section)

    /** A deck that passes all 13 rules, so each test can break exactly one thing. */
    private fun validDeck(
        legend: Card? = legend(),
        champion: Card? = champion(),
        extraEntries: List<DeckEntry> = emptyList(),
        mainFiller: Int = 39,
    ): Deck {
        val entries = buildList {
            champion?.let { add(entry(it, quantity = 1)) }
            repeat(mainFiller) { add(entry(card(name = "Filler $it"), quantity = 1)) }
            add(entry(card(name = "Rune", type = CardType.RUNE), quantity = 12, section = DeckSection.RUNE))
            repeat(3) {
                add(entry(card(name = "Field $it", type = CardType.BATTLEFIELD), section = DeckSection.BATTLEFIELD))
            }
            addAll(extraEntries)
        }
        return Deck(
            id = "deck-1", createdDate = 0, lastModifiedDate = 0,
            legendCardId = legend?.id, championCardId = champion?.id,
            legend = legend, champion = champion, entries = entries,
        )
    }

    @Test
    fun `a well formed deck has no errors`() {
        assertThat(DeckValidator.validate(validDeck())).isEmpty()
    }

    // ── Rules 1-4: legend and champion ───────────────────────────────────────

    @Test
    fun `rule 1 - a missing legend is reported`() {
        val errors = DeckValidator.validate(validDeck(legend = null))
        assertThat(errors).contains(DeckValidationError.NoLegend)
    }

    @Test
    fun `rule 2 - a missing champion is reported`() {
        val errors = DeckValidator.validate(validDeck(champion = null, mainFiller = 40))
        assertThat(errors).contains(DeckValidationError.NoChampion)
    }

    @Test
    fun `rule 3 - the champion must share a tag with the legend`() {
        val errors = DeckValidator.validate(
            validDeck(legend = legend(tags = listOf("Akali")), champion = champion(tags = listOf("Jinx"))),
        )
        assertThat(errors).contains(DeckValidationError.ChampionTagMismatch)
    }

    @Test
    fun `rule 4 - the champion must be a champion unit`() {
        val notAChampion = card(name = "Plain Unit", type = CardType.UNIT, supertype = null)
        val errors = DeckValidator.validate(validDeck(champion = notAChampion))
        assertThat(errors).contains(DeckValidationError.ChampionNotUnit)
    }

    // ── Rule 5: main deck minimum ────────────────────────────────────────────

    @Test
    fun `rule 5 - the main deck is a minimum not an exact count`() {
        assertThat(DeckValidator.validate(validDeck(mainFiller = 38)))
            .contains(DeckValidationError.MainDeckTooSmall(39, 40))
        // More than 40 is perfectly legal.
        assertThat(DeckValidator.validate(validDeck(mainFiller = 59))).isEmpty()
    }

    @Test
    fun `rule 5 - an entry with a missing card still counts toward the total`() {
        // Section totals are quantity-based; a null card only disables the name rules.
        val deck = validDeck(mainFiller = 38, extraEntries = listOf(entry(card = null, quantity = 1)))
        assertThat(DeckValidator.validate(deck)).doesNotContain(DeckValidationError.MainDeckTooSmall(39, 40))
    }

    // ── Rule 6: copy limit ───────────────────────────────────────────────────

    @Test
    fun `rule 6 - more than three copies of a name is rejected`() {
        val repeated = card(name = "Repeated")
        val deck = validDeck(mainFiller = 35, extraEntries = listOf(entry(repeated, quantity = 4)))
        assertThat(DeckValidator.validate(deck))
            .contains(DeckValidationError.TooManyCopies("Repeated", 4, 3))
    }

    @Test
    fun `rule 6 - the copy limit keys on cleanName so alternate art is not a fourth copy`() {
        val base = card(name = "Akali", cleanName = "Akali")
        val altArt = card(name = "Akali (Alternate Art)", cleanName = "Akali")
        val deck = validDeck(
            mainFiller = 35,
            extraEntries = listOf(entry(base, quantity = 3), entry(altArt, quantity = 1)),
        )
        assertThat(DeckValidator.validate(deck))
            .contains(DeckValidationError.TooManyCopies("Akali", 4, 3))
    }

    @Test
    fun `rule 6 - the copy limit spans main and sideboard and counts the champion once`() {
        val shared = card(name = "Shared")
        val deck = validDeck(
            mainFiller = 36,
            extraEntries = listOf(
                entry(shared, quantity = 2),
                entry(shared, quantity = 2, section = DeckSection.SIDEBOARD),
            ),
        )
        assertThat(DeckValidator.validate(deck))
            .contains(DeckValidationError.TooManyCopies("Shared", 4, 3))
    }

    @Test
    fun `rule 6 - runes are outside the copy limit`() {
        // A legal deck needs 12 runes, so they cannot be subject to a 3-copy cap.
        assertThat(DeckValidator.validate(validDeck()))
            .isEmpty()
    }

    // ── Rule 7: domain identity ──────────────────────────────────────────────

    @Test
    fun `rule 7 - a card outside the legend's domains is rejected`() {
        val offDomain = card(name = "Calm Card", domains = listOf("Calm"))
        val deck = validDeck(mainFiller = 38, extraEntries = listOf(entry(offDomain)))
        assertThat(DeckValidator.validate(deck).map { it.id }).contains("domainViolation-Calm Card")
    }

    @Test
    fun `rule 7 - a card with no domains is exempt`() {
        // This carve-out is what keeps colourless cards and most gear playable anywhere.
        val colorless = card(name = "Colorless Gear", type = CardType.GEAR, domains = emptyList())
        val deck = validDeck(mainFiller = 38, extraEntries = listOf(entry(colorless)))
        assertThat(DeckValidator.validate(deck)).isEmpty()
    }

    @Test
    fun `rule 7 - a multi domain legend admits each of its domains`() {
        val dualLegend = legend(domains = listOf("Fury", "Calm"))
        val calmCard = card(name = "Calm Card", domains = listOf("Calm"))
        val deck = validDeck(legend = dualLegend, mainFiller = 38, extraEntries = listOf(entry(calmCard)))
        assertThat(DeckValidator.validate(deck)).isEmpty()
    }

    // ── Rules 8-9: signature cards ───────────────────────────────────────────

    @Test
    fun `rule 8 - more than three signature cards is rejected`() {
        val sig = card(name = "Sig", signature = true)
        val deck = validDeck(mainFiller = 35, extraEntries = listOf(entry(sig, quantity = 4)))
        assertThat(DeckValidator.validate(deck))
            .contains(DeckValidationError.TooManySignatureCards(4, 3))
    }

    @Test
    fun `rule 8 - sideboard signatures are not counted`() {
        // The signature cap is main-deck only; the copy limit is what governs the
        // sideboard. Getting this wrong makes legal decks look illegal.
        val sig = card(name = "Sig", signature = true)
        val deck = validDeck(
            mainFiller = 36,
            extraEntries = listOf(
                entry(sig, quantity = 3),
                entry(card(name = "Other Sig", signature = true), quantity = 3, section = DeckSection.SIDEBOARD),
            ),
        )
        assertThat(DeckValidator.validate(deck).filterIsInstance<DeckValidationError.TooManySignatureCards>())
            .isEmpty()
    }

    @Test
    fun `rule 9 - a signature card must share a tag with the legend`() {
        val foreignSig = card(name = "Jinx Sig", signature = true, tags = listOf("Jinx"))
        val deck = validDeck(mainFiller = 38, extraEntries = listOf(entry(foreignSig)))
        assertThat(DeckValidator.validate(deck))
            .contains(DeckValidationError.SignatureTagMismatch("Jinx Sig"))
    }

    // ── Rules 10-12: runes and battlefields ──────────────────────────────────

    @Test
    fun `rule 10 - runes must total exactly twelve`() {
        val deck = validDeck().let { d ->
            d.copy(entries = d.entries.map { if (it.section == DeckSection.RUNE) it.copy(quantity = 11) else it })
        }
        assertThat(DeckValidator.validate(deck)).contains(DeckValidationError.RuneCountWrong(11, 12))
    }

    @Test
    fun `rule 10 - too many runes is also wrong`() {
        val deck = validDeck().let { d ->
            d.copy(entries = d.entries.map { if (it.section == DeckSection.RUNE) it.copy(quantity = 13) else it })
        }
        assertThat(DeckValidator.validate(deck)).contains(DeckValidationError.RuneCountWrong(13, 12))
    }

    @Test
    fun `rule 11 - battlefields must total exactly three`() {
        val deck = validDeck().let { d ->
            d.copy(entries = d.entries.filterNot { it.section == DeckSection.BATTLEFIELD })
        }
        assertThat(DeckValidator.validate(deck)).contains(DeckValidationError.BattlefieldCountWrong(0, 3))
    }

    @Test
    fun `rule 12 - duplicate battlefields are rejected by cleanName`() {
        val field = card(name = "Howling Abyss", type = CardType.BATTLEFIELD, cleanName = "Howling Abyss")
        val altField = card(name = "Howling Abyss (Alt)", type = CardType.BATTLEFIELD, cleanName = "Howling Abyss")
        val deck = validDeck().let { d ->
            d.copy(
                entries = d.entries.filterNot { it.section == DeckSection.BATTLEFIELD } + listOf(
                    entry(field, section = DeckSection.BATTLEFIELD),
                    entry(altField, section = DeckSection.BATTLEFIELD),
                    entry(card(name = "Third", type = CardType.BATTLEFIELD), section = DeckSection.BATTLEFIELD),
                ),
            )
        }
        assertThat(DeckValidator.validate(deck))
            .contains(DeckValidationError.DuplicateBattlefield("Howling Abyss"))
    }

    // ── Rule 13: sideboard ───────────────────────────────────────────────────

    @Test
    fun `rule 13 - the sideboard is a maximum of eight`() {
        val extras = (1..9).map { entry(card(name = "Side $it"), section = DeckSection.SIDEBOARD) }
        val deck = validDeck(extraEntries = extras)
        assertThat(DeckValidator.validate(deck)).contains(DeckValidationError.SideboardTooLarge(9, 8))
    }

    @Test
    fun `rule 13 - exactly eight is fine`() {
        val extras = (1..8).map { entry(card(name = "Side $it"), section = DeckSection.SIDEBOARD) }
        assertThat(DeckValidator.validate(validDeck(extraEntries = extras))).isEmpty()
    }

    // ── Builder helpers ──────────────────────────────────────────────────────

    @Test
    fun `runes get a copy limit of twelve and everything else three`() {
        assertThat(DeckValidator.maxCopies(card(type = CardType.RUNE))).isEqualTo(12)
        assertThat(DeckValidator.maxCopies(card(type = CardType.UNIT))).isEqualTo(3)
        assertThat(DeckValidator.maxCopies(card(type = CardType.BATTLEFIELD))).isEqualTo(3)
    }

    @Test
    fun `section inference routes runes and battlefields and defaults to main`() {
        assertThat(DeckValidator.inferSection(card(type = CardType.RUNE))).isEqualTo(DeckSection.RUNE)
        assertThat(DeckValidator.inferSection(card(type = CardType.BATTLEFIELD))).isEqualTo(DeckSection.BATTLEFIELD)
        assertThat(DeckValidator.inferSection(card(type = CardType.SPELL))).isEqualTo(DeckSection.MAIN_DECK)
        assertThat(DeckValidator.inferSection(card(type = CardType.UNIT))).isEqualTo(DeckSection.MAIN_DECK)
    }

    @Test
    fun `copies in deck counts by cleanName across every section`() {
        val akali = card(name = "Akali", cleanName = "Akali")
        val altAkali = card(name = "Akali (Alt)", cleanName = "Akali")
        val deck = validDeck(
            mainFiller = 36,
            extraEntries = listOf(
                entry(akali, quantity = 2),
                entry(altAkali, quantity = 1, section = DeckSection.SIDEBOARD),
            ),
        )
        assertThat(DeckValidator.copiesInDeck(deck, akali)).isEqualTo(3)
    }

    @Test
    fun `champion eligibility requires a champion unit sharing a legend tag`() {
        val myLegend = legend(tags = listOf("Akali"))
        assertThat(DeckValidator.isEligibleChampion(champion(tags = listOf("Akali")), myLegend)).isTrue()
        assertThat(DeckValidator.isEligibleChampion(champion(tags = listOf("Jinx")), myLegend)).isFalse()
        assertThat(DeckValidator.isEligibleChampion(card(type = CardType.SPELL), myLegend)).isFalse()
        // With no legend set, nothing is eligible yet.
        assertThat(DeckValidator.isEligibleChampion(champion(), null)).isFalse()
    }
}
