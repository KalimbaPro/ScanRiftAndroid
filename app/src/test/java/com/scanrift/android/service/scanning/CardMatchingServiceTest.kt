package com.scanrift.android.service.scanning

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import org.junit.Test

/**
 * Port of iOS `CardMatchingServiceTests`, plus coverage for the exact-match path that
 * the iOS suite leaves to manual testing.
 */
class CardMatchingServiceTest {

    private fun card(
        name: String = "Test Card",
        publicCode: String = "OGN-021/298",
        collectorNumber: Int = 21,
        setId: String = "OGN",
        cleanName: String = "Test Card",
    ) = Card(
        id = "id-$publicCode-$name",
        name = name,
        riftboundId = "ogn-021-298",
        publicCode = publicCode,
        collectorNumber = collectorNumber,
        type = "Unit",
        rarity = "Common",
        setId = setId,
        setLabel = "Origins",
        sourceSetId = setId,
        cleanName = cleanName,
    )

    // ── looksLikeCard ────────────────────────────────────────────────────────

    @Test fun `standard code looks like a card`() =
        assertThat(CardMatchingService(emptyList()).looksLikeCard("OGN-021")).isTrue()

    @Test fun `a code with a suffix looks like a card`() =
        assertThat(CardMatchingService(emptyList()).looksLikeCard("OGN-021a")).isTrue()

    @Test fun `a code with no separator looks like a card`() =
        assertThat(CardMatchingService(emptyList()).looksLikeCard("OGN021")).isTrue()

    @Test fun `a space separated code looks like a card`() =
        assertThat(CardMatchingService(emptyList()).looksLikeCard("OGN 021")).isTrue()

    @Test fun `a two letter set looks like a card`() =
        assertThat(CardMatchingService(emptyList()).looksLikeCard("AB-001")).isTrue()

    @Test fun `random text does not look like a card`() =
        assertThat(CardMatchingService(emptyList()).looksLikeCard("random text here")).isFalse()

    @Test fun `a single letter does not look like a card`() =
        assertThat(CardMatchingService(emptyList()).looksLikeCard("A")).isFalse()

    @Test fun `an empty string does not look like a card`() =
        assertThat(CardMatchingService(emptyList()).looksLikeCard("")).isFalse()

    @Test fun `a code embedded in other text is found`() =
        assertThat(CardMatchingService(emptyList()).looksLikeCard("Some text OGN-021 more text")).isTrue()

    @Test
    fun `OCR dash variants are accepted`() {
        // Vision and ML Kit both render the printed hyphen as an en or em dash often
        // enough that the pattern has to allow all three.
        val service = CardMatchingService(emptyList())
        assertThat(service.looksLikeCard("OGN–021")).isTrue()
        assertThat(service.looksLikeCard("OGN — 021")).isTrue()
    }

    // ── findByCardCode ───────────────────────────────────────────────────────

    @Test
    fun `an exact code match returns full confidence`() {
        val service = CardMatchingService(listOf(card()))
        val match = service.findByCardCode("OGN-021")

        assertThat(match).isNotNull()
        assertThat(match!!.card.publicCode).isEqualTo("OGN-021/298")
        assertThat(match.confidence).isEqualTo(1.0)
        assertThat(match.matchedText).isEqualTo("OGN-021")
    }

    @Test
    fun `an unpadded scan matches a padded card code`() {
        val service = CardMatchingService(listOf(card(publicCode = "OGN-007/298", collectorNumber = 7)))
        assertThat(service.findByCardCode("OGN-7")?.card?.collectorNumber).isEqualTo(7)
    }

    @Test
    fun `an alternate art suffix matches the alternate art card, not the base`() {
        val base = card(name = "Base", publicCode = "OGN-021/298")
        val alt = card(name = "Alt", publicCode = "OGN-021a/298")
        val service = CardMatchingService(listOf(base, alt))

        assertThat(service.findByCardCode("OGN-021a")?.card?.name).isEqualTo("Alt")
        assertThat(service.findByCardCode("OGN-021")?.card?.name).isEqualTo("Base")
    }

    @Test
    fun `a code from the wrong set does not match`() {
        val service = CardMatchingService(listOf(card(setId = "OGN")))
        assertThat(service.findByCardCode("SFD-021")).isNull()
    }

    @Test
    fun `a code whose number is not in the catalogue does not match`() {
        val service = CardMatchingService(listOf(card()))
        assertThat(service.findByCardCode("OGN-999")).isNull()
    }

    @Test
    fun `matching is case insensitive on the set code`() {
        val service = CardMatchingService(listOf(card()))
        assertThat(service.findByCardCode("ogn-021")).isNotNull()
    }

    // ── findMatch ────────────────────────────────────────────────────────────

    @Test
    fun `findMatch bails when the frame does not look like a card`() {
        val service = CardMatchingService(listOf(card()))
        val result = service.findMatch(OcrResult(extractedText = "just some words"))
        assertThat(result).isNull()
    }

    @Test
    fun `findMatch falls back to the set code band`() {
        // Full-frame text carries a code-shaped string that matches nothing; the
        // set-code band holds the real one.
        val service = CardMatchingService(listOf(card()))
        val result = service.findMatch(
            OcrResult(extractedText = "ABC-999 noise", setCodeRegion = "OGN-021"),
        )
        assertThat(result?.card?.publicCode).isEqualTo("OGN-021/298")
    }

    // ── searchCards ──────────────────────────────────────────────────────────

    @Test
    fun `search finds by name`() {
        val service = CardMatchingService(listOf(card(name = "Fire Dragon", cleanName = "Fire Dragon")))
        assertThat(service.searchCards("fire").map { it.name }).containsExactly("Fire Dragon")
    }

    @Test
    fun `search finds by clean name`() {
        val service = CardMatchingService(listOf(card(name = "Fire Dragon (Alt)", cleanName = "Fire Dragon")))
        assertThat(service.searchCards("fire dragon")).hasSize(1)
    }

    @Test
    fun `search finds by public code`() {
        val service = CardMatchingService(listOf(card(publicCode = "OGN-021")))
        assertThat(service.searchCards("ogn-021")).hasSize(1)
    }

    @Test
    fun `search is case insensitive`() {
        val service = CardMatchingService(listOf(card(name = "Fire Dragon", cleanName = "Fire Dragon")))
        assertThat(service.searchCards("FIRE")).hasSize(1)
    }

    @Test
    fun `search returns nothing for a single character query`() {
        val service = CardMatchingService(listOf(card(name = "Fire Dragon", cleanName = "Fire Dragon")))
        assertThat(service.searchCards("F")).isEmpty()
    }

    @Test
    fun `search returns nothing when there is no match`() {
        val service = CardMatchingService(listOf(card(name = "Fire Dragon", cleanName = "Fire Dragon")))
        assertThat(service.searchCards("ice golem")).isEmpty()
    }

    @Test
    fun `search caps results at twenty`() {
        val cards = (0 until 30).map {
            card(
                name = "Dragon $it",
                publicCode = "OGN-${"%03d".format(it)}",
                collectorNumber = it,
                cleanName = "Dragon $it",
            )
        }
        assertThat(CardMatchingService(cards).searchCards("dragon")).hasSize(20)
    }

    @Test
    fun `search trims whitespace`() {
        val service = CardMatchingService(listOf(card(name = "Fire Dragon", cleanName = "Fire Dragon")))
        assertThat(service.searchCards("  fire  ")).hasSize(1)
    }
}
