package com.scanrift.android.service.scanning

import com.scanrift.android.core.Constants
import kotlin.math.hypot

/** What one processed frame should be matched against. */
sealed interface FocusDecision {
    /** At most one card in view: match the whole frame, as single scanning always has. */
    data object Wide : FocusDecision

    /** Several cards in view: only [card], the one in the guide, counts. */
    data class Focused(val card: MultiCardScanner.PageCard) : FocusDecision

    /** Several cards in view and none in the guide: no match this frame, never a neighbour. */
    data object NoCardInGuide : FocusDecision
}

/**
 * Picks which card a single-mode frame is about.
 *
 * Matching used to run on the whole frame and take the first collector code, so on a
 * binder page it often matched the card above the one in the guide. Android has no
 * rectangle detector, so collector codes are the "card-like thing" signal:
 *
 * - **Wide** while at most one code is read — today's behaviour, unchanged.
 * - **Focus** as soon as [minCards] codes are read in one frame. Each code becomes a card
 *   region ([MultiCardScanner.scan]); only a card whose region lies at least
 *   [minOverlap] inside the guide counts, the best-overlapping one (then the one nearest
 *   the guide centre) wins, and with none inside nothing matches.
 * - Back to **wide** only after [releaseFrames] consecutive processed frames show no card
 *   outside the guide, so a neighbour flickering in and out of OCR does not flip modes.
 *
 * No UI: the guide and overlay are unchanged. Stateful (the hysteresis), but otherwise a
 * pure function of the lines and the guide, so it is tested with fabricated boxes.
 * Not thread-safe; the scanner processes one frame at a time.
 */
class ScanFocusPolicy(
    private val minCards: Int = Constants.Scanning.FOCUS_MIN_CARDS,
    private val releaseFrames: Int = Constants.Scanning.FOCUS_RELEASE_FRAMES,
    private val minOverlap: Float = Constants.Scanning.FOCUS_MIN_OVERLAP,
) {
    var isFocused: Boolean = false
        private set

    private var clearFrames = 0

    fun reset() {
        isFocused = false
        clearFrames = 0
    }

    /**
     * Decides one processed frame and advances the hysteresis. Call exactly once per frame.
     *
     * @param guide the scan guide in the frame's normalised coordinates
     *   ([OcrGeometry.guideInImage]); null before the screen has been measured, in which
     *   case the whole frame is the guide.
     */
    fun evaluate(
        lines: List<TextLine>,
        guide: NormalizedBox?,
        knownSetIds: Set<String> = emptySet(),
        imageAspect: Float = 1f,
    ): FocusDecision {
        val area = guide ?: FULL_FRAME
        val cards = if (lines.isEmpty()) emptyList() else MultiCardScanner.scan(lines, knownSetIds, imageAspect).cards
        val inGuide = cards.filter { overlapFraction(it.bounds, area) >= minOverlap }
        val outside = cards.size - inGuide.size

        if (cards.size >= minCards) {
            isFocused = true
            clearFrames = 0
        } else if (isFocused) {
            clearFrames = if (outside > 0) 0 else clearFrames + 1
            if (clearFrames >= releaseFrames) reset()
        }

        if (!isFocused) return FocusDecision.Wide
        val best = inGuide.maxWithOrNull(
            compareBy<MultiCardScanner.PageCard> { overlapFraction(it.bounds, area) }
                .thenByDescending { distance(it.bounds, area) },
        )
        return best?.let { FocusDecision.Focused(it) } ?: FocusDecision.NoCardInGuide
    }

    companion object {
        private val FULL_FRAME = NormalizedBox(0f, 0f, 1f, 1f)

        /** Share of [region]'s area inside [guide], 0..1. */
        fun overlapFraction(region: NormalizedBox, guide: NormalizedBox): Float {
            val w = minOf(region.right, guide.right) - maxOf(region.left, guide.left)
            val h = minOf(region.bottom, guide.bottom) - maxOf(region.top, guide.top)
            val area = region.width * region.height
            if (w <= 0f || h <= 0f || area <= 0f) return 0f
            return (w * h / area).coerceAtMost(1f)
        }

        private fun distance(region: NormalizedBox, guide: NormalizedBox): Float =
            hypot(region.centerX - guide.centerX, region.centerY - guide.centerY)

        /**
         * [ocr] cut down to [card]: its lines only (the name band recomputed inside the
         * card's region, the collector line as the set-code region) and only the
         * Chinese/Korean lines that fall inside it. Language detection then reads the
         * focused card, not its neighbours.
         */
        fun focusedOcr(ocr: OcrResult, card: MultiCardScanner.PageCard): OcrResult {
            val b = card.bounds
            fun inCard(line: TextLine) =
                line.box.centerX in b.left..b.right && line.box.centerY in b.top..b.bottom
            val name = card.lines.filter { line ->
                if (b.height <= 0f || b.width <= 0f) return@filter false
                // Same bands as OcrGeometry, measured within the card instead of the frame.
                val fromBottom = (b.bottom - line.box.bottom) / b.height
                val midX = (line.box.centerX - b.left) / b.width
                fromBottom > Constants.Scanning.NAME_BAND_MIN_Y &&
                    fromBottom < Constants.Scanning.NAME_BAND_MAX_Y &&
                    midX < Constants.Scanning.NAME_BAND_MAX_MID_X
            }
            val chinese = ocr.chineseLines.filter(::inCard)
            val korean = ocr.koreanLines.filter(::inCard)
            return ocr.copy(
                extractedText = card.lines.joinToString("\n") { it.text },
                cardNameRegion = name.joinToString(" ") { it.text }.trim().takeIf { it.isNotEmpty() },
                setCodeRegion = card.anchorText,
                scriptText = (chinese + korean).joinToString("\n") { it.text }.takeIf { it.isNotEmpty() },
                lines = card.lines,
                chineseLines = chinese,
                koreanLines = korean,
            )
        }
    }
}
