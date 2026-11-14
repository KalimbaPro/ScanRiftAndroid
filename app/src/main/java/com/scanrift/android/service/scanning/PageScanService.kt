package com.scanrift.android.service.scanning

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.scanrift.android.core.log.Log
import com.scanrift.android.di.DefaultDispatcher
import com.scanrift.android.di.IoDispatcher
import com.scanrift.android.domain.model.ScanLanguage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Binder-page OCR: one full pass, a 3×3 tile pass when the page reads short of nine
 * cards, and the Chinese/Korean passes when a card's language is still open. Geometry is
 * [MultiCardScanner]'s and identification [PageIdentifier]'s; this class feeds them pixels.
 */
@Singleton
class PageScanService @Inject constructor(
    private val ocr: OcrService,
    private val contentResolver: ContentResolver,
    @param:DefaultDispatcher private val default: CoroutineDispatcher,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun decode(uri: Uri): Bitmap = withContext(io) { PageBitmaps.decode(contentResolver, uri) }

    /**
     * Reads a page and identifies every card on it with the live scanner's rules
     * ([PageIdentifier]). The Chinese and Korean recognizers run (each kept apart) only
     * when [preference] is Auto and some card's Latin text does not settle its language.
     */
    suspend fun scan(
        bitmap: Bitmap,
        knownSetIds: Set<String>,
        identifier: ScanIdentifier,
        preference: ScanLanguage,
        art: PrintingArt? = null,
    ): List<PageCardResult> =
        withContext(default) {
            val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
            var lines = ocr.recognizeLines(bitmap)
            var page = MultiCardScanner.scan(lines, knownSetIds, aspect)
            Log.scanning.d("Page pass: %d card(s) from %d line(s)", page.cards.size, lines.size)

            if (page.needsTiling) {
                for (tile in MultiCardScanner.tiles()) {
                    val crop = PageBitmaps.crop(bitmap, tile)
                    val tileLines = runCatching { ocr.recognizeLines(crop) }
                        .onFailure { Log.scanning.w(it, "Tile OCR failed") }
                        .getOrDefault(emptyList())
                    lines = MultiCardScanner.mergeLines(lines, tileLines.map { MultiCardScanner.fromTile(it, tile) })
                    if (crop !== bitmap) crop.recycle()
                }
                page = MultiCardScanner.scan(lines, knownSetIds, aspect)
                Log.scanning.d("Tile pass: %d card(s)", page.cards.size)
            }

            // Every line with its box, so a bad page can be replayed as a JUnit fixture.
            lines.forEach { Log.ocr.v("Page line %s %.4f %.4f %.4f %.4f", it.text, it.box.left, it.box.top, it.box.right, it.box.bottom) }
            page.cards.forEach { Log.scanning.v("Page card %s at %.3f,%.3f", it.code, it.anchorBox.left, it.anchorBox.top) }

            var pageOcr = PageIdentifier.pageOcr(lines, imageAspect = aspect)
            if (PageIdentifier.needsScriptPass(page, pageOcr, identifier, preference)) {
                val (zh, ko) = ocr.recognizeScriptPasses(bitmap)
                pageOcr = PageIdentifier.pageOcr(lines, zh, ko, aspect)
            }
            PageIdentifier.identify(
                page, pageOcr, identifier, preference,
                frameFor = { found -> BitmapFrameArt(bitmap, found.bounds) },
                art = art,
            )
        }
}
