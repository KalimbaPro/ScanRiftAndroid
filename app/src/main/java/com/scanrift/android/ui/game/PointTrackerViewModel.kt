package com.scanrift.android.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.core.Constants
import com.scanrift.android.core.log.Log
import com.scanrift.android.data.local.dao.GameRecordDao
import com.scanrift.android.data.prefs.UserPreferences
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.data.repository.DeckRepository
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.GameRecord
import com.scanrift.android.domain.model.GameResult
import com.scanrift.android.domain.model.Rarity
import com.scanrift.android.domain.model.ScoreCategory
import com.scanrift.android.domain.model.ScoreInputMode
import com.scanrift.android.data.local.mapper.toEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One seat.
 *
 * Counters are `@Transient`-by-omission: the roster persists names and deck/legend
 * assignments, but scores are always zeroed on load, matching iOS. Nobody wants last
 * week's score when they sit down for a new game.
 */
@Serializable
data class PlayerState(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val conquer: Int = 0,
    val hold: Int = 0,
    val ability: Int = 0,
    val xp: Int = 0,
    /**
     * Every point scored this game, oldest first.
     *
     * This is what makes the tap-zone layout's single decrement button unambiguous:
     * there is no "which category loses the point" question, because the seat remembers
     * the order they were taken in. Kept up to date in **both** input modes so switching
     * mid-game does not desynchronise it.
     */
    val scoreLog: List<ScoreCategory> = emptyList(),
    val legendCardId: String? = null,
    val deckId: String? = null,
) {
    val score: Int get() = conquer + hold + ability

    fun count(category: ScoreCategory): Int = when (category) {
        ScoreCategory.CONQUER -> conquer
        ScoreCategory.HOLD -> hold
        ScoreCategory.ABILITY -> ability
    }

    fun withCount(category: ScoreCategory, value: Int): PlayerState = when (category) {
        ScoreCategory.CONQUER -> copy(conquer = value)
        ScoreCategory.HOLD -> copy(hold = value)
        ScoreCategory.ABILITY -> copy(ability = value)
    }

    /** Adds a point and remembers which category it came from. */
    fun scored(category: ScoreCategory): PlayerState =
        withCount(category, count(category) + 1).copy(scoreLog = scoreLog + category)

    /** Takes back a point from a named category — the classic layout's long-press. */
    fun unscored(category: ScoreCategory): PlayerState {
        if (count(category) <= 0) return this
        return withCount(category, count(category) - 1)
            .copy(scoreLog = scoreLog.withoutLast(category))
    }

    /**
     * Takes back the most recently scored point, whatever it was.
     *
     * Falls back to the largest category when the log cannot answer — a roster restored
     * from a version that had no log, or counts reached some other way. Without that
     * fallback the decrement would silently do nothing and look broken.
     */
    fun undone(): PlayerState {
        if (score <= 0) return copy(scoreLog = emptyList())
        val logged = scoreLog.lastOrNull()?.takeIf { count(it) > 0 }
        val category = logged
            ?: ScoreCategory.entries.filter { count(it) > 0 }.maxByOrNull { count(it) }
            ?: return copy(scoreLog = emptyList())
        // A log that could not answer has already proven it disagrees with the counts,
        // so drop it rather than leave stale entries to misdirect the next undo.
        val trimmed = if (logged != null) scoreLog.dropLast(1) else emptyList()
        return withCount(category, count(category) - 1).copy(scoreLog = trimmed)
    }

    /** Only a player bound to a deck or a legend produces a game record. */
    val isRecordable: Boolean get() = deckId != null || legendCardId != null
}

/**
 * Every point scored, oldest first — one entry per point, reconciled against the counts.
 *
 * [PlayerState.scoreLog] is normally already this, but it can disagree: a roster restored
 * from a build that had no log, or counts reached some other way. Rather than trust it
 * blindly, honour the log only as far as each category's actual count allows, then append
 * whatever the counts still owe. The result always has exactly [PlayerState.score]
 * entries, which is what lets the track draw one cell per point without ever lying.
 */
fun PlayerState.orderedPoints(): List<ScoreCategory> {
    val remaining = ScoreCategory.entries.associateWith { count(it) }.toMutableMap()
    val ordered = ArrayList<ScoreCategory>(score)

    scoreLog.forEach { category ->
        val left = remaining.getValue(category)
        if (left > 0) {
            ordered += category
            remaining[category] = left - 1
        }
    }
    // Anything the log could not account for lands at the end, in category order.
    ScoreCategory.entries.forEach { category ->
        repeat(remaining.getValue(category)) { ordered += category }
    }
    return ordered
}

/** Drops the last occurrence of [category], leaving the list untouched if it has none. */
private fun List<ScoreCategory>.withoutLast(category: ScoreCategory): List<ScoreCategory> {
    val index = lastIndexOf(category)
    return if (index < 0) this else filterIndexed { i, _ -> i != index }
}

