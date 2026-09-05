package com.scanrift.android.service.scanning

/**
 * @param confidence mean per-line confidence, or 1.0 when the device reported none —
 *   see `OcrService` for why that fallback exists.
 */
data class OcrResult(
    val extractedText: String,
    val cardNameRegion: String? = null,
    val setCodeRegion: String? = null,
    val confidence: Double = 0.0,
) {
    val isEmpty: Boolean get() = extractedText.isBlank()

    companion object {
        val EMPTY = OcrResult(extractedText = "")
    }
}
