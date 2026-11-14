package com.scanrift.android.service.scanning

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Single-mode focus with fabricated OCR line boxes: a portrait 9:16 frame, the guide in
 * the middle, and binder neighbours above and below it.
 */
class ScanFocusPolicyTest {

    private val aspect = 9f / 16f
    private val guide = NormalizedBox(0.2f, 0.3f, 0.8f, 0.7f)

    /** One card: a name line and a collector line at its bottom-left, card ~0.6 wide. */
    private fun card(code: String, bottom: Float, name: String = "Jinx Loose Cannon"): List<TextLine> = listOf(
        TextLine(name, NormalizedBox(0.28f, bottom - 0.2f, 0.6f, bottom - 0.18f)),
        TextLine("$code/298 • EN", NormalizedBox(0.25f, bottom - 0.02f, 0.45f, bottom)),
    )

    private val centre = card("OGN-021", bottom = 0.68f)
    private val above = card("OGN-007", bottom = 0.30f)
    private val below = card("OGN-099", bottom = 1.06f).map { it } // half out of frame, below the guide

    private fun FocusDecision.code() = (this as? FocusDecision.Focused)?.card?.code

    @Test fun `one code stays wide`() {
        val policy = ScanFocusPolicy()
        assertThat(policy.evaluate(centre, guide, imageAspect = aspect)).isEqualTo(FocusDecision.Wide)
        assertThat(policy.isFocused).isFalse()
    }

    @Test fun `two codes, one in the guide, picks that one`() {
        val policy = ScanFocusPolicy()
        val decision = policy.evaluate(above + centre, guide, imageAspect = aspect)
        assertThat(decision.code()).isEqualTo("OGN-021")
        assertThat(policy.isFocused).isTrue()
    }

    @Test fun `line order does not matter`() {
        assertThat(ScanFocusPolicy().evaluate(centre + above, guide, imageAspect = aspect).code()).isEqualTo("OGN-021")
    }

    @Test fun `two codes and none in the guide matches nothing`() {
        val policy = ScanFocusPolicy()
        val far = card("OGN-007", bottom = 0.25f) + card("OGN-099", bottom = 0.98f)
        val tightGuide = NormalizedBox(0.3f, 0.45f, 0.7f, 0.55f)
        assertThat(policy.evaluate(far, tightGuide, imageAspect = aspect)).isEqualTo(FocusDecision.NoCardInGuide)
    }

    @Test fun `focus releases only after consecutive clear frames`() {
        val policy = ScanFocusPolicy(releaseFrames = 3)
        policy.evaluate(above + centre, guide, imageAspect = aspect)

        // The neighbour drops out of OCR: still focused, still on the guided card.
        repeat(2) {
            assertThat(policy.evaluate(centre, guide, imageAspect = aspect).code()).isEqualTo("OGN-021")
        }
        // It flickers back: the count restarts.
        policy.evaluate(above + centre, guide, imageAspect = aspect)
        repeat(2) { policy.evaluate(centre, guide, imageAspect = aspect) }
        assertThat(policy.isFocused).isTrue()
        // Third clear frame in a row: back to wide.
        assertThat(policy.evaluate(centre, guide, imageAspect = aspect)).isEqualTo(FocusDecision.Wide)
        assertThat(policy.isFocused).isFalse()
    }

    @Test fun `a lone neighbour outside the guide keeps focus and matches nothing`() {
        val policy = ScanFocusPolicy(releaseFrames = 3)
        policy.evaluate(above + centre, guide, imageAspect = aspect)
        repeat(5) {
            assertThat(policy.evaluate(above, guide, imageAspect = aspect)).isEqualTo(FocusDecision.NoCardInGuide)
        }
        assertThat(policy.isFocused).isTrue()
    }

    @Test fun `focused ocr keeps only the focused card's lines and script lines`() {
        val policy = ScanFocusPolicy()
        val focused = policy.evaluate(above + centre, guide, imageAspect = aspect) as FocusDecision.Focused
        val ocr = OcrResult(
            extractedText = (above + centre).joinToString("\n") { it.text },
            confidence = 1.0,
            lines = above + centre,
            scriptText = "金克丝\n아리",
            scriptLines = listOf(
                TextLine("金克丝", NormalizedBox(0.3f, 0.48f, 0.5f, 0.5f)), // inside the guided card
                TextLine("아리", NormalizedBox(0.3f, 0.1f, 0.5f, 0.12f)), // the card above
            ),
            imageAspect = aspect,
        )
        val view = ScanFocusPolicy.focusedOcr(ocr, focused.card)
        assertThat(view.extractedText).contains("OGN-021")
        assertThat(view.extractedText).doesNotContain("OGN-007")
        assertThat(view.scriptText).isEqualTo("金克丝")
        assertThat(view.setCodeRegion).startsWith("OGN-021")
    }

    // ── Guide mapping ────────────────────────────────────────────────────────

    @Test fun `guide maps through a fill-centre crop of a rotated buffer`() {
        // 1280×720 landscape buffer, rotated 90° → 720×1280 upright. A 1080×1920 view is
        // the same 9:16, so no crop: the mapping is a plain scale.
        val guide = OcrGeometry.guideInImage(
            1080f, 1920f, NormalizedBox(270f, 480f, 810f, 1440f),
            bufferWidth = 1280, bufferHeight = 720, rotationDegrees = 90,
        )
        assertThat(guide.left).isWithin(1e-4f).of(0.25f)
        assertThat(guide.top).isWithin(1e-4f).of(0.25f)
        assertThat(guide.right).isWithin(1e-4f).of(0.75f)
        assertThat(guide.bottom).isWithin(1e-4f).of(0.75f)
    }

    @Test fun `a wider view crops the top and bottom of the frame`() {
        // View 1080×1440 (3:4) over a 9:16 upright frame: scale = 1.5 (width-bound), the
        // frame is 1920 tall on screen, 240px cropped off top and bottom.
        val guide = OcrGeometry.guideInImage(
            1080f, 1440f, NormalizedBox(0f, 0f, 1080f, 1440f),
            bufferWidth = 1280, bufferHeight = 720, rotationDegrees = 270,
        )
        assertThat(guide.left).isWithin(1e-4f).of(0f)
        assertThat(guide.right).isWithin(1e-4f).of(1f)
        assertThat(guide.top).isWithin(1e-4f).of(240f / 1920f)
        assertThat(guide.bottom).isWithin(1e-4f).of(1f - 240f / 1920f)
    }

    @Test fun `an unrotated buffer keeps its own axes`() {
        // Landscape view and buffer, 0°: a narrower view crops the sides.
        val guide = OcrGeometry.guideInImage(
            1000f, 720f, NormalizedBox(0f, 0f, 1000f, 720f),
            bufferWidth = 1280, bufferHeight = 720, rotationDegrees = 0,
        )
        assertThat(guide.left).isWithin(1e-4f).of(140f / 1280f)
        assertThat(guide.right).isWithin(1e-4f).of(1140f / 1280f)
        assertThat(guide.top).isWithin(1e-4f).of(0f)
    }
}
