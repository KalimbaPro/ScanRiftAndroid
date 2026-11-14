package com.scanrift.android.service.scanning

import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.ScanLanguage

/** What one frame identified: the OCR it was read from (null when nothing counts) and the match. */
data class Identification(val view: OcrResult?, val match: MatchCandidate?)

/**
 * A frame fully identified: card, language and whether the printing is a guess.
 *
 * @param ambiguous the code was read without its variant suffix and nothing settled
 *   which printing it is; the base printing was kept.
 */
data class ScanOutcome(
    val view: OcrResult?,
    val match: MatchCandidate?,
    val language: LanguageDetector.Detection,
    val ambiguous: Boolean = false,
    /** The printing was settled by comparing art, not read from the code. */
    val resolvedByArt: Boolean = false,
)

/**
 * OCR result → card and language, for the live scanner and the instrumented benchmark
 * alike, so the benchmark measures exactly what ships.
 */
class ScanIdentifier(private val matcher: CardMatchingService) {

    /**
     * The frame as [decision] sees it — the whole OCR, or just the focused card's lines —
     * and its match. A focused card matches on its own collector code first: its OCR
     * line can also carry a neighbour's code when two cards' bottoms read as one line.
     */
    fun identify(ocr: OcrResult, decision: FocusDecision = FocusDecision.Wide): Identification {
        if (ocr.confidence <= Constants.Scanning.MINIMUM_OCR_CONFIDENCE) return Identification(null, null)
        return when (decision) {
            FocusDecision.Wide -> Identification(ocr, matcher.findMatch(ocr))
            is FocusDecision.Focused -> {
                val view = ScanFocusPolicy.focusedOcr(ocr, decision.card)
                Identification(view, matcher.findByCardCode(decision.card.code) ?: matcher.findMatch(view))
            }
            FocusDecision.NoCardInGuide -> Identification(null, null)
        }
    }

    /** Whether the Chinese/Korean recognizers should also read this frame. */
    fun needsScriptPass(latin: OcrResult, decision: FocusDecision, preference: ScanLanguage): Boolean {
        if (preference != ScanLanguage.AUTO) return false
        // Not just "no Latin name": OCR reads plenty of Latin noise off a Chinese card.
        val id = identify(latin, decision)
        return id.view != null && id.match != null && !LanguageDetector.latinPassSettles(id.view.extractedText, id.match.card)
    }

    /** Whether the analysed frame should be kept for an art comparison (an ambiguous printing). */
    fun needsFrame(latin: OcrResult, decision: FocusDecision): Boolean =
        identify(latin, decision).match?.isAmbiguous == true

    /**
     * The language to record. Once a card is matched its names are evidence too
     * ([LanguageDetector.detectMatched]); a fixed scan language always wins.
     */
    fun language(preference: ScanLanguage, id: Identification): LanguageDetector.Detection {
        val view = id.view ?: return LanguageDetector.resolve(preference, "")
        return LanguageDetector.resolveMatched(
            preference,
            LanguageDetector.Evidence(
                latinText = view.extractedText,
                chinesePass = view.chinesePass,
                koreanPass = view.koreanPass,
                card = id.match?.card,
            ),
        )
    }

    /**
     * [identify], settle an ambiguous printing by its art ([PrintingChooser]), then
     * [language]. A printing the art cannot settle stays the base one, flagged
     * [ScanOutcome.ambiguous] so the version chip asks the user.
     */
    suspend fun identifyFrame(
        ocr: OcrResult,
        preference: ScanLanguage,
        frame: FrameArt? = null,
        art: PrintingArt? = null,
        decision: FocusDecision = FocusDecision.Wide,
    ): ScanOutcome {
        val id = identify(ocr, decision)
        val match = id.match
        if (match == null || !match.isAmbiguous) return ScanOutcome(id.view, match, language(preference, id))
        val chosen = PrintingChooser.resolve(match.unresolvedPrintings, frame, art)
        val settled = if (chosen != null) match.copy(card = chosen, unresolvedPrintings = emptyList()) else match
        val resolved = id.copy(match = settled)
        return ScanOutcome(id.view, settled, language(preference, resolved), ambiguous = chosen == null, resolvedByArt = chosen != null)
    }
}
