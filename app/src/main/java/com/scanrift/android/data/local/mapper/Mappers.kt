package com.scanrift.android.data.local.mapper

import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CardListEntity
import com.scanrift.android.data.local.entity.CardListWithCards
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.CollectionEntryWithCard
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.entity.DeckEntryWithCard
import com.scanrift.android.data.local.entity.DeckWithEntries
import com.scanrift.android.data.local.entity.GameRecordEntity
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardCondition
import com.scanrift.android.domain.model.CardList
import com.scanrift.android.domain.model.CollectionEntry
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckEntry
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.domain.model.GameRecord
import com.scanrift.android.domain.model.GameResult

/**
 * Entity to domain conversion.
 *
 * The split costs one map per Flow emission over at most a couple of thousand rows,
 * and buys a domain layer with no Room or Android imports — which is what lets the
 * deck rules, set ordering and de-duplication comparators be tested on plain JUnit.
 */

fun CardEntity.toDomain(): Card = Card(
    id = id,
    name = name,
    riftboundId = riftboundId,
    publicCode = publicCode,
    collectorNumber = collectorNumber,
    energy = energy,
    might = might,
    power = power,
    type = type,
    supertype = supertype,
    rarity = rarity,
    domains = domains,
    richText = richText,
    plainText = plainText,
    setId = setId,
    setLabel = setLabel,
    sourceSetId = sourceSetId,
    imageUrl = imageUrl,
    artist = artist,
    accessibilityText = accessibilityText,
    cleanName = cleanName,
    alternateArt = alternateArt,
    overnumbered = overnumbered,
    signature = signature,
    updatedOn = updatedOn,
    orientation = orientation,
    tags = tags,
)

fun Card.toEntity(): CardEntity = CardEntity(
    id = id,
    name = name,
    riftboundId = riftboundId,
    publicCode = publicCode,
    collectorNumber = collectorNumber,
    energy = energy,
    might = might,
    power = power,
    type = type,
    supertype = supertype,
    rarity = rarity,
    domains = domains,
    richText = richText,
    plainText = plainText,
    setId = setId,
    setLabel = setLabel,
    sourceSetId = sourceSetId,
    imageUrl = imageUrl,
    artist = artist,
    accessibilityText = accessibilityText,
    cleanName = cleanName,
    alternateArt = alternateArt,
    overnumbered = overnumbered,
    signature = signature,
    updatedOn = updatedOn,
    orientation = orientation,
    tags = tags,
)

fun CollectionEntryEntity.toDomain(card: Card? = null): CollectionEntry = CollectionEntry(
    id = id,
    cardId = cardId,
    card = card,
    quantity = quantity,
    isFoil = isFoil,
    dateAdded = dateAdded,
    condition = CardCondition.fromValue(condition),
    notes = notes,
    folder = folder,
)

fun CollectionEntryWithCard.toDomain(): CollectionEntry = entry.toDomain(card?.toDomain())

fun CollectionEntry.toEntity(): CollectionEntryEntity = CollectionEntryEntity(
    id = id,
    cardId = cardId,
    quantity = quantity,
    isFoil = isFoil,
    dateAdded = dateAdded,
    condition = condition.value,
    notes = notes,
    folder = folder,
)

fun CardListEntity.toDomain(cards: List<Card> = emptyList()): CardList = CardList(
    id = id,
    name = name,
    colorHex = colorHex,
    isSystem = isSystem,
    systemType = systemType,
    createdDate = createdDate,
    cards = cards,
)

fun CardListWithCards.toDomain(): CardList = list.toDomain(cards.map { it.toDomain() })

fun CardList.toEntity(): CardListEntity = CardListEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    isSystem = isSystem,
    systemType = systemType,
    createdDate = createdDate,
)

fun DeckEntryEntity.toDomain(card: Card? = null): DeckEntry = DeckEntry(
    id = id,
    deckId = deckId,
    cardId = cardId,
    card = card,
    quantity = quantity,
    section = DeckSection.fromValue(section),
)

fun DeckEntryWithCard.toDomain(): DeckEntry = entry.toDomain(card?.toDomain())

fun DeckEntry.toEntity(): DeckEntryEntity = DeckEntryEntity(
    id = id,
    deckId = deckId,
    cardId = cardId,
    quantity = quantity,
    section = section.value,
)

fun DeckEntity.toDomain(
    legend: Card? = null,
    champion: Card? = null,
    entries: List<DeckEntry> = emptyList(),
): Deck = Deck(
    id = id,
    name = name,
    createdDate = createdDate,
    lastModifiedDate = lastModifiedDate,
    legendCardId = legendCardId,
    championCardId = championCardId,
    legend = legend,
    champion = champion,
    entries = entries,
)

fun DeckWithEntries.toDomain(): Deck = deck.toDomain(
    legend = legend?.toDomain(),
    champion = champion?.toDomain(),
    entries = entries.map { it.toDomain() },
)

fun Deck.toEntity(): DeckEntity = DeckEntity(
    id = id,
    name = name,
    createdDate = createdDate,
    lastModifiedDate = lastModifiedDate,
    legendCardId = legendCardId,
    championCardId = championCardId,
)

fun GameRecordEntity.toDomain(): GameRecord = GameRecord(
    id = id,
    date = date,
    name = name,
    result = GameResult.fromValue(result),
    playerName = playerName,
    opponentName = opponentName,
    pointsScored = pointsScored,
    pointsAllowed = pointsAllowed,
    ties = ties,
    conquerCount = conquerCount,
    holdCount = holdCount,
    abilityCount = abilityCount,
    notes = notes,
    deckId = deckId,
    legendId = legendId,
    opponentLegendId = opponentLegendId,
)

fun GameRecord.toEntity(): GameRecordEntity = GameRecordEntity(
    id = id,
    date = date,
    name = name,
    result = result.value,
    playerName = playerName,
    opponentName = opponentName,
    pointsScored = pointsScored,
    pointsAllowed = pointsAllowed,
    ties = ties,
    conquerCount = conquerCount,
    holdCount = holdCount,
    abilityCount = abilityCount,
    notes = notes,
    deckId = deckId,
    legendId = legendId,
    opponentLegendId = opponentLegendId,
)
