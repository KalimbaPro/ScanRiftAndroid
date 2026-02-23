package com.scanrift.android.service.scanning

import com.scanrift.android.data.local.entity.CardEntity
import timber.log.Timber

/**
 * Candidate match result from the card matching service.
 */
data class MatchCandidate(
    val card: CardEntity,
    val confidence: Double,
    val matchedText: String
)

/**
 * Card matching service — direct port of iOS CardMatchingService.swift.
 *
 * Uses regex patterns to extract card codes like "OGN-021", "OGN-021a", "OGN-300*"
 * from OCR text and matches them against the card database.
 */
class CardMatchingService(private val cards: List<CardEntity>) {

    // Regex patterns for card codes
    // Group 1: set code, Group 2: number, Group 3: optional suffix (a, b, *, etc.)
    private val codePatterns = listOf(
        Regex("""([A-Z]{2,4})\s*[-\u2013\u2014]\s*(\d{1,3})([a-zA-Z*]?)""", RegexOption.IGNORE_CASE),  // OGN-021a or OGN - 021
        Regex("""([A-Z]{2,4})(\d{3})([a-zA-Z*]?)""", RegexOption.IGNORE_CASE),                          // OGN021a (no separator)
        Regex("""([A-Z]{2,4})\s+(\d{1,3})([a-zA-Z*]?)""", RegexOption.IGNORE_CASE)                      // OGN 021a (space separator)
    )

    init {
        Timber.d("CardMatchingService initialized with %d cards", cards.size)
    }

    /**
     * Check if text looks like it could be from a card (has card code pattern).
     */
    fun looksLikeCard(text: String): Boolean {
        return codePatterns.any { it.containsMatchIn(text) }
    }

    /**
     * Find the best match — returns null if no confident match found.
     */
    fun findMatch(ocrResult: OCRResult): MatchCandidate? {
        val fullText = ocrResult.extractedText

        // First check if this looks like a card at all
        if (!looksLikeCard(fullText)) {
            Timber.d("Text doesn't look like a card, skipping")
            return null
        }

        Timber.d("Full OCR text: %s", fullText)

        // Try to find card code pattern in full text
        findByCardCode(fullText)?.let {
            Timber.d("Found by card code")
            return it
        }

        // Try set code region specifically
        ocrResult.setCodeRegion?.takeIf { it.isNotEmpty() }?.let { setCode ->
            Timber.d("Trying set code region: %s", setCode)
            findByCardCode(setCode)?.let {
                Timber.d("Found by set code region")
                return it
            }
        }

        Timber.d("No confident match found")
        return null
    }

    /**
     * Search cards by name for manual correction.
     */
    fun searchCards(query: String): List<CardEntity> {
        val normalizedQuery = query.lowercase().trim()
        if (normalizedQuery.length < 2) return emptyList()

        return cards.filter { card ->
            card.name.lowercase().contains(normalizedQuery) ||
                    card.cleanName.lowercase().contains(normalizedQuery) ||
                    card.publicCode.lowercase().contains(normalizedQuery)
        }.take(20)
    }

    private fun findByCardCode(text: String): MatchCandidate? {
        for (pattern in codePatterns) {
            tryCodePattern(pattern, text)?.let { return it }
        }
        return null
    }

    private fun tryCodePattern(pattern: Regex, text: String): MatchCandidate? {
        val matches = pattern.findAll(text)

        for (match in matches) {
            val setCode = match.groupValues[1].uppercase()
            val numberStr = match.groupValues[2]
            val suffix = match.groupValues.getOrElse(3) { "" }

            val number = numberStr.toIntOrNull() ?: continue
            val paddedNumber = "%03d".format(number)
            val expectedCode = "$setCode-$paddedNumber$suffix".uppercase()

            Timber.d("Extracted code - Set: '%s', Number: %d, Suffix: '%s', Expected: '%s'",
                setCode, number, suffix, expectedCode)

            // Find matching card by setId, collectorNumber, AND exact code (including suffix)
            for (card in cards) {
                val cardSetId = card.setId.uppercase()

                if (cardSetId == setCode && card.collectorNumber == number) {
                    // Extract the code portion of publicCode (before "/" if present)
                    val cardCodePart = card.publicCode.uppercase().split("/").firstOrNull()
                        ?: card.publicCode.uppercase()

                    if (cardCodePart == expectedCode) {
                        Timber.d("Verified match '%s' - code %s, publicCode: %s",
                            card.name, expectedCode, card.publicCode)
                        return MatchCandidate(
                            card = card,
                            confidence = 1.0,
                            matchedText = "$setCode-$paddedNumber$suffix"
                        )
                    }
                }
            }
        }

        return null
    }
}
