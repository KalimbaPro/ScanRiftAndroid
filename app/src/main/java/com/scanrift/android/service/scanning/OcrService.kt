package com.scanrift.android.service.scanning

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device text recognition.
 *
 * Uses the **bundled** ML Kit model, not the Play Services one: the model ships inside
 * the APK (~4MB), so scanning works offline, on first launch, on every device from API
 * 21 up — including devices with no Play Services. For a scanner app that is the whole
 * product, and worth the download size.
 */
@Singleton
class OcrService @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Recognises text in one camera frame and splits it into the card-name and
     * set-code bands.
     *
     * Always closes [imageProxy]: leaving one open stalls the analyser permanently once
     * `imageQueueDepth` frames are outstanding.
     */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    suspend fun extractText(imageProxy: ImageProxy): OcrResult {
        return try {
            val mediaImage = imageProxy.image ?: return OcrResult.EMPTY
            val rotation = imageProxy.imageInfo.rotationDegrees
            val input = InputImage.fromMediaImage(mediaImage, rotation)
            val (width, height) = OcrGeometry.rotatedSize(mediaImage.width, mediaImage.height, rotation)
            process(input, width, height)
        } catch (e: IllegalStateException) {
            OcrResult.EMPTY
        } finally {
            imageProxy.close()
        }
    }

    suspend fun extractText(image: InputImage): OcrResult {
        val (width, height) = OcrGeometry.rotatedSize(image.width, image.height, image.rotationDegrees)
        return process(image, width, height)
    }

    private suspend fun process(image: InputImage, width: Int, height: Int): OcrResult =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { text -> continuation.resume(text.toOcrResult(width, height)) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
                .addOnCanceledListener { continuation.cancel() }
        }

    fun close() = recognizer.close()

    internal fun Text.toOcrResult(width: Int, height: Int): OcrResult {
        val allLines = mutableListOf<String>()
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
        )
    }
}
