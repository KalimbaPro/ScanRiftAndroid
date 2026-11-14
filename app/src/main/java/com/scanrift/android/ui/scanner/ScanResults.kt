package com.scanrift.android.ui.scanner

import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardLanguage
import java.util.UUID

/**
 * One row of the scan session: one physical card (or a stack the user grew with "+1").
 *
 * Rows never merge while scanning. Scanning the same card twice gives two rows, newest
 * first, so a binder's English and French copies can each have their language and
 * version fixed on their own row (ManaBox does the same). Identical rows only sum when
 * the session is written out, on the collection's entry key — see [collectionStacks].
 *
 * [id] is a per-row UUID and the only stable identity: LazyColumn keys and every edit
 * use it, never the card id.
 */
data class ScanResult(
    val card: Card,
    val quantity: Int = 1,
    val isFoil: Boolean = card.isAlwaysFoil,
    val language: CardLanguage = CardLanguage.EN,
    val id: String = UUID.randomUUID().toString(),
    /**
     * The collector code was read without its variant suffix and the art comparison could
     * not settle the printing: [card] is the base printing, and the version chip asks the
     * user to confirm. Cleared by any version pick or correction.
     */
    val ambiguous: Boolean = false,
)

/** Adds one scan as a new row at the top (newest first). */
fun List<ScanResult>.addingScan(card: Card, language: CardLanguage = CardLanguage.EN): List<ScanResult> =
    addingScanReturningId(card, language).first

/** As [addingScan], also returning the new row's id. */
fun List<ScanResult>.addingScanReturningId(
    card: Card,
    language: CardLanguage,
    ambiguous: Boolean = false,
): Pair<List<ScanResult>, String> {
    val row = ScanResult(card, language = language, ambiguous = ambiguous)
    return (listOf(row) + this) to row.id
}

/** Adds [quantity] copies as a new top row with an explicit foil flag — the binder-page path. */
fun List<ScanResult>.adding(card: Card, isFoil: Boolean, language: CardLanguage, quantity: Int = 1): List<ScanResult> =
    listOf(ScanResult(card, quantity = quantity, isFoil = isFoil || card.isAlwaysFoil, language = language)) + this

fun List<ScanResult>.withQuantity(id: String, quantity: Int): List<ScanResult> =
    if (quantity <= 0) filterNot { it.id == id } else map { if (it.id == id) it.copy(quantity = quantity) else it }

/**
 * Flips the foil flag of row [id]. A single copy flips in place; a "+1" stack splits one
 * copy off into a new row right after it, since only one of those cards is the foil.
 */
fun List<ScanResult>.togglingFoil(id: String): List<ScanResult> {
    val index = indexOfFirst { it.id == id }
    if (index < 0 || this[index].card.isAlwaysFoil) return this
    val result = this[index]
    val results = toMutableList()
    if (result.quantity > 1) {
        results[index] = result.copy(quantity = result.quantity - 1)
        results.add(index + 1, ScanResult(result.card, isFoil = !result.isFoil, language = result.language))
    } else {
        results[index] = result.copy(isFoil = !result.isFoil)
    }
    return results
}

/** Re-labels the whole row [id]; other rows of the same card keep their language. */
fun List<ScanResult>.withLanguage(id: String, language: CardLanguage): List<ScanResult> =
    map { if (it.id == id) it.copy(language = language) else it }

/**
 * The session as collection stacks: rows summed on the entry key `(card, isFoil,
 * language)` (scanning never sets a condition). This is the only place identical rows
 * merge.
 */
fun List<ScanResult>.collectionStacks(): List<ScanResult> =
    groupBy { Triple(it.card.id, it.isFoil, it.language) }
        .map { (_, rows) -> rows.first().copy(quantity = rows.sumOf { it.quantity }) }

/**
 * The foil flag a copy keeps when it is re-pointed at [newCard]: the new card's default
 * (`isAlwaysFoil`) unless the user had explicitly made the old one foil, which is only
 * possible when the old card was not always-foil.
 */
fun versionFoil(oldCard: Card?, oldFoil: Boolean, newCard: Card): Boolean =
    newCard.isAlwaysFoil || (oldFoil && oldCard?.isAlwaysFoil != true)

/** As [replacingCard], dropping the landing row id. */
fun List<ScanResult>.correcting(id: String, card: Card): List<ScanResult> = replacingCard(id, card).first

/**
 * Re-points row [id] at [card] (a correction, or another printing from the version
 * picker), keeping its quantity and language and following [versionFoil] for the foil
 * flag. Only that row changes: an identical row stays separate until write-out.
 *
 * @return the new list and the row's id (null when [id] is not in the list).
 */
fun List<ScanResult>.replacingCard(id: String, card: Card): Pair<List<ScanResult>, String?> {
    val index = indexOfFirst { it.id == id }
    if (index < 0) return this to null
    val corrected = this[index]
    val results = toMutableList()
    if (corrected.card.id == card.id) {
        // Picking the printing it already had confirms it.
        if (corrected.ambiguous) results[index] = corrected.copy(ambiguous = false)
        return results to id
    }
    results[index] = corrected.copy(card = card, isFoil = versionFoil(corrected.card, corrected.isFoil, card), ambiguous = false)
    return results to id
}
