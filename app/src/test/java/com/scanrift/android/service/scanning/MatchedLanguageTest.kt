package com.scanrift.android.service.scanning

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.domain.model.ScanLanguage
import com.scanrift.android.service.scanning.LanguageDetector.Evidence
import com.scanrift.android.service.scanning.LanguageDetector.Source
import org.junit.Test

/**
 * Language detection once the card is matched, on real card data and OCR output shaped
 * like the ML Kit benchmark's (including its noise).
 */
class MatchedLanguageTest {

    private val ballista = SeedCatalogue.card("ogn-017-298") // Iron Ballista / 钢铁弩炮 / 강철 쇠뇌

    private fun detect(e: Evidence) = LanguageDetector.detectMatched(e)

    // ── Fuzzy names ──────────────────────────────────────────────────────────

    @Test fun `bigram dice tolerates OCR noise and spacing`() {
        assertThat(TextSimilarity.dice("Iron Ballista", "IRON BALLISTA")).isEqualTo(1f)
        assertThat(TextSimilarity.dice("Iron Ballista", "lron Ba1lista")).isGreaterThan(0.6f)
        assertThat(TextSimilarity.dice("Iron Ballista", "Blazing Scorcher")).isLessThan(0.2f)
    }

    @Test fun `cjk names compare by character so one misread glyph still matches`() {
        // The benchmark read 钢铁弩炮 as 钢铁警炮.
        assertThat(TextSimilarity.dice("钢铁弩炮", "钢铁警炮")).isEqualTo(0.75f)
        assertThat(TextSimilarity.bestLineScore("钢铁弩炮", "3\n【安装备\n钢铁警炮\n此牌以休眠状态进场。")).isEqualTo(0.75f)
    }

    @Test fun `english names are the whole name and the subtitle, never the bare champion`() {
        val ahri = SeedCatalogue.card("ogn-066-298")
        assertThat(LanguageDetector.englishNames(ahri)).containsExactly("Ahri - Alluring", "Alluring")
    }

    // ── Evidence order ───────────────────────────────────────────────────────

    @Test fun `the marker wins`() {
        val e = Evidence(latinText = "OGN • 017/298 • KR", chinesePass = "钢铁弩炮", card = ballista)
        assertThat(detect(e)).isEqualTo(LanguageDetector.Detection(CardLanguage.KO, Source.MARKER))
    }

    @Test fun `the chinese name decides a chinese print the korean model filled with hangul noise`() {
        // Benchmark row: the Korean pass produced stray Hangul from a Chinese card.
        val e = Evidence(
            latinText = "3\n: tbhLêg g\$EAt, 2 Rt5.\nOGN• 017/298",
            chinesePass = "3\n【安装备\n钢铁警炮\n此牌以休眠状态进场。\nB: 对战场上的一名单位造成2点伤害。",
            koreanPass = "3\n: 것-온뿌 2 규.\nOGN• 017/298",
            card = ballista,
        )
        assertThat(detect(e)).isEqualTo(LanguageDetector.Detection(CardLanguage.ZH, Source.NAME))
    }

    @Test fun `the korean name decides a korean print without its marker`() {
        val e = Evidence(
            latinText = "OGN 017/2",
            chinesePass = "强铁 鲁弩",
            koreanPass = "강철 쇠뇌\n이 카드는 소진 상태로 들어옵니다.",
            card = ballista,
        )
        assertThat(detect(e).language).isEqualTo(CardLanguage.KO)
    }

    @Test fun `the english name decides english`() {
        val e = Evidence(latinText = "Iron Ballista\nThis enters exhausted.\nOGN 017/298", card = ballista)
        assertThat(detect(e)).isEqualTo(LanguageDetector.Detection(CardLanguage.EN, Source.NAME))
        assertThat(LanguageDetector.latinPassSettles(e.latinText, ballista)).isTrue()
    }

    @Test fun `latin noise off a chinese card does not settle the language`() {
        assertThat(LanguageDetector.latinPassSettles("H (EROSOGRTH. )\nOGN 024/298", ballista)).isFalse()
    }

    // ── Script ───────────────────────────────────────────────────────────────

    @Test fun `script compares the two passes rather than trusting any hangul`() {
        // Counts as measured: a Chinese card gives the Korean model ~0.3 Hangul per Han.
        val chinese = "钢".repeat(43)
        assertThat(LanguageDetector.cjkLanguage(chinesePass = chinese, koreanPass = "것".repeat(13))).isEqualTo(CardLanguage.ZH)
        // A Korean card: the Chinese model still emits some Han, but far less than the Hangul.
        assertThat(LanguageDetector.cjkLanguage(chinesePass = "钢".repeat(29), koreanPass = "강".repeat(37))).isEqualTo(CardLanguage.KO)
        assertThat(LanguageDetector.cjkLanguage(null, null)).isNull()
    }

    // ── English vs French ────────────────────────────────────────────────────

    @Test fun `english rules text from the catalogue reads as english`() {
        SeedCatalogue.cards.filter { it.setId == "OGN" }.mapNotNull { it.plainText }.filter { it.length > 60 }.take(40)
            .forEach { assertThat(LanguageDetector.latinLanguage(it) ?: CardLanguage.EN).isEqualTo(CardLanguage.EN) }
    }

    @Test fun `french rules text reads as french`() {
        // Official French wording of Iron Ballista and Blazing Scorcher.
        listOf(
            "Ce permanent arrive épuisé. Épuisez : Infligez 2 à une unité sur un champ de bataille.",
            "[Accélération] (Vous pouvez payer un coût supplémentaire pour que je arrive prête.)",
            "Quand vous jouez cette unité, piochez une carte. Les unités ennemies de la zone",
        ).forEach { assertThat(LanguageDetector.latinLanguage(it)).isEqualTo(CardLanguage.FR) }
    }

    @Test fun `a few french-looking words in english text are not enough`() {
        assertThat(LanguageDetector.latinLanguage("Deal 2 to a unit at a battlefield. Le Blanc")).isNotEqualTo(CardLanguage.FR)
    }

    @Test fun `a fixed scan language still overrides everything`() {
        val e = Evidence(latinText = "OGN • 017/298 • KR", card = ballista)
        assertThat(LanguageDetector.resolveMatched(ScanLanguage.FR, e).language).isEqualTo(CardLanguage.FR)
    }
}
