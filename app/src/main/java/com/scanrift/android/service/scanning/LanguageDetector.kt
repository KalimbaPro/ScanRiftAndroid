package com.scanrift.android.service.scanning

import com.scanrift.android.domain.model.CardLanguage
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

    enum class Source { MARKER, SCRIPT, STOP_WORDS, DEFAULT, FIXED }

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
        "et", "est", "pour", "avec", "sur", "dans", "qui", "au", "aux", "ce", "cette", "chaque",
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
