package com.scanrift.android.demo

import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.domain.model.GameResult

/**
 * The store-screenshot data set, as plain values.
 *
 * Every card is named by its `riftbound_id` from the bundled catalogue rather than by
 * row id: riftbound ids are readable in review, and they survive the catalogue's
 * de-duplication pass, which can swap the row id of a preliminary record for its final
 * one. A missing id fails the seed loudly instead of quietly dropping a card.
 *
 * The collection is shaped for the screenshots, not at random:
 * - few Origins cards below #077, so Zhonya's Hourglass (banned, errata'd) and its
 *   BANNED band land on the first screen of the grid sorted by number;
 * - three French, three Chinese and three Korean copies — one row each on a phone —
 *   so the bottom of the owned view shows all four flag sections on one screen, with
 *   a banned card (Baited Hook, Zhonya's) carrying its BANNED band inside two of them;
 * - every card the three decks need, so the deck builder has nothing missing.
 */
internal object DemoData {

    data class Stack(
        val riftboundId: String,
        val quantity: Int,
        val foil: Boolean = false,
        val language: CardLanguage = CardLanguage.EN,
    )

    data class DemoDeck(
        val name: String,
        val legend: String,
        val champion: String,
        val main: List<Pair<String, Int>>,
        val runes: List<Pair<String, Int>>,
        val battlefields: List<String>,
        /** Hours before "now" the deck was last edited; orders the deck list. */
        val editedHoursAgo: Int,
    )

    data class DemoGame(
        val daysAgo: Int,
        val deck: String?,
        val legend: String,
        val opponentLegend: String,
        val opponent: String,
        val result: GameResult,
        val scored: Int,
        val allowed: Int,
        val conquer: Int,
        val hold: Int,
    )

    data class Seat(val name: String, val legend: String, val deck: String?)

    private fun stack(
        riftboundId: String,
        quantity: Int,
        foil: Boolean = false,
        language: CardLanguage = CardLanguage.EN,
    ) = Stack(riftboundId, quantity, foil, language)