data class PointTrackerState(
    val players: List<PlayerState> = emptyList(),
    val startingPlayerIndex: Int? = null,
    val isRandomizing: Boolean = false,
    val isFullScreen: Boolean = false,
    val legendsById: Map<String, Card> = emptyMap(),
    val decks: List<Deck> = emptyList(),
    val inputMode: ScoreInputMode = ScoreInputMode.TAP_ZONES,
) {
    val recordablePlayers: List<PlayerState> get() = players.filter { it.isRecordable }
}

@HiltViewModel
class PointTrackerViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val gameRecordDao: GameRecordDao,
    collectionRepository: CollectionRepository,
    deckRepository: DeckRepository,
) : ViewModel() {

    private val players = MutableStateFlow(defaultRoster())
    private val startingPlayerIndex = MutableStateFlow<Int?>(null)
    private val isRandomizing = MutableStateFlow(false)
    private val isFullScreen = MutableStateFlow(false)

    private var randomizeJob: Job? = null

    private val _state = MutableStateFlow(PointTrackerState(players = defaultRoster()))
    val state: StateFlow<PointTrackerState> = _state.asStateFlow()

    /**
     * Legends offered by the picker.
     *
     * The filters are iOS's, including the one that looks like an oversight and is
     * not: `(Starter)` printings are **kept**, because some OGS legends have no other
     * non-promo printing at all.
     */
    val legends: StateFlow<List<Card>> = collectionRepository.observeAllCards()
        .map { cards ->
            cards.filter { card ->
                card.type == CardType.LEGEND &&
                    !card.name.contains("(Metal)") &&
                    !card.isAlternateArt &&
                    card.rarity != Rarity.PROMO
            }.sortedBy { it.name }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val decks: StateFlow<List<Deck>> = deckRepository.observeDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Paired into the combine rather than spliced in like `decks`, because flipping the
     * setting has to redraw the seats immediately — there is no navigation event to
     * piggyback on.
     */
    private val inputMode: StateFlow<ScoreInputMode> = userPreferences.scoreInputMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoreInputMode.TAP_ZONES)

    init {
        viewModelScope.launch { restoreRoster() }
        viewModelScope.launch {
            combine(
                players,
                startingPlayerIndex,
                isRandomizing,
                isFullScreen,
                combine(legends, inputMode, ::Pair),
            ) { roster, starting, randomizing, fullScreen, (legendCards, mode) ->
                PointTrackerState(
                    players = roster,
                    startingPlayerIndex = starting,
                    isRandomizing = randomizing,
                    isFullScreen = fullScreen,
                    legendsById = legendCards.associateBy { it.id },
                    inputMode = mode,
                )
            }.collect { next -> _state.value = next.copy(decks = decks.value) }
        }
    }

    // ── Roster ───────────────────────────────────────────────────────────────

    fun setPlayerCount(count: Int) {
        val clamped = count.coerceIn(Constants.PointTracker.MIN_PLAYERS, Constants.PointTracker.MAX_PLAYERS)
        players.update { current ->
            when {
                clamped > current.size ->
                    current + (current.size until clamped).map { PlayerState(name = "Player ${it + 1}") }
                clamped < current.size -> current.take(clamped)
                else -> current
            }
        }
        persistRoster()
    }

    fun rename(playerId: String, name: String) {
        players.update { roster -> roster.map { if (it.id == playerId) it.copy(name = name) else it } }
        persistRoster()
    }

    fun assignDeck(playerId: String, deck: Deck) {
        players.update { roster ->
            roster.map {
                if (it.id == playerId) it.copy(deckId = deck.id, legendCardId = deck.legendCardId) else it
            }
        }
        persistRoster()
    }

    /** Assigning a bare legend clears any deck, since the two are alternatives. */
    fun assignLegend(playerId: String, legendId: String) {
        players.update { roster ->
            roster.map { if (it.id == playerId) it.copy(legendCardId = legendId, deckId = null) else it }
        }
        persistRoster()
    }

    fun clearAssignment(playerId: String) {
        players.update { roster ->
            roster.map { if (it.id == playerId) it.copy(legendCardId = null, deckId = null) else it }
        }
        persistRoster()
    }

    /** Drag a name chip onto another seat to swap the two. */
    fun swapPlayers(playerId: String, targetIndex: Int) {
        players.update { roster ->
            val from = roster.indexOfFirst { it.id == playerId }
            if (from < 0 || targetIndex !in roster.indices || from == targetIndex) return@update roster
            roster.toMutableList().apply {
                val moved = this[from]
                this[from] = this[targetIndex]
                this[targetIndex] = moved
            }
        }
        persistRoster()
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    fun addPoint(index: Int, category: ScoreCategory) = mutate(index) { player ->
        // Clamp on the total, not the category: the cap is on the score.
        if (player.score >= Constants.PointTracker.SCORE_MAX) player else player.scored(category)
    }

    fun removePoint(index: Int, category: ScoreCategory) = mutate(index) { it.unscored(category) }

    /** The tap-zone layout's decrement: undo the last point, whichever it was. */
    fun undoPoint(index: Int) = mutate(index) { it.undone() }

    private fun mutate(index: Int, transform: (PlayerState) -> PlayerState) {
        players.update { roster ->
            if (index !in roster.indices) return@update roster
            roster.toMutableList().apply { this[index] = transform(this[index]) }
        }
    }

    fun setXp(index: Int, value: Int) {
        players.update { roster ->
            if (index !in roster.indices) return@update roster
            roster.toMutableList().apply {
                this[index] = this[index].copy(xp = value.coerceIn(0, Constants.PointTracker.XP_MAX))
            }
        }
    }

    fun resetCounters() {
        players.update { roster ->
            roster.map { it.copy(conquer = 0, hold = 0, ability = 0, xp = 0, scoreLog = emptyList()) }
        }
        startingPlayerIndex.value = null
    }

    fun setFullScreen(value: Boolean) { isFullScreen.value = value }

    /** Roulette: cycle the highlight, land on a real random seat, then fade it out. */
    fun randomizeFirstPlayer() {
        randomizeJob?.cancel()
        randomizeJob = viewModelScope.launch {
            val count = players.value.size
            if (count == 0) return@launch

            isRandomizing.value = true
            val steps = (Constants.PointTracker.RANDOMIZE_ANIMATION_MS /
                Constants.PointTracker.RANDOMIZE_HIGHLIGHT_INTERVAL_MS).toInt()
            repeat(steps) { step ->
                startingPlayerIndex.value = step % count
                delay(Constants.PointTracker.RANDOMIZE_HIGHLIGHT_INTERVAL_MS)
            }

            startingPlayerIndex.value = Random.nextInt(count)
            isRandomizing.value = false

            delay(Constants.PointTracker.STARTING_HIGHLIGHT_FADE_MS)
            startingPlayerIndex.value = null
        }
    }

    // ── Recording ────────────────────────────────────────────────────────────

    /**
     * Writes one record per recordable seat and resets the board.
     *
     * `legendId` and `opponentLegendId` are the matchup key and are easy to drop, so
     * they are filled from the seats here rather than left to the UI.
     */
    fun saveGame(gameName: String?, onSaved: () -> Unit = {}) {
        val roster = players.value
        val recordable = roster.filter { it.isRecordable }
        if (recordable.isEmpty()) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val topScore = roster.maxOfOrNull { it.score } ?: 0

            recordable.forEach { player ->
                val opponents = roster.filter { it.id != player.id }
                val bestOpponent = opponents.maxByOrNull { it.score }
                val hasBreakdown = player.conquer + player.hold + player.ability > 0

                val record = GameRecord(
                    id = UUID.randomUUID().toString(),
                    date = now,
                    name = gameName?.takeIf { it.isNotBlank() },
                    result = when {
                        player.score > (bestOpponent?.score ?: 0) -> GameResult.WIN
                        player.score == topScore && opponents.count { it.score == topScore } > 0 -> GameResult.DRAW
                        else -> GameResult.LOSS
                    },
                    playerName = player.name,
                    opponentName = bestOpponent?.name,
                    pointsScored = player.score,
                    pointsAllowed = bestOpponent?.score,
                    conquerCount = player.conquer.takeIf { hasBreakdown },
                    holdCount = player.hold.takeIf { hasBreakdown },
                    abilityCount = player.ability.takeIf { hasBreakdown },
                    deckId = player.deckId,
                    legendId = player.legendCardId,
                    opponentLegendId = bestOpponent?.legendCardId,
                )
                gameRecordDao.upsert(record.toEntity())
            }

            resetCounters()
            onSaved()
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private suspend fun restoreRoster() {
        val stored = userPreferences.pointTrackerRoster.first() ?: return
        runCatching { JSON.decodeFromString<List<PlayerState>>(stored) }
            .onSuccess { roster ->
                if (roster.isEmpty()) return@onSuccess
                // Names and assignments survive; scores never do.
                players.value = roster.map {
                    it.copy(conquer = 0, hold = 0, ability = 0, xp = 0, scoreLog = emptyList())
                }
            }
            .onFailure { Log.general.w(it, "Could not restore the point-tracker roster") }
    }

    private fun persistRoster() {
        val roster = players.value
        viewModelScope.launch {
            runCatching { userPreferences.setPointTrackerRoster(JSON.encodeToString(roster)) }
        }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        fun defaultRoster() = listOf(PlayerState(name = "Player 1"), PlayerState(name = "Player 2"))
    }
}
