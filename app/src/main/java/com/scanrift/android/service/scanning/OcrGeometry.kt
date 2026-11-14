package com.scanrift.android.service.scanning

import android.graphics.Rect
import com.scanrift.android.core.Constants

/**
 * Converts ML Kit's text-box coordinates into the Vision coordinate space the iOS
 * banding rules are written in.
 *
 * This is the file that fixes a real parity bug. The two frameworks disagree twice
 * over:
 *
 * - **Origin.** Vision normalises to 0..1 with the origin at the **bottom** left;
 *   ML Kit returns pixels with the origin at the **top** left.
 * - **Which edge `origin.y` means.** In Vision, `boundingBox.origin.y` is the distance
 *   from the image bottom to the box's *bottom* edge.
 *
 * So `visionOriginY = (imageHeight - rect.bottom) / imageHeight`.
 *
 * The old Android port instead compared `rect.top / height` against 0.20 and 0.80,
 * which is a completely different pair of bands from iOS's 0.35–0.65 (card name, the
 * *middle* of the card) and < 0.25 (set code, the bottom quarter).
 *
 * Pure functions so this can be tested at every rotation without a camera.
 */
object OcrGeometry {

    /** Dimensions after rotation is applied; ML Kit reports the unrotated frame size. */
    fun rotatedSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (((rotationDegrees % 360) + 360) % 360 % 180 != 0) height to width else width to height

    /**
     * The on-screen scan guide in the analysis image's normalised, upright coordinates
     * (the space OCR line boxes live in).
     *
     * The preview shows the camera image scaled to *fill* the view and centre-cropped
     * (`PreviewView.ScaleType.FILL_CENTER`), so the view is a window onto the middle of
     * the image. The buffer arrives unrotated: [rotationDegrees] (ImageProxy's) swaps its
     * dimensions to the upright frame first. Assumes the preview and the analysis stream
     * share a field of view, which `CameraService` ensures by giving both the same
     * aspect ratio.
     *
     * @param guide the guide rect in view pixels.
     * @return the guide clamped to the image (part of it may fall outside a crop).
     */
    fun guideInImage(
        viewWidth: Float,
        viewHeight: Float,
        guide: NormalizedBox,
        bufferWidth: Int,
        bufferHeight: Int,
        rotationDegrees: Int,
    ): NormalizedBox {
        val (w, h) = rotatedSize(bufferWidth, bufferHeight, rotationDegrees)
        if (viewWidth <= 0f || viewHeight <= 0f || w <= 0 || h <= 0) return NormalizedBox(0f, 0f, 1f, 1f)
        val scale = maxOf(viewWidth / w, viewHeight / h)
        val offsetX = (viewWidth - w * scale) / 2f
        val offsetY = (viewHeight - h * scale) / 2f
        fun x(v: Float) = (v - offsetX) / scale / w
        fun y(v: Float) = (v - offsetY) / scale / h
        return NormalizedBox(x(guide.left), y(guide.top), x(guide.right), y(guide.bottom)).clamped()
    }

    /** Pixel rect → [NormalizedBox] (0..1, top-left origin), for page geometry. */
    fun normalize(box: Rect, imageWidth: Int, imageHeight: Int): NormalizedBox {
        val w = imageWidth.coerceAtLeast(1).toFloat()
        val h = imageHeight.coerceAtLeast(1).toFloat()
        return NormalizedBox(box.left / w, box.top / h, box.right / w, box.bottom / h)
    }

    /** Vision's `boundingBox.origin.y`: normalised distance from image bottom to box bottom. */
    fun visionOriginY(box: Rect, imageHeight: Int): Float {
        if (imageHeight <= 0) return 0f
        return 1f - (box.bottom.toFloat() / imageHeight.toFloat())
    }

    /** Normalised horizontal centre of the box. */
    fun visionMidX(box: Rect, imageWidth: Int): Float {
        if (imageWidth <= 0) return 0f
        return ((box.left + box.right) / 2f) / imageWidth.toFloat()
    }

    /**
     * The card-name band: vertically the middle third, and left of the 0.7 mark so a
     * cost or rarity glyph on the right doesn't get pulled into the name.
     */
    fun isNameBand(box: Rect, imageWidth: Int, imageHeight: Int): Boolean {
        val y = visionOriginY(box, imageHeight)
        return y > Constants.Scanning.NAME_BAND_MIN_Y &&
            y < Constants.Scanning.NAME_BAND_MAX_Y &&
            visionMidX(box, imageWidth) < Constants.Scanning.NAME_BAND_MAX_MID_X
    }

    /** The set-code band: the bottom quarter of the card, where `OGN-021` is printed. */
    fun isSetCodeBand(box: Rect, imageHeight: Int): Boolean =
        visionOriginY(box, imageHeight) < Constants.Scanning.SET_CODE_BAND_MAX_Y
}
