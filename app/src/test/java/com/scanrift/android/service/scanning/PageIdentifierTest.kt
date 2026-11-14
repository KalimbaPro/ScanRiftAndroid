package com.scanrift.android.service.scanning

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.domain.model.ScanLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Binder pages through the live scanner's rules: a fabricated row of three cards, each
 * in a different language, one with a damaged set code and one with no variant suffix.
 */
class PageIdentifierTest {

    private val identifier = ScanIdentifier(CardMatchingService(SeedCatalogue.cards))
    private val knownSets = SeedCatalogue.cards.flatMapTo(HashSet()) { listOf(it.setId, it.sourceSetId) }

    /** Card column [col] (0..2) of a 3-wide row: lines placed inside that column. */
    private fun at(col: Int, text: String, top: Float, left: Float = 0.03f, width: Float = 0.2f) =
        TextLine(text, NormalizedBox(col / 3f + left, top, col / 3f + left + width, top + 0.02f))

    private val latin = listOf(
        // Column 0: an English Iron Ballista.
        at(0, "Iron Ballista", 0.35f),
        at(0, "This enters exhausted.", 0.50f),
        at(0, "OGN • 017/298", 0.78f),
        // Column 1: a Chinese Iron Ballista; Latin OCR only finds noise and a 0GN code.
        at(1, "H (EROSOGRTH. )", 0.35f),
        at(1, "0GN• 017/298", 0.78f),
        // Column 2: an English Ahri, code read without its alternate-art suffix.
        at(2, "Ahri - Alluring", 0.35f),
        at(2, "When I hold, you score 1 point.", 0.50f),
        at(2, "OGN • 066/298", 0.78f),
    )
    private val chinese = listOf(at(1, "钢铁警炮", 0.35f), at(1, "此牌以休眠状态进场。", 0.50f), at(0, "铁", 0.35f))
    private val korean = listOf(at(1, "것-온뿌 2 규.", 0.50f))

    private val page = MultiCardScanner.scan(latin, knownSets, imageAspect = 1.4f)

    @Test fun `every card is read, the damaged set code included`() {
        assertThat(page.cards.map { it.code }).containsExactly("OGN-017", "OGN-017", "OGN-066").inOrder()
    }

    @Test fun `the cjk passes run only because one card's latin text settles nothing`() {
        val latinOnly = PageIdentifier.pageOcr(latin)
        assertThat(PageIdentifier.needsScriptPass(page, latinOnly, identifier, ScanLanguage.AUTO)).isTrue()
        assertThat(PageIdentifier.needsScriptPass(page, latinOnly, identifier, ScanLanguage.EN)).isFalse()
        val englishOnly = MultiCardScanner.scan(latin.filter { it.box.left < 1 / 3f }, knownSets, 1.4f)
        assertThat(PageIdentifier.needsScriptPass(englishOnly, latinOnly, identifier, ScanLanguage.AUTO)).isFalse()
    }

    @Test fun `each card gets its own language from its own region`() = runTest {
        val results = PageIdentifier.identify(
            page, PageIdentifier.pageOcr(latin, chinese, korean, 1.4f), identifier, ScanLanguage.AUTO,
        )
        assertThat(results.map { it.outcome.match?.card?.riftboundId })
            .containsExactly("ogn-017-298", "ogn-017-298", "ogn-066-298").inOrder()
        assertThat(results.map { it.outcome.language.language })
            .containsExactly(CardLanguage.EN, CardLanguage.ZH, CardLanguage.EN).inOrder()
        // No art to compare: the suffix-less Ahri stays the base printing, flagged.
        assertThat(results.map { it.outcome.ambiguous }).containsExactly(false, false, true).inOrder()
    }

    @Test fun `the art tie-break settles a suffix-less code per card`() = runTest {
        val alt = SeedCatalogue.card("ogn-066a-298")
        val results = PageIdentifier.identify(
            page, PageIdentifier.pageOcr(latin, chinese, korean, 1.4f), identifier, ScanLanguage.AUTO,
            frameFor = { FrameArt { -1L } },
            art = PrintingArt { card -> if (card == alt) -1L else 0L },
        )
        assertThat(results.last().outcome.match?.card).isEqualTo(alt)
        assertThat(results.last().outcome.ambiguous).isFalse()
    }

    @Test fun `a fixed scan language applies to every card`() = runTest {
        val results = PageIdentifier.identify(page, PageIdentifier.pageOcr(latin, chinese, korean), identifier, ScanLanguage.KO)
        assertThat(results.map { it.outcome.language.language }.toSet()).containsExactly(CardLanguage.KO)
    }
}
