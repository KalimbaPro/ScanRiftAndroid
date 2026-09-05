package com.scanrift.android.service.scanning

import com.scanrift.android.domain.model.Card
import java.util.Locale

/**
 * Turns OCR text into a card.
 *
 * Despite the "matching" name this is **not** fuzzy: it reads the printed card code
 * (`OGN-021a`) with three regexes and then requires an exact triple match on set id,
 * collector number and public code. A match is either certain or absent, which is why
 * every result carries `confidence = 1.0` and there is no candidate ranking.
 *
 * That design has a consequence worth knowing: it depends entirely on `publicCode`
 * being correct. Every card the live API returns has `public_code: null`, so the
 * derivation in `CardDto.resolvedPublicCode` is load-bearing — get it wrong and the
 * scanner silently matches nothing at all.
 *
 * The Levenshtein helpers in `core/text` are unused here, exactly as on iOS.
 */
class CardMatchingService(private val cards: List<Card>) {

    /**
     * Set code, collector number, optional suffix. Ordered most- to least-specific.
     *
     * The en dash and em dash alternatives in the first pattern matter: OCR frequently
     * renders the hyphen in `OGN-021` as one of those.
     */
    private val patterns = listOf(
        Regex("""([A-Z]{2,4})\s*[-–—]\s*(\d{1,3})([a-zA-Z*]?)""", RegexOption.IGNORE_CASE),
        Regex("""([A-Z]{2,4})(\d{3})([a-zA-Z*]?)""", RegexOption.IGNORE_CASE),
        Regex("""([A-Z]{2,4})\s+(\d{1,3})([a-zA-Z*]?)""", RegexOption.IGNORE_CASE),
    )

    /** Cheap gate so the pipeline ignores frames that clearly aren't a card. */
    fun looksLikeCard(text: String): Boolean = patterns.any { it.containsMatchIn(text) }

    fun findMatch(ocr: OcrResult): MatchCandidate? {
        if (!looksLikeCard(ocr.extractedText)) return null
        findByCardCode(ocr.extractedText)?.let { return it }
        // The set-code band alone is a useful second pass: it is where the code is
        // printed, so it has far less noise than the full-frame text.
        ocr.setCodeRegion?.let { region -> findByCardCode(region)?.let { return it } }
        return null
    }

    fun findByCardCode(text: String): MatchCandidate? {
        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                tryMatch(match)?.let { return it }
            }
        }
        return null
    }

    private fun tryMatch(match: MatchResult): MatchCandidate? {
        val setCode = match.groupValues[1].uppercase(Locale.ROOT)
        val number = match.groupValues[2].toIntOrNull() ?: return null
        val suffix = match.groupValues.getOrNull(3).orEmpty()

        // Locale.ROOT: an Arabic-locale device would otherwise produce Arabic-Indic
        // digits here and no card would ever match.
        val padded = "%03d".format(Locale.ROOT, number)
        val expectedCode = "$setCode-$padded$suffix".uppercase(Locale.ROOT)

        val card = cards.firstOrNull {
            it.setId.uppercase(Locale.ROOT) == setCode &&
                it.collectorNumber == number &&
                it.publicCodePrefix.uppercase(Locale.ROOT) == expectedCode
        } ?: return null

        return MatchCandidate(card = card, confidence = 1.0, matchedText = "$setCode-$padded$suffix")
    }

    /** Manual-correction search: substring over name, cleanName and public code. */
    fun searchCards(query: String): List<Card> {
        val trimmed = query.trim().lowercase(Locale.ROOT)
        if (trimmed.length < MIN_SEARCH_LENGTH) return emptyList()
        return cards.filter {
            it.name.lowercase(Locale.ROOT).contains(trimmed) ||
                it.cleanName.lowercase(Locale.ROOT).contains(trimmed) ||
                it.publicCode.lowercase(Locale.ROOT).contains(trimmed)
        }.take(MAX_SEARCH_RESULTS)
    }

    private companion object {
        const val MIN_SEARCH_LENGTH = 2
        const val MAX_SEARCH_RESULTS = 20
    }
}

data class MatchCandidate(
    val card: Card,
    val confidence: Double,
    val matchedText: String,
)
