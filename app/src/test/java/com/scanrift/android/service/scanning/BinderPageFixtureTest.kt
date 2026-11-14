package com.scanrift.android.service.scanning

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.ScanLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A clean 3×3 page (Charm, Lillia, Irelia / Field Musicians, Sanctum Conservator,
 * Mosstomper / Promising Future, Brutalizer, Mister Root), with the collector lines ML Kit
 * actually returned for it: the full-page pass first, then the overlapping tile pass,
 * mapped back to page coordinates. Boxes are copied from the scan log (left, top, right,
 * bottom); the photo is 3000×4000.
 *
 * On this page the scanner used to list Mister Root twice — once as itself and once as
 * Arena Kingpin (`UNL-001`, from the clipped tile read `UNL• 1.`) — and miss Irelia, whose
 * line only ever came back as `SFD 057/221`, with the bullet lost.
 */
class BinderPageFixtureTest {

    private val known = SeedCatalogue.cards.flatMapTo(HashSet()) { listOf(it.setId, it.sourceSetId) }
    private val identifier = ScanIdentifier(CardMatchingService(SeedCatalogue.cards))
    private val aspect = 0.75f

    private fun line(text: String, l: Float, t: Float, r: Float, b: Float) = TextLine(text, NormalizedBox(l, t, r, b))

    private val pagePass = listOf(
        line("Charm", 0.0900f, 0.2620f, 0.1300f, 0.2700f),
        line("OGN 043/298", 0.0827f, 0.3228f, 0.1263f, 0.3270f),
        line("Field Musicians", 0.0900f, 0.5580f, 0.1800f, 0.5660f),
        line("VEN• 026/166 EN", 0.0840f, 0.6285f, 0.1400f, 0.6348f),
        line("OGN• 115/298", 0.0783f, 0.9345f, 0.1217f, 0.9395f),
        line("CHAMPION UNIT LILLIA 1ONIA • FAL", 0.4013f, 0.2005f, 0.5207f, 0.2085f),
        line("UNL 058/219", 0.3807f, 0.3268f, 0.4230f, 0.3310f),
        line("RAD • 030/167 • EN", 0.3767f, 0.6310f, 0.4347f, 0.6352f),
        line("SED 042/221", 0.3733f, 0.9365f, 0.4143f, 0.9402f),
        line("CHAMPION UNIT • /RELIA 10NIA", 0.6953f, 0.1980f, 0.7997f, 0.2068f),
        line("SFD 057/221", 0.6740f, 0.3245f, 0.7163f, 0.3288f),
        line("UNL 047/219", 0.6760f, 0.6345f, 0.7183f, 0.6388f),
        line("UNL • 127/219", 0.6733f, 0.9402f, 0.7157f, 0.9445f),
    )

    private val tilePass = listOf(
        line("CHAMPION UNIT LILLIA 10NIA • FAE", 0.3997f, 0.2008f, 0.5249f, 0.2087f),
        line("UNL • 058/219", 0.3807f, 0.3267f, 0.4230f, 0.3311f),
        line("M CHAMPION UNIT RELIA • 1OIA", 0.6817f, 0.1991f, 0.7997f, 0.2065f),
        line("OGN • 043/298", 0.0827f, 0.3228f, 0.1263f, 0.3269f),
        line("VEN 026/166 • EN", 0.0823f, 0.6298f, 0.1401f, 0.6342f),
        line("RAD •", 0.3766f, 0.6310f, 0.3920f, 0.6350f),
        line("RAD 030/167 • EN", 0.3762f, 0.6305f, 0.4339f, 0.6352f),
        line("UNL•(", 0.6790f, 0.6342f, 0.6973f, 0.6389f),
        line("OGN 115/298", 0.0783f, 0.9344f, 0.1219f, 0.9393f),
        line("SFD· 042/221", 0.3748f, 0.9356f, 0.4148f, 0.9412f),
        line("UNL• 1.", 0.6731f, 0.9400f, 0.6957f, 0.9439f),
        line("UNL • 047/219", 0.6758f, 0.6344f, 0.7184f, 0.6386f),
        line("UNL 127/219", 0.6732f, 0.9403f, 0.7158f, 0.9444f),
    )

