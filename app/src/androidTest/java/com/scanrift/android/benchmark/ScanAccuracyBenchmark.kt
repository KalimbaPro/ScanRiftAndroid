package com.scanrift.android.benchmark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.scanrift.android.R
import com.scanrift.android.data.local.mapper.toDomain
import com.scanrift.android.data.remote.dto.PaginatedCardsResponse
import com.scanrift.android.di.SerializationModule
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardPrintings
import com.scanrift.android.domain.model.ScanLanguage
import com.scanrift.android.service.scanning.BitmapFrameArt
import com.scanrift.android.service.scanning.CardMatchingService
import com.scanrift.android.service.scanning.CatalogueArt
import com.scanrift.android.service.scanning.NormalizedBox
import com.scanrift.android.service.scanning.OcrService
import com.scanrift.android.service.scanning.ScanIdentifier
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scan accuracy against official card images, through the real ML Kit recognisers and
 * the app's own [OcrService] + [ScanIdentifier]: language confusion matrix and exact-
 * printing match rate.
 *
 * Opt-in only, never part of CI (which runs JVM unit tests only), and skipped by a plain
 * `connectedAndroidTest` too:
 *
 * ```
 * ./gradlew connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.scanrift.android.benchmark.ScanAccuracyBenchmark \
 *   -Pandroid.testInstrumentationRunnerArguments.benchmark=true
 * adb pull /sdcard/Android/data/com.scanrift.android.debug/files/scan-benchmark.txt
 * ```
 *
 * Add `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` to keep the app
 * (and so the report files) after the run; the report is also logged under the
 * `ScanBenchmark` tag. Add `-PbundledCjkOcr` on emulators whose Play Services TFLite
 * crashes with SIGILL (Apple-silicon AVDs): it swaps in the bundled Chinese/Korean
 * models, same API, for that build only.
 *
 * The samples (assets/scan-benchmark.json) are 40 Origins cards in English, Chinese and
 * Korean plus 20 alternate-art, signature and overnumbered printings. Images download on
 * first run and are cached in the app's cache directory. Each image is read twice: as
 * published, and as a camera-like 720×1280 frame (card at ~82% width, rotated 3°, on a
 * dark background). The Chinese/Korean recognisers are Play Services models, so the
 * emulator image must have Play Services.
 */
@RunWith(AndroidJUnit4::class)
class ScanAccuracyBenchmark {

    @Serializable
    data class Sample(val id: String, val lang: String, val url: String, val variant: Boolean = false)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun benchmark() = runBlocking {
        assumeTrue(
            "opt-in: pass -Pandroid.testInstrumentationRunnerArguments.benchmark=true",
            InstrumentationRegistry.getArguments().getString("benchmark") == "true",
        )
        val json = Json { ignoreUnknownKeys = true }
        val samples = json.decodeFromString<List<Sample>>(
            instrumentation.context.assets.open("scan-benchmark.json").bufferedReader().readText(),
        )
        val cards = loadCatalogue()
        val identity = CardPrintings.index(cards)
        val identifier = ScanIdentifier(CardMatchingService(cards))
        val ocr = OcrService()
        val art = CatalogueArt(context)
        val http = OkHttpClient()

        val rows = mutableListOf<Row>()
        val raw = StringBuilder()
        for (sample in samples) {
            val image = download(http, sample.url) ?: continue
            for ((frameName, frame) in listOf("full" to image, "camera" to cameraFrame(image))) {
                // The card's outline: the whole published image, or where cameraFrame put it
                // (what the on-screen guide gives the live scanner).
                val region = if (frameName == "full") NormalizedBox(0f, 0f, 1f, 1f) else cameraCardRegion(image)
                val result = identifier.scan(ocr, frame, ScanLanguage.AUTO, BitmapFrameArt(frame, region), art)
                val matched = result.match?.card
                val expected = cards.firstOrNull { it.riftboundId == sample.id }
                rows += Row(
                    sample = sample,
                    frame = frameName,
                    matchedId = matched?.riftboundId,
                    sameCard = matched != null && expected != null && identity.groupOf(matched) == identity.groupOf(expected),
                    language = result.language.language.value,
                    source = result.language.source.name,
                    ambiguous = result.ambiguous,
                    byArt = result.resolvedByArt,
                )
                raw.append(
                    JSONObject()
                        .put("id", sample.id).put("lang", sample.lang).put("frame", frameName)
                        .put("matched", matched?.riftboundId).put("detected", result.language.language.value)
                        .put("source", result.language.source.name)
                        .put("ambiguous", result.ambiguous).put("byArt", result.resolvedByArt)
                        .put("name", result.view?.cardNameRegion).put("code", result.view?.setCodeRegion)
                        .put("text", result.view?.extractedText)
                        .put("zh", result.view?.chinesePass).put("ko", result.view?.koreanPass)
                        .toString(),
                ).append('\n')
            }
            frameRecycle(image)
        }
        ocr.close()

