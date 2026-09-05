package com.scanrift.android.service.scanning

import com.scanrift.android.core.Constants
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

/**
 * Decides when the camera is looking at a card that has stopped moving.
 *
 * The state machine is ported verbatim from iOS, including the `scanStatus` strings,
 * which the ported tests assert on.
 *
 * Two deliberate differences from iOS:
 *
 * - iOS samples the B channel of a BGRA buffer; here we sample the Y (luma) plane of
 *   CameraX's YUV output. Different signal, comparable thresholds — luma is arguably
 *   the better one for detecting a card being placed down.
 * - The old Android port allocated a `MutableList<Byte>` and copied it to a `ByteArray`
 *   on every frame, ~100 boxed appends plus a copy at 30fps. This keeps two
 *   preallocated buffers and swaps them.
 *
 * Kept free of Android imports (bar `java.nio.ByteBuffer`) so the state machine tests
 * run on plain JUnit.
 */
class MotionDetector {

    private var bufferA: ByteArray? = null
    private var bufferB: ByteArray? = null
    private var useA = true
    private var sampleCount = 0
    private var hasPreviousFrame = false

    /**
     * Frame geometry the current buffers were sampled from.
     *
     * Tracked as dimensions rather than sample count: two different geometries can
     * sample to the same count (100x100 and 200x50 both yield 100 samples), and
     * comparing those two against each other would report phantom motion.
     */
    private var lastWidth = 0
    private var lastHeight = 0

    var stableFrameCount: Int = 0
        private set

    /** Externally settable: the scanner clears it after triggering a capture. */
    var readyToScan: Boolean = false

    var waitingForChange: Boolean = true
        private set

    val scanStatus: String
        get() = when {
            waitingForChange -> "Waiting for card"
            readyToScan -> "Ready to scan"
            stableFrameCount > 0 ->
                "Stabilizing ($stableFrameCount/${Constants.MotionDetection.STABLE_FRAMES_REQUIRED})"
            else -> "Idle"
        }

    /**
     * Samples the luma plane on a fixed grid and reports whether enough of it changed.
     *
     * @param yPlane the Y plane of a YUV_420_888 frame.
     * @param rowStride bytes per row, which is **not** necessarily [width].
     */
    fun detectMotion(yPlane: ByteBuffer, width: Int, height: Int, rowStride: Int): Boolean {
        if (width <= 0 || height <= 0) return false

        val strideX = max(1, width / Constants.MotionDetection.SAMPLE_STRIDE)
        val strideY = max(1, height / Constants.MotionDetection.SAMPLE_STRIDE)
        val expectedCount = ceilDiv(height, strideY) * ceilDiv(width, strideX)

        if (width != lastWidth || height != lastHeight || sampleCount != expectedCount) {
            // Rotation or a resolution switch: the previous samples describe a
            // different picture, so start over rather than diff across the change.
            bufferA = ByteArray(expectedCount)
            bufferB = ByteArray(expectedCount)
            sampleCount = expectedCount
            lastWidth = width
            lastHeight = height
            hasPreviousFrame = false
        }

        val current = if (useA) bufferA!! else bufferB!!
        val previous = if (useA) bufferB!! else bufferA!!

        var index = 0
        var y = 0
        while (y < height) {
            val rowOffset = y * rowStride
            var x = 0
            while (x < width) {
                val offset = rowOffset + x
                current[index++] = if (offset < yPlane.limit()) yPlane.get(offset) else 0
                x += strideX
            }
            y += strideY
        }

        useA = !useA
        if (!hasPreviousFrame) {
            hasPreviousFrame = true
            return false
        }

        var different = 0
        for (i in 0 until index) {
            val delta = abs((current[i].toInt() and 0xFF) - (previous[i].toInt() and 0xFF))
            if (delta > Constants.MotionDetection.PIXEL_CHANGE_THRESHOLD) different++
        }

        val changeRatio = different.toFloat() / index.toFloat()
        return changeRatio > Constants.MotionDetection.MOTION_THRESHOLD
    }

    /**
     * Advances the state machine.
     *
     * @return true when the detector is ready for a capture.
     */
    fun processMotionResult(imageChanged: Boolean): Boolean {
        if (imageChanged) {
            stableFrameCount = 0
            readyToScan = false
            // Motion is what clears the "waiting" latch, so a new card must physically
            // move into frame before another scan can fire.
            if (waitingForChange) waitingForChange = false
            return false
        }

        if (waitingForChange) return false

        stableFrameCount++
        if (stableFrameCount >= Constants.MotionDetection.STABLE_FRAMES_REQUIRED && !readyToScan) {
            readyToScan = true
        }
        return readyToScan
    }

    /** After a successful scan: wait for the card to be swapped before scanning again. */
    fun markScanned() {
        stableFrameCount = 0
        readyToScan = false
        waitingForChange = true
    }

    /** After a failed match: allow an immediate retry on the same card. */
    fun markRetry() {
        readyToScan = true
    }

    fun reset(waitForChange: Boolean) {
        hasPreviousFrame = false
        lastWidth = 0
        lastHeight = 0
        stableFrameCount = 0
        readyToScan = false
        waitingForChange = waitForChange
    }

    private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor
}