    val collection: List<Stack> = listOf(
        stack("ogn-042-298", 6), // Calm Rune
        stack("ogn-042a-298", 1, foil = true), // Calm Rune (Alternate Art)
        stack("ogn-043-298", 3), // Charm
        stack("ogn-047-298", 3), // Find Your Center
        stack("ogn-055-298", 3), // Wielder of Water
        stack("ogn-062-298", 3), // Reinforce
        stack("ogn-077-298", 3), // Zhonya's Hourglass
        stack("ogn-078-298", 1), // Lee Sin - Ascetic
        stack("ogn-079-298", 3), // Leona - Zealot
        stack("ogn-081-298", 2), // Seal of Focus
        stack("ogn-088-298", 2), // Mega-Mech
        stack("ogn-089-298", 6), // Mind Rune
        stack("ogn-115-298", 3), // Promising Future
        stack("ogn-123-298", 1, foil = true), // Unchecked Power
        stack("ogn-146-298", 3), // Wallop
        stack("ogn-147-298", 3), // Wildclaw Shaman
        stack("ogn-159-298", 1), // Warwick - Hunter
        stack("ogn-165-298", 1), // Cemetery Attendant
        stack("ogn-166-298", 6), // Chaos Rune
        stack("ogn-171-298", 2), // Mystic Poro
        stack("ogn-174-298", 2), // Sai Scout
        stack("ogn-186-298", 1), // Treasure Trove
        stack("ogn-199-298", 3), // Tideturner
        stack("ogn-204-298", 3), // Seal of Discord
        stack("ogn-212-298", 2), // Forge of the Future
        stack("ogn-214-298", 6), // Order Rune
        stack("ogn-222-298", 3), // Noxian Drummer
        stack("ogn-231-298", 3), // Commander Ledros
        stack("ogn-242-298", 2), // Baited Hook
        stack("ogn-255-298", 1), // Ahri - Nine-Tailed Fox
        stack("ogn-259-298", 1), // Yasuo - Unforgiven
        stack("ogn-261-298", 1), // Leona - Radiant Dawn
        stack("ogn-282-298", 1), // Monastery of Hirana
        stack("ogn-286-298", 3), // Reckoner's Arena
        stack("sfd-013-221", 3), // Blast Corps Cadet
        stack("sfd-019-221", 2), // Assembly Rig
        stack("sfd-028-221", 1), // Lucian - Gunslinger
        stack("sfd-032-221", 3), // Disarming Rake
        stack("sfd-034-221", 3), // Feral Strength
        stack("sfd-035-221", 2), // Guardian of the Passage
        stack("sfd-042-221", 3), // Brutalizer
        stack("sfd-045-221", 3), // Not So Fast
        stack("sfd-047-221", 2), // Simian Ancestor
        stack("sfd-051-221", 2), // Guardian Angel
        stack("sfd-057-221", 3), // Irelia - Fervent
        stack("sfd-057a-221", 1, foil = true), // Irelia - Fervent (Alternate Art)
        stack("sfd-066-221", 2), // Frigid Touch
        stack("sfd-072-221", 1), // Dropboarder
        stack("sfd-103-221", 1), // Jaull-Fish
        stack("sfd-105-221", 2), // Ruin Runner
        stack("sfd-111-221", 2), // Here to Help
        stack("sfd-118-221", 1), // Boneshiver
        stack("sfd-121-221", 3), // Black Market Broker
        stack("sfd-125-221", 2), // Fae Porter
        stack("sfd-131-221", 3), // Ancient Warmonger
        stack("sfd-142-221", 3), // Jae Medarda
        stack("sfd-156-221", 3), // Laurent Duelist
        stack("sfd-162-221", 1), // Blood Money
        stack("sfd-191-221", 1), // Rabadon's Deathcrown
        stack("sfd-195-221", 1), // Irelia - Blade Dancer
        stack("sfd-210-221", 1), // Hall of Legends
        stack("sfd-246-221", 1, foil = true), // Irelia - Blade Dancer (Overnumbered)
        stack("unl-016-219", 1, foil = true), // Scorchclaw
        stack("unl-033-219", 3), // Frisky Hunter
        stack("unl-036-219", 3), // Mutated Mouser
        stack("unl-038-219", 1), // Skyward Strike
        stack("unl-040-219", 2), // Wuju Apprentice
        stack("unl-047-219", 3), // Mosstomper
        stack("unl-058-219", 3), // Lillia - Protector of Dreams
        stack("unl-058a-219", 1, foil = true), // Lillia - Protector of Dreams (Alternate Art)
        stack("unl-060-219", 3), // Vilemaw
        stack("unl-079-219", 3), // Diana - Lunari
        stack("unl-084-219", 3), // Sprite Queen
        stack("unl-088-219", 3), // Gutter Palace
        stack("unl-103-219", 3), // Disposal Order
        stack("unl-121-219", 3), // Bewitching Spirit
        stack("unl-127-219", 3), // Mister Root
        stack("unl-134-219", 2), // Existential Dread
        stack("unl-135-219", 1), // Insightful Investigator
        stack("unl-136-219", 3), // Scryer's Bloom
        stack("unl-138-219", 3), // The List
        stack("unl-150-219", 1), // Vex - Apathetic
        stack("unl-165-219", 3), // Shadow's Call
        stack("unl-174-219", 3), // Shard of Undoing
        stack("unl-189-219", 1), // Lillia - Bashful Bloom
        stack("unl-198-219", 1), // Moonfall
        stack("unl-208-219", 2), // Black Flame Altar
        stack("unl-210-219", 3), // Forbidding Waste
        stack("unl-214-219", 1), // Ripper's Bay
        stack("unl-230*-219", 1, foil = true), // Lillia - Bashful Bloom (Signature)
        stack("ven-sp2-006", 1, foil = true), // Sona, Harmonious
        stack("ven-026-166", 3), // Field Musicians
        stack("ven-028-166", 2), // Mournful Witness
        stack("ven-029-166", 2), // Ol' Poro
        stack("ven-037-166", 2, foil = true), // Tomb-Raider Barbara
        stack("ven-061-166", 1), // Decree of Insight
        stack("ven-064-166", 3), // Plaza Guardian
        stack("ven-067-166", 3), // Bottled Constellation
        stack("ven-072-166", 3), // Guttural Roar
        stack("ven-091-166", 1), // Corrupted Dragon
        stack("ven-101-166", 3), // Gust Monk
        stack("ven-107-166", 1), // Decree of Discord
        stack("ven-111-166", 1), // Minah Swiftfoot
        stack("ven-112-166", 2), // Zed, Without a Sound
        stack("ven-115-166", 1, foil = true), // Ocean Drake
        stack("ven-137-166", 3), // Shady Spectacles
        stack("ven-140-166", 1), // Shuriken Flip
        stack("ven-145-166", 1), // Curator of the Sands
        stack("ven-146-166", 1), // Siphoning Strike
        stack("ven-158-166", 1), // Heisho, Shell of the World
        stack("ven-161-166", 3), // Piltovan Forge
        stack("ven-163-166", 1), // Risen Altar
        stack("ven-165-166", 2), // Shadow Temple
        stack("ven-175-166", 2), // Jayce, Man of Progress
        stack("ven-181-166", 2), // Gangplank, Naval
        stack("ven-187-166", 1), // Ambessa, Respected and Feared
        stack("rad-024-167", 1), // Blossoming Dreamroot
        stack("rad-025-167", 1, foil = true), // Consult the Heavens
        stack("rad-030-167", 3), // Sanctum Conservator
        stack("rad-031-167", 3), // Stoic Ferryman
        stack("rad-036-167", 3), // Storm Raptor
        stack("rad-052-167", 2), // Resource Extractor
        stack("rad-064-167", 3), // Scrutinizing Scope
        stack("rad-065-167", 3, foil = true), // Seraphine - Inspiring
        stack("rad-067-167", 1, foil = true), // Clocktower Guardian
        stack("rad-070-167", 2, foil = true), // Hold 'Em
        stack("rad-074-167", 3), // Raving Witness
        stack("rad-090-167", 1), // Evelynn - Consuming
        stack("rad-091-167", 1), // Jarvan IV - Martial Paragon
        stack("rad-096-167", 3), // Satchel Charge
        stack("rad-114-167", 1), // Riven - Deserter
        stack("rad-120-167", 2), // Meticulous Restorationist
        stack("rad-129-167", 3), // Trifarian Captain
        stack("rad-131-167", 2), // Demacian Illuminators
        stack("rad-149-167", 1), // Riven - The Exile
        stack("rad-158-167", 1), // Black Market
        stack("rad-162-167", 1), // Firelights' Hideout
        stack("rad-164-167", 1), // Packed Amphitheater
        stack("ogn-143-298", 1, language = CardLanguage.FR), // Pirate's Haven
        stack("ogn-242-298", 1, language = CardLanguage.FR), // Baited Hook: banned, in a flag section
        stack("ven-109-166", 2, foil = true, language = CardLanguage.FR), // Illaoi, Prophet of the Great Kraken
        stack("ogn-114-298", 2, foil = true, language = CardLanguage.ZH), // Progress Day
        stack("ogn-130-298", 1, language = CardLanguage.ZH), // Crackshot Corsair
        stack("rad-008-167", 1, language = CardLanguage.ZH), // War Scout
        stack("ogn-077-298", 1, language = CardLanguage.KO), // Zhonya's Hourglass: banned, Korean art
        stack("ogn-120-298", 1, language = CardLanguage.KO), // Seal of Insight
        stack("ogn-213-298", 1, foil = true, language = CardLanguage.KO), // Hidden Blade
    )

