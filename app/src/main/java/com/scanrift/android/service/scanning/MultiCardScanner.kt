package com.scanrift.android.service.scanning

import java.util.Locale
import kotlin.math.abs

/**
 * Binder-page geometry: turns the recognised lines of one photo into one entry per card.
 *
 * Pure — `[TextLine(text, box)]` in, [PageScan] out, no Android types — so the whole
 * page layout logic is covered by plain JUnit with synthetic pages. iOS runs the same
 * algorithm.
 *
 * 1. **Anchors.** Every collector-code match is one card. Two identical cards on one
 *    page are two anchors and therefore two copies — by design, that is what a binder
 *    page holds. Codes whose set prefix is not a known set are ignored, so "Deal 3" in
 *    rules text never becomes a card. Reads at the **same position** are one card,
 *    whatever they say ([dedupe]): the page pass and the overlapping tiles each read a
 *    collector line, and a second anchor there listed the card twice — or, from a
 *    clipped read like `UNL• 1.`, listed a card that is not on the page at all.
 * 2. **Card width** is the median horizontal pitch between anchors on the same row
 *    (binder pages are grids), else the vertical pitch × the card aspect ratio, else a
 *    multiple of the code line's own width for a lone card.
 * 3. **Line assignment.** The code sits on the card's bottom-left edge, so each other
 *    line belongs to the nearest anchor *below* it whose one-card-width band contains
 *    the line's centre, no more than one card height away.
 * 4. **Tiles.** Small text reads far better when cropped, so a page that yields fewer
 *    anchors than a full 3×3 page is re-read as overlapping 3×3 tiles; the tile lines are
 *    mapped back to page coordinates and merged by position (see [mergeLines]).
 */
object MultiCardScanner {

    /** Width ÷ height of a portrait card (63 × 88 mm). */
    const val CARD_ASPECT = 63f / 88f

    /** A full binder page. Fewer anchors than this on a page shot triggers the tile pass. */
    const val EXPECTED_PER_PAGE = 9

    /** Tile overlap as a fraction of a tile, so a code cut by one tile edge is whole in its neighbour. */
    const val TILE_OVERLAP = 0.18f

    /**
     * The collector code. Stricter than live scanning, because a page carries far more
     * rules text than one card:
     *
     * - the number is always three digits, as printed (`043`, never `43`): a clipped
     *   read such as `UNL• 1.` is not a code, and it used to become `UNL-001`, a card
     *   that was nowhere on the page;
     * - the set and number are joined by a separator, glued (`RAD010`), or — only when
     *   the printed total follows (`OGN 043/298`) — by plain space, which is how OCR
     *   most often reads the newer `OGN • 043/298` line once the bullet is lost.
     *
     * Group 1 set, 2 number, 3 suffix.
     */
    private val codePattern = Regex(
        """(?<![A-Za-z])([A-Z]{2,4})(?:\s*[-–—•·∙]\s*|\s+(?=\d{3}[a-z*]?\s*/\s*\d)|(?=\d{3}))(\d{3})([a-z*]?)(?![0-9])""",
    )

    private val totalAfter = Regex("""^\s*/\s*\d{2,3}""")

    /**
     * @param hasTotal the printed total (`/298`) follows the number, which a clipped or
     *   garbled read rarely reproduces; it breaks ties between reads of one position.
     */
    data class Anchor(
        val code: String,
        val setId: String,
        val number: Int,
        val line: TextLine,
        val box: NormalizedBox,
        val hasTotal: Boolean = false,
    )

    /**
     * One card found on the page.
     *
     * @param code normalised code, e.g. `RAD-010a`.
     * @param anchorText the whole collector line, which carries the language marker.
     * @param bounds the card's estimated outline, for the review overlay.
     * @param lines every line assigned to this card, anchor included, top to bottom.
     */
    data class PageCard(
        val code: String,
        val anchorText: String,
        val anchorBox: NormalizedBox,
        val bounds: NormalizedBox,
        val lines: List<TextLine>,
    ) {
        val text: String get() = lines.joinToString("\n") { it.text }

        /** Lines other than the collector line: where the name and rules text are. */
        val bodyText: String get() = lines.filter { it.text != anchorText }.joinToString("\n") { it.text }
    }

    data class PageScan(val cards: List<PageCard>, val cardWidth: Float) {
        val needsTiling: Boolean get() = cards.size < EXPECTED_PER_PAGE
    }

