package com.scanrift.android.core.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The cross-platform interop contract.
 *
 * Swift decodes backup snapshots with `.iso8601`, which is `.withInternetDateTime`
 * only — no fractional seconds. A single sub-second timestamp anywhere in the file
 * makes iOS throw while decoding the *whole* snapshot, so the restore fails silently.
 */
class Iso8601Test {

    @Test
    fun `format never emits fractional seconds`() {
        val withMillis = 1_780_000_000_123L
        val formatted = Iso8601.format(withMillis)
        assertThat(formatted).doesNotContain(".")
        assertThat(formatted).endsWith("Z")
    }

    @Test
    fun `format truncates rather than rounds`() {
        val formatted = Iso8601.format(1_780_000_000_999L)
        assertThat(Iso8601.parse(formatted)).isEqualTo(1_780_000_000_000L)
    }

    @Test
    fun `parse accepts a seconds precision timestamp`() {
        assertThat(Iso8601.parse("2026-06-05T12:34:56Z")).isNotNull()
    }

    @Test
    fun `parse accepts fractional seconds from the api`() {
        // The API emits microseconds, which is more precision than Instant.parse wants.
        assertThat(Iso8601.parse("2026-03-19T20:26:19.376852+00:00")).isNotNull()
    }

    @Test
    fun `parse accepts a numeric offset`() {
        val utc = Iso8601.parse("2026-06-05T12:34:56Z")
        val offset = Iso8601.parse("2026-06-05T14:34:56+02:00")
        assertThat(offset).isEqualTo(utc)
    }

    @Test
    fun `parse accepts a timestamp with no zone and treats it as utc`() {
        assertThat(Iso8601.parse("2026-06-05T12:34:56"))
            .isEqualTo(Iso8601.parse("2026-06-05T12:34:56Z"))
    }

    @Test
    fun `parse returns null rather than throwing on junk`() {
        assertThat(Iso8601.parse("not a date")).isNull()
        assertThat(Iso8601.parse("")).isNull()
        assertThat(Iso8601.parse(null)).isNull()
    }

    @Test
    fun `format and parse round trip at seconds precision`() {
        val original = 1_780_000_000_000L
        assertThat(Iso8601.parse(Iso8601.format(original))).isEqualTo(original)
    }
}
