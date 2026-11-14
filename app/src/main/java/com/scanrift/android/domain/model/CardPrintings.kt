package com.scanrift.android.domain.model

import java.text.Normalizer
import java.util.Locale

/**
 * Every printing of one card, default printing first.
 *
 * @param identity normalised base identity ([CardPrintings.identity]).
 * @param name display name: the default printing's name without its variant suffix.
 * @param printings never empty; [defaultPrinting] comes first.
 */
data class PrintingGroup(
    val identity: String,
    val name: String,
    val printings: List<Card>,
) {
    val defaultPrinting: Card get() = printings.first()
    val hasChoice: Boolean get() = printings.size > 1
}

/**
 * Groups the printings (versions) of every card: the regular print, its alternate art,
 * overnumbered and signature copies, promos, starter and organized-play reprints.
 *
 * A card's identity is its name with every trailing parenthetical variant suffix
 * removed, case- and diacritic-folded: "Kai'Sa - Daughter of the Void (Overnumbered)"
 * and "(Signature)" both belong to "Kai'Sa - Daughter of the Void". Anything that
 * compares or aggregates *cards* rather than *printings* must use this identity, never
 * the card id: the id names one printing.
 *
 * Grouping is by name across every type and set. The catalogue never reuses a name for
 * two different cards of different types, which the seed test pins.
 */
object CardPrintings {

    /** Suffixes naming a print treatment. Any other suffix is shown verbatim. */
    private val TREATMENT_SUFFIXES = setOf("metal", "signature", "overnumbered", "alternate art", "starter")

    /** Supplemental products: their printings lose to a main-set printing for the default. */
    private val SUPPLEMENTAL_SET_IDS = setOf("OGS", "SGN")

    // ── Identity ─────────────────────────────────────────────────────────────

    /** [name] with every trailing `(...)` suffix removed. */
    fun baseName(name: String): String = split(name).first

    /** The trailing `(...)` suffixes of [name], in reading order ("A (B) (C)" → [B, C]). */
    fun variantSuffixes(name: String): List<String> = split(name).second

    /** Comparison key for a card name: base name, case- and diacritic-folded. */
    fun identity(name: String): String =
        Normalizer.normalize(baseName(name), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)

    /** Comparison key for a card. Equal for every printing of one card. */
    fun identity(card: Card): String = identity(card.name)

    /** True when both cards are printings of the same card. */
    fun isSameCard(lhs: Card?, rhs: Card?): Boolean =
        lhs != null && rhs != null && identity(lhs) == identity(rhs)

    // ── Grouping ─────────────────────────────────────────────────────────────

