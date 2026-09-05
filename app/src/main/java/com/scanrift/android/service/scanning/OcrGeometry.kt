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
