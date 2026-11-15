package com.scanrift.android.demo

import androidx.room.withTransaction
import com.scanrift.android.core.log.Log
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.dao.CardDao
import com.scanrift.android.data.local.dao.CardListDao
import com.scanrift.android.data.local.dao.CollectionEntryDao
import com.scanrift.android.data.local.dao.DeckDao
import com.scanrift.android.data.local.dao.GameRecordDao
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CardListCrossRef
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.entity.GameRecordEntity
import com.scanrift.android.data.prefs.UserPreferences
import com.scanrift.android.data.repository.CardListRepository
import com.scanrift.android.di.IoDispatcher
import com.scanrift.android.domain.model.CardCondition
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.domain.model.ScanLanguage
import com.scanrift.android.domain.model.ScoreCategory
import com.scanrift.android.ui.game.PlayerState
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Replaces the user's data with [DemoData]: collection, decks, game history, wishlist
 * and the point tracker's roster. The catalogue is never touched — every write here
 * goes to the user tables, and the cards are looked up, not created.
 *
 * Debug source set only, so it cannot ship. It is destructive by design: it exists to
 * put an emulator into a known state for store screenshots.
 */
class DemoSeeder @Inject constructor(
    private val db: ScanRiftDatabase,
    private val cardDao: CardDao,
    private val collectionDao: CollectionEntryDao,
    private val deckDao: DeckDao,
    private val gameRecordDao: GameRecordDao,
    private val cardListDao: CardListDao,
    private val listRepository: CardListRepository,
    private val userPreferences: UserPreferences,
    private val demoScores: DemoScores,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    data class Summary(val stacks: Int, val copies: Int, val decks: Int, val games: Int)

    /** [fourPlayers] swaps the two-seat roster for [DemoData.fourPlayerSeats], scores and all. */
    suspend fun seed(now: Long = System.currentTimeMillis(), fourPlayers: Boolean = false): Summary = withContext(io) {
        val cards = resolveCards()
        fun card(rid: String): CardEntity = cards.getValue(rid)
        val deckIds = DemoData.decks.associate { it.name to UUID.nameUUIDFromBytes(it.name.toByteArray()).toString() }

        db.withTransaction {
            collectionDao.deleteAll()
            deckDao.getAll().forEach { deckDao.deleteDeck(it) }
            gameRecordDao.getAll().forEach { gameRecordDao.delete(it) }

            // Oldest first, so "date added" sorts the way a real collection grew.
            collectionDao.upsertAll(
                DemoData.collection.mapIndexed { index, stack ->
                    CollectionEntryEntity(
                        cardId = card(stack.riftboundId).id,
                        quantity = stack.quantity,
                        isFoil = stack.foil,
                        dateAdded = now - TimeUnit.HOURS.toMillis((DemoData.collection.size - index).toLong()),
                        condition = CardCondition.NEAR_MINT.value,
                        language = stack.language.value,
                    )
                },
            )

            DemoData.decks.forEach { deck ->
                val id = deckIds.getValue(deck.name)
                val edited = now - TimeUnit.HOURS.toMillis(deck.editedHoursAgo.toLong())
                val entries = deck.main.map { (rid, qty) -> entry(id, card(rid), qty, DeckSection.MAIN_DECK) } +
                    deck.runes.map { (rid, qty) -> entry(id, card(rid), qty, DeckSection.RUNE) } +
                    deck.battlefields.map { rid -> entry(id, card(rid), 1, DeckSection.BATTLEFIELD) }
                val row = DeckEntity(
                    id = id,
                    name = deck.name,
                    createdDate = edited - TimeUnit.DAYS.toMillis(14),
                    lastModifiedDate = edited,
                    legendCardId = card(deck.legend).id,
                    championCardId = card(deck.champion).id,
                )
                // replaceContents writes the entries before the deck row, which assumes
                // the deck already exists; a brand-new one has to be inserted first.
                deckDao.upsertDeck(row)
                deckDao.replaceContents(row, entries)
            }

            gameRecordDao.upsertAll(
                DemoData.games.mapIndexed { index, game ->
                    GameRecordEntity(
                        id = UUID.nameUUIDFromBytes("demo-game-$index".toByteArray()).toString(),
                        date = now - TimeUnit.DAYS.toMillis(game.daysAgo.toLong()) - TimeUnit.HOURS.toMillis(index.toLong()),
                        result = game.result.value,
                        playerName = "Lena",
                        opponentName = game.opponent,
                        pointsScored = game.scored,
                        pointsAllowed = game.allowed,
                        conquerCount = game.conquer,
                        holdCount = game.hold,
                        abilityCount = game.scored - game.conquer - game.hold,
                        deckId = game.deck?.let(deckIds::getValue),
                        legendId = card(game.legend).id,
                        opponentLegendId = card(game.opponentLegend).id,
                    )
                },
            )
        }

        listRepository.reconcileWishlist(now)
        listRepository.wishlistId()?.let { wishlist ->
            cardListDao.clearList(wishlist)
            cardListDao.addCardsToList(DemoData.wishlist.map { CardListCrossRef(wishlist, card(it).id) })
        }

        val seats = if (fourPlayers) DemoData.fourPlayerSeats else DemoData.seats
        val roster = seats.map { seat ->
            PlayerState(
                name = seat.name,
                conquer = seat.count(ScoreCategory.CONQUER),
                hold = seat.count(ScoreCategory.HOLD),
                ability = seat.count(ScoreCategory.OTHER),
                xp = seat.xp,
                scoreLog = seat.scoreLog,
                legendCardId = card(seat.legend).id,
                deckId = seat.deck?.let(deckIds::getValue),
            )
        }
        userPreferences.setPointTrackerRoster(Json.encodeToString(roster))
        demoScores.keepScores = fourPlayers
        // The demo binder page is all English. Forcing English also skips the Chinese and
        // Korean OCR pass, whose Play Services TFLite module dies with SIGILL on the
        // arm64 emulator — with Auto, importing a page photo kills the app there.
        userPreferences.setScanLanguage(ScanLanguage.EN)

        Summary(
            stacks = DemoData.collection.size,
            copies = DemoData.collection.sumOf { it.quantity },
            decks = DemoData.decks.size,
            games = DemoData.games.size,
        ).also { Log.database.i("Demo data seeded: %s", it) }
    }

    /** Every riftbound id [DemoData] names, resolved to the catalogue row that survived de-duplication. */
    private suspend fun resolveCards(): Map<String, CardEntity> {
        val wanted = buildSet {
            DemoData.collection.forEach { add(it.riftboundId) }
            DemoData.decks.forEach { deck ->
                add(deck.legend)
                add(deck.champion)
                deck.main.forEach { add(it.first) }
                deck.runes.forEach { add(it.first) }
                addAll(deck.battlefields)
            }
            DemoData.games.forEach { add(it.legend); add(it.opponentLegend) }
            (DemoData.seats + DemoData.fourPlayerSeats).forEach { add(it.legend) }
            addAll(DemoData.wishlist)
        }
        val resolved = wanted.associateWith { rid ->
            cardDao.getByRiftboundId(rid).maxByOrNull { it.updatedOn ?: 0L }
        }
        val missing = resolved.filterValues { it == null }.keys
        check(missing.isEmpty()) { "Demo data names cards the catalogue does not have: $missing" }
        return resolved.mapValues { it.value!! }
    }

    private fun entry(deckId: String, card: CardEntity, quantity: Int, section: DeckSection) =
        DeckEntryEntity(deckId = deckId, cardId = card.id, quantity = quantity, section = section.value)
}
