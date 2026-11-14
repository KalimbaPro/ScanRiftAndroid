package com.scanrift.android.service.scanning

import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.domain.model.CardPrintings
import com.scanrift.android.domain.model.ScanLanguage
import java.util.Locale

/**
 * Works out which language a scanned copy was printed in. Pure, so every rule is unit
 * tested; iOS runs the same rules in the same order.
 *
 * First match wins:
 * 1. **Collector-line marker.** From Vendetta on (and on every Korean print) the
 *    bottom line ends `RAD • 010/167 • EN`. `EN`→en, `FR`→fr, `SC`/`CS`/`TC`→zh,
 *    `KR`→ko. The marker is searched only *after* a collector number on the same line,
 *    so an "EN" inside rules text never counts.
 * 2. **Script.** Hangul ⇒ Korean, Han ⇒ Chinese. Checked in that order because Korean
 *    text can carry the odd Han character, while Chinese never carries Hangul.
 * 3. **French vs English stop words** over the Latin text — covers French prints from
 *    before the marker existed.
 * 4. English.
 */
object LanguageDetector {

    /**
     * Everything a live scan knows once its card is matched.
     *
     * @param latinText the Latin pass, where small print (the marker) reads best.
     * @param chinesePass / [koreanPass] each CJK recogniser's own output, kept apart: the
     *   Chinese model happily emits Han for a Korean card, so their outputs are compared,
     *   not merged.
     * @param card the matched card, whose English and localised names are the evidence.
     */
    data class Evidence(
        val latinText: String,
        val chinesePass: String? = null,
        val koreanPass: String? = null,
        val card: Card? = null,
    )

    /** [detectMatched] unless the user fixed a scan language. */
    fun resolveMatched(preference: ScanLanguage, evidence: Evidence): Detection =
        preference.language?.let { Detection(it, Source.FIXED) } ?: detectMatched(evidence)

    /**
     * The live scanner's detection, strongest evidence first (iOS runs the same rules):
     *
     * 1. **Marker** after the collector number, from the Latin pass.
     * 2. **Name**: the OCR compared with the matched card's English name and its `zh` / `ko`
     *    localised names (bigram Dice); a clear winner decides.
     * 3. **Script**, comparing the two CJK passes: the Korean pass reading mostly Hangul is
     *    Korean; otherwise Han is Chinese. Korean prints always carry `KR` (Korea only has
     *    Origins) while Chinese Origins prints carry no marker, so unmarked CJK leans zh.
     * 4. **English vs French**: French only on clear evidence — accents and stop words
     *    outnumbering English ones two to one. English otherwise.
     */
    fun detectMatched(evidence: Evidence): Detection {
        markerLanguage(evidence.latinText)?.let { return Detection(it, Source.MARKER) }
        nameLanguage(evidence)?.let { return Detection(it, Source.NAME) }
        cjkLanguage(evidence.chinesePass, evidence.koreanPass)?.let { return Detection(it, Source.SCRIPT) }
        latinLanguage(evidence.latinText)?.let { return Detection(it, Source.STOP_WORDS) }
        return Detection(CardLanguage.EN, Source.DEFAULT)
    }

    /** Name scores per language, 0..1: how well each known name of [card] appears in its pass. */
    fun nameScores(evidence: Evidence): Map<CardLanguage, Float> {
        val card = evidence.card ?: return emptyMap()
        val scores = mutableMapOf<CardLanguage, Float>()
        englishNames(card).maxOfOrNull { TextSimilarity.bestLineScore(it, evidence.latinText) }
            ?.let { scores[CardLanguage.EN] = it }
        card.localizations[CardLanguage.ZH.value]?.name?.takeIf { it.isNotBlank() }?.let {
            scores[CardLanguage.ZH] = TextSimilarity.bestLineScore(it, evidence.chinesePass)
        }
        card.localizations[CardLanguage.KO.value]?.name?.takeIf { it.isNotBlank() }?.let {
            scores[CardLanguage.KO] = TextSimilarity.bestLineScore(it, evidence.koreanPass)
        }
        return scores
    }

    /** How well [card]'s English name appears in [latinText], 0..1. */
    fun englishNameScore(card: Card, latinText: String): Float =
        englishNames(card).maxOfOrNull { TextSimilarity.bestLineScore(it, latinText) } ?: 0f

