package com.scanrift.android.core.time

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * ISO-8601 handling, and the single most interop-critical file in the app.
 *
 * Swift's `JSONEncoder.dateEncodingStrategy = .iso8601` is `ISO8601DateFormatter` with
 * `.withInternetDateTime` only — **no fractional seconds**. If Android writes
 * `2026-06-05T12:34:56.123Z` into a backup snapshot, iOS's decode of the *entire
 * snapshot* throws and the restore silently fails. So [format] always truncates to
 * whole seconds, while [parse] stays lenient in both directions.
 */
object Iso8601 {

    /** The API emits six fractional digits; `Instant.parse` accepts at most nine. */
    private val EXCESS_FRACTION = Regex("\\.(\\d{3})\\d+")

    /**
     * Seconds-precision UTC, e.g. `2026-06-05T12:34:56Z`.
     * Never emit sub-second precision into anything iOS will read.
     */
    fun format(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).truncatedTo(ChronoUnit.SECONDS).toString()

    fun format(instant: Instant): String =
        instant.truncatedTo(ChronoUnit.SECONDS).toString()

    /**
     * Parses an ISO-8601 timestamp with or without fractional seconds and with either
     * a `Z` or a numeric offset. Returns null rather than throwing — a single bad
     * timestamp in an imported file should skip one row, not abort the import.
     */
    fun parse(text: String?): Long? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null

        // Trim over-long fractions (the API sends microseconds) before parsing.
        val normalized = EXCESS_FRACTION.replace(raw) { ".${it.groupValues[1]}" }

        return runCatching { Instant.parse(normalized).toEpochMilli() }
            .recoverCatching {
                DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .parse(normalized, Instant::from)
                    .toEpochMilli()
            }
            .recoverCatching {
                // Some payloads omit the offset entirely; treat those as UTC.
                Instant.parse("${normalized}Z").toEpochMilli()
            }
            .getOrElse { error ->
                if (error is DateTimeParseException) null else null
            }
    }
}
