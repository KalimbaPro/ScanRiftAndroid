package com.scanrift.android.service.scanning

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.ScanLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PrintingChooserTest {

    private val base = SeedCatalogue.card("ogn-066-298")
    private val alt = SeedCatalogue.card("ogn-066a-298")

    /** A 9×8 luma ramp: left-to-right brighter (all bits set) or darker (none). */
    private fun ramp(rising: Boolean) = IntArray(72) { i -> val x = i % 9; if (rising) x * 20 else 200 - x * 20 }

    @Test fun `dhash sets a bit where the right neighbour is brighter`() {
        assertThat(DHash.of(ramp(rising = true))).isEqualTo(-1L) // all 64 bits
        assertThat(DHash.of(ramp(rising = false))).isEqualTo(0L)
        assertThat(DHash.distance(-1L, 0L)).isEqualTo(64)
    }

    @Test fun `the closest printing wins with a clear margin`() {
        val frame = 0b1111L
        val chosen = PrintingChooser.choose(frame, listOf(base to 0b1110L, alt to -1L))
        assertThat(chosen).isEqualTo(base)
    }

    @Test fun `too close a race or too far a match settles nothing`() {
        // Within the margin: a signature shares its overnumbered twin's art.
        assertThat(PrintingChooser.choose(0L, listOf(base to 0b1L, alt to 0b11L))).isNull()
        // Nothing resembles the frame.
        assertThat(PrintingChooser.choose(0L, listOf(base to -1L, alt to 0x0FFF_FFFF_FFFFL))).isNull()
    }

    @Test fun `resolve needs both the frame and every printing's art`() = runTest {
        val art = PrintingArt { card: Card -> if (card == alt) -1L else 0L }
        assertThat(PrintingChooser.resolve(listOf(base, alt), FrameArt { -1L }, art)).isEqualTo(alt)
        assertThat(PrintingChooser.resolve(listOf(base, alt), null, art)).isNull()
        assertThat(PrintingChooser.resolve(listOf(base, alt), FrameArt { -1L }, PrintingArt { null })).isNull()
    }

    @Test fun `an unresolved printing stays the base one, flagged ambiguous`() = runTest {
        val identifier = ScanIdentifier(CardMatchingService(SeedCatalogue.cards))
        val ocr = OcrResult(extractedText = "Ahri - Alluring\nWhen I hold, you score 1 point.\nOGN • 066/298", confidence = 1.0)

        val unsure = identifier.identifyFrame(ocr, ScanLanguage.AUTO)
        assertThat(unsure.match!!.card).isEqualTo(base)
        assertThat(unsure.ambiguous).isTrue()

        val settled = identifier.identifyFrame(
            ocr, ScanLanguage.AUTO,
            frame = FrameArt { -1L },
            art = PrintingArt { card -> if (card == alt) -1L else 0L },
        )
        assertThat(settled.match!!.card).isEqualTo(alt)
        assertThat(settled.ambiguous).isFalse()
        assertThat(settled.resolvedByArt).isTrue()
        assertThat(settled.language.language.value).isEqualTo("en")
    }
}
