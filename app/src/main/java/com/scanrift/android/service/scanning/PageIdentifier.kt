package com.scanrift.android.service.scanning

import com.scanrift.android.domain.model.ScanLanguage

/** One card of a binder page, identified. */
data class PageCardResult(val found: MultiCardScanner.PageCard, val outcome: ScanOutcome)

/**
 * Binder-page identification through the same rules as live scanning ([ScanIdentifier]):
 * each card is a [FocusDecision.Focused] view of the page — its own lines, its own code
 * first, the Chinese/Korean lines inside its outline — so the marker → localised name →
 * compared CJK passes → French margin order, the collector-code repairs and the art
 * tie-break for a suffix-less code all apply per card. Pure; [PageScanService] feeds it.
 */
object PageIdentifier {

    /** The page's OCR as one result: Latin lines plus the two CJK passes, kept apart. */
    fun pageOcr(
        lines: List<TextLine>,
        chineseLines: List<TextLine> = emptyList(),
        koreanLines: List<TextLine> = emptyList(),
        imageAspect: Float = 1f,
    ) = OcrResult(
        extractedText = lines.joinToString("\n") { it.text },
        confidence = 1.0,
        lines = lines,
        chineseLines = chineseLines,
        koreanLines = koreanLines,
        imageAspect = imageAspect,
    )

    /** Whether any matched card's Latin text leaves its language open: then the CJK passes run. */
    fun needsScriptPass(page: MultiCardScanner.PageScan, latin: OcrResult, identifier: ScanIdentifier, preference: ScanLanguage) =
        page.cards.any { identifier.needsScriptPass(latin, FocusDecision.Focused(it), preference) }

    /**
     * Every card of [page]. [frameFor] gives a card's art in the page photo, for the
     * printing tie-break; null skips it (the card then stays ambiguous).
     */
    suspend fun identify(
        page: MultiCardScanner.PageScan,
        ocr: OcrResult,
        identifier: ScanIdentifier,
        preference: ScanLanguage,
        frameFor: (MultiCardScanner.PageCard) -> FrameArt? = { null },
        art: PrintingArt? = null,
    ): List<PageCardResult> = page.cards.map { found ->
        PageCardResult(found, identifier.identifyFrame(ocr, preference, frameFor(found), art, FocusDecision.Focused(found)))
    }
}
