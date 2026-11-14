package com.scanrift.android.service.scanning

import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.Card

/**
 * 64-bit difference hash ("dHash"): the art shrunk to 9×8 grey pixels, one bit per pair
 * of horizontal neighbours (is the left one darker?). Robust to scale, brightness and the
 * camera's blur; an alternate art differs from its base in most of its bits. Pure, over a
 * luma array, so the choice logic runs on plain JUnit; [ArtHashing] feeds it pixels.
 */
object DHash {
    const val WIDTH = 9
    const val HEIGHT = 8

    /** [luma] is row-major, [WIDTH] × [HEIGHT]. */
    fun of(luma: IntArray): Long {
        require(luma.size == WIDTH * HEIGHT) { "dHash needs ${WIDTH * HEIGHT} samples" }
        var hash = 0L
        var bit = 0
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH - 1) {
                if (luma[y * WIDTH + x] < luma[y * WIDTH + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

/** The scanned frame's art, hashed on demand (only when a printing is ambiguous). */
fun interface FrameArt {
    fun hash(): Long?
}

/** A printing's reference art, hashed from its catalogue image; null when unavailable. */
fun interface PrintingArt {
    suspend fun hash(card: Card): Long?
}

/**
 * Settles which printing a suffix-less collector code meant (`066` vs `066a`, `299` vs
 * `299*`) by comparing the frame's art with each candidate's catalogue image.
 *
 * The closest printing wins only when it is close enough ([maxDistance]) and clearly
 * closer than the runner-up ([minMargin]); otherwise null, and the scan keeps the base
 * printing marked ambiguous for the user to confirm.
 */
object PrintingChooser {

    fun choose(
        frame: Long,
        candidates: List<Pair<Card, Long>>,
        maxDistance: Int = Constants.Scanning.ART_HASH_MAX_DISTANCE,
        minMargin: Int = Constants.Scanning.ART_HASH_MIN_MARGIN,
    ): Card? {
        if (candidates.isEmpty()) return null
        val ranked = candidates.map { (card, hash) -> card to DHash.distance(frame, hash) }.sortedBy { it.second }
        val (best, distance) = ranked.first()
        val runnerUp = ranked.getOrNull(1)?.second ?: return best.takeIf { distance <= maxDistance }
        return best.takeIf { distance <= maxDistance && runnerUp - distance >= minMargin }
    }

    /** [choose] over [printings], hashing each through [art]; null when any hash is missing. */
    suspend fun resolve(printings: List<Card>, frame: FrameArt?, art: PrintingArt?): Card? {
        if (frame == null || art == null || printings.size < 2) return null
        val frameHash = frame.hash() ?: return null
        val hashes = printings.map { card -> card to (art.hash(card) ?: return null) }
        return choose(frameHash, hashes)
    }
}
