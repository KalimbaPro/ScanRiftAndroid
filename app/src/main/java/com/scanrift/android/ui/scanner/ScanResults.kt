package com.scanrift.android.ui.scanner

import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardLanguage
import java.util.UUID

/**
 * One row of the scan session. Rows merge on `(card, isFoil, language)` — the same
 * identity an owned stack has, minus condition, which scanning never sets.
 */
data class ScanResult(
    val card: Card,
    val quantity: Int = 1,
    val isFoil: Boolean = card.isAlwaysFoil,
    val language: CardLanguage = CardLanguage.EN,
    val id: String = UUID.randomUUID().toString(),
)

private fun ScanResult.sameStack(card: Card, isFoil: Boolean, language: CardLanguage) =
    this.card.id == card.id && this.isFoil == isFoil && this.language == language

/** Adds one scan, merging into an existing row with the same identity. */
fun List<ScanResult>.addingScan(card: Card, language: CardLanguage = CardLanguage.EN): List<ScanResult> =
    addingScanReturningId(card, language).first

/** As [addingScan], also returning the id of the row the scan landed in. */
fun List<ScanResult>.addingScanReturningId(card: Card, language: CardLanguage): Pair<List<ScanResult>, String> {
    val index = indexOfFirst { it.sameStack(card, card.isAlwaysFoil, language) }
    if (index < 0) {
        val row = ScanResult(card, language = language)
        return (this + row) to row.id
    }
    val results = toMutableList().apply { this[index] = this[index].copy(quantity = this[index].quantity + 1) }
    return results to results[index].id
}

fun List<ScanResult>.withQuantity(id: String, quantity: Int): List<ScanResult> =
    if (quantity <= 0) filterNot { it.id == id } else map { if (it.id == id) it.copy(quantity = quantity) else it }

fun List<ScanResult>.togglingFoil(id: String): List<ScanResult> {
    val index = indexOfFirst { it.id == id }
    if (index < 0 || this[index].card.isAlwaysFoil) return this
    val result = this[index]
    val targetFoil = !result.isFoil
    val twin = indexOfFirst { it.id != id && it.sameStack(result.card, targetFoil, result.language) }
    val results = toMutableList()
    when {
        result.quantity > 1 && twin >= 0 -> {
            results[index] = result.copy(quantity = result.quantity - 1)
            results[twin] = results[twin].copy(quantity = results[twin].quantity + 1)
        }
        result.quantity > 1 -> {
            results[index] = result.copy(quantity = result.quantity - 1)
            results.add(index + 1, ScanResult(result.card, isFoil = targetFoil, language = result.language))
        }
        twin >= 0 -> {
            results[twin] = results[twin].copy(quantity = results[twin].quantity + result.quantity)
            results.removeAt(index)
        }
        else -> results[index] = result.copy(isFoil = targetFoil)
    }
    return results
}

/**
 * Moves a whole row to [language], merging into a twin row that already has it.
 * Unlike the foil toggle this moves every copy: a mis-detected language is wrong for
 * the whole stack, not for one card of it.
 */
fun List<ScanResult>.withLanguage(id: String, language: CardLanguage): List<ScanResult> {
    val index = indexOfFirst { it.id == id }
    if (index < 0 || this[index].language == language) return this
    val result = this[index]
    val twin = indexOfFirst { it.id != id && it.sameStack(result.card, result.isFoil, language) }
    val results = toMutableList()
    if (twin >= 0) {
        results[twin] = results[twin].copy(quantity = results[twin].quantity + result.quantity)
        results.removeAt(index)
    } else {
        results[index] = result.copy(language = language)
    }
    return results
}

fun List<ScanResult>.correcting(id: String, card: Card): List<ScanResult> {
    val index = indexOfFirst { it.id == id }
    if (index < 0) return this
    val corrected = this[index]
    val keepsChosenFoil = corrected.isFoil && !corrected.card.isAlwaysFoil
    val isFoil = card.isAlwaysFoil || keepsChosenFoil
    val twin = indexOfFirst { it.id != id && it.sameStack(card, isFoil, corrected.language) }
    val results = toMutableList()
    if (twin >= 0) {
        results[twin] = results[twin].copy(quantity = results[twin].quantity + corrected.quantity)
        results.removeAt(index)
    } else {
        results[index] = corrected.copy(card = card, isFoil = isFoil)
    }
    return results
}