    private val lines = MultiCardScanner.mergeLines(pagePass, tilePass)

    @Test
    fun `nine pockets give nine cards, one per pocket, in reading order`() {
        val scan = MultiCardScanner.scan(lines, known, aspect)
        assertThat(scan.cards.map { it.code }).containsExactly(
            "OGN-043", "UNL-058", "SFD-057",
            "VEN-026", "RAD-030", "UNL-047",
            "OGN-115", "SFD-042", "UNL-127",
        ).inOrder()
    }

    @Test
    fun `a clipped read is not a code`() {
        val codes = MultiCardScanner.findAnchors(listOf(line("UNL• 1.", 0.67f, 0.94f, 0.70f, 0.944f)), known).map { it.code }
        assertThat(codes).isEmpty()
    }

    @Test
    fun `a space-separated code needs its printed total`() {
        assertThat(MultiCardScanner.findAnchors(listOf(line("SFD 057/221", 0.67f, 0.32f, 0.72f, 0.33f)), known).map { it.code })
            .containsExactly("SFD-057")
        // Rules text with a set-like word and a number is not a code without the total.
        assertThat(MultiCardScanner.findAnchors(listOf(line("Gain XP 123 when", 0.1f, 0.5f, 0.3f, 0.51f)), known)).isEmpty()
    }

    @Test
    fun `two reads of one collector line are one card even when they disagree`() {
        val page = listOf(
            line("UNL • 127/219", 0.6733f, 0.9402f, 0.7157f, 0.9445f),
            line("UNL • 172/219", 0.6735f, 0.9400f, 0.7160f, 0.9443f), // a tile's misread
            line("UNL 127/219", 0.6732f, 0.9403f, 0.7158f, 0.9444f),
        )
        assertThat(MultiCardScanner.scan(page, known, aspect).cards.map { it.code }).containsExactly("UNL-127")
    }

    @Test
    fun `identical cards in two pockets stay two copies`() {
        val page = listOf(
            line("UNL • 127/219", 0.0733f, 0.9402f, 0.1157f, 0.9445f),
            line("UNL • 127/219", 0.6733f, 0.9402f, 0.7157f, 0.9445f),
        )
        assertThat(MultiCardScanner.scan(page, known, aspect).cards.map { it.code }).containsExactly("UNL-127", "UNL-127")
    }

    @Test
    fun `every card is identified as itself and nothing else`() = runTest {
        val scan = MultiCardScanner.scan(lines, known, aspect)
        val results = PageIdentifier.identify(scan, PageIdentifier.pageOcr(lines, imageAspect = aspect), identifier, ScanLanguage.EN)
        assertThat(results.map { it.outcome.match?.card?.name }).containsExactly(
            "Charm", "Lillia - Protector of Dreams", "Irelia - Fervent",
            "Field Musicians", "Sanctum Conservator", "Mosstomper",
            "Promising Future", "Brutalizer", "Mister Root",
        ).inOrder()
    }

    @Test
    fun `a card whose own code names nothing is left for the user, not matched to a neighbour`() = runTest {
        // A pocket whose code reads as a number no card has, with the card above's
        // collector line assigned into its body.
        val page = listOf(
            line("OGN • 043/298", 0.08f, 0.32f, 0.126f, 0.327f),
            line("OGN • 043/298", 0.08f, 0.62f, 0.126f, 0.627f), // second copy, so the grid has a pitch
            line("SFD • 999/221", 0.38f, 0.62f, 0.42f, 0.627f),
            line("SFD 057/221 text", 0.40f, 0.40f, 0.45f, 0.405f),
        )
        val scan = MultiCardScanner.scan(page, known, aspect)
        val results = PageIdentifier.identify(scan, PageIdentifier.pageOcr(page, imageAspect = aspect), identifier, ScanLanguage.EN)
        val unknown = results.single { it.found.code == "SFD-999" }
        assertThat(unknown.outcome.match).isNull()
    }
}
