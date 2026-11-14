package com.scanrift.android.service.scanning

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.Card
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Bitmap → [DHash] of a card's art region. */
object ArtHashing {

    /**
     * Hash of the art inside [card], a card's outline in [bitmap]'s normalised
     * coordinates (the whole bitmap for a catalogue image).
     */
    fun hash(bitmap: Bitmap, card: NormalizedBox = NormalizedBox(0f, 0f, 1f, 1f)): Long? {
        val c = card.clamped()
        val art = Constants.Scanning.ART_REGION
        val left = (c.left + c.width * art.left) * bitmap.width
        val top = (c.top + c.height * art.top) * bitmap.height
        val right = (c.left + c.width * art.right) * bitmap.width
        val bottom = (c.top + c.height * art.bottom) * bitmap.height
        val w = (right - left).toInt()
        val h = (bottom - top).toInt()
        if (w < DHash.WIDTH || h < DHash.HEIGHT) return null
        val crop = Bitmap.createBitmap(bitmap, left.toInt(), top.toInt(), w, h)
        val small = crop.scale(DHash.WIDTH, DHash.HEIGHT)
        val pixels = IntArray(DHash.WIDTH * DHash.HEIGHT)
        small.getPixels(pixels, 0, DHash.WIDTH, 0, 0, DHash.WIDTH, DHash.HEIGHT)
        if (crop !== bitmap) crop.recycle()
        small.recycle()
        return DHash.of(IntArray(pixels.size) { i -> luma(pixels[i]) })
    }

    private fun luma(argb: Int): Int = (Color.red(argb) * 299 + Color.green(argb) * 587 + Color.blue(argb) * 114) / 1000
}

/** A frame's art: [bitmap] with the card at [card] (normalised). Hashed lazily, once. */
class BitmapFrameArt(private val bitmap: Bitmap, private val card: NormalizedBox) : FrameArt {
    private val value by lazy { runCatching { ArtHashing.hash(bitmap, card) }.getOrNull() }
    override fun hash(): Long? = value
}

/**
 * Catalogue art hashes, loaded through the app's Coil image loader so they come from (and
 * fill) the same disk cache the collection grid uses. Memoised per card id.
 */
@Singleton
class CatalogueArt @Inject constructor(@param:ApplicationContext private val context: Context) : PrintingArt {

    private val hashes = ConcurrentHashMap<String, Long>()

    override suspend fun hash(card: Card): Long? {
        hashes[card.id]?.let { return it }
        val url = card.imageUrl ?: return null
        if (card.isJudgePromo) return null // its image is the regular print, never the promo
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(ART_HASH_SOURCE_SIZE)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        val hash = ArtHashing.hash(result.image.toBitmap()) ?: return null
        hashes[card.id] = hash
        return hash
    }

    private companion object {
        const val ART_HASH_SOURCE_SIZE = 256
    }
}
