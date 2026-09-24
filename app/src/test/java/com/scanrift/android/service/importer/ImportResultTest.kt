package com.scanrift.android.service.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImportResultTest {

    @Test
    fun `summary lists only the non-zero counts like ios`() {
        assertThat(ImportResult(created = 3, updated = 0, skipped = 2).summary).isEqualTo("3 added, 2 skipped")
        assertThat(ImportResult(created = 1, updated = 4).summary).isEqualTo("1 added, 4 updated")
        assertThat(ImportResult().summary).isEmpty()
    }

    @Test
    fun `message names the first five skipped cards and counts the rest`() {
        val names = listOf("A", "B", "C", "D", "E", "F", "G")
        val result = ImportResult(created = 1, skipped = names.size, skippedNames = names)

        assertThat(result.message).isEqualTo("1 added, 7 skipped\n\nSkipped cards: A, B, C, D, E and 2 more")
    }

    @Test
    fun `message omits the remainder when five or fewer cards were skipped`() {
        val result = ImportResult(skipped = 2, skippedNames = listOf("A", "B"))

        assertThat(result.message).isEqualTo("2 skipped\n\nSkipped cards: A, B")
    }

    @Test
    fun `message is the bare summary when nothing was skipped`() {
        assertThat(ImportResult(updated = 2).message).isEqualTo("2 updated")
    }
}
