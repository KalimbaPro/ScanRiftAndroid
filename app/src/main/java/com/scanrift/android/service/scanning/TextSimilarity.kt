package com.scanrift.android.service.scanning

import java.text.Normalizer
import java.util.Locale

/**
 * Fuzzy text comparison for OCR output against known names. Pure; iOS uses the same
 * normalisation and the same character-bigram Dice score.
 */
object TextSimilarity {

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** NFKC, case-folded, accents stripped, spaces and punctuation removed. */
    fun normalize(text: String): String =
        Normalizer.normalize(Normalizer.normalize(text, Normalizer.Form.NFKC), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)
            .replace(NOT_WORD, "")

    /**
     * Sørensen–Dice over character bigrams of the normalised strings, 0..1. Strings of one
     * character compare by equality. CJK names are short, so bigrams (not words) are the
     * unit for every script.
     */
    fun dice(a: String, b: String): Float {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0f
        if (x == y) return 1f
        // CJK names are a handful of characters, and one misread glyph destroys two of
        // three bigrams: compare characters instead.
        if (isCjk(x) || isCjk(y)) return unigramDice(x, y)
        if (x.length < 2 || y.length < 2) return 0f
        val bx = bigrams(x)
        val by = bigrams(y).toMutableList()
        var shared = 0
        for (g in bx) {
            val i = by.indexOf(g)
            if (i >= 0) {
                shared++
                by.removeAt(i)
            }
        }
        return 2f * shared / (bx.size + bigrams(y).size)
    }

    /**
     * How well [name] appears somewhere in [text]: the best [dice] of [name] against any
     * line of [text], and against any run of up to three consecutive lines (a name OCR'd
     * across two lines).
     */
    fun bestLineScore(name: String, text: String?): Float {
        if (text.isNullOrBlank() || name.isBlank()) return 0f
        val lines = text.lines().filter { it.isNotBlank() }
        var best = 0f
        for (i in lines.indices) {
            for (span in 1..3) {
                if (i + span > lines.size) break
                best = maxOf(best, dice(name, lines.subList(i, i + span).joinToString(" ")))
            }
        }
        return best
    }

    private fun isCjk(s: String): Boolean = s.any {
        val script = Character.UnicodeScript.of(it.code)
        script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HANGUL
    }

    private fun unigramDice(x: String, y: String): Float {
        val pool = y.toMutableList()
        var shared = 0
        for (c in x) if (pool.remove(c)) shared++
        return 2f * shared / (x.length + y.length)
    }

    private fun bigrams(s: String): List<String> = (0 until s.length - 1).map { s.substring(it, it + 2) }
}