    val decks: List<DemoDeck> = listOf(
        DemoDeck(
            name = "Lillia Dreams",
            legend = "unl-189-219",
            champion = "unl-058-219",
            main = listOf(
                "unl-058-219" to 3, "ogn-043-298" to 3, "unl-036-219" to 3, "sfd-032-221" to 3,
                "ogn-115-298" to 3, "unl-047-219" to 3, "ven-067-166" to 3, "rad-030-167" to 3,
                "unl-088-219" to 3, "ven-026-166" to 3, "rad-064-167" to 3, "ogn-047-298" to 3,
                "ogn-081-298" to 2, "unl-060-219" to 2,
            ),
            runes = listOf("ogn-042-298" to 6, "ogn-089-298" to 6),
            battlefields = listOf("rad-158-167", "ven-158-166", "unl-214-219"),
            editedHoursAgo = 2,
        ),
        DemoDeck(
            name = "Irelia Blade Dance",
            legend = "sfd-195-221",
            champion = "sfd-057-221",
            main = listOf(
                "sfd-057-221" to 3, "ogn-055-298" to 3, "rad-030-167" to 3, "unl-127-219" to 3,
                "ven-101-166" to 3, "unl-136-219" to 3, "rad-096-167" to 3, "unl-138-219" to 3,
                "sfd-042-221" to 3, "ogn-204-298" to 3, "sfd-032-221" to 3, "unl-060-219" to 3,
                "ven-029-166" to 2, "ogn-174-298" to 2,
            ),
            runes = listOf("ogn-042-298" to 6, "ogn-166-298" to 6),
            battlefields = listOf("rad-162-167", "ogn-282-298", "ven-163-166"),
            editedHoursAgo = 30,
        ),
        // Runs Zhonya's Hourglass and Baited Hook, both banned: the "Illegal" deck.
        DemoDeck(
            name = "Leona Control",
            legend = "ogn-261-298",
            champion = "ogn-079-298",
            main = listOf(
                "ogn-079-298" to 3, "ogn-077-298" to 2, "ogn-242-298" to 1, "ogn-222-298" to 3,
                "rad-036-167" to 3, "ogn-062-298" to 3, "unl-036-219" to 3, "ven-137-166" to 3,
                "sfd-045-221" to 3, "sfd-156-221" to 3, "unl-033-219" to 3, "unl-060-219" to 3,
                "rad-031-167" to 3, "sfd-035-221" to 2, "unl-040-219" to 2,
            ),
            runes = listOf("ogn-042-298" to 6, "ogn-214-298" to 6),
            battlefields = listOf("ven-163-166", "rad-164-167", "sfd-210-221"),
            editedHoursAgo = 120,
        ),
    )

