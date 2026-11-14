package com.scanrift.android.service.scanning

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.scanrift.android.core.log.Log
import com.scanrift.android.di.DefaultDispatcher
import com.scanrift.android.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Binder-page OCR: one full pass, a 3×3 tile pass when the page reads short of nine
 * cards, and a Chinese/Korean pass when a card on it has no Latin name. All geometry is
 * in [MultiCardScanner]; this class only feeds it pixels.
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
     * @param detectScripts run the Chinese/Korean recognizers when a card has no Latin
     *   name — only worth it when the scan language is Auto.
     */
    suspend fun scan(bitmap: Bitmap, knownSetIds: Set<String>, detectScripts: Boolean): MultiCardScanner.PageScan =
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

            if (detectScripts && page.cards.any { !LanguageDetector.hasLatinName(it.bodyText) }) {
                lines = MultiCardScanner.mergeLines(lines, ocr.recognizeLines(bitmap, scripts = true))
                page = MultiCardScanner.scan(lines, knownSetIds, aspect)
            }
            page
        }
}
