package com.scanrift.android.data.remote

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.mapper.toDomain
import com.scanrift.android.data.remote.dto.LegalityResponseDto
import com.scanrift.android.data.remote.dto.PaginatedCardsResponse
import com.scanrift.android.data.remote.dto.PaginatedSetsResponse
import com.scanrift.android.data.remote.dto.VersionDto
import com.scanrift.android.di.SerializationModule
import com.scanrift.android.domain.model.CardLanguage
import java.io.File
import org.junit.Test

/**
 * Real responses from the live own API, captured 2026-11-14 into `test/resources/live`,
 * decoded with the exact `Json` configuration the app's Retrofit converter uses.
 *
 * - `cards_rad.json`      `/cards?set_id=RAD&size=5` — Radiance, zh localisations
 * - `cards_ogn_p6.json`   `/cards?set_id=OGN&size=5&page=6` — an errata card and a banned one, zh + ko
 * - `cards_jdg.json`      `/cards?set_id=JDG&size=5` — judge promos with `image_url: null`
 * - `sets.json`           `/sets`
 * - `legality.json`       `/legality?region=global`
 * - `version.json`        `/version`
 *
 * If the API changes shape, recapture these rather than editing them by hand.
 */
class LiveApiDecodeTest {

    private val json = SerializationModule.provideApiJson()

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream("live/$name")) { "missing live/$name" }
            .bufferedReader().use { it.readText() }

    private inline fun <reified T> decode(name: String): T = json.decodeFromString<T>(resource(name))

    @Test
    fun `version decodes`() {
        val version = decode<VersionDto>("version.json")

        assertThat(version.tag).matches("[0-9a-f]{16}")
        assertThat(version.generatedAt).isEqualTo("2026-11-14T15:40:23+00:00")
        assertThat(version.cardCount).isEqualTo(1708)
    }

    @Test
    fun `sets decode, despite cardmarket_id being a string or an array`() {
        val sets = decode<PaginatedSetsResponse>("sets.json")

        assertThat(sets.items).hasSize(12)
        assertThat(sets.items.map { it.setId }).containsAtLeast("OGN", "SFD", "UNL", "VEN", "RAD", "OPP", "PR", "JDG")
        assertThat(sets.items.single { it.setId == "RAD" }.cardCount).isEqualTo(219)
        // Captured together with version.json: the sets account for every card.
        assertThat(sets.items.sumOf { it.cardCount }).isEqualTo(decode<VersionDto>("version.json").cardCount)
    }

    @Test
    fun `radiance cards decode with paging and zh localisations`() {
        val page = decode<PaginatedCardsResponse>("cards_rad.json")

        assertThat(page.total).isEqualTo(219)
        assertThat(page.pages).isEqualTo(44)
        assertThat(page.tag).isNull()
        assertThat(page.items).hasSize(5)

        val card = page.items.first().toEntity().toDomain()
        assertThat(card.name).isEqualTo("Amateur Demolitionist")
        assertThat(card.publicCode).isEqualTo("RAD-001/167")
        assertThat(card.setLabel).isEqualTo("Radiance")
        assertThat(card.banned).isFalse()
        assertThat(card.errata).isNull()
        assertThat(card.localizations.getValue("zh").name).isNotEmpty()
        assertThat(card.imageUrl(CardLanguage.ZH)).isNotEqualTo(card.imageUrl(CardLanguage.EN))
        // Every card maps to an entity without throwing.
        page.items.forEach { it.toEntity() }
    }

    @Test
    fun `banned and errata cards decode with both localisations`() {
        val byId = decode<PaginatedCardsResponse>("cards_ogn_p6.json").items
            .associate { it.riftboundId to it.toEntity().toDomain() }

        val blindFury = byId.getValue("ogn-025-298")
        assertThat(blindFury.banned).isFalse()
        assertThat(blindFury.errata).startsWith("[Action]")
        assertThat(blindFury.errataDate).isEqualTo("2025-10-28")

        val brynhir = byId.getValue("ogn-026-298")
        assertThat(brynhir.banned).isTrue()
        assertThat(brynhir.bannedFormats).containsExactly("standard", "2v2").inOrder()
        assertThat(brynhir.errata).isNull()
        assertThat(brynhir.localizations.keys).containsExactly("zh", "ko")
        assertThat(brynhir.localizations.getValue("ko").name).isEqualTo("브린히르 썬더송")
        assertThat(brynhir.imageUrl(CardLanguage.KO)).isNotNull()

        // The API omits `banned` entirely on a legal card; that must read as legal.
        assertThat(byId.getValue("ogn-027-298").banned).isFalse()
    }

    @Test
    fun `judge promos decode with a null image`() {
        val cards = decode<PaginatedCardsResponse>("cards_jdg.json").items

        assertThat(cards).hasSize(3)
        cards.map { it.toEntity().toDomain() }.forEach { card ->
            assertThat(card.imageUrl).isNull()
            assertThat(card.imageUrl(CardLanguage.EN)).isNull()
        }
    }

    @Test
    fun `legality decodes`() {
        val legality = decode<LegalityResponseDto>("legality.json")

        assertThat(legality.region).isEqualTo("global")
        assertThat(legality.updatedAt).isNotNull()
        assertThat(legality.items).hasSize(127)
        assertThat(legality.items.count { it.banned }).isEqualTo(28)
        val brynhir = legality.items.single { it.riftboundId == "ogn-026-298" }
        assertThat(brynhir.banned).isTrue()
        assertThat(brynhir.bannedFormats).containsExactly("standard", "2v2")
        assertThat(legality.items.single { it.riftboundId == "ogn-025-298" }.errataDate).isEqualTo("2025-10-28")
    }

    @Test
    fun `the bundled seed carries a version tag`() {
        // Unit tests run from the module directory.
        val seed = json.decodeFromString<PaginatedCardsResponse>(File("src/main/res/raw/cards.json").readText())

        assertThat(seed.tag).matches("[0-9a-f]{16}")
        assertThat(seed.items.size).isAtLeast(1700)
    }
}
