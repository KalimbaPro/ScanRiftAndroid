package com.scanrift.android.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Direct port of iOS `ScanRiftTests/StringExtensionsTests.swift`.
 *
 * All 24 cases, in the same order, so a behavioural drift between the two platforms
 * shows up as the same test failing on both.
 */
class StringExtensionsTest {

    // ── normalized ───────────────────────────────────────────────────────────

    @Test
    fun `normalized lowercases`() {
        assertThat("Hello World".normalized).isEqualTo("hello world")
    }

    @Test
    fun `normalized removes special characters`() {
        assertThat("OGN-021".normalized).isEqualTo("ogn021")
    }

    @Test
    fun `normalized trims whitespace`() {
        assertThat("  hello  ".normalized).isEqualTo("hello")
    }

    @Test
    fun `normalized collapses multiple spaces`() {
        assertThat("hello   world".normalized).isEqualTo("hello world")
    }

    @Test
    fun `normalized empty string`() {
        assertThat("".normalized).isEqualTo("")
    }

    @Test
    fun `normalized preserves digits`() {
        assertThat("abc123".normalized).isEqualTo("abc123")
    }

    // ── cleanedForOCR ────────────────────────────────────────────────────────

    @Test
    fun `cleanedForOCR replaces zero with O`() {
        assertThat("H0use".cleanedForOCR).contains("O")
    }

    @Test
    fun `cleanedForOCR replaces one with I`() {
        assertThat("1tem".cleanedForOCR).isEqualTo("Item")
    }

    @Test
    fun `cleanedForOCR replaces pipe with I`() {
        assertThat("|tem".cleanedForOCR).isEqualTo("Item")
    }

    @Test
    fun `cleanedForOCR trims whitespace`() {
        assertThat("  hello  ".cleanedForOCR).isEqualTo("hello")
    }

    // ── levenshteinDistance ──────────────────────────────────────────────────

    @Test
    fun `levenshtein identical strings`() {
        assertThat("test".levenshteinDistance("test")).isEqualTo(0)
    }

    @Test
    fun `levenshtein from empty string`() {
        assertThat("".levenshteinDistance("test")).isEqualTo(4)
    }

    @Test
    fun `levenshtein to empty string`() {
        assertThat("test".levenshteinDistance("")).isEqualTo(4)
    }

    @Test
    fun `levenshtein both empty`() {
        assertThat("".levenshteinDistance("")).isEqualTo(0)
    }

    @Test
    fun `levenshtein known distance`() {
        assertThat("kitten".levenshteinDistance("sitting")).isEqualTo(3)
    }

    @Test
    fun `levenshtein single char difference`() {
        assertThat("cat".levenshteinDistance("bat")).isEqualTo(1)
    }

    // ── similarity ───────────────────────────────────────────────────────────

    @Test
    fun `similarity identical strings`() {
        assertThat("test".similarity("test")).isEqualTo(1.0)
    }

    @Test
    fun `similarity both empty`() {
        assertThat("".similarity("")).isEqualTo(1.0)
    }

    @Test
    fun `similarity completely different`() {
        assertThat("abc".similarity("xyz")).isLessThan(0.5)
    }

    @Test
    fun `similarity close strings`() {
        assertThat("hello".similarity("hallo")).isGreaterThan(0.7)
    }

    // ── containsWords ────────────────────────────────────────────────────────

    @Test
    fun `containsWords full match`() {
        assertThat("fire dragon warrior".containsWords(from = "fire dragon warrior")).isTrue()
    }

    @Test
    fun `containsWords partial match`() {
        assertThat("fire dragon warrior".containsWords(from = "fire dragon", threshold = 0.8)).isTrue()
    }

    @Test
    fun `containsWords no match`() {
        assertThat("fire dragon warrior".containsWords(from = "ice golem")).isFalse()
    }

    @Test
    fun `containsWords empty other`() {
        assertThat("fire dragon".containsWords(from = "")).isFalse()
    }

    // ── String?.isNilOrEmpty ─────────────────────────────────────────────────

    @Test
    fun `isNilOrEmpty with null`() {
        assertThat((null as String?).isNilOrEmpty()).isTrue()
    }

    @Test
    fun `isNilOrEmpty with empty`() {
        assertThat("".isNilOrEmpty()).isTrue()
    }

    @Test
    fun `isNilOrEmpty with whitespace`() {
        assertThat("   ".isNilOrEmpty()).isTrue()
    }

    @Test
    fun `isNilOrEmpty with value`() {
        assertThat("hello".isNilOrEmpty()).isFalse()
    }
}