    /**
     * Whether the Latin pass alone already settles the language: a printed marker, or the
     * English name. Otherwise the Chinese and Korean recognisers must read the frame.
     */
    fun latinPassSettles(latinText: String, card: Card): Boolean =
        markerLanguage(latinText) != null || englishNameScore(card, latinText) >= NAME_MIN_SCORE

    /** Step 2: the best-matching name, when it clears [NAME_MIN_SCORE] by [NAME_MARGIN]. */
    fun nameLanguage(evidence: Evidence): CardLanguage? {
        val ranked = nameScores(evidence).entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)?.value ?: 0f
        return best.key.takeIf { best.value >= NAME_MIN_SCORE && best.value - second >= NAME_MARGIN }
    }

    /**
     * The English spellings worth looking for: the whole base name, and the subtitle of
     * a "Champion - Subtitle" name. The champion part alone is not evidence — "Jinx" is
     * "Jinx" in French too — and neither is any part shorter than six letters.
     */
    fun englishNames(card: Card): List<String> {
        val base = CardPrintings.baseName(card.name)
        val parts = base.split(" - ", ", ").map { it.trim() }
        val subtitles = if (parts.size > 1) parts.drop(1) else emptyList()
        return (listOf(base) + subtitles).filter { TextSimilarity.normalize(it).length >= 6 }.distinct()
    }

    /** Step 3 with both recognisers' outputs. */
    fun cjkLanguage(chinesePass: String?, koreanPass: String?): CardLanguage? {
        val koHangul = count(koreanPass, Character.UnicodeScript.HANGUL)
        val koHan = count(koreanPass, Character.UnicodeScript.HAN)
        val zhHan = count(chinesePass, Character.UnicodeScript.HAN)
        val zhHangul = count(chinesePass, Character.UnicodeScript.HANGUL)
        // Each model reads the other script as noise: the Korean one turns a Chinese card
        // into a little stray Hangul, the Chinese one turns a Korean card into some Han.
        // Measured on official images, a Korean card's Korean pass yields at least ~1.3
        // Hangul per Han of the Chinese pass, a Chinese card's at most ~0.45.
        return when {
            koHangul + zhHangul >= MIN_SCRIPT_CHARS &&
                koHangul + zhHangul >= KOREAN_TO_HAN_RATIO * maxOf(zhHan, koHan) -> CardLanguage.KO
            zhHan >= MIN_SCRIPT_CHARS || koHan >= MIN_SCRIPT_CHARS -> CardLanguage.ZH
            else -> null
        }
    }

    /** Step 4: French only with a clear margin; English when there is any Latin evidence at all. */
    fun latinLanguage(text: String): CardLanguage? {
        val words = words(text)
        val accents = text.count { it in FRENCH_ACCENTS }
        val french = words.count { it in frenchWords } + accents
        val english = words.count { it in englishWords }
        if (french + english < MIN_STOP_WORD_HITS) return null
        return if (french >= MIN_STOP_WORD_HITS && french >= 2 * english + 1) CardLanguage.FR else CardLanguage.EN
    }

    private fun count(text: String?, script: Character.UnicodeScript): Int =
        text?.count { Character.UnicodeScript.of(it.code) == script } ?: 0

    private fun words(text: String): List<String> =
        text.lowercase(Locale.ROOT)
            .split(Regex("""[^\p{L}']+"""))
            .flatMap { it.split('\'') }
            .filter { it.isNotEmpty() }

    private const val KOREAN_TO_HAN_RATIO = 0.8f
    const val NAME_MIN_SCORE = 0.6f
    private const val NAME_MARGIN = 0.2f
    private const val FRENCH_ACCENTS = "éèàçùêâîôûëïÉÈÀÇ"

    enum class Source { MARKER, NAME, SCRIPT, STOP_WORDS, DEFAULT, FIXED }

    data class Detection(val language: CardLanguage, val source: Source)

    /** `010/167`, `303a/298`, signature `229*` numbers, tolerant of spaces OCR puts round the slash. */
    private val collectorNumber = Regex("""\d{1,3}[a-zA-Z*]?\s*/\s*\d{2,3}""")

    /** A marker token on its own: bullets often read as `·`, `*`, `-`, `.` or vanish. */
    private val markerToken = Regex("""(?<![A-Za-z])(EN|FR|SC|CS|TC|KR)(?![A-Za-z])""")

    private val markerLanguages = mapOf(
        "EN" to CardLanguage.EN,
        "FR" to CardLanguage.FR,
        "SC" to CardLanguage.ZH,
        "CS" to CardLanguage.ZH,
        "TC" to CardLanguage.ZH,
        "KR" to CardLanguage.KO,
    )

    private val frenchWords = setOf(
        "le", "la", "les", "de", "des", "du", "un", "une", "votre", "vos", "unité", "unités", "vous",
        "à", "et", "est", "pour", "avec", "sur", "dans", "qui", "au", "aux", "ce", "cette", "chaque",
        "piochez", "adversaire", "tour", "carte", "cartes", "lorsque", "quand", "si", "peut", "pouvez",
        "jouez", "joué", "gagnez", "ennemie", "ennemi", "alliée", "allié",
    )

    private val englishWords = setOf(
        "the", "your", "unit", "units", "you", "when", "and", "is", "for", "with", "on", "in", "of",
        "to", "a", "an", "each", "draw", "opponent", "turn", "card", "cards", "this", "that", "if",
        "may", "can", "play", "played", "gain", "enemy", "friendly", "it", "its", "at",
    )

    private const val MIN_SCRIPT_CHARS = 2
    private const val MIN_STOP_WORD_HITS = 3

    private val latinLetter = Regex("""[A-Za-z]""")

    /**
     * Whether the OCR name band holds a Latin card name (three or more Latin letters).
     * When it does not, the copy is probably a zh/ko print and the scanner runs the
     * Chinese and Korean recognizers on the frame.
     */
    fun hasLatinName(nameRegion: String?): Boolean =
        nameRegion != null && latinLetter.findAll(nameRegion).count() >= 3

    /** The language to record: a fixed [preference] wins; `auto` detects from [text]. */
    fun resolve(preference: ScanLanguage, text: String): Detection =
        preference.language?.let { Detection(it, Source.FIXED) } ?: detect(text)

    fun detect(text: String): Detection {
        markerLanguage(text)?.let { return Detection(it, Source.MARKER) }
        scriptLanguage(text)?.let { return Detection(it, Source.SCRIPT) }
        stopWordLanguage(text)?.let { return Detection(it, Source.STOP_WORDS) }
        return Detection(CardLanguage.EN, Source.DEFAULT)
    }

    /** Step 1: the printed `• EN|FR|SC|CS|TC|KR` after a collector number on the same line. */
    fun markerLanguage(text: String): CardLanguage? {
        for (line in text.lineSequence()) {
            val number = collectorNumber.find(line) ?: continue
            val tail = line.substring(number.range.last + 1)
            val marker = markerToken.find(tail) ?: continue
            return markerLanguages[marker.groupValues[1]]
        }
        return null
    }

    /** Step 2: Hangul before Han. */
    fun scriptLanguage(text: String): CardLanguage? {
        var hangul = 0
        var han = 0
        for (ch in text) {
            when (Character.UnicodeScript.of(ch.code)) {
                Character.UnicodeScript.HANGUL -> hangul++
                Character.UnicodeScript.HAN -> han++
                else -> Unit
            }
        }
        return when {
            hangul >= MIN_SCRIPT_CHARS -> CardLanguage.KO
            han >= MIN_SCRIPT_CHARS -> CardLanguage.ZH
            else -> null
        }
    }

    /**
     * Step 3: counts French and English function words. Needs a few hits before it
     * commits, and ties go to English — a card name alone ("Jinx") is no evidence.
     */
    fun stopWordLanguage(text: String): CardLanguage? {
        val words = text.lowercase(Locale.ROOT)
            .split(Regex("""[^\p{L}']+"""))
            .flatMap { it.split('\'') } // "l'unité" → "l", "unité"
            .filter { it.isNotEmpty() }
        val french = words.count { it in frenchWords }
        val english = words.count { it in englishWords }
        if (french + english < MIN_STOP_WORD_HITS) return null
        return if (french > english) CardLanguage.FR else CardLanguage.EN
    }
}
