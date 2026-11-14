package com.scanrift.android.service.scanning

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.scanrift.android.core.log.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device text recognition.
 *
 * The Latin recognizer uses the **bundled** ML Kit model, not the Play Services one:
 * the model ships inside the APK (~4MB), so scanning works offline, on first launch, on
 * every device — including devices with no Play Services. For a scanner app that is the
 * whole product, and worth the download size. It always runs, because the collector
 * code that identifies the card is Latin on every print.
 *
 * The Chinese and Korean recognizers are the **unbundled** Play Services variants and
 * run only when the caller asks for a script pass — in practice a matched frame whose
 * name band has no Latin name, i.e. a zh/ko print. A device without Play Services fails
 * that pass, which is caught: language detection then falls back to the printed marker.
 */
@Singleton
class OcrService @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var chinese: TextRecognizer? = null
    private var korean: TextRecognizer? = null

    private fun scriptRecognizers(): List<TextRecognizer> = listOf(
        chinese ?: TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()).also { chinese = it },
        korean ?: TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()).also { korean = it },
    )

    /**
     * Recognises text in one camera frame and splits it into the card-name and
     * set-code bands.
     *
     * Always closes [imageProxy]: leaving one open stalls the analyser permanently once
     * `imageQueueDepth` frames are outstanding.
     */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    suspend fun extractText(
        imageProxy: ImageProxy,
        needsScriptPass: (OcrResult) -> Boolean = { false },
        needsFrame: (OcrResult) -> Boolean = { false },
    ): OcrResult {
        return try {
            val mediaImage = imageProxy.image ?: return OcrResult.EMPTY
            val rotation = imageProxy.imageInfo.rotationDegrees
            val input = InputImage.fromMediaImage(mediaImage, rotation)
            val (width, height) = OcrGeometry.rotatedSize(mediaImage.width, mediaImage.height, rotation)
            // The script pass and the frame copy must happen before the finally below
            // closes the frame.
            val result = process(input, width, height, needsScriptPass)
            if (!needsFrame(result)) return result
            result.copy(frame = runCatching { uprightBitmap(imageProxy, rotation) }.getOrNull())
        } catch (e: IllegalStateException) {
            OcrResult.EMPTY
        } finally {
            imageProxy.close()
        }
    }

    /** The frame as an upright bitmap, for the art comparison of an ambiguous printing. */
    private fun uprightBitmap(imageProxy: ImageProxy, rotation: Int): Bitmap {
        val raw = imageProxy.toBitmap()
        if (rotation % 360 == 0) return raw
        val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { if (it !== raw) raw.recycle() }
    }

    suspend fun extractText(image: InputImage, needsScriptPass: (OcrResult) -> Boolean = { false }): OcrResult {
        val (width, height) = OcrGeometry.rotatedSize(image.width, image.height, image.rotationDegrees)
        return process(image, width, height, needsScriptPass)
    }

    private suspend fun process(
        image: InputImage,
        width: Int,
        height: Int,
        needsScriptPass: (OcrResult) -> Boolean,
    ): OcrResult {
        val latin = recognize(recognizer, image).toOcrResult(width, height)
        if (!needsScriptPass(latin)) return latin
        // Both recognisers, kept apart (see OcrResult.chineseLines).
        val (zh, ko) = scriptRecognizers().map { client ->
            runCatching { recognize(client, image) }
                .onFailure { Log.ocr.w(it, "Script recognizer unavailable") }
                .getOrNull()
                ?.toOcrResult(width, height)
        }
        val chinese = zh?.lines.orEmpty()
        val korean = ko?.lines.orEmpty()
        return latin.copy(
            scriptText = (chinese + korean).joinToString("\n") { it.text }.takeIf { it.isNotBlank() },
            chineseLines = chinese,
            koreanLines = korean,
        )
    }

    /**
     * Line-level OCR of a still (binder page or tile). With [scripts], the Chinese and
     * Korean recognizers run too and their lines are appended — the caller merges
     * duplicates by position.
     */
    suspend fun recognizeLines(bitmap: Bitmap, scripts: Boolean = false): List<TextLine> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val clients = if (scripts) scriptRecognizers() else listOf(recognizer)
        return clients.flatMap { client ->
            runCatching { recognize(client, image).toOcrResult(bitmap.width, bitmap.height).lines }
                .onFailure { if (client !== recognizer) Log.ocr.w(it, "Script recognizer unavailable") else throw it }
                .getOrDefault(emptyList())
        }
    }

    /** A still through the Chinese and the Korean recognizer, each pass kept apart: (zh, ko). */
    suspend fun recognizeScriptPasses(bitmap: Bitmap): Pair<List<TextLine>, List<TextLine>> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val (zh, ko) = scriptRecognizers().map { client ->
            runCatching { recognize(client, image).toOcrResult(bitmap.width, bitmap.height).lines }
                .onFailure { Log.ocr.w(it, "Script recognizer unavailable") }
                .getOrDefault(emptyList())
        }
        return zh to ko
    }

    private suspend fun recognize(client: TextRecognizer, image: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            client.process(image)
                .addOnSuccessListener { text -> continuation.resume(text) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
                .addOnCanceledListener { continuation.cancel() }
        }

    fun close() {
        recognizer.close()
        chinese?.close()
        korean?.close()
        chinese = null
        korean = null
    }

    internal fun Text.toOcrResult(width: Int, height: Int): OcrResult {
        val allLines = mutableListOf<String>()
        val boxedLines = mutableListOf<TextLine>()
        val nameParts = mutableListOf<String>()
        val setCodeParts = mutableListOf<String>()
        var confidenceSum = 0f
        var confidenceCount = 0

        for (block in textBlocks) {
            for (line in block.lines) {
                val value = line.text
                if (value.isBlank()) continue
                allLines += value

                val box: Rect? = line.boundingBox
                if (box != null) {
                    boxedLines += TextLine(value, OcrGeometry.normalize(box, width, height))
                    if (OcrGeometry.isNameBand(box, width, height)) nameParts += value
                    if (OcrGeometry.isSetCodeBand(box, height)) setCodeParts += value
                }

                line.confidence?.let { confidenceSum += it; confidenceCount++ }
            }
        }

        // Many devices report no per-line confidence at all. Averaging nothing yields
        // 0.0, which the scanner's `> 0.3` gate would reject — so every single frame
        // would be discarded and the scanner would appear completely broken. Treat
        // "recognised text but no confidence signal" as full confidence instead.
        val confidence = when {
            confidenceCount > 0 -> (confidenceSum / confidenceCount).toDouble()
            allLines.isNotEmpty() -> 1.0
            else -> 0.0
        }

        return OcrResult(
            extractedText = allLines.joinToString("\n"),
            cardNameRegion = nameParts.joinToString(" ").trim().takeIf { it.isNotEmpty() },
            setCodeRegion = setCodeParts.joinToString(" ").trim().takeIf { it.isNotEmpty() },
            confidence = confidence,
            lines = boxedLines,
            imageAspect = if (height > 0) width.toFloat() / height else 1f,
        )
    }
}
