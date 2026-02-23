package com.scanrift.android.service.scanning

import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR result from text recognition.
 */
data class OCRResult(
    val extractedText: String,
    val cardNameRegion: String?,
    val setCodeRegion: String?,
    val confidence: Double
)

/**
 * OCR service using ML Kit Text Recognition.
 *
 * Key difference from iOS Vision:
 * ML Kit uses top-left origin in pixel coordinates (not normalized bottom-left like Vision).
 * - Card name = blocks where boundingBox.top < imageHeight * 0.20
 * - Set code = blocks where boundingBox.top > imageHeight * 0.80
 */
class OCRService {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extract text from a camera frame (ImageProxy).
     */
    @androidx.camera.core.ExperimentalGetImage
    suspend fun extractText(mediaImage: Image, rotationDegrees: Int): OCRResult {
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        return performOCR(inputImage)
    }

    private suspend fun performOCR(inputImage: InputImage): OCRResult =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val imageHeight = inputImage.height
                    val allText = mutableListOf<String>()
                    val nameRegionText = mutableListOf<String>()
                    val setCodeRegionText = mutableListOf<String>()
                    var totalConfidence = 0f
                    var confidenceCount = 0

                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val text = line.text
                            allText.add(text)

                            val boundingBox = line.boundingBox
                            if (boundingBox != null && imageHeight > 0) {
                                val topRatio = boundingBox.top.toFloat() / imageHeight.toFloat()

                                // Card name: top 20% of image
                                if (topRatio < 0.20f) {
                                    nameRegionText.add(text)
                                }

                                // Set code: bottom 20% of image
                                if (topRatio > 0.80f) {
                                    setCodeRegionText.add(text)
                                }
                            }

                            // ML Kit doesn't expose per-line confidence, approximate with block confidence
                            line.confidence?.let {
                                totalConfidence += it
                                confidenceCount++
                            }
                        }
                    }

                    val avgConfidence = if (confidenceCount > 0) {
                        (totalConfidence / confidenceCount).toDouble()
                    } else {
                        0.0
                    }

                    val cardName = nameRegionText.joinToString(" ").trim().ifEmpty { null }
                    val setCode = setCodeRegionText.joinToString(" ").trim().ifEmpty { null }

                    Timber.d("OCR all text: %s", allText.joinToString(" | "))
                    Timber.d("OCR name region: %s", cardName)
                    Timber.d("OCR set code region: %s", setCode)
                    Timber.d("OCR confidence: %.2f", avgConfidence)

                    continuation.resume(
                        OCRResult(
                            extractedText = allText.joinToString("\n"),
                            cardNameRegion = cardName,
                            setCodeRegion = setCode,
                            confidence = avgConfidence
                        )
                    )
                }
                .addOnFailureListener { exception ->
                    Timber.e(exception, "OCR failed")
                    continuation.resumeWithException(exception)
                }
        }

    fun close() {
        recognizer.close()
    }
}
