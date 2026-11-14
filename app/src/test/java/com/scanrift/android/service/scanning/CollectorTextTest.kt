package com.scanrift.android.service.scanning

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tolerant collector-code reading, with strings taken from the ML Kit benchmark's misreads. */
class CollectorTextTest {

    private val matcher = CardMatchingService(SeedCatalogue.cards)

    private fun read(text: String) = matcher.findMatch(OcrResult(extractedText = text, confidence = 1.0))

    @Test fun `normalisation repairs the usual damage`() {
        assertThat(CollectorText.normalize("OGN • 066 a/298")).isEqualTo("OGN • 066a/298")
        assertThat(CollectorText.normalize("OGN • 066o/298")).isEqualTo("OGN • 066a/298")
        assertThat(CollectorText.normalize("OGN • 066d/298")).isEqualTo("OGN • 066a/298")
        listOf("×", "✱", "+", "x", " * ").forEach { star ->
            assertThat(CollectorText.normalize("SFD • 227$star/221")).isEqualTo("SFD • 227*/221")
        }
        assertThat(CollectorText.normalize("OGN • 066a1298")).isEqualTo("OGN • 066a/298")
        assertThat(CollectorText.normalize("OGN 1l6/298")).isEqualTo("OGN 116/298")
        assertThat(CollectorText.normalize("RAD l81/167 • EN")).isEqualTo("RAD 181/167 • EN")
        assertThat(CollectorText.normalize("0GN• 024/298 KR")).isEqualTo("OGN• 024/298 KR")
    }

    @Test fun `rules text is left alone`() {
        val rules = "Deal 3 to a unit. Draw 1. You may pay 2 to return me to your hand."
        assertThat(CollectorText.normalize(rules)).isEqualTo(rules)
    }

    @Test fun `misread codes still find the exact printing`() {
        assertThat(read("0GN• 024/298 KR")?.card?.riftboundId).isEqualTo("ogn-024-298")
        assertThat(read("OGN 1l6/298\nJSix More Vodka")?.card?.riftboundId).isEqualTo("ogn-116-298")
        assertThat(read("RAD l81/167 • EN")?.card?.riftboundId).isEqualTo("rad-181-167")
        assertThat(read("OGN • 066 a/298")?.card?.riftboundId).isEqualTo("ogn-066a-298")
        assertThat(read("SFD • 227×/221")?.card?.riftboundId).isEqualTo("sfd-227*-221")
    }

    @Test fun `a set code with one wrong letter is repaired only when the total agrees`() {
        assertThat(read("\"Man, I'm g0od.\"\nSED 020a/221\nJLeague Splash Team")?.card?.riftboundId)
            .isEqualTo("sfd-020a-221")
        // Same typo, a total no set prints: no guess.
        assertThat(read("SED 020a/999")).isNull()
    }

    @Test fun `a signature that lost a digit is not another card`() {
        // "SFD 23*/221" is sfd-223* with its first digit unread; it must not become sfd-023.
        assertThat(read("Goodbye..for now.\"\nSFD 23*/221")).isNull()
    }

    @Test fun `a suffix-less code over a number with variants is ambiguous`() {
        val match = read("OGN • 066/298")!!
        assertThat(match.card.riftboundId).isEqualTo("ogn-066-298")
        assertThat(match.isAmbiguous).isTrue()
        assertThat(match.unresolvedPrintings.map { it.riftboundId }).containsExactly("ogn-066-298", "ogn-066a-298")
        // Read with its suffix it is certain.
        assertThat(read("OGN • 066a/298")!!.isAmbiguous).isFalse()
        // A number with a single printing is never ambiguous.
        assertThat(read("OGN • 001/298")!!.isAmbiguous).isFalse()
    }
}
