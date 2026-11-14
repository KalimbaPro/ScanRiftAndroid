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
     * renders the hyphen in `OGN-021` as one of those. The bullets are the newer
     * collector line, `RAD • 010/167 • EN`, which separates set and number with `•`
     * (often read as `·` or `∙`).
     */
    private val patterns = listOf(
        Regex("""([A-Z]{2,4})\s*[-–—•·∙]\s*(\d{1,3})([a-zA-Z*]?)""", RegexOption.IGNORE_CASE),
        Regex("""([A-Z]{2,4})(\d{3})([a-zA-Z*]?)""", RegexOption.IGNORE_CASE),
        Regex("""([A-Z]{2,4})\s+(\d{1,3})([a-zA-Z*]?)""", RegexOption.IGNORE_CASE),
    )

    /** Cheap gate so the pipeline ignores frames that clearly aren't a card. */
    fun looksLikeCard(text: String): Boolean = patterns.any { it.containsMatchIn(text) }

    fun findMatch(ocr: OcrResult): MatchCandidate? {
        val text = CollectorText.normalize(ocr.extractedText)
        if (!looksLikeCard(text)) return null
        findByCardCode(text)?.let { return it }
        // The set-code band alone is a useful second pass: it is where the code is
        // printed, so it has far less noise than the full-frame text.
        ocr.setCodeRegion?.let { region -> findByCardCode(CollectorText.normalize(region))?.let { return it } }
        return null
    }

    fun findByCardCode(text: String): MatchCandidate? {
        val normalized = CollectorText.normalize(text)
        for (pattern in patterns) {
            for (match in pattern.findAll(normalized)) {
                tryMatch(match, normalized)?.let { return it }
            }
        }
        // A letter suffix that names no printing ("066e" for a card with no alternate art)
        // is OCR noise more often than not: retry on the bare number. Never for `*` or a
        // short number — "23*" is a signature that lost a digit, not card 23.
        for (pattern in patterns) {
            for (match in pattern.findAll(normalized)) {
                val suffix = match.groupValues.getOrNull(3).orEmpty()
                if (suffix.isEmpty() || !suffix[0].isLetter() || match.groupValues[2].length < 3) continue
                tryMatch(match, normalized, ignoreSuffix = true)?.let { return it }
            }
        }
        return null
    }

    /** Every printing sharing [card]'s set and collector number (base, `a`, `*`, …), [card] included. */
    fun printingsAtNumber(card: Card): List<Card> =
        byNumber[card.setId.uppercase(Locale.ROOT) to card.collectorNumber].orEmpty()

    private val byNumber: Map<Pair<String, Int>, List<Card>> by lazy {
        cards.groupBy { it.setId.uppercase(Locale.ROOT) to it.collectorNumber }
    }

    /** Set id → the printed totals after the slash (`298` for Origins). */
    private val totalsBySet: Map<String, Set<String>> by lazy {
        cards.groupBy { it.setId.uppercase(Locale.ROOT) }
            .mapValues { (_, list) -> list.mapNotNullTo(HashSet()) { it.publicCode.substringAfter('/', "").takeIf(String::isNotEmpty) } }
    }

    /**
     * A set code OCR got one letter wrong (`SED` for `SFD`): the one known set id at one
     * substitution whose printed total is the total that follows the number. The total
     * is what makes this safe — a wrong letter alone is not enough.
     */
    private fun repairedSet(setCode: String, total: String?): String? {
        if (total == null || setCode in totalsBySet) return null
        return totalsBySet.keys.singleOrNull { known ->
            known.length == setCode.length &&
                known.indices.count { known[it] != setCode[it] } == 1 &&
                total in totalsBySet.getValue(known)
        }
    }

    private val totalAfter = Regex("""^\s*/\s*(\d{2,3})""")

    private fun tryMatch(match: MatchResult, text: String, ignoreSuffix: Boolean = false): MatchCandidate? {
        val total = totalAfter.find(text.substring(match.range.last + 1))?.groupValues?.get(1)
        val readSet = match.groupValues[1].uppercase(Locale.ROOT)
        val setCode = repairedSet(readSet, total) ?: readSet
        val number = match.groupValues[2].toIntOrNull() ?: return null
        val suffix = if (ignoreSuffix) "" else match.groupValues.getOrNull(3).orEmpty()

        // Locale.ROOT: an Arabic-locale device would otherwise produce Arabic-Indic
        // digits here and no card would ever match.
        val padded = "%03d".format(Locale.ROOT, number)
        val expectedCode = "$setCode-$padded$suffix".uppercase(Locale.ROOT)

        val card = cards.firstOrNull {
            it.setId.uppercase(Locale.ROOT) == setCode &&
                it.collectorNumber == number &&
                it.publicCodePrefix.uppercase(Locale.ROOT) == expectedCode
        } ?: return null

        // Read without its suffix, the code also names every suffixed printing of that
        // number: the scan cannot tell `066` from `066a` by the code alone.
        val siblings = if (suffix.isEmpty()) printingsAtNumber(card) else listOf(card)
        return MatchCandidate(
            card = card,
            confidence = 1.0,
            matchedText = "$setCode-$padded$suffix",
            unresolvedPrintings = siblings.takeIf { it.size > 1 }.orEmpty(),
        )
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

/**
 * @param unresolvedPrintings non-empty when the code was read without a variant suffix
 *   and several printings share its number: [card] is then the base printing and these
 *   are all the candidates (base included), for [PrintingChooser] to settle.
 */
data class MatchCandidate(
    val card: Card,
    val confidence: Double,
    val matchedText: String,
    val unresolvedPrintings: List<Card> = emptyList(),
) {
    val isAmbiguous: Boolean get() = unresolvedPrintings.size > 1
}

/**
 * Repairs the usual OCR damage to a collector line before the code regexes run:
 *
 * - `066 a/298` → `066a/298` (space before the suffix)
 * - `066o/298`, `066d/298` → `066a/298` (a read as o or d)
 * - `227×`, `227✱`, `227+`, `227x` before the slash → `227*` (signature star)
 * - a slash read as `1`, `l`, `I` or `|` (`066a1298` → `066a/298`)
 * - `l`/`I`/`|` for 1 and `O` for 0 inside the number (`1l6/298`, `l81/167`)
 * - a digit for a letter in the set code (`0GN` → `OGN`)
 *
 * A set code with one wrong letter (`SED 020a/221`) is repaired at match time, where
 * the printed total can confirm it.
 *
 * Each rule needs the slash (or the three-digit total) to anchor it, so rules text is
 * left alone. iOS applies the same rules.
 */
object CollectorText {
    private val star = Regex("""(\d{1,3})\s*[*×✱✳＊+xX]\s*(?=/)""")
    private val spacedSuffix = Regex("""(\d{1,3})\s+([a-z])\s*(?=/)""")
    private val misreadA = Regex("""(\d{3})[od](?=\s*/)""")

    /** `0GN`, `5FD`: a digit read for a letter in the set code (one letter at least must remain). */
    private val setLookalikes = Regex("""(?<![A-Za-z0-9])(?=[A-Z0-9]{3}\b)(?=[^\s]*[A-Z])([A-Z0-9]{3})(?=\s*[•·∙\-–—]?\s*\d{1,3}[a-z*]?\s*/)""")

    /** `1l6`, `l81`, `O66`: l/I/| for 1 and O/o for 0 inside a number that runs into the slash. */
    private val digitLookalikes = Regex("""(?<=[\s•·∙\-–—])([0-9lI|Oo]{3})(?=[a-z*]?\s*/)""")

    fun normalize(text: String): String = text
        .replace(digitLookalikes) { m ->
            m.value.map { c -> when (c) { 'l', 'I', '|' -> '1'; 'O', 'o' -> '0'; else -> c } }.joinToString("")
        }
        .replace(setLookalikes) { m ->
            m.value.map { c -> when (c) { '0' -> 'O'; '1' -> 'I'; '5' -> 'S'; '8' -> 'B'; else -> c } }.joinToString("")
        }
        .replace(star) { "${it.groupValues[1]}*" }
        .replace(spacedSuffix) { "${it.groupValues[1]}${it.groupValues[2]}" }
        .replace(misreadA) { "${it.groupValues[1]}a" }
        .let { fixSlash(it) }

    /** `066a1298`/`066l298` → `/`, but never inside a longer digit run. */
    private fun fixSlash(text: String): String =
        Regex("""(?<!\d)(\d{3}[a-z*]?)[1lI|](\d{3})(?!\d)""").replace(text) { "${it.groupValues[1]}/${it.groupValues[2]}" }
}
