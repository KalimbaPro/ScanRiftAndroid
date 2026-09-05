package com.scanrift.android.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class CollectionEntryWithCard(
    @Embedded val entry: CollectionEntryEntity,
    @Relation(parentColumn = "cardId", entityColumn = "id")
    val card: CardEntity?,
)

data class CardListWithCards(
    @Embedded val list: CardListEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = CardListCrossRef::class,
            parentColumn = "listId",
            entityColumn = "cardId",
        ),
    )
    val cards: List<CardEntity>,
)

data class DeckEntryWithCard(
    @Embedded val entry: DeckEntryEntity,
    @Relation(parentColumn = "cardId", entityColumn = "id")
    val card: CardEntity?,
)

data class DeckWithEntries(
    @Embedded val deck: DeckEntity,
    @Relation(parentColumn = "id", entityColumn = "deckId", entity = DeckEntryEntity::class)
    val entries: List<DeckEntryWithCard>,
    @Relation(parentColumn = "legendCardId", entityColumn = "id")
    val legend: CardEntity?,
    @Relation(parentColumn = "championCardId", entityColumn = "id")
    val champion: CardEntity?,
)
