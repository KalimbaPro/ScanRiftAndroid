package com.scanrift.android.service.scanning

import com.scanrift.android.util.Constants
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

/**
 * Pixel-level frame comparison state machine.
 * Direct port of iOS MotionDetector.swift.
 *
 * States: waitingForChange → (motion detected) → counting stable frames → readyToScan → markScanned → waitingForChange
 */
class MotionDetector {

    private var previousFrameSamples: ByteArray? = null
    var stableFrameCount: Int = 0
        private set
    var readyToScan: Boolean = false
        private set
    var waitingForChange: Boolean = true
        private set

    val scanStatus: String
        get() = when {
            waitingForChange -> "Waiting for card"
            readyToScan -> "Ready to scan"
            stableFrameCount > 0 -> "Stabilizing ($stableFrameCount/${Constants.MotionDetection.STABLE_FRAMES_REQUIRED})"
            else -> "Idle"
        }

    /**
     * Detect if motion occurred in the frame by sampling pixels.
     * Returns true if significant pixel changes were found.
     *
     * @param yPlane Y-channel ByteBuffer from ImageProxy (luminance only for performance)
     * @param width Image width
     * @param height Image height
     * @param rowStride Row stride of the Y plane
     */
    fun detectMotion(yPlane: ByteBuffer, width: Int, height: Int, rowStride: Int): Boolean {
        val strideX = max(1, width / Constants.MotionDetection.SAMPLE_STRIDE)
        val strideY = max(1, height / Constants.MotionDetection.SAMPLE_STRIDE)

        val currentSamples = mutableListOf<Byte>()

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val offset = y * rowStride + x
                if (offset < yPlane.capacity()) {
                    currentSamples.add(yPlane.get(offset))
                }
                x += strideX
            }
            y += strideY
        }

        val currentArray = currentSamples.toByteArray()
        val previous = previousFrameSamples

        previousFrameSamples = currentArray

        if (previous == null || previous.size != currentArray.size) {
            return false
        }

        // Calculate difference
        var differentPixels = 0
        for (i in currentArray.indices) {
            val diff = abs((currentArray[i].toInt() and 0xFF) - (previous[i].toInt() and 0xFF))
            if (diff > Constants.MotionDetection.PIXEL_CHANGE_THRESHOLD) {
                differentPixels++
            }
        }

        val changeRatio = differentPixels.toFloat() / currentArray.size.toFloat()
        return changeRatio > Constants.MotionDetection.MOTION_THRESHOLD
    }

    /**
     * Process a motion detection result and update the state machine.
     * Returns true if the detector is ready to scan.
     */
    fun processMotionResult(imageChanged: Boolean): Boolean {
        if (imageChanged) {
            stableFrameCount = 0
            readyToScan = false
            if (waitingForChange) {
                waitingForChange = false
            }
            return false
        }

        // Image is stable
        if (waitingForChange) {
            return false
        }

        stableFrameCount++

        if (stableFrameCount >= Constants.MotionDetection.STABLE_FRAMES_REQUIRED && !readyToScan) {
            readyToScan = true
        }

        return readyToScan
    }

    /** Reset state after a successful scan. Waits for next card change. */
    fun markScanned() {
        stableFrameCount = 0
        readyToScan = false
        waitingForChange = true
    }

    /** Reset for retry (allow scanning again on next stable frame). */
    fun markRetry() {
        readyToScan = true
    }

    /** Full reset. */
    fun reset(waitForChange: Boolean) {
        previousFrameSamples = null
        stableFrameCount = 0
        readyToScan = false
        waitingForChange = waitForChange
    }
}
