package com.scanrift.android.service.importer

import com.scanrift.android.core.text.normalized
import com.scanrift.android.data.local.dao.CardDao
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.mapper.toDomain
import com.scanrift.android.data.repository.DeckRepository
import com.scanrift.android.di.IoDispatcher
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.service.deck.DeckValidator
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Builds a deck from a pasted list, replacing whatever the deck held before.
 *
 * Parsing is [DeckListParser]'s job; this resolves the parsed tokens against the
 * catalogue and writes the result. Unresolved lines are collected and reported rather
 * than aborting the import — a decklist routinely mentions cards from a set the local
 * catalogue has not synced yet, and dropping the other fifty-odd cards over that would
 * be useless.
 */
class DeckImportService @Inject constructor(
    private val cardDao: CardDao,
    private val deckRepository: DeckRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun import(deckId: String, parsed: ParsedDeckList): DeckImportResult = withContext(io) {
        if (parsed.isEmpty) return@withContext DeckImportResult(notes = listOf("Nothing to import."))

        val skipped = mutableListOf<String>()
        val notes = mutableListOf<String>()

        var legend: Card? = null
        var champion: Card? = null
        // Keyed on (card id, section) to match the unique index on deck_entries; two
        // lines naming the same card in the same section have to fold into one row.
        val entries = LinkedHashMap<Pair<String, DeckSection>, Int>()
        var linesImported = 0

        parsed.lines.forEach { line ->
            val card = resolve(line.token, parsed.source)
            if (card == null) {
                skipped += line.token
                return@forEach
            }
            linesImported++

            when {
                // A legend is a slot, whatever section the list filed it under. The
                // first one wins; a list with two is malformed and the rest are noise.
                card.type == CardType.LEGEND -> {
                    if (legend == null) legend = card else skipped += line.token
                }

                // The champion is a slot. It gets no entry of its own here: a text list
                // names it under `Champion:` *and* again in `MainDeck:`, so counting it
                // in both places would double it. The post-pass below guarantees the
                // one main-deck copy a champion always has.
                line.section == DeckListSection.CHAMPION && champion == null -> champion = card

                // TTS carries no sections, so the champion has to be spotted by type —
                // and the exporter emits one extra token for the slot on top of the
                // main-deck copies, which is what the -1 takes back off.
                parsed.source == DeckListSource.TTS && champion == null && card.isChampionUnit -> {
                    champion = card
                    addEntry(entries, card, DeckSection.MAIN_DECK, maxOf(1, line.quantity - 1))
                }

                else -> {
                    val section = line.section?.deckSection ?: DeckValidator.inferSection(card)
                    addEntry(entries, card, section, line.quantity)
                }
            }
        }

        // A champion always sits in the main deck. If the list never mentioned it
        // outside its own section, put the copy there now.
        champion?.let { card ->
            val key = card.id to DeckSection.MAIN_DECK
            if (entries[key] == null) entries[key] = 1
        }

        if (parsed.source == DeckListSource.TTS) {
            notes += "TTS codes carry no sections, so sideboard cards land in the main deck."
        }

        val rows = entries.map { (key, quantity) ->
            DeckEntryEntity(
                deckId = deckId,
                cardId = key.first,
                quantity = quantity,
                section = key.second.value,
            )
        }

        deckRepository.replaceContents(
            deckId = deckId,
            legendCardId = legend?.id,
            championCardId = champion?.id,
            entries = rows,
        )

        DeckImportResult(
            cardsAdded = rows.sumOf { it.quantity },
            linesImported = linesImported,
            skippedLines = skipped,
            notes = notes,
        )
    }

    /**
     * Sums copies into the target row, clamped at the card's legal maximum.
     *
     * Clamping here rather than refusing the line keeps an over-long list importable —
     * the validation banner is what tells the user their deck is still wrong.
     */
    private fun addEntry(
        entries: MutableMap<Pair<String, DeckSection>, Int>,
        card: Card,
        section: DeckSection,
        quantity: Int,
    ) {
        val key = card.id to section
        val limit = DeckValidator.maxCopies(card)
        // Battlefields are singleton by name, so a second copy is never legal.
        val ceiling = if (section == DeckSection.BATTLEFIELD) 1 else limit
        entries[key] = ((entries[key] ?: 0) + quantity).coerceIn(1, ceiling)
    }

    /**
     * The resolution ladder: exact spelling first, then the comma/hyphen swap that
     * separates decklist spelling from card-data spelling, then a normalised form.
     *
     * The normalised step is cheap and effective: `cleanName` in the catalogue is the
     * name with its punctuation stripped, which is exactly what `normalized` produces,
     * so `Ornn, Fire Below the Mountain` finds `Ornn - Fire Below the Mountain`
     * through it even if neither literal spelling matched.
     */
    private suspend fun resolve(token: String, source: DeckListSource): Card? {
        if (token.isBlank()) return null
        if (source == DeckListSource.TTS) {
            return cardDao.findByPublicCodePrefix(token)?.toDomain()
        }

        DeckListParser.nameVariants(token).forEach { variant ->
            best(cardDao.findByName(variant))?.let { return it.toDomain() }
        }
        return best(cardDao.findByName(token.normalized))?.toDomain()
    }

    /** `findByName` already orders base art first, so the head of the list is the pick. */
    private fun best(candidates: List<CardEntity>): CardEntity? = candidates.firstOrNull()
}
