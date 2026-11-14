package com.scanrift.android.service.scanning

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.domain.model.ScanLanguage
import com.scanrift.android.service.scanning.LanguageDetector.Source
import org.junit.Test

class LanguageDetectorTest {

    // ── Step 1: collector-line marker ────────────────────────────────────────

    @Test
    fun `each printed marker maps to its language`() {
        val cases = mapOf(
            "RAD • 010/167 • EN" to CardLanguage.EN,
            "RAD • 010/167 • FR" to CardLanguage.FR,
            "RAD • 010/167 • SC" to CardLanguage.ZH,
            "RAD • 010/167 • CS" to CardLanguage.ZH,
            "RAD • 010/167 • TC" to CardLanguage.ZH,
            "OGN • 303/298 • KR" to CardLanguage.KO,
        )
        cases.forEach { (line, language) ->
            assertThat(LanguageDetector.markerLanguage(line)).isEqualTo(language)
            assertThat(LanguageDetector.detect(line).source).isEqualTo(Source.MARKER)
        }
    }

    @Test
    fun `the marker survives OCR mangling the bullets and spacing`() {
        assertThat(LanguageDetector.markerLanguage("VEN · 042a / 166 · FR")).isEqualTo(CardLanguage.FR)
        assertThat(LanguageDetector.markerLanguage("VEN-042a/166*FR")).isEqualTo(CardLanguage.FR)
        assertThat(LanguageDetector.markerLanguage("RAD 010/167 SC")).isEqualTo(CardLanguage.ZH)
        assertThat(LanguageDetector.markerLanguage("UNL • 229*/219 • EN")).isEqualTo(CardLanguage.EN)
    }

    @Test
    fun `a signed korean exclusive skips the S marker to reach KR`() {
        assertThat(LanguageDetector.markerLanguage("OGN-303a/298 • S • KR")).isEqualTo(CardLanguage.KO)
    }

    @Test
    fun `a marker-like token before the collector number or in rules text does not count`() {
        assertThat(LanguageDetector.markerLanguage("FR 010/167")).isNull()
        assertThat(LanguageDetector.markerLanguage("Draw a card.\nYou may play TC cards")).isNull()
        assertThat(LanguageDetector.markerLanguage("OGN-021/298")).isNull()
        // Part of a word, not a token.
        assertThat(LanguageDetector.markerLanguage("010/167 FRIEND")).isNull()
    }

    @Test
    fun `the marker wins over every later step`() {
        val text = "의 카드를 뽑습니다\nRAD • 010/167 • EN"
        assertThat(LanguageDetector.detect(text)).isEqualTo(LanguageDetector.Detection(CardLanguage.EN, Source.MARKER))
    }

    // ── Step 2: script ───────────────────────────────────────────────────────

    @Test
    fun `hangul means korean and han means chinese`() {
        assertThat(LanguageDetector.detect("리븐, 부러진 검\nOGN-001/298").language).isEqualTo(CardLanguage.KO)
        assertThat(LanguageDetector.detect("锐雯 折翼之刃\nOGN-001/298").language).isEqualTo(CardLanguage.ZH)
        assertThat(LanguageDetector.detect("锐雯").source).isEqualTo(Source.SCRIPT)
    }

    @Test
    fun `hangul is checked before han`() {
        assertThat(LanguageDetector.scriptLanguage("剑 리븐 부러진")).isEqualTo(CardLanguage.KO)
    }

    @Test
    fun `a single stray glyph is not evidence`() {
        assertThat(LanguageDetector.scriptLanguage("Jinx 中")).isNull()
    }

    // ── Step 3: stop words ───────────────────────────────────────────────────

    @Test
    fun `french rules text without a marker is french`() {
        val text = "Lorsque vous jouez cette unité, piochez une carte. Les unités ennemies de la zone"
        assertThat(LanguageDetector.detect(text)).isEqualTo(LanguageDetector.Detection(CardLanguage.FR, Source.STOP_WORDS))
    }

    @Test
    fun `elided articles still count`() {
        assertThat(LanguageDetector.stopWordLanguage("Donnez à l'unité et à l'adversaire une carte")).isEqualTo(CardLanguage.FR)
    }

    @Test
    fun `english rules text is english`() {
        val text = "When you play this unit, draw a card. Your units in the battlefield gain"
        assertThat(LanguageDetector.stopWordLanguage(text)).isEqualTo(CardLanguage.EN)
    }

    @Test
    fun `too little text is no evidence and falls through to english`() {
        assertThat(LanguageDetector.stopWordLanguage("Jinx")).isNull()
        assertThat(LanguageDetector.detect("Jinx\nOGN-001/298")).isEqualTo(LanguageDetector.Detection(CardLanguage.EN, Source.DEFAULT))
    }

    // ── Preference ───────────────────────────────────────────────────────────

    @Test
    fun `a fixed scan language overrides detection`() {
        val detection = LanguageDetector.resolve(ScanLanguage.KO, "RAD • 010/167 • FR")
        assertThat(detection).isEqualTo(LanguageDetector.Detection(CardLanguage.KO, Source.FIXED))
    }

    @Test
    fun `auto detects`() {
        assertThat(LanguageDetector.resolve(ScanLanguage.AUTO, "RAD • 010/167 • FR").language).isEqualTo(CardLanguage.FR)
    }
}

class LatinNameTest {
    @Test
    fun `a latin name skips the script pass and a cjk or empty band does not`() {
        assertThat(LanguageDetector.hasLatinName("Riven, Broken Blade")).isTrue()
        assertThat(LanguageDetector.hasLatinName("锐雯 折翼之刃")).isFalse()
        assertThat(LanguageDetector.hasLatinName("리븐")).isFalse()
        assertThat(LanguageDetector.hasLatinName(null)).isFalse()
        assertThat(LanguageDetector.hasLatinName("3 2")).isFalse()
    }
}
