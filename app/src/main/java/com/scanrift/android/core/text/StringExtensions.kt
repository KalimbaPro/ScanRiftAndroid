package com.scanrift.android.core.text

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * String helpers ported from iOS `String+Extensions.swift`.
 *
 * Note these fuzzy-matching helpers are **not** wired into the card matcher, on either
 * platform: `CardMatchingService` does strict card-code matching only. They are kept
 * because they are covered by the ported iOS test suite and are the obvious foundation
 * if fuzzy name matching is ever added.
 */

private val NON_ALPHANUMERIC = Regex("[^a-z0-9 ]")
private val WHITESPACE_RUN = Regex("\\s+")

/** Lowercase, trim, strip anything outside `[a-z0-9 ]`, then collapse whitespace runs. */
val String.normalized: String
    get() = lowercase(Locale.ROOT)
        .trim()
        .replace(NON_ALPHANUMERIC, "")
        .replace(WHITESPACE_RUN, " ")

/**
 * Undo the glyph confusions OCR makes on card text.
 *
 * Applied in the same order as iOS, which matters: `0 -> O` runs before `5 -> S`, so a
 * literal "05" becomes "OS".
 */
val String.cleanedForOCR: String
    get() = this
        .replace("0", "O")
        .replace("1", "I")
        .replace("5", "S")
        .replace("|", "I")
        .replace("’", "'")
        .replace("“", "\"")
        .replace("”", "\"")
        .trim()

/** Classic Levenshtein edit distance over the raw (un-normalised) characters. */
fun String.levenshteinDistance(other: String): Int {
    val source = this
    if (source.isEmpty()) return other.length
    if (other.isEmpty()) return source.length

    var previous = IntArray(other.length + 1) { it }
    var current = IntArray(other.length + 1)

    for (i in 1..source.length) {
        current[0] = i
        for (j in 1..other.length) {
            val substitutionCost = if (source[i - 1] == other[j - 1]) 0 else 1
            current[j] = min(
                min(previous[j] + 1, current[j - 1] + 1),
                previous[j - 1] + substitutionCost,
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[other.length]
}

/**
 * Similarity in 0.0..1.0.
 *
 * Faithful to iOS, including its quirk: the distance is measured on the *normalised*
 * strings but divided by the *raw* lengths, so a string with lots of punctuation scores
 * lower than a pure edit-distance ratio would suggest. Kept as-is for parity.
 */
fun String.similarity(other: String): Double {
    val distance = this.normalized.levenshteinDistance(other.normalized)
    val maxLength = max(this.length, other.length)
    if (maxLength == 0) return 1.0
    return 1.0 - (distance.toDouble() / maxLength.toDouble())
}

/** True when at least [threshold] of [other]'s words have a close match in this string. */
fun String.containsWords(from: String, threshold: Double = 0.8): Boolean {
    val selfWords = this.normalized.split(" ").filter { it.isNotEmpty() }
    val otherWords = from.normalized.split(" ").filter { it.isNotEmpty() }
    if (otherWords.isEmpty()) return false

    val matched = otherWords.count { otherWord ->
        selfWords.any { it.similarity(otherWord) >= threshold }
    }
    return matched.toDouble() / otherWords.size.toDouble() >= threshold
}

/** True for null, empty, or whitespace-only. */
fun String?.isNilOrEmpty(): Boolean = this == null || this.trim().isEmpty()
