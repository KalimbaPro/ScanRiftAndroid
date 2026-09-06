package com.scanrift.android.service.importer

enum class ImportFormat { RIFTBOUND_GG, CSV, JSON }

data class ImportResult(
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val skippedNames: List<String> = emptyList(),
) {
    val summary: String get() = "Imported $created new, updated $updated existing."
}

class ImportException(message: String) : Exception(message)
