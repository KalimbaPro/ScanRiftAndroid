package com.scanrift.android.service.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The parser is the half of the import that can be pinned without a database. The
 * sample list is the one a user actually pastes, headers, blank lines and all.
 */
class DeckListParserTest {

    private val sample = """
        Legend:
        1 Ornn, Fire Below the Mountain

        Champion:
        1 Ornn, Blacksmith

        MainDeck:
        3 Scuttle Crab
        3 Clockwork Keeper
        2 Aspiring Engineer
        1 Retreat

        Battlefields:
        1 Ornn's Forge
        1 Seat of Power
        1 Veiled Temple

        Runes:
        8 Calm Rune
        4 Mind Rune

        Sideboard:
        2 Decree of Focus
        3 Disarming Rake
    """.trimIndent()

    private fun ParsedDeckList.section(section: DeckListSection) =
        lines.filter { it.section == section }

    @Test
    fun `parses a pasted list into its sections`() {
        val parsed = DeckListParser.parseText(sample)

        assertThat(parsed.source).isEqualTo(DeckListSource.TEXT)
        assertThat(parsed.section(DeckListSection.LEGEND).map { it.token })
            .containsExactly("Ornn, Fire Below the Mountain")
        assertThat(parsed.section(DeckListSection.CHAMPION).map { it.token })
            .containsExactly("Ornn, Blacksmith")
        assertThat(parsed.section(DeckListSection.MAIN_DECK)).hasSize(4)
        assertThat(parsed.section(DeckListSection.BATTLEFIELDS).map { it.token })
            .containsExactly("Ornn's Forge", "Seat of Power", "Veiled Temple")
        assertThat(parsed.section(DeckListSection.RUNES).sumOf { it.quantity }).isEqualTo(12)
        assertThat(parsed.section(DeckListSection.SIDEBOARD).sumOf { it.quantity }).isEqualTo(5)
    }

    @Test
    fun `keeps quantities and never drops a name to the quantity prefix`() {
        val parsed = DeckListParser.parseText(sample)
        val main = parsed.section(DeckListSection.MAIN_DECK).associate { it.token to it.quantity }

        assertThat(main).containsEntry("Scuttle Crab", 3)
        assertThat(main).containsEntry("Aspiring Engineer", 2)
        assertThat(main).containsEntry("Retreat", 1)
    }

    @Test
    fun `accepts the 3x spelling without eating a leading X in the name`() {
        val parsed = DeckListParser.parseText("MainDeck:\n3x Scuttle Crab\n2 Xerath")
        val main = parsed.section(DeckListSection.MAIN_DECK).associate { it.token to it.quantity }

        assertThat(main).containsEntry("Scuttle Crab", 3)
        assertThat(main).containsEntry("Xerath", 2)
    }

    @Test
    fun `a bare name counts as a single copy`() {
        val parsed = DeckListParser.parseText("MainDeck:\nScuttle Crab")
        assertThat(parsed.lines.single().quantity).isEqualTo(1)
        assertThat(parsed.lines.single().token).isEqualTo("Scuttle Crab")
    }

    @Test
    fun `header matching ignores case and spacing`() {
        val parsed = DeckListParser.parseText("MAIN DECK :\n1 Scuttle Crab")
        assertThat(parsed.lines.single().section).isEqualTo(DeckListSection.MAIN_DECK)
    }

    @Test
    fun `an unrecognised header leaves the section unset rather than inheriting`() {
        val parsed = DeckListParser.parseText("MainDeck:\n1 Scuttle Crab\nTokens:\n1 Poro")
        assertThat(parsed.lines.first { it.token == "Poro" }.section).isNull()
    }

    @Test
    fun `duplicate names in a section fold into one line`() {
        val parsed = DeckListParser.parseText("MainDeck:\n2 Charm\n1 Charm")
        assertThat(parsed.lines).hasSize(1)
        assertThat(parsed.lines.single().quantity).isEqualTo(3)
    }

    @Test
    fun `the same name in two sections stays two lines`() {
        val parsed = DeckListParser.parseText("MainDeck:\n2 Charm\n\nSideboard:\n1 Charm")
        assertThat(parsed.lines).hasSize(2)
    }

    @Test
    fun `tts tokens collapse into per-code quantities`() {
        val parsed = DeckListParser.parseTts("VEN-001-1 VEN-002-1 VEN-021-1 VEN-021-1 VEN-R01-1")

        assertThat(parsed.source).isEqualTo(DeckListSource.TTS)
        assertThat(parsed.lines.map { it.token })
            .containsExactly("VEN-001", "VEN-002", "VEN-021", "VEN-R01").inOrder()
        assertThat(parsed.lines.first { it.token == "VEN-021" }.quantity).isEqualTo(2)
        // TTS says nothing about sections; every line has to be placed by card type.
        assertThat(parsed.lines.map { it.section }.distinct()).containsExactly(null)
    }

    @Test
    fun `tts parsing keeps the alternate-art suffix on the code`() {
        val parsed = DeckListParser.parseTts("OGN-021a-1")
        assertThat(parsed.lines.single().token).isEqualTo("OGN-021a")
    }

    @Test
    fun `name variants offer both the comma and the hyphen spelling`() {
        assertThat(DeckListParser.nameVariants("Ornn, Blacksmith"))
            .containsExactly("Ornn, Blacksmith", "Ornn - Blacksmith").inOrder()
        assertThat(DeckListParser.nameVariants("Ornn - Blacksmith"))
            .containsExactly("Ornn - Blacksmith", "Ornn, Blacksmith").inOrder()
        // A name with no separator to swap offers only itself.
        assertThat(DeckListParser.nameVariants("Scuttle Crab")).containsExactly("Scuttle Crab")
    }

    @Test
    fun `the raw spelling is tried first so a real comma in a name wins`() {
        // "Allay, Eager Admirer" is a real card name with a real comma in it.
        assertThat(DeckListParser.nameVariants("Allay, Eager Admirer").first())
            .isEqualTo("Allay, Eager Admirer")
    }
}
