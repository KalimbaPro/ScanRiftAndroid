package com.scanrift.android.core.log

import timber.log.Timber

/**
 * Tagged logging facade mirroring the iOS `Log.<category>` os.Logger setup, so the
 * same grep works against both codebases' output.
 */
object Log {
    val general: Timber.Tree get() = tagged("ScanRift")
    val scanning: Timber.Tree get() = tagged("Scanning")
    val ocr: Timber.Tree get() = tagged("OCR")
    val matching: Timber.Tree get() = tagged("Matching")
    val camera: Timber.Tree get() = tagged("Camera")
    val database: Timber.Tree get() = tagged("Database")
    val deckBuilder: Timber.Tree get() = tagged("DeckBuilder")
    val backup: Timber.Tree get() = tagged("Backup")

    private fun tagged(tag: String): Timber.Tree = Timber.tag(tag)
}
