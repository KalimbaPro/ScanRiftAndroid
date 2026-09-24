package com.scanrift.android.ui.scanner

import com.scanrift.android.domain.model.Card
import java.util.UUID

data class ScanResult(
    val card: Card,
    val quantity: Int = 1,
    val isFoil: Boolean = card.isAlwaysFoil,
    val id: String = UUID.randomUUID().toString(),
)

fun List<ScanResult>.addingScan(card: Card): List<ScanResult> {
    val index = indexOfFirst { it.card.id == card.id && it.isFoil == card.isAlwaysFoil }
    if (index < 0) return this + ScanResult(card)
    return toMutableList().apply { this[index] = this[index].copy(quantity = this[index].quantity + 1) }
}

fun List<ScanResult>.withQuantity(id: String, quantity: Int): List<ScanResult> =
    if (quantity <= 0) filterNot { it.id == id } else map { if (it.id == id) it.copy(quantity = quantity) else it }

fun List<ScanResult>.togglingFoil(id: String): List<ScanResult> {
    val index = indexOfFirst { it.id == id }
    if (index < 0 || this[index].card.isAlwaysFoil) return this
    val result = this[index]
    val targetFoil = !result.isFoil
    val twin = indexOfFirst { it.id != id && it.card.id == result.card.id && it.isFoil == targetFoil }
    val results = toMutableList()
    when {
        result.quantity > 1 && twin >= 0 -> {
            results[index] = result.copy(quantity = result.quantity - 1)
            results[twin] = results[twin].copy(quantity = results[twin].quantity + 1)
        }
        result.quantity > 1 -> {
            results[index] = result.copy(quantity = result.quantity - 1)
            results.add(index + 1, ScanResult(result.card, isFoil = targetFoil))
        }
        twin >= 0 -> {
            results[twin] = results[twin].copy(quantity = results[twin].quantity + result.quantity)
            results.removeAt(index)
        }
        else -> results[index] = result.copy(isFoil = targetFoil)
    }
    return results
}

fun List<ScanResult>.correcting(id: String, card: Card): List<ScanResult> {
    val index = indexOfFirst { it.id == id }
    if (index < 0) return this
    val corrected = this[index]
    val keepsChosenFoil = corrected.isFoil && !corrected.card.isAlwaysFoil
    val isFoil = card.isAlwaysFoil || keepsChosenFoil
    val twin = indexOfFirst { it.id != id && it.card.id == card.id && it.isFoil == isFoil }
    val results = toMutableList()
    if (twin >= 0) {
        results[twin] = results[twin].copy(quantity = results[twin].quantity + corrected.quantity)
        results.removeAt(index)
    } else {
        results[index] = corrected.copy(card = card, isFoil = isFoil)
    }
    return results
}