    /**
     * @param imageAspect the photo's width ÷ height. Boxes are normalised per axis, so a
     *   card's normalised height is `width × imageAspect ÷ CARD_ASPECT`.
     */
    fun scan(rawLines: List<TextLine>, knownSetIds: Set<String> = emptySet(), imageAspect: Float = 1f): PageScan {
        // The same collector-line repairs as live matching (`0GN`, `1l6`, `066 a`, …).
        val lines = rawLines.map { line ->
            val repaired = CollectorText.normalize(line.text)
            if (repaired == line.text) line else line.copy(text = repaired)
        }
        val anchors = dedupe(findAnchors(lines, knownSetIds))
        if (anchors.isEmpty()) return PageScan(emptyList(), 0f)

        val width = estimateCardWidth(anchors, imageAspect)
        val height = width * imageAspect / CARD_ASPECT
        val anchorLines = anchors.mapTo(HashSet()) { it.line }
        val assigned = anchors.associateWith { mutableListOf(it.line) }

        for (line in lines) {
            if (line in anchorLines) continue
            owner(line, anchors, width, height)?.let { assigned.getValue(it) += line }
        }

        val cards = anchors.map { anchor ->
            val left = anchor.box.left - width * CODE_LEFT_INSET
            PageCard(
                code = anchor.code,
                anchorText = anchor.line.text,
                anchorBox = anchor.box,
                bounds = NormalizedBox(left, anchor.box.bottom + height * BOTTOM_MARGIN - height, left + width, anchor.box.bottom + height * BOTTOM_MARGIN),
                lines = assigned.getValue(anchor).sortedBy { it.box.top },
            )
        }
        // Reading order: rows top to bottom, then left to right.
        val rowTolerance = height * 0.5f
        return PageScan(
            cards.sortedWith(compareBy<PageCard>({ (it.anchorBox.centerY / rowTolerance).toInt() }, { it.anchorBox.left })),
            width,
        )
    }

    fun findAnchors(lines: List<TextLine>, knownSetIds: Set<String> = emptySet()): List<Anchor> {
        val anchors = mutableListOf<Anchor>()
        for (line in lines) {
            val text = line.text
            for (match in codePattern.findAll(text)) {
                val setId = match.groupValues[1].uppercase(Locale.ROOT)
                if (knownSetIds.isNotEmpty() && setId !in knownSetIds) continue
                val number = match.groupValues[2].toIntOrNull() ?: continue
                // Locale.ROOT: Arabic-Indic digits would never match a card.
                val code = "$setId-${"%03d".format(Locale.ROOT, number)}${match.groupValues[3]}"
                // Two codes on one OCR line (adjacent cards read as one line) each get
                // their share of the line's box, by character position.
                val length = text.length.coerceAtLeast(1).toFloat()
                val box = NormalizedBox(
                    left = line.box.left + line.box.width * (match.range.first / length),
                    top = line.box.top,
                    right = line.box.left + line.box.width * ((match.range.last + 1) / length),
                    bottom = line.box.bottom,
                )
                val hasTotal = totalAfter.containsMatchIn(text.substring(match.range.last + 1))
                anchors += Anchor(code, setId, number, line, box, hasTotal)
            }
        }
        return anchors
    }

    /**
     * One anchor per physical collector line. Two anchors are the same line when they
     * start within one code-line width of each other and sit within two line heights
     * vertically — far closer than two cards can be (a card is ~2.5 code-line widths
     * wide). The page pass and every overlapping tile re-read the same line, and
     * [mergeLines] only drops a re-read whose text is identical, so `UNL • 127/219`,
     * `UNL 127/219` and `UNL• 1.` all used to arrive as separate cards.
     *
     * Within a group the code read most often wins, then a read carrying the printed
     * total, then the earliest (the full-page pass comes first). Identical cards in
     * different pockets are never merged: they are a card width apart.
     */
    fun dedupe(anchors: List<Anchor>): List<Anchor> {
        val groups = mutableListOf<MutableList<Anchor>>()
        for (anchor in anchors) {
            val group = groups.firstOrNull { members -> members.any { samePosition(it, anchor) } }
            if (group == null) groups += mutableListOf(anchor) else group += anchor
        }
        return groups.map { members ->
            val byCode = members.groupBy { it.code }
            val best = byCode.values.maxWith(
                compareBy<List<Anchor>> { it.size }
                    .thenBy { reads -> reads.any { it.hasTotal } }
                    .thenByDescending { reads -> members.indexOf(reads.first()) },
            )
            best.firstOrNull { it.hasTotal } ?: best.first()
        }
    }

