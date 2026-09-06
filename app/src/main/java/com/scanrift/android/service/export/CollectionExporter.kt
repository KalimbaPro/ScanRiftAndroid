package com.scanrift.android.service.export

import com.scanrift.android.core.time.Iso8601
import com.scanrift.android.domain.model.CollectionEntry
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.GameRecord
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The three share formats, byte-identical to iOS.
 *
 * Every quirk below is intentional and pinned by golden-file tests — riftbound.gg
 * parses the CSV it emitted, so "tidying" the spacing would break imports there.
 */
object CollectionExporter {

    /**
     * riftbound.gg CSV.
     *
     * Note the spaces after the commas — in the header **and** the data rows. That is
     * what the site emits and expects.
     */
    fun exportAsRiftboundGg(entries: List<CollectionEntry>): String = buildString {
        append("CardId, Normal, Foil, Name, Set\n")

        entries.filter { it.card != null }
            .groupBy { it.card!!.id }
            .toSortedMap()
            .forEach { (_, group) ->
                val card = group.first().card!!
                val normal = group.filterNot { it.isFoil }.sumOf { it.quantity }
                val foil = group.filter { it.isFoil }.sumOf { it.quantity }
                append("${card.dotGgId}, $normal, $foil, \"${card.name}\", \"${card.setLabel}\"\n")
            }
    }

    /** Standard CSV. Quantity is deliberately unquoted; everything else is quoted. */
    fun exportAsCsv(entries: List<CollectionEntry>): String = buildString {
        append("Name,Set,Rarity,Quantity,Condition,Date Added\n")
        entries.forEach { entry ->
            val card = entry.card ?: return@forEach
            append("\"${card.name}\",\"${card.setLabel}\",\"${card.rarity}\",")
            append("${entry.quantity},\"${entry.condition.value}\",\"${Iso8601.format(entry.dateAdded)}\"\n")
        }
    }

    /**
     * Full JSON: collection plus every deck with its cards and game history.
     *
     * Keys are hand-built rather than serialized from a class because optional fields
     * are omitted rather than emitted as null, matching iOS.
     *
     * Note this export drops the conquer/hold/ability breakdown from history — iOS
     * does the same. The backup snapshot is the lossless channel; this one is for
     * sharing.
     */
    fun exportAsJson(
        entries: List<CollectionEntry>,
        decks: List<Deck>,
        gameRecordsByDeck: Map<String, List<GameRecord>> = emptyMap(),
        now: Long,
        json: Json = PRETTY,
    ): String {
        val root = buildJsonObject {
            put("exported_at", Iso8601.format(now))
            put("collection", collectionArray(entries))
            put("decks", decksArray(decks, gameRecordsByDeck))
        }
        return json.encodeToString(root)
    }

    private fun collectionArray(entries: List<CollectionEntry>): JsonArray = buildJsonArray {
        entries.forEach { entry ->
            val card = entry.card ?: return@forEach
            add(
                buildJsonObject {
                    put("name", card.name)
                    put("id", card.id)
                    put("riftbound_id", card.riftboundId)
                    put("set", card.setLabel)
                    put("set_id", card.setId)
                    put("rarity", card.rarity)
                    put("quantity", entry.quantity)
                    put("is_foil", entry.isFoil)
                    put("condition", entry.condition.value)
                    put("date_added", Iso8601.format(entry.dateAdded))
                },
            )
        }
    }

    private fun decksArray(
        decks: List<Deck>,
        gameRecordsByDeck: Map<String, List<GameRecord>>,
    ): JsonArray = buildJsonArray {
        decks.forEach { deck ->
            add(
                buildJsonObject {
                    put("id", deck.id)
                    put("name", deck.name)
                    put("created_date", Iso8601.format(deck.createdDate))
                    put("last_modified_date", Iso8601.format(deck.lastModifiedDate))
                    deck.legend?.let { put("legend_card_id", it.id); put("legend_name", it.name) }
                    deck.champion?.let { put("champion_card_id", it.id); put("champion_name", it.name) }
                    put(
                        "cards",
                        buildJsonArray {
                            deck.entries.forEach { entry ->
                                val card = entry.card ?: return@forEach
                                add(
                                    buildJsonObject {
                                        put("card_id", card.id)
                                        put("name", card.name)
                                        put("quantity", entry.quantity)
                                        put("section", entry.section.value)
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "history",
                        buildJsonArray {
                            gameRecordsByDeck[deck.id].orEmpty().sortedBy { it.date }.forEach { record ->
                                add(
                                    buildJsonObject {
                                        put("date", Iso8601.format(record.date))
                                        put("result", record.result.value)
                                        record.name?.let { put("name", it) }
                                        record.playerName?.let { put("player_name", it) }
                                        record.opponentName?.let { put("opponent_name", it) }
                                        record.pointsScored?.let { put("points_scored", it) }
                                        record.pointsAllowed?.let { put("points_allowed", it) }
                                        record.ties?.let { put("ties", it) }
                                        record.notes?.let { put("notes", it) }
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
    }

    /**
     * Tabletop Simulator export: one token per physical copy, ordered legend,
     * champion, main deck, battlefields, runes, sideboard.
     */
    fun exportAsTts(deck: Deck): String {
        val tokens = mutableListOf<String>()
        fun addCopies(code: String, count: Int) = repeat(count) { tokens += "$code-1" }

        deck.legend?.let { addCopies(it.publicCodePrefix, 1) }
        deck.champion?.let { addCopies(it.publicCodePrefix, 1) }
        listOf(
            com.scanrift.android.domain.model.DeckSection.MAIN_DECK,
            com.scanrift.android.domain.model.DeckSection.BATTLEFIELD,
            com.scanrift.android.domain.model.DeckSection.RUNE,
            com.scanrift.android.domain.model.DeckSection.SIDEBOARD,
        ).forEach { section ->
            deck.entries.filter { it.section == section }.forEach { entry ->
                entry.card?.let { addCopies(it.publicCodePrefix, entry.quantity) }
            }
        }
        return tokens.joinToString(" ")
    }

    /** Human-readable deck list. */
    fun exportAsText(deck: Deck): String = buildString {
        deck.legend?.let { legend ->
            appendLine("Legend:")
            val tag = legend.tags.firstOrNull()
            appendLine(if (tag != null) "1 $tag, ${legend.name}" else "1 ${legend.name}")
            appendLine()
        }
        deck.champion?.let {
            appendLine("Champion:")
            appendLine("1 ${it.name}")
            appendLine()
        }
        listOf(
            "MainDeck:" to com.scanrift.android.domain.model.DeckSection.MAIN_DECK,
            "Battlefields:" to com.scanrift.android.domain.model.DeckSection.BATTLEFIELD,
            "Runes:" to com.scanrift.android.domain.model.DeckSection.RUNE,
            "Sideboard:" to com.scanrift.android.domain.model.DeckSection.SIDEBOARD,
        ).forEach { (header, section) ->
            val entries = deck.entries.filter { it.section == section && it.card != null }
            if (entries.isEmpty()) return@forEach
            appendLine(header)
            entries.forEach { appendLine("${it.quantity} ${it.card!!.name}") }
            appendLine()
        }
    }.trimEnd().plus("\n")

    private val PRETTY = Json { prettyPrint = true; prettyPrintIndent = "  " }

    @Suppress("unused")
    private fun pad(value: Int) = "%03d".format(Locale.ROOT, value)
}
