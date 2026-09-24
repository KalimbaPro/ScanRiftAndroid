package com.scanrift.android.service.importer

enum class ImportFormat { RIFTBOUND_GG, CSV, JSON }

data class ImportResult(
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val skippedNames: List<String> = emptyList(),
) {
    val summary: String
        get() = listOfNotNull(
            "$created added".takeIf { created > 0 },
            "$updated updated".takeIf { updated > 0 },
            "$skipped skipped".takeIf { skipped > 0 },
        ).joinToString(", ")

    val message: String
        get() {
            if (skippedNames.isEmpty()) return summary
            val names = skippedNames.take(SKIPPED_NAMES_SHOWN).joinToString(", ")
            val more = skippedNames.size - SKIPPED_NAMES_SHOWN
            return "$summary\n\nSkipped cards: $names" + if (more > 0) " and $more more" else ""
        }

    private companion object {
        const val SKIPPED_NAMES_SHOWN = 5
    }
}

class ImportException(message: String) : Exception(message)
