package com.scanrift.android.ui.scanner

import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardLanguage

/*
 * Picking another printing (version) of a scanned card. Pure state transitions, so the
 * replace / merge / auto-add re-key rules run on plain JUnit; the ViewModel only applies
 * them and performs the database move they ask for.
 */

/** What the version picker is choosing a printing for. */
sealed interface VersionTarget {
    /** A session row (the last scan's row, or any row of the summary / sidebar). */
    data class Row(val id: String) : VersionTarget

    /** A binder-page item under review. */
    data class PageItem(val id: String) : VersionTarget

    /** The last scan in auto-add mode, which keeps no session row: its copies are in the database. */
    data object AutoAdded : VersionTarget
}

/**
 * Copies auto-add wrote for the last scan: the scan itself plus every "+1" since. These
 * are what a version change re-keys; the user's earlier copies of the card are not.
 */
data class AutoAddedScan(
    val card: Card,
    val isFoil: Boolean,
    val language: CardLanguage,
    val quantity: Int = 1,
)

/** A database re-key the ViewModel must perform after a version change in auto-add mode. */
data class CopyMove(val from: AutoAddedScan, val to: AutoAddedScan)

/** The card the picker currently stands on, or null when the target is gone. */
fun ScannerUiState.versionTargetCard(target: VersionTarget? = versionTarget): Card? = when (target) {
    is VersionTarget.Row -> results.firstOrNull { it.id == target.id }?.card
    is VersionTarget.PageItem -> page?.items?.firstOrNull { it.id == target.id }?.card
    VersionTarget.AutoAdded -> lastAutoAdded?.card ?: lastScanned
    null -> null
}

/** The picker target for the last-scanned banner: its session row, or the auto-added copies. */
fun ScannerUiState.lastScanVersionTarget(): VersionTarget? = when {
    lastResultId != null && lastResult != null -> VersionTarget.Row(lastResultId)
    lastAutoAdded != null -> VersionTarget.AutoAdded
    else -> null
}

/**
 * Re-points session row [id] at [card] (that row only) and, when it is the last scan,
 * moves the last-scanned banner with it.
 */
fun ScannerUiState.withRowCard(id: String, card: Card): ScannerUiState {
    val (results, landedId) = results.replacingCard(id, card)
    if (id != lastResultId) return copy(results = results)
    val landed = results.firstOrNull { it.id == landedId }
    return copy(
        results = results,
        lastResultId = landed?.id,
        lastScanned = landed?.card ?: card,
        lastLanguage = landed?.language ?: lastLanguage,
    )
}

/** Re-points binder-page item [id] at [card], foil following [versionFoil]. */
fun ScannerUiState.withPageItemCard(id: String, card: Card): ScannerUiState {
    val page = page ?: return this
    return copy(page = page.copy(items = page.items.withItemCard(id, card)))
}

/** Binder-page items with item [id] re-pointed at [card], foil following [versionFoil]. */
fun List<PageItem>.withItemCard(id: String, card: Card): List<PageItem> =
    map { if (it.id == id) it.copy(card = card, isFoil = versionFoil(it.card, it.isFoil, card)) else it }

/**
 * Re-points the auto-added last scan at [card]. Returns the new state and the database
 * move that makes the collection agree; null move when nothing changes.
 */
fun ScannerUiState.withAutoAddedCard(card: Card): Pair<ScannerUiState, CopyMove?> {
    val from = lastAutoAdded ?: return this to null
    if (from.card.id == card.id) return copy(lastAmbiguous = false) to null
    val to = from.copy(card = card, isFoil = versionFoil(from.card, from.isFoil, card))
    return copy(lastAutoAdded = to, lastScanned = card, lastAmbiguous = false) to CopyMove(from, to)
}

/** Applies a version pick for [target]. The move is non-null only for [VersionTarget.AutoAdded]. */
fun ScannerUiState.withVersion(target: VersionTarget, card: Card): Pair<ScannerUiState, CopyMove?> = when (target) {
    is VersionTarget.Row -> withRowCard(target.id, card) to null
    is VersionTarget.PageItem -> withPageItemCard(target.id, card) to null
    VersionTarget.AutoAdded -> withAutoAddedCard(card)
}
