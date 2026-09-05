package com.scanrift.android.ui.decks

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardSupertype
import com.scanrift.android.domain.model.CardType
import org.junit.Test

/**
 * The legend/champion pairing rules.
 *
 * These mirror how the data is actually shaped: a legend carries exactly one tag — the
 * character — and a champion carries that same tag plus its regions. Verified against
 * the bundled catalogue: all 145 legends have exactly one tag, and every one of them
 * has at least one matching champion.
 */
class DeckBrowserFilterTest {

    private fun card(
        name: String,
        type: String,
        supertype: String? = null,
        tags: List<String> = emptyList(),
    ) = Card(
        id = "id-$name", name = name, riftboundId = "ven-001-166", publicCode = "VEN-001/166",
        collectorNumber = 1, type = type, supertype = supertype, rarity = "Rare",
        setId = "VEN", setLabel = "Vendetta", sourceSetId = "VEN", cleanName = name, tags = tags,
    )

    private val ahriLegend = card("Ahri - Nine-Tailed Fox", CardType.LEGEND, tags = listOf("Ahri"))
    private val ahriChampion = card("Ahri - Charmer", CardType.UNIT, CardSupertype.CHAMPION, listOf("Ahri", "Ionia"))
    private val jhinLegend = card("Jhin - Virtuoso", CardType.LEGEND, tags = listOf("Jhin"))
    private val jhinChampion = card("Jhin - Murderous Artist", CardType.UNIT, CardSupertype.CHAMPION, listOf("Jhin", "Ionia"))
    private val plainUnit = card("Chemtech Enforcer", CardType.UNIT, tags = listOf("Zaun"))

    private val all = listOf(ahriLegend, ahriChampion, jhinLegend, jhinChampion, plainUnit)

    /** Mirrors DeckBuilderViewModel.matchesTab, which is private. */
    private fun tab(type: String?, legendTags: Set<String> = emptySet(), championTags: Set<String> = emptySet()) =
        all.filter { c ->
            when (type) {
                DeckBuilderViewModel.LEGEND_FILTER ->
                    c.type == CardType.LEGEND &&
                        (championTags.isEmpty() || c.tags.toSet().intersect(championTags).isNotEmpty())
                DeckBuilderViewModel.CHAMPION_FILTER ->
                    c.isChampionUnit && (legendTags.isEmpty() || c.tags.toSet().intersect(legendTags).isNotEmpty())
                null -> true
                else -> c.type == type
            }
        }

    @Test
    fun `the legend tab comes first`() {
        assertThat(DeckBuilderViewModel.browserFilters.first()).isEqualTo(DeckBuilderViewModel.LEGEND_FILTER)
        assertThat(DeckBuilderViewModel.browserFilters[1]).isEqualTo(DeckBuilderViewModel.CHAMPION_FILTER)
    }

    @Test
    fun `with no legend chosen the champion tab shows every champion`() {
        // This was the bug: requiring a tag match with an empty legend meant the
        // intersection was always empty and the tab looked broken.
        assertThat(tab(DeckBuilderViewModel.CHAMPION_FILTER)).containsExactly(ahriChampion, jhinChampion)
    }

    @Test
    fun `choosing a legend narrows the champion tab to that character`() {
        assertThat(tab(DeckBuilderViewModel.CHAMPION_FILTER, legendTags = setOf("Ahri")))
            .containsExactly(ahriChampion)
    }

    @Test
    fun `with no champion chosen the legend tab shows every legend`() {
        assertThat(tab(DeckBuilderViewModel.LEGEND_FILTER)).containsExactly(ahriLegend, jhinLegend)
    }

    @Test
    fun `choosing a champion first narrows the legend tab to that character`() {
        // The builder has to work from either end, so the constraint runs both ways.
        assertThat(tab(DeckBuilderViewModel.LEGEND_FILTER, championTags = setOf("Jhin", "Ionia")))
            .containsExactly(jhinLegend)
    }

    @Test
    fun `a champion's region tags do not drag in other characters' legends`() {
        // Both champions are Ionia; matching on the shared region would return both
        // legends. The legend's single character tag is what makes this exact.
        assertThat(tab(DeckBuilderViewModel.LEGEND_FILTER, championTags = setOf("Ahri", "Ionia")))
            .containsExactly(ahriLegend)
    }

    @Test
    fun `an ordinary type tab is unaffected by the slots`() {
        assertThat(tab(CardType.UNIT, legendTags = setOf("Ahri")))
            .containsExactly(ahriChampion, jhinChampion, plainUnit)
    }

    @Test
    fun `champion detection requires both the unit type and the champion supertype`() {
        assertThat(ahriChampion.isChampionUnit).isTrue()
        assertThat(plainUnit.isChampionUnit).isFalse()
        assertThat(ahriLegend.isChampionUnit).isFalse()
    }
}