    /** One group per card identity in [cards], sorted by name. */
    fun groups(cards: List<Card>): List<PrintingGroup> =
        cards.asSequence()
            .distinctBy { it.id }
            .groupBy { identity(it) }
            .map { (identity, printings) ->
                val ordered = sortedPrintings(printings)
                PrintingGroup(identity, baseName(ordered.first().name), ordered)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, PrintingGroup::name).thenBy { it.identity })

    /** A lookup from any printing to its group. Build once per catalogue load. */
    fun index(cards: List<Card>): PrintingIndex = PrintingIndex(groups(cards))

    /** Groups whose name contains [query]; all groups when it is blank. */
    fun filter(groups: List<PrintingGroup>, query: String): List<PrintingGroup> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return groups
        return groups.filter { it.name.contains(trimmed, ignoreCase = true) }
    }

    /**
     * The printings matching a "Filter sets" query: set label, set id (raw or
     * normalised), public code or variant label. Every printing when [query] is blank.
     */
    fun filterPrintings(printings: List<Card>, query: String): List<Card> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return printings
        return printings.filter { card ->
            listOf(card.setLabel, card.setId, card.sourceSetId, card.publicCode, variantLabel(card))
                .any { it.contains(trimmed, ignoreCase = true) }
        }
    }

    // ── Default printing ─────────────────────────────────────────────────────

    /**
     * The printing a group stands for: the standard one, with special treatments pushed
     * back. A card that only exists as a Starter / Proving Grounds printing (plus promos)
     * still gets that Starter card.
     */
    fun defaultPrinting(printings: List<Card>): Card? = sortedPrintings(printings).firstOrNull()

    /** [printings] from most to least standard. Stable across runs: ties break on id. */
    fun sortedPrintings(printings: List<Card>): List<Card> =
        printings.sortedWith(
            Comparator<Card> { a, b -> compareRanks(rank(a), rank(b)) }
                .thenBy { it.collectorNumber }
                .thenBy { it.id },
        )

    /** Lower is more standard. Each component is a 0/1 penalty, most decisive first. */
    private fun rank(card: Card): IntArray {
        val suffixes = lowercasedSuffixes(card)
        return intArrayOf(
            if (isMetal(card)) 1 else 0,
            if (card.isJudgePromo) 1 else 0,
            if (card.isAlternateArt) 1 else 0,
            if (card.signature || "signature" in suffixes) 1 else 0,
            if (card.overnumbered || "overnumbered" in suffixes) 1 else 0,
            if (card.isSpecialPrint) 1 else 0,
            if (isPromo(card)) 1 else 0,
            if (suffixes.isEmpty()) 0 else 1,
            if (card.setId in SUPPLEMENTAL_SET_IDS) 1 else 0,
        )
    }

    private fun compareRanks(a: IntArray, b: IntArray): Int {
        for (i in a.indices) {
            val c = a[i].compareTo(b[i])
            if (c != 0) return c
        }
        return 0
    }

    // ── Labels ───────────────────────────────────────────────────────────────

    /**
     * What distinguishes this printing: "Regular", "Alternate Art", "Overnumbered",
     * "Signature", "Metal", "Starter", "Promo", or the suffix text ("Lunar Revel 2026").
     */
    fun variantLabel(card: Card): String {
        if (isMetal(card)) return "Metal"
        val suffixes = variantSuffixes(card.name)
        suffixes.firstOrNull { it.lowercase(Locale.ROOT) !in TREATMENT_SUFFIXES }?.let { return it }
        val lowered = suffixes.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        return when {
            card.signature || "signature" in lowered -> "Signature"
            card.overnumbered || "overnumbered" in lowered -> "Overnumbered"
            card.isAlternateArt || "alternate art" in lowered -> "Alternate Art"
            "starter" in lowered -> "Starter"
            isPromo(card) -> "Promo"
            else -> "Regular"
        }
    }

    /** "Secret Garden · Promo", "Spiritforged · Regular". */
    fun printingLabel(card: Card): String = "${card.setLabel} · ${variantLabel(card)}"

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val DIACRITICS = Regex("\\p{Mn}+")

    /** Peels trailing `(...)` groups off [name]; never strips the name down to nothing. */
    private fun split(name: String): Pair<String, List<String>> {
        var rest = name.trim()
        val suffixes = ArrayDeque<String>()
        while (rest.endsWith(")")) {
            val open = rest.lastIndexOf('(')
            if (open < 0) break
            val stripped = rest.substring(0, open).trim()
            if (stripped.isEmpty()) break
            suffixes.addFirst(rest.substring(open + 1, rest.length - 1).trim())
            rest = stripped
        }
        return rest to suffixes.toList()
    }

    private fun lowercasedSuffixes(card: Card): Set<String> =
        variantSuffixes(card.name).mapTo(HashSet()) { it.lowercase(Locale.ROOT) }

    private fun isMetal(card: Card): Boolean = "metal" in lowercasedSuffixes(card)

    private fun isPromo(card: Card): Boolean =
        card.rarity == Rarity.PROMO || card.setId == SetNormalizer.PROMO_SET_ID
}

/**
 * Printing groups keyed for O(1) lookup by any member's card id. Built once from the
 * catalogue snapshot (the scanner builds it when its matcher loads), never per frame.
 */
class PrintingIndex(val groups: List<PrintingGroup>) {

    private val byCardId: Map<String, PrintingGroup> =
        HashMap<String, PrintingGroup>().apply {
            groups.forEach { group -> group.printings.forEach { put(it.id, group) } }
        }

    /** The group [card] belongs to; a single-printing group when the index does not know it. */
    fun groupOf(card: Card): PrintingGroup =
        byCardId[card.id] ?: PrintingGroup(CardPrintings.identity(card), CardPrintings.baseName(card.name), listOf(card))

    /** Every printing of [card], default first; at least [card] itself. */
    fun printingsOf(card: Card): List<Card> = groupOf(card).printings

    /** True when [card] has another printing to switch to. */
    fun hasChoice(card: Card?): Boolean = card != null && groupOf(card).hasChoice

    companion object {
        val EMPTY = PrintingIndex(emptyList())
    }
}
