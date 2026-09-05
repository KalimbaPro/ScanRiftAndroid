package com.scanrift.android.service.scanning

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The ML Kit to Vision coordinate conversion.
 *
 * This is where a real parity bug lived. iOS bands text using Vision's normalised,
 * **bottom-left** coordinates, where `origin.y` is the distance from the image bottom
 * to the box's *bottom* edge. ML Kit hands back **top-left pixels**. The old Android
 * port ignored the difference and compared `rect.top / height` against 0.20 and 0.80,
 * which are different bands entirely from iOS's 0.35-0.65 and < 0.25.
 *
 * Robolectric only for `android.graphics.Rect`; the logic itself is pure.
 */
@RunWith(RobolectricTestRunner::class)
class OcrGeometryTest {

    private val width = 1000
    private val height = 2000

    /** A box positioned by its fraction from the **top**, as ML Kit would report it. */
    private fun boxFromTop(topFraction: Float, heightFraction: Float = 0.05f, left: Int = 0, right: Int = 500) =
        Rect(left, (height * topFraction).toInt(), right, (height * (topFraction + heightFraction)).toInt())

    // ── visionOriginY ────────────────────────────────────────────────────────

    @Test
    fun `a box at the very top of the image maps to the top of vision space`() {
        val box = Rect(0, 0, 500, 0)
        assertThat(OcrGeometry.visionOriginY(box, height)).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `a box at the very bottom of the image maps to zero`() {
        val box = Rect(0, height, 500, height)
        assertThat(OcrGeometry.visionOriginY(box, height)).isWithin(0.001f).of(0.0f)
    }

    @Test
    fun `the conversion measures from the box bottom, not its top`() {
        // A box occupying the top 10% has its bottom edge at 0.1 from the top, so its
        // Vision origin.y is 0.9. Using the top edge would give 1.0 — the bug.
        val box = Rect(0, 0, 500, (height * 0.1f).toInt())
        assertThat(OcrGeometry.visionOriginY(box, height)).isWithin(0.001f).of(0.9f)
    }

    @Test
    fun `a zero height image does not divide by zero`() {
        assertThat(OcrGeometry.visionOriginY(Rect(0, 0, 10, 10), 0)).isEqualTo(0f)
        assertThat(OcrGeometry.visionMidX(Rect(0, 0, 10, 10), 0)).isEqualTo(0f)
    }

    // ── Name band ────────────────────────────────────────────────────────────

    @Test
    fun `text in the middle of the card is the card name`() {
        // Vision origin.y 0.5 means the box bottom sits halfway up the card.
        assertThat(OcrGeometry.isNameBand(boxFromTop(0.45f), width, height)).isTrue()
    }

    @Test
    fun `text at the top of the card is not the card name`() {
        // The old Android banding treated the top 20% as the name; iOS does not.
        assertThat(OcrGeometry.isNameBand(boxFromTop(0.05f), width, height)).isFalse()
    }

    @Test
    fun `text at the bottom of the card is not the card name`() {
        assertThat(OcrGeometry.isNameBand(boxFromTop(0.9f), width, height)).isFalse()
    }

    @Test
    fun `text on the right hand side is excluded from the name band`() {
        // Costs and rarity glyphs sit right of the 0.7 mark and must not be pulled
        // into the card name.
        val rightSide = boxFromTop(0.45f, left = (width * 0.75f).toInt(), right = width)
        assertThat(OcrGeometry.isNameBand(rightSide, width, height)).isFalse()
    }

    // ── Set code band ────────────────────────────────────────────────────────

    @Test
    fun `text in the bottom quarter is the set code`() {
        assertThat(OcrGeometry.isSetCodeBand(boxFromTop(0.9f), height)).isTrue()
    }

    @Test
    fun `text above the bottom quarter is not the set code`() {
        assertThat(OcrGeometry.isSetCodeBand(boxFromTop(0.5f), height)).isFalse()
    }

    @Test
    fun `the two bands do not overlap`() {
        val positions = (0..20).map { it / 20f }
        positions.forEach { top ->
            val box = boxFromTop(top, heightFraction = 0.02f)
            val name = OcrGeometry.isNameBand(box, width, height)
            val setCode = OcrGeometry.isSetCodeBand(box, height)
            assertThat(name && setCode).isFalse()
        }
    }

    // ── rotatedSize ──────────────────────────────────────────────────────────

    @Test
    fun `quarter turns swap the reported dimensions`() {
        assertThat(OcrGeometry.rotatedSize(1280, 720, 0)).isEqualTo(1280 to 720)
        assertThat(OcrGeometry.rotatedSize(1280, 720, 90)).isEqualTo(720 to 1280)
        assertThat(OcrGeometry.rotatedSize(1280, 720, 180)).isEqualTo(1280 to 720)
        assertThat(OcrGeometry.rotatedSize(1280, 720, 270)).isEqualTo(720 to 1280)
    }

    @Test
    fun `rotation is normalised so negative and over-360 values behave`() {
        assertThat(OcrGeometry.rotatedSize(1280, 720, -90)).isEqualTo(720 to 1280)
        assertThat(OcrGeometry.rotatedSize(1280, 720, 450)).isEqualTo(720 to 1280)
        assertThat(OcrGeometry.rotatedSize(1280, 720, 360)).isEqualTo(1280 to 720)
    }

    @Test
    fun `banding uses the rotated height, so a sideways frame still bands correctly`() {
        // A portrait card in a landscape frame: the analyser reports 1280x720 with a
        // 90 degree rotation, so the effective height is 1280, not 720.
        val (w, h) = OcrGeometry.rotatedSize(1280, 720, 90)
        assertThat(h).isEqualTo(1280)

        val setCodeBox = Rect(0, (h * 0.9f).toInt(), 300, (h * 0.95f).toInt())
        assertThat(OcrGeometry.isSetCodeBand(setCodeBox, h)).isTrue()
        assertThat(OcrGeometry.isNameBand(setCodeBox, w, h)).isFalse()
    }
}
