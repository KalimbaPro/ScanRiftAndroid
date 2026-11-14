package com.scanrift.android.service.scanning

/**
 * @param confidence mean per-line confidence, or 1.0 when the device reported none —
 *   see `OcrService` for why that fallback exists.
 * @param scriptText text from the Chinese/Korean recognizers, when the Latin pass found
 *   a card but no Latin name. Only language detection reads it.
 * @param lines every recognised line with its box, for multi-card page scanning and
 *   the single-card focus mode ([ScanFocusPolicy]).
 */
data class OcrResult(
    val extractedText: String,
    val cardNameRegion: String? = null,
    val setCodeRegion: String? = null,
    val confidence: Double = 0.0,
    val scriptText: String? = null,
    val lines: List<TextLine> = emptyList(),
    /**
     * Each CJK recogniser's lines, kept apart: the Chinese model emits Han for a Korean
     * card, so language detection compares the two passes rather than merging them.
     */
    val chineseLines: List<TextLine> = emptyList(),
    val koreanLines: List<TextLine> = emptyList(),
    /** Upright image width ÷ height; boxes are normalised per axis. */
    val imageAspect: Float = 1f,
    /** The upright frame, kept only when an ambiguous printing needs its art compared. */
    val frame: android.graphics.Bitmap? = null,
) {
    val isEmpty: Boolean get() = extractedText.isBlank()

    val chinesePass: String? get() = chineseLines.joinToString("\n") { it.text }.takeIf { it.isNotBlank() }
    val koreanPass: String? get() = koreanLines.joinToString("\n") { it.text }.takeIf { it.isNotBlank() }

    /** Everything language detection should look at: the Latin pass plus any CJK pass. */
    val languageText: String
        get() = listOfNotNull(extractedText, scriptText).joinToString("\n")

    companion object {
        val EMPTY = OcrResult(extractedText = "")
    }
}

/**
 * A box in normalised image coordinates: 0..1, origin top-left, y down. Plain floats
 * rather than `android.graphics.RectF`, so page geometry runs on plain JUnit.
 */
data class NormalizedBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun offset(dx: Float, dy: Float) = NormalizedBox(left + dx, top + dy, right + dx, bottom + dy)

    fun scale(sx: Float, sy: Float) = NormalizedBox(left * sx, top * sy, right * sx, bottom * sy)

    fun union(other: NormalizedBox) = NormalizedBox(
        minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom),
    )

    /** Clamped to the image, for drawing. */
    fun clamped() = NormalizedBox(left.coerceIn(0f, 1f), top.coerceIn(0f, 1f), right.coerceIn(0f, 1f), bottom.coerceIn(0f, 1f))
}

/** One recognised line of text and where it sits in the image. */
data class TextLine(val text: String, val box: NormalizedBox)