        val report = report(rows)
        android.util.Log.i("ScanBenchmark", report)
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        File(dir, "scan-benchmark.txt").writeText(report)
        File(dir, "scan-benchmark-ocr.jsonl").writeText(raw.toString())
    }

    private data class Row(
        val sample: Sample,
        val frame: String,
        val matchedId: String?,
        val sameCard: Boolean,
        val language: String,
        val source: String,
        val ambiguous: Boolean,
        val byArt: Boolean,
    )

    private fun report(rows: List<Row>): String = buildString {
        for (frame in listOf("full", "camera")) {
            val set = rows.filter { it.frame == frame }
            appendLine("== $frame frames (${set.size}) ==")
            val langRows = set.filter { !it.sample.variant }
            appendLine("Card identified (any printing): ${pct(langRows.count { it.sameCard }, langRows.size)}")
            appendLine("Language confusion (rows expected, columns detected; identified cards only):")
            val langs = listOf("en", "fr", "zh", "ko")
            appendLine("      " + langs.joinToString("") { it.padStart(6) })
            for (expected in listOf("en", "zh", "ko")) {
                val r = langRows.filter { it.sample.lang == expected && it.sameCard }
                appendLine(expected.padEnd(6) + langs.joinToString("") { l -> r.count { it.language == l }.toString().padStart(6) })
            }
            val identified = langRows.filter { it.sameCard }
            appendLine("Language accuracy: ${pct(identified.count { it.language == it.sample.lang }, identified.size)}")
            appendLine("Sources: " + identified.groupingBy { it.source }.eachCount())
            val variants = set.filter { it.sample.variant }
            appendLine("Variant printings: same card ${pct(variants.count { it.sameCard }, variants.size)}, " +
                "exact printing ${pct(variants.count { it.matchedId == it.sample.id }, variants.size)}, " +
                "ambiguous ${variants.count { it.ambiguous }}")
            variants.filter { it.matchedId != it.sample.id }.forEach {
                appendLine("  ${it.sample.id} -> ${it.matchedId}${if (it.ambiguous) " (ambiguous)" else ""}")
            }
            val base = langRows.filter { it.sample.lang == "en" }
            appendLine("Exact printing (regular EN): ${pct(base.count { it.matchedId == it.sample.id }, base.size)}")
            appendLine("Regular cards flagged ambiguous: ${langRows.count { it.ambiguous }}, " +
                "printings settled by art: ${set.count { it.byArt }} (correct ${set.count { it.byArt && it.matchedId == it.sample.id }})")
            appendLine()
        }
    }

    private fun pct(n: Int, d: Int) = if (d == 0) "n/a" else "$n/$d (${n * 100 / d}%)"

    private fun loadCatalogue(): List<Card> {
        val text = context.resources.openRawResource(R.raw.cards).bufferedReader().readText()
        val seed = SerializationModule.provideApiJson().decodeFromString<PaginatedCardsResponse>(text)
        return com.scanrift.android.service.sync.CardDedup.dedupedByRiftboundId(seed.items)
            .map { it.toEntity().toDomain() }
    }

    private fun download(http: OkHttpClient, url: String): Bitmap? {
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(context.cacheDir, "scan-benchmark/$name")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val bytes = runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { it.body.bytes() }
            }.getOrNull() ?: return null
            file.writeBytes(bytes)
        }
        return BitmapFactory.decodeFile(file.path)
    }

    /** Where [cameraFrame] puts the card, normalised (ignoring the 3° tilt). */
    private fun cameraCardRegion(card: Bitmap): NormalizedBox {
        val w = 0.82f
        val h = 720f * 0.82f * card.height / card.width / 1280f
        return NormalizedBox(0.5f - w / 2, 0.5f - h / 2, 0.5f + w / 2, 0.5f + h / 2)
    }

    /** The card as a camera would frame it: 720×1280, ~82% width, tilted 3°, dark surround. */
    private fun cameraFrame(card: Bitmap): Bitmap {
        val frame = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        canvas.drawColor(Color.rgb(40, 40, 40))
        val scale = 720f * 0.82f / card.width
        canvas.translate(360f, 640f)
        canvas.rotate(3f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(card, -card.width / 2f, -card.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
        return frame
    }

    private fun frameRecycle(bitmap: Bitmap) = bitmap.recycle()
}

/** One still through the live pipeline: Latin pass, optional script pass, match, language. */
private suspend fun ScanIdentifier.scan(
    ocr: OcrService,
    bitmap: Bitmap,
    preference: ScanLanguage,
    frame: BitmapFrameArt,
    art: CatalogueArt,
) = identifyFrame(
    ocr = ocr.extractText(InputImage.fromBitmap(bitmap, 0)) { latin ->
        needsScriptPass(latin, com.scanrift.android.service.scanning.FocusDecision.Wide, preference)
    },
    preference = preference,
    frame = frame,
    art = art,
)
