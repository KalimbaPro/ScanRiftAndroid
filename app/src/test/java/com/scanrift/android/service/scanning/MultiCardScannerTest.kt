package com.scanrift.android.service.scanning

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Synthetic binder pages. A 3×3 page photographed in portrait (aspect 0.75) puts each
 * card ~0.31 wide and ~0.32 tall in normalised coordinates; every card prints its name
 * near the top, rules text in the middle, and its collector line at the bottom left.
 */
class MultiCardScannerTest {

    private val known = setOf("OGN", "RAD", "SFD", "VEN")
    private val aspect = 0.75f
    private val pitchX = 0.32f
    private val cardW = 0.30f
    private val cardH = cardW * aspect / MultiCardScanner.CARD_ASPECT // ≈ 0.314
    private val pitchY = 0.33f

    private fun box(left: Float, top: Float, width: Float, height: Float = 0.015f) =
        NormalizedBox(left, top, left + width, top + height)

    /** The three lines of one card whose top-left corner is at ([x], [y]). */
    private fun cardLines(x: Float, y: Float, name: String, code: String, rules: String = "When you play me, draw 1."): List<TextLine> =
        listOf(
            TextLine(name, box(x + 0.03f, y + 0.02f, 0.2f)),
            TextLine(rules, box(x + 0.03f, y + cardH * 0.6f, 0.24f)),
            TextLine(code, box(x + cardW * 0.08f, y + cardH * 0.93f, 0.12f, 0.01f)),
        )

    private fun page(codes: List<String>): List<TextLine> = codes.flatMapIndexed { i, code ->
        val row = i / 3
        val col = i % 3
        cardLines(0.02f + col * pitchX, 0.01f + row * pitchY, "Card $i", code)
    }

    private val nineCodes = (1..9).map { "OGN • ${"%03d".format(it)}/298 • EN" }

    @Test
    fun `a full page yields nine cards in reading order with their own name lines`() {
        val scan = MultiCardScanner.scan(page(nineCodes), known, aspect)

        assertThat(scan.cards).hasSize(9)
        assertThat(scan.needsTiling).isFalse()
        assertThat(scan.cards.map { it.code }).containsExactlyElementsIn((1..9).map { "OGN-${"%03d".format(it)}" }).inOrder()
        scan.cards.forEachIndexed { i, card ->
            assertThat(card.lines.first().text).isEqualTo("Card $i")
            assertThat(card.lines).hasSize(3)
        }
    }

    @Test
    fun `card width comes from the horizontal pitch of the grid`() {
        val scan = MultiCardScanner.scan(page(nineCodes), known, aspect)
        assertThat(scan.cardWidth).isWithin(0.005f).of(pitchX)
    }

    @Test
    fun `a single column falls back to the vertical pitch through the card aspect`() {
        val lines = listOf(0, 3, 6).flatMap { i -> cardLines(0.02f, 0.01f + (i / 3) * pitchY, "Card $i", nineCodes[i]) }
        val anchors = MultiCardScanner.findAnchors(lines, known)
        val width = MultiCardScanner.estimateCardWidth(anchors, aspect)
        assertThat(width).isWithin(0.01f).of(pitchY * MultiCardScanner.CARD_ASPECT / aspect)
    }

    @Test
    fun `two identical cards on one page are two copies`() {
        val codes = nineCodes.toMutableList().apply { this[4] = this[0] }
        val scan = MultiCardScanner.scan(page(codes), known, aspect)
        assertThat(scan.cards.count { it.code == "OGN-001" }).isEqualTo(2)
        assertThat(scan.cards).hasSize(9)
    }

    @Test
    fun `codes from unknown sets and rules text are not anchors`() {
        val lines = page(nineCodes.take(2)) + TextLine("Deal 3 to an enemy unit. THE-3 rule", box(0.7f, 0.2f, 0.2f))
        val scan = MultiCardScanner.scan(lines, known, aspect)
        assertThat(scan.cards.map { it.code }).containsExactly("OGN-001", "OGN-002")
    }

    @Test
    fun `suffixes and glued codes are read`() {
        val lines = listOf(
            TextLine("VEN-042a/166", box(0.1f, 0.3f, 0.1f)),
            TextLine("RAD010/167", box(0.5f, 0.3f, 0.1f)),
            TextLine("SFD • 229*/221 • FR", box(0.1f, 0.7f, 0.12f)),
        )
        assertThat(MultiCardScanner.findAnchors(lines, known).map { it.code })
            .containsExactly("VEN-042a", "RAD-010", "SFD-229*")
    }

    @Test
    fun `two codes merged onto one OCR line become two anchors split along the line`() {
        val line = TextLine("OGN-001/298 EN      OGN-002/298 EN", box(0.05f, 0.4f, 0.6f))
        val anchors = MultiCardScanner.findAnchors(listOf(line), known)
        assertThat(anchors.map { it.code }).containsExactly("OGN-001", "OGN-002").inOrder()
        assertThat(anchors[0].box.right).isLessThan(anchors[1].box.left)
    }