    private fun samePosition(a: Anchor, b: Anchor): Boolean {
        val width = maxOf(a.box.width, b.box.width)
        val height = maxOf(a.box.height, b.box.height)
        return abs(a.box.left - b.box.left) < width && abs(a.box.centerY - b.box.centerY) < height * 2f
    }

    /**
     * Median horizontal pitch between neighbouring anchors on the same row; else the
     * vertical pitch converted through the card aspect; else a lone-card estimate.
     */
    fun estimateCardWidth(anchors: List<Anchor>, imageAspect: Float = 1f): Float {
        val rowTolerance = anchors.map { it.box.height }.median() * 2f
        val horizontal = anchors.mapNotNull { a ->
            anchors.filter { b -> b !== a && b.box.left > a.box.left && abs(b.box.centerY - a.box.centerY) < rowTolerance }
                .minOfOrNull { it.box.left - a.box.left }
        }.filter { it > 0f }
        if (horizontal.isNotEmpty()) return horizontal.median()

        val vertical = anchors.mapNotNull { a ->
            anchors.filter { b -> b !== a && b.box.top > a.box.top + rowTolerance && abs(b.box.left - a.box.left) < 0.1f }
                .minOfOrNull { it.box.top - a.box.top }
        }
        if (vertical.isNotEmpty()) return vertical.median() * CARD_ASPECT / imageAspect

        return (anchors.maxOf { it.line.box.width } * LONE_CARD_WIDTH_PER_CODE).coerceIn(0.05f, 1f)
    }

    private fun owner(line: TextLine, anchors: List<Anchor>, width: Float, height: Float): Anchor? {
        val x = line.box.centerX
        val y = line.box.centerY
        return anchors
            .filter { a ->
                val left = a.box.left - width * CODE_LEFT_INSET
                x >= left && x <= left + width &&
                    y <= a.box.bottom + height * BOTTOM_MARGIN &&
                    y >= a.box.bottom - height * MAX_REACH
            }
            .minByOrNull { a -> abs(a.box.bottom - y) }
    }

    // ── Tiles ────────────────────────────────────────────────────────────────

    /** Overlapping [rows]×[columns] tiles covering the unit square. */
    fun tiles(rows: Int = 3, columns: Int = 3, overlap: Float = TILE_OVERLAP): List<NormalizedBox> {
        val tileW = 1f / columns * (1f + overlap)
        val tileH = 1f / rows * (1f + overlap)
        return buildList {
            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    val left = if (columns == 1) 0f else (1f - tileW) * c / (columns - 1)
                    val top = if (rows == 1) 0f else (1f - tileH) * r / (rows - 1)
                    add(NormalizedBox(left, top, left + tileW, top + tileH))
                }
            }
        }
    }

    /** A line read from a crop of [tile], mapped back into page coordinates. */
    fun fromTile(line: TextLine, tile: NormalizedBox): TextLine =
        TextLine(line.text, line.box.scale(tile.width, tile.height).offset(tile.left, tile.top))

    /**
     * Adds [extra] lines (from tiles) to [base], dropping any that repeat a line already
     * present: same text ignoring case and spacing, centres within a small distance.
     * Tiles overlap, so the same line usually arrives several times.
     */
    fun mergeLines(base: List<TextLine>, extra: List<TextLine>): List<TextLine> {
        val merged = base.toMutableList()
        for (line in extra) {
            val key = line.text.normalizedKey()
            val duplicate = merged.any { existing ->
                existing.text.normalizedKey() == key &&
                    abs(existing.box.centerX - line.box.centerX) < MERGE_DISTANCE &&
                    abs(existing.box.centerY - line.box.centerY) < MERGE_DISTANCE
            }
            if (!duplicate) merged += line
        }
        return merged
    }

    private fun String.normalizedKey() = lowercase(Locale.ROOT).filterNot { it.isWhitespace() }

    private fun List<Float>.median(): Float {
        if (isEmpty()) return 0f
        val sorted = sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
    }

    /** The collector line starts this far in from the card's left edge, in card widths. */
    private const val CODE_LEFT_INSET = 0.08f

    /** The card's bottom edge sits this far below the code line, in card heights. */
    private const val BOTTOM_MARGIN = 0.04f

    /** A line further above its anchor than this, in card heights, belongs to no card. */
    private const val MAX_REACH = 1.02f

    /** A lone collector line spans roughly this fraction of the card's width, inverted. */
    private const val LONE_CARD_WIDTH_PER_CODE = 2.5f

    private const val MERGE_DISTANCE = 0.02f
}