    private const val LILLIA = "unl-189-219"
    private const val IRELIA = "sfd-195-221"
    private const val LEONA = "ogn-261-298"
    private const val JINX = "ogn-251-298"
    private const val AHRI = "ogn-255-298"
    private const val DARIUS = "ogn-253-298"

    val games: List<DemoGame> = listOf(
        DemoGame(1, "Lillia Dreams", LILLIA, IRELIA, "Hugo", GameResult.WIN, 8, 6, 5, 2),
        DemoGame(1, "Lillia Dreams", LILLIA, JINX, "Hugo", GameResult.LOSS, 5, 8, 3, 2),
        DemoGame(3, "Irelia Blade Dance", IRELIA, AHRI, "Sam", GameResult.WIN, 8, 4, 6, 1),
        DemoGame(5, "Lillia Dreams", LILLIA, DARIUS, "Chloé", GameResult.WIN, 8, 7, 4, 3),
        DemoGame(8, "Leona Control", LEONA, JINX, "Hugo", GameResult.LOSS, 6, 8, 2, 4),
        DemoGame(12, "Irelia Blade Dance", IRELIA, LILLIA, "Sam", GameResult.WIN, 8, 5, 5, 2),
    )

    /**
     * The point tracker's two seats. Special printings on purpose — the Lillia
     * Signature and the overnumbered Irelia — so the seat art is not the plain one.
     * Scores are never persisted (the tracker zeroes them on load), so a mid-game
     * score is tapped in when the screenshot is taken.
     */
    val seats: List<Seat> = listOf(
        Seat(name = "Lena", legend = "unl-230*-219", deck = "Lillia Dreams"),
        Seat(name = "Hugo", legend = "sfd-246-221", deck = null),
    )

    /** Cards on the system Wishlist. */
    val wishlist: List<String> = listOf("unl-058a-219", "sfd-246-221", "rad-149-167", "ven-109-166")
}
