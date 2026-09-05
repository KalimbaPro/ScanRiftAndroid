package com.scanrift.android.service.scanning

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.junit.Test

/**
 * Port of iOS `MotionDetectorTests`, including the exact `scanStatus` strings.
 *
 * The state machine is small but subtle: the "waiting for change" latch is what stops
 * a single card being scanned over and over while it sits in frame.
 */
class MotionDetectorTest {

    private fun detector(started: Boolean = true) = MotionDetector().apply {
        // reset(waitForChange = false) is what the scanner does on start, so most
        // tests want the detector already past the initial latch.
        if (started) reset(waitForChange = false)
    }

    /** A uniform frame; `value` shifts every sample so a delta is easy to force. */
    private fun frame(value: Int, width: Int = 100, height: Int = 100): ByteBuffer =
        ByteBuffer.allocate(width * height).apply {
            val b = value.toByte()
            for (i in 0 until width * height) put(i, b)
        }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    fun `a fresh detector waits for a card`() {
        val d = MotionDetector()
        assertThat(d.waitingForChange).isTrue()
        assertThat(d.readyToScan).isFalse()
        assertThat(d.stableFrameCount).isEqualTo(0)
        assertThat(d.scanStatus).isEqualTo("Waiting for card")
    }

    // ── processMotionResult ──────────────────────────────────────────────────

    @Test
    fun `motion clears the waiting latch but does not arm a scan`() {
        val d = MotionDetector()
        assertThat(d.processMotionResult(imageChanged = true)).isFalse()
        assertThat(d.waitingForChange).isFalse()
        assertThat(d.readyToScan).isFalse()
    }

    @Test
    fun `a stable frame while waiting does nothing`() {
        val d = MotionDetector()
        assertThat(d.processMotionResult(imageChanged = false)).isFalse()
        assertThat(d.stableFrameCount).isEqualTo(0)
        assertThat(d.waitingForChange).isTrue()
    }

    @Test
    fun `a stable frame after motion arms the scan`() {
        val d = detector()
        assertThat(d.processMotionResult(imageChanged = false)).isTrue()
        assertThat(d.readyToScan).isTrue()
        assertThat(d.scanStatus).isEqualTo("Ready to scan")
    }

    @Test
    fun `motion resets the stable count and disarms`() {
        val d = detector()
        d.processMotionResult(imageChanged = false)
        assertThat(d.readyToScan).isTrue()

        d.processMotionResult(imageChanged = true)
        assertThat(d.readyToScan).isFalse()
        assertThat(d.stableFrameCount).isEqualTo(0)
    }

    @Test
    fun `the stable count keeps climbing while the frame is still`() {
        val d = detector()
        repeat(3) { d.processMotionResult(imageChanged = false) }
        assertThat(d.stableFrameCount).isEqualTo(3)
    }

    // ── markScanned / markRetry / reset ──────────────────────────────────────

    @Test
    fun `markScanned re-arms the waiting latch so one card scans once`() {
        val d = detector()
        d.processMotionResult(imageChanged = false)

        d.markScanned()

        assertThat(d.waitingForChange).isTrue()
        assertThat(d.readyToScan).isFalse()
        assertThat(d.stableFrameCount).isEqualTo(0)
        // A still frame is now ignored: the card has to physically move first.
        assertThat(d.processMotionResult(imageChanged = false)).isFalse()
    }

    @Test
    fun `markRetry allows another attempt at the same card`() {
        val d = detector()
        d.processMotionResult(imageChanged = false)
        d.readyToScan = false

        d.markRetry()

        assertThat(d.readyToScan).isTrue()
    }

    @Test
    fun `reset with waitForChange true returns to the initial state`() {
        val d = detector()
        d.processMotionResult(imageChanged = false)

        d.reset(waitForChange = true)

        assertThat(d.waitingForChange).isTrue()
        assertThat(d.readyToScan).isFalse()
        assertThat(d.stableFrameCount).isEqualTo(0)
        assertThat(d.scanStatus).isEqualTo("Waiting for card")
    }

    @Test
    fun `reset with waitForChange false starts scanning immediately`() {
        val d = MotionDetector()
        d.reset(waitForChange = false)
        assertThat(d.waitingForChange).isFalse()
        assertThat(d.scanStatus).isEqualTo("Idle")
    }

    // ── scanStatus ───────────────────────────────────────────────────────────

    @Test
    fun `scan status reports stabilising progress`() {
        // With STABLE_FRAMES_REQUIRED = 1 the detector arms on the first still frame,
        // so "Stabilizing" is only reachable by clearing readyToScan by hand — which
        // is exactly what the scanner does after firing a capture.
        val d = detector()
        d.processMotionResult(imageChanged = false)
        d.readyToScan = false
        assertThat(d.scanStatus).isEqualTo("Stabilizing (1/1)")
    }

    // ── detectMotion ─────────────────────────────────────────────────────────

    @Test
    fun `the first frame never reports motion`() {
        // There is nothing to compare against yet.
        val d = detector()
        assertThat(d.detectMotion(frame(100), width = 100, height = 100, rowStride = 100)).isFalse()
    }

    @Test
    fun `an identical second frame reports no motion`() {
        val d = detector()
        d.detectMotion(frame(100), 100, 100, 100)
        assertThat(d.detectMotion(frame(100), 100, 100, 100)).isFalse()
    }

    @Test
    fun `a large brightness change reports motion`() {
        val d = detector()
        d.detectMotion(frame(10), 100, 100, 100)
        assertThat(d.detectMotion(frame(200), 100, 100, 100)).isTrue()
    }

    @Test
    fun `a change below the per pixel threshold is ignored`() {
        // Threshold is 30, so a delta of 20 is sensor noise, not a moving card.
        val d = detector()
        d.detectMotion(frame(100), 100, 100, 100)
        assertThat(d.detectMotion(frame(120), 100, 100, 100)).isFalse()
    }

    @Test
    fun `row stride padding is respected`() {
        // CameraX frequently hands back a Y plane whose rowStride exceeds the width;
        // reading it as tightly packed would sample garbage and report false motion.
        val width = 100
        val height = 100
        val rowStride = 128
        fun padded(value: Int) = ByteBuffer.allocate(rowStride * height).apply {
            for (y in 0 until height) for (x in 0 until width) put(y * rowStride + x, value.toByte())
        }

        val d = detector()
        d.detectMotion(padded(100), width, height, rowStride)
        assertThat(d.detectMotion(padded(100), width, height, rowStride)).isFalse()
        assertThat(d.detectMotion(padded(200), width, height, rowStride)).isTrue()
    }

    @Test
    fun `a frame geometry change starts sampling over rather than crashing`() {
        // Rotating the device changes the analyser's frame size mid-stream.
        val d = detector()
        d.detectMotion(frame(100, width = 100, height = 100), 100, 100, 100)
        assertThat(d.detectMotion(frame(200, width = 200, height = 50), 200, 50, 200)).isFalse()
    }

    @Test
    fun `a zero sized frame is ignored`() {
        val d = detector()
        assertThat(d.detectMotion(frame(100), width = 0, height = 0, rowStride = 0)).isFalse()
    }
}