    @Test
    fun `a line belongs to the nearest anchor below it, not the card above or beside`() {
        val scan = MultiCardScanner.scan(page(nineCodes), known, aspect)
        val middle = scan.cards[4]
        assertThat(middle.lines.map { it.text }).containsExactly("Card 4", "When you play me, draw 1.", nineCodes[4]).inOrder()
        assertThat(middle.bodyText).doesNotContain("OGN")
    }

    @Test
    fun `a page with missing codes asks for the tile pass`() {
        val scan = MultiCardScanner.scan(page(nineCodes.take(6)), known, aspect)
        assertThat(scan.cards).hasSize(6)
        assertThat(scan.needsTiling).isTrue()
    }

    @Test
    fun `a lone card still gets its lines`() {
        val lines = cardLines(0.3f, 0.2f, "Lone", "OGN-021/298")
        val scan = MultiCardScanner.scan(lines, known, aspect)
        assertThat(scan.cards.single().lines.map { it.text }).contains("Lone")
    }

    @Test
    fun `nothing readable yields no cards`() {
        assertThat(MultiCardScanner.scan(emptyList(), known).cards).isEmpty()
        assertThat(MultiCardScanner.scan(listOf(TextLine("hello", box(0.1f, 0.1f, 0.1f))), known).cards).isEmpty()
    }

    // ── Tiles ────────────────────────────────────────────────────────────────

    @Test
    fun `tiles overlap and cover the whole page`() {
        val tiles = MultiCardScanner.tiles()
        assertThat(tiles).hasSize(9)
        assertThat(tiles.minOf { it.left }).isWithin(1e-5f).of(0f)
        assertThat(tiles.minOf { it.top }).isWithin(1e-5f).of(0f)
        assertThat(tiles.maxOf { it.right }).isWithin(1e-5f).of(1f)
        assertThat(tiles.maxOf { it.bottom }).isWithin(1e-5f).of(1f)
        // Neighbours overlap, so a line cut by one tile's edge is whole in the next.
        assertThat(tiles[0].right).isGreaterThan(tiles[1].left)
        assertThat(tiles[0].bottom).isGreaterThan(tiles[3].top)
    }

    @Test
    fun `a tile line maps back to page coordinates`() {
        val tile = NormalizedBox(0.5f, 0.25f, 0.9f, 0.75f)
        val mapped = MultiCardScanner.fromTile(TextLine("x", NormalizedBox(0.5f, 0.5f, 1f, 1f)), tile)
        assertThat(mapped.box.left).isWithin(1e-5f).of(0.7f)
        assertThat(mapped.box.top).isWithin(1e-5f).of(0.5f)
        assertThat(mapped.box.right).isWithin(1e-5f).of(0.9f)
        assertThat(mapped.box.bottom).isWithin(1e-5f).of(0.75f)
    }

    @Test
    fun `merging drops repeats from overlapping tiles but keeps a second identical card elsewhere`() {
        val base = listOf(TextLine("OGN-001/298", box(0.1f, 0.3f, 0.1f)))
        val extra = listOf(
            TextLine("OGN-001 /298", box(0.105f, 0.302f, 0.1f)), // same line, re-read by a tile
            TextLine("OGN-001/298", box(0.6f, 0.3f, 0.1f)), // the same card, another pocket
            TextLine("OGN-002/298", box(0.1f, 0.7f, 0.1f)), // a code only the tile could read
        )
        val merged = MultiCardScanner.mergeLines(base, extra)
        assertThat(merged).hasSize(3)
        assertThat(MultiCardScanner.findAnchors(merged, known).map { it.code })
            .containsExactly("OGN-001", "OGN-001", "OGN-002")
    }

    @Test
    fun `tile pass recovers the missing cards end to end`() {
        val full = page(nineCodes.take(6))
        // Pretend the bottom row was only legible once cropped: its lines arrive in tile
        // coordinates from the bottom-left tile and its neighbours.
        val bottomRow = page(nineCodes).drop(18)
        val tiles = MultiCardScanner.tiles()
        val fromTiles = bottomRow.map { line ->
            val tile = tiles.first { line.box.centerX in it.left..it.right && line.box.centerY in it.top..it.bottom && it.top > 0.5f }
            val local = NormalizedBox(
                (line.box.left - tile.left) / tile.width, (line.box.top - tile.top) / tile.height,
                (line.box.right - tile.left) / tile.width, (line.box.bottom - tile.top) / tile.height,
            )
            MultiCardScanner.fromTile(TextLine(line.text, local), tile)
        }
        val scan = MultiCardScanner.scan(MultiCardScanner.mergeLines(full, fromTiles), known, aspect)
        assertThat(scan.cards).hasSize(9)
    }

    @Test
    fun `each card carries its own language evidence`() {
        val lines = cardLines(0.02f, 0.01f, "Carte", "OGN • 001/298 • FR") +
            cardLines(0.34f, 0.01f, "Card", "OGN • 002/298 • EN")
        val scan = MultiCardScanner.scan(lines, known, aspect)
        assertThat(scan.cards.map { LanguageDetector.detect(it.text).language.code }).containsExactly("FR", "EN").inOrder()
    }
}
