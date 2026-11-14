package com.scanrift.android.service.scanning

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import com.scanrift.android.core.Constants
import kotlin.math.max
import kotlin.math.roundToInt

/** Bitmap plumbing for binder-page stills: rotation, size caps, crops and decoding. */
object PageBitmaps {

    /** Applies the capture rotation and caps the long edge. */
    fun upright(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val rotated = if (rotationDegrees % 360 == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
        }
        return capped(rotated, Constants.Scanning.PAGE_MAX_DIMENSION)
    }

    fun capped(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longEdge
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
    }

    /** Crops a normalised [tile] and upscales it so small print gets more pixels per glyph. */
    fun crop(bitmap: Bitmap, tile: NormalizedBox): Bitmap {
        val left = (tile.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (tile.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (tile.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (tile.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val longEdge = max(cropped.width, cropped.height)
        val target = Constants.Scanning.PAGE_TILE_MIN_DIMENSION
        if (longEdge >= target) return cropped
        val scale = target.toFloat() / longEdge
        return Bitmap.createScaledBitmap(cropped, (cropped.width * scale).roundToInt(), (cropped.height * scale).roundToInt(), true)
    }

    /**
     * Decodes a library photo. `ImageDecoder` (API 28+) applies EXIF orientation; the
     * API 26–27 fallback does not, so a sideways photo from those devices reads sideways.
     */
    fun decode(resolver: ContentResolver, uri: Uri): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > Constants.Scanning.PAGE_MAX_DIMENSION) {
                    val scale = Constants.Scanning.PAGE_MAX_DIMENSION.toFloat() / longEdge
                    decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                }
            }
        } else {
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: error("Could not read that image")
        }
        return capped(bitmap, Constants.Scanning.PAGE_MAX_DIMENSION)
    }
}
