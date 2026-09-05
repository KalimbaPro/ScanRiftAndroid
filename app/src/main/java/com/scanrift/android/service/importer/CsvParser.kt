package com.scanrift.android.service.importer

/**
 * Quote-aware CSV line splitter, ported from iOS.
 *
 * Deliberately does **not** handle doubled quotes (`""` as an escaped quote) — iOS
 * doesn't either, and the formats this reads never emit them. Matching the limitation
 * keeps the two importers accepting exactly the same set of files.
 */
object CsvParser {

    fun parseLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields += current.toString().trim()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        fields += current.toString().trim()
        return fields
    }

    fun lines(content: String): List<String> =
        content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
}
