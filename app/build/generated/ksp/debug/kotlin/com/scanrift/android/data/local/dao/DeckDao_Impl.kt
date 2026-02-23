package com.scanrift.android.`data`.local.dao

import androidx.collection.ArrayMap
import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndex
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.room.util.recursiveFetchArrayMap
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.scanrift.android.`data`.local.converter.Converters
import com.scanrift.android.`data`.local.entity.CardEntity
import com.scanrift.android.`data`.local.entity.DeckEntity
import com.scanrift.android.`data`.local.entity.DeckEntryEntity
import com.scanrift.android.`data`.local.entity.DeckEntryWithCard
import com.scanrift.android.`data`.local.entity.DeckWithEntries
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlin.text.StringBuilder
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class DeckDao_Impl(
  __db: RoomDatabase,
) : DeckDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfDeckEntity: EntityInsertAdapter<DeckEntity>

  private val __insertAdapterOfDeckEntryEntity: EntityInsertAdapter<DeckEntryEntity>

  private val __deleteAdapterOfDeckEntity: EntityDeleteOrUpdateAdapter<DeckEntity>

  private val __deleteAdapterOfDeckEntryEntity: EntityDeleteOrUpdateAdapter<DeckEntryEntity>

  private val __updateAdapterOfDeckEntity: EntityDeleteOrUpdateAdapter<DeckEntity>

  private val __updateAdapterOfDeckEntryEntity: EntityDeleteOrUpdateAdapter<DeckEntryEntity>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__insertAdapterOfDeckEntity = object : EntityInsertAdapter<DeckEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `decks` (`id`,`name`,`createdDate`,`lastModifiedDate`,`legendCardId`,`championCardId`) VALUES (?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: DeckEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindLong(3, entity.createdDate)
        statement.bindLong(4, entity.lastModifiedDate)
        val _tmpLegendCardId: String? = entity.legendCardId
        if (_tmpLegendCardId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpLegendCardId)
        }
        val _tmpChampionCardId: String? = entity.championCardId
        if (_tmpChampionCardId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpChampionCardId)
        }
      }
    }
    this.__insertAdapterOfDeckEntryEntity = object : EntityInsertAdapter<DeckEntryEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `deck_entries` (`id`,`deckId`,`cardId`,`quantity`,`section`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: DeckEntryEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.deckId)
        statement.bindText(3, entity.cardId)
        statement.bindLong(4, entity.quantity.toLong())
        statement.bindText(5, entity.section)
      }
    }
    this.__deleteAdapterOfDeckEntity = object : EntityDeleteOrUpdateAdapter<DeckEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `decks` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: DeckEntity) {
        statement.bindText(1, entity.id)
      }
    }
    this.__deleteAdapterOfDeckEntryEntity = object : EntityDeleteOrUpdateAdapter<DeckEntryEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `deck_entries` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: DeckEntryEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfDeckEntity = object : EntityDeleteOrUpdateAdapter<DeckEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `decks` SET `id` = ?,`name` = ?,`createdDate` = ?,`lastModifiedDate` = ?,`legendCardId` = ?,`championCardId` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: DeckEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindLong(3, entity.createdDate)
        statement.bindLong(4, entity.lastModifiedDate)
        val _tmpLegendCardId: String? = entity.legendCardId
        if (_tmpLegendCardId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpLegendCardId)
        }
        val _tmpChampionCardId: String? = entity.championCardId
        if (_tmpChampionCardId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpChampionCardId)
        }
        statement.bindText(7, entity.id)
      }
    }
    this.__updateAdapterOfDeckEntryEntity = object : EntityDeleteOrUpdateAdapter<DeckEntryEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `deck_entries` SET `id` = ?,`deckId` = ?,`cardId` = ?,`quantity` = ?,`section` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: DeckEntryEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.deckId)
        statement.bindText(3, entity.cardId)
        statement.bindLong(4, entity.quantity.toLong())
        statement.bindText(5, entity.section)
        statement.bindLong(6, entity.id)
      }
    }
  }

  public override suspend fun insertDeck(deck: DeckEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfDeckEntity.insert(_connection, deck)
  }

  public override suspend fun insertEntry(entry: DeckEntryEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfDeckEntryEntity.insertAndReturnId(_connection, entry)
    _result
  }

  public override suspend fun deleteDeck(deck: DeckEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfDeckEntity.handle(_connection, deck)
  }

  public override suspend fun deleteEntry(entry: DeckEntryEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfDeckEntryEntity.handle(_connection, entry)
  }

  public override suspend fun updateDeck(deck: DeckEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfDeckEntity.handle(_connection, deck)
  }

  public override suspend fun updateEntry(entry: DeckEntryEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfDeckEntryEntity.handle(_connection, entry)
  }

  public override fun getAllDecks(): Flow<List<DeckEntity>> {
    val _sql: String = "SELECT * FROM decks ORDER BY lastModifiedDate DESC"
    return createFlow(__db, false, arrayOf("decks")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCreatedDate: Int = getColumnIndexOrThrow(_stmt, "createdDate")
        val _columnIndexOfLastModifiedDate: Int = getColumnIndexOrThrow(_stmt, "lastModifiedDate")
        val _columnIndexOfLegendCardId: Int = getColumnIndexOrThrow(_stmt, "legendCardId")
        val _columnIndexOfChampionCardId: Int = getColumnIndexOrThrow(_stmt, "championCardId")
        val _result: MutableList<DeckEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DeckEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCreatedDate: Long
          _tmpCreatedDate = _stmt.getLong(_columnIndexOfCreatedDate)
          val _tmpLastModifiedDate: Long
          _tmpLastModifiedDate = _stmt.getLong(_columnIndexOfLastModifiedDate)
          val _tmpLegendCardId: String?
          if (_stmt.isNull(_columnIndexOfLegendCardId)) {
            _tmpLegendCardId = null
          } else {
            _tmpLegendCardId = _stmt.getText(_columnIndexOfLegendCardId)
          }
          val _tmpChampionCardId: String?
          if (_stmt.isNull(_columnIndexOfChampionCardId)) {
            _tmpChampionCardId = null
          } else {
            _tmpChampionCardId = _stmt.getText(_columnIndexOfChampionCardId)
          }
          _item = DeckEntity(_tmpId,_tmpName,_tmpCreatedDate,_tmpLastModifiedDate,_tmpLegendCardId,_tmpChampionCardId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getDeckById(id: String): DeckEntity? {
    val _sql: String = "SELECT * FROM decks WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCreatedDate: Int = getColumnIndexOrThrow(_stmt, "createdDate")
        val _columnIndexOfLastModifiedDate: Int = getColumnIndexOrThrow(_stmt, "lastModifiedDate")
        val _columnIndexOfLegendCardId: Int = getColumnIndexOrThrow(_stmt, "legendCardId")
        val _columnIndexOfChampionCardId: Int = getColumnIndexOrThrow(_stmt, "championCardId")
        val _result: DeckEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCreatedDate: Long
          _tmpCreatedDate = _stmt.getLong(_columnIndexOfCreatedDate)
          val _tmpLastModifiedDate: Long
          _tmpLastModifiedDate = _stmt.getLong(_columnIndexOfLastModifiedDate)
          val _tmpLegendCardId: String?
          if (_stmt.isNull(_columnIndexOfLegendCardId)) {
            _tmpLegendCardId = null
          } else {
            _tmpLegendCardId = _stmt.getText(_columnIndexOfLegendCardId)
          }
          val _tmpChampionCardId: String?
          if (_stmt.isNull(_columnIndexOfChampionCardId)) {
            _tmpChampionCardId = null
          } else {
            _tmpChampionCardId = _stmt.getText(_columnIndexOfChampionCardId)
          }
          _result = DeckEntity(_tmpId,_tmpName,_tmpCreatedDate,_tmpLastModifiedDate,_tmpLegendCardId,_tmpChampionCardId)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getDeckWithEntries(id: String): Flow<DeckWithEntries?> {
    val _sql: String = "SELECT * FROM decks WHERE id = ?"
    return createFlow(__db, true, arrayOf("deck_entries", "decks")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCreatedDate: Int = getColumnIndexOrThrow(_stmt, "createdDate")
        val _columnIndexOfLastModifiedDate: Int = getColumnIndexOrThrow(_stmt, "lastModifiedDate")
        val _columnIndexOfLegendCardId: Int = getColumnIndexOrThrow(_stmt, "legendCardId")
        val _columnIndexOfChampionCardId: Int = getColumnIndexOrThrow(_stmt, "championCardId")
        val _collectionEntries: ArrayMap<String, MutableList<DeckEntryEntity>> = ArrayMap<String, MutableList<DeckEntryEntity>>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfId)
          if (!_collectionEntries.containsKey(_tmpKey)) {
            _collectionEntries.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipdeckEntriesAscomScanriftAndroidDataLocalEntityDeckEntryEntity(_connection, _collectionEntries)
        val _result: DeckWithEntries?
        if (_stmt.step()) {
          val _tmpDeck: DeckEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCreatedDate: Long
          _tmpCreatedDate = _stmt.getLong(_columnIndexOfCreatedDate)
          val _tmpLastModifiedDate: Long
          _tmpLastModifiedDate = _stmt.getLong(_columnIndexOfLastModifiedDate)
          val _tmpLegendCardId: String?
          if (_stmt.isNull(_columnIndexOfLegendCardId)) {
            _tmpLegendCardId = null
          } else {
            _tmpLegendCardId = _stmt.getText(_columnIndexOfLegendCardId)
          }
          val _tmpChampionCardId: String?
          if (_stmt.isNull(_columnIndexOfChampionCardId)) {
            _tmpChampionCardId = null
          } else {
            _tmpChampionCardId = _stmt.getText(_columnIndexOfChampionCardId)
          }
          _tmpDeck = DeckEntity(_tmpId,_tmpName,_tmpCreatedDate,_tmpLastModifiedDate,_tmpLegendCardId,_tmpChampionCardId)
          val _tmpEntriesCollection: MutableList<DeckEntryEntity>
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfId)
          _tmpEntriesCollection = _collectionEntries.getValue(_tmpKey_1)
          _result = DeckWithEntries(_tmpDeck,_tmpEntriesCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllDecksWithEntries(): Flow<List<DeckWithEntries>> {
    val _sql: String = "SELECT * FROM decks ORDER BY lastModifiedDate DESC"
    return createFlow(__db, true, arrayOf("deck_entries", "decks")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCreatedDate: Int = getColumnIndexOrThrow(_stmt, "createdDate")
        val _columnIndexOfLastModifiedDate: Int = getColumnIndexOrThrow(_stmt, "lastModifiedDate")
        val _columnIndexOfLegendCardId: Int = getColumnIndexOrThrow(_stmt, "legendCardId")
        val _columnIndexOfChampionCardId: Int = getColumnIndexOrThrow(_stmt, "championCardId")
        val _collectionEntries: ArrayMap<String, MutableList<DeckEntryEntity>> = ArrayMap<String, MutableList<DeckEntryEntity>>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfId)
          if (!_collectionEntries.containsKey(_tmpKey)) {
            _collectionEntries.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipdeckEntriesAscomScanriftAndroidDataLocalEntityDeckEntryEntity(_connection, _collectionEntries)
        val _result: MutableList<DeckWithEntries> = mutableListOf()
        while (_stmt.step()) {
          val _item: DeckWithEntries
          val _tmpDeck: DeckEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCreatedDate: Long
          _tmpCreatedDate = _stmt.getLong(_columnIndexOfCreatedDate)
          val _tmpLastModifiedDate: Long
          _tmpLastModifiedDate = _stmt.getLong(_columnIndexOfLastModifiedDate)
          val _tmpLegendCardId: String?
          if (_stmt.isNull(_columnIndexOfLegendCardId)) {
            _tmpLegendCardId = null
          } else {
            _tmpLegendCardId = _stmt.getText(_columnIndexOfLegendCardId)
          }
          val _tmpChampionCardId: String?
          if (_stmt.isNull(_columnIndexOfChampionCardId)) {
            _tmpChampionCardId = null
          } else {
            _tmpChampionCardId = _stmt.getText(_columnIndexOfChampionCardId)
          }
          _tmpDeck = DeckEntity(_tmpId,_tmpName,_tmpCreatedDate,_tmpLastModifiedDate,_tmpLegendCardId,_tmpChampionCardId)
          val _tmpEntriesCollection: MutableList<DeckEntryEntity>
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfId)
          _tmpEntriesCollection = _collectionEntries.getValue(_tmpKey_1)
          _item = DeckWithEntries(_tmpDeck,_tmpEntriesCollection)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getEntriesForDeck(deckId: String): List<DeckEntryEntity> {
    val _sql: String = "SELECT * FROM deck_entries WHERE deckId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, deckId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDeckId: Int = getColumnIndexOrThrow(_stmt, "deckId")
        val _columnIndexOfCardId: Int = getColumnIndexOrThrow(_stmt, "cardId")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfSection: Int = getColumnIndexOrThrow(_stmt, "section")
        val _result: MutableList<DeckEntryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DeckEntryEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDeckId: String
          _tmpDeckId = _stmt.getText(_columnIndexOfDeckId)
          val _tmpCardId: String
          _tmpCardId = _stmt.getText(_columnIndexOfCardId)
          val _tmpQuantity: Int
          _tmpQuantity = _stmt.getLong(_columnIndexOfQuantity).toInt()
          val _tmpSection: String
          _tmpSection = _stmt.getText(_columnIndexOfSection)
          _item = DeckEntryEntity(_tmpId,_tmpDeckId,_tmpCardId,_tmpQuantity,_tmpSection)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getEntriesWithCardsForDeck(deckId: String): Flow<List<DeckEntryWithCard>> {
    val _sql: String = "SELECT * FROM deck_entries WHERE deckId = ?"
    return createFlow(__db, true, arrayOf("cards", "deck_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, deckId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDeckId: Int = getColumnIndexOrThrow(_stmt, "deckId")
        val _columnIndexOfCardId: Int = getColumnIndexOrThrow(_stmt, "cardId")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfSection: Int = getColumnIndexOrThrow(_stmt, "section")
        val _collectionCard: ArrayMap<String, CardEntity?> = ArrayMap<String, CardEntity?>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfCardId)
          _collectionCard.put(_tmpKey, null)
        }
        _stmt.reset()
        __fetchRelationshipcardsAscomScanriftAndroidDataLocalEntityCardEntity(_connection, _collectionCard)
        val _result: MutableList<DeckEntryWithCard> = mutableListOf()
        while (_stmt.step()) {
          val _item: DeckEntryWithCard
          val _tmpEntry: DeckEntryEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDeckId: String
          _tmpDeckId = _stmt.getText(_columnIndexOfDeckId)
          val _tmpCardId: String
          _tmpCardId = _stmt.getText(_columnIndexOfCardId)
          val _tmpQuantity: Int
          _tmpQuantity = _stmt.getLong(_columnIndexOfQuantity).toInt()
          val _tmpSection: String
          _tmpSection = _stmt.getText(_columnIndexOfSection)
          _tmpEntry = DeckEntryEntity(_tmpId,_tmpDeckId,_tmpCardId,_tmpQuantity,_tmpSection)
          val _tmpCard: CardEntity?
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfCardId)
          _tmpCard = _collectionCard.get(_tmpKey_1)
          if (_tmpCard == null) {
            error("Relationship item 'card' was expected to be NON-NULL but is NULL in @Relation involving a parent column named 'cardId' and entityColumn named 'id'.")
          }
          _item = DeckEntryWithCard(_tmpEntry,_tmpCard)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun findEntry(
    deckId: String,
    cardId: String,
    section: String,
  ): DeckEntryEntity? {
    val _sql: String = "SELECT * FROM deck_entries WHERE deckId = ? AND cardId = ? AND section = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, deckId)
        _argIndex = 2
        _stmt.bindText(_argIndex, cardId)
        _argIndex = 3
        _stmt.bindText(_argIndex, section)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDeckId: Int = getColumnIndexOrThrow(_stmt, "deckId")
        val _columnIndexOfCardId: Int = getColumnIndexOrThrow(_stmt, "cardId")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfSection: Int = getColumnIndexOrThrow(_stmt, "section")
        val _result: DeckEntryEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDeckId: String
          _tmpDeckId = _stmt.getText(_columnIndexOfDeckId)
          val _tmpCardId: String
          _tmpCardId = _stmt.getText(_columnIndexOfCardId)
          val _tmpQuantity: Int
          _tmpQuantity = _stmt.getLong(_columnIndexOfQuantity).toInt()
          val _tmpSection: String
          _tmpSection = _stmt.getText(_columnIndexOfSection)
          _result = DeckEntryEntity(_tmpId,_tmpDeckId,_tmpCardId,_tmpQuantity,_tmpSection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllEntriesForDeck(deckId: String) {
    val _sql: String = "DELETE FROM deck_entries WHERE deckId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, deckId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  private fun __fetchRelationshipdeckEntriesAscomScanriftAndroidDataLocalEntityDeckEntryEntity(_connection: SQLiteConnection, _map: ArrayMap<String, MutableList<DeckEntryEntity>>) {
    val __mapKeySet: Set<String> = _map.keys
    if (__mapKeySet.isEmpty()) {
      return
    }
    if (_map.size > 999) {
      recursiveFetchArrayMap(_map, true) { _tmpMap ->
        __fetchRelationshipdeckEntriesAscomScanriftAndroidDataLocalEntityDeckEntryEntity(_connection, _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`deckId`,`cardId`,`quantity`,`section` FROM `deck_entries` WHERE `deckId` IN (")
    val _inputSize: Int = __mapKeySet.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    val _stmt: SQLiteStatement = _connection.prepare(_sql)
    var _argIndex: Int = 1
    for (_item: String in __mapKeySet) {
      _stmt.bindText(_argIndex, _item)
      _argIndex++
    }
    try {
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "deckId")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfDeckId: Int = 1
      val _columnIndexOfCardId: Int = 2
      val _columnIndexOfQuantity: Int = 3
      val _columnIndexOfSection: Int = 4
      while (_stmt.step()) {
        val _tmpKey: String
        _tmpKey = _stmt.getText(_itemKeyIndex)
        val _tmpRelation: MutableList<DeckEntryEntity>? = _map.get(_tmpKey)
        if (_tmpRelation != null) {
          val _item_1: DeckEntryEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDeckId: String
          _tmpDeckId = _stmt.getText(_columnIndexOfDeckId)
          val _tmpCardId: String
          _tmpCardId = _stmt.getText(_columnIndexOfCardId)
          val _tmpQuantity: Int
          _tmpQuantity = _stmt.getLong(_columnIndexOfQuantity).toInt()
          val _tmpSection: String
          _tmpSection = _stmt.getText(_columnIndexOfSection)
          _item_1 = DeckEntryEntity(_tmpId,_tmpDeckId,_tmpCardId,_tmpQuantity,_tmpSection)
          _tmpRelation.add(_item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  private fun __fetchRelationshipcardsAscomScanriftAndroidDataLocalEntityCardEntity(_connection: SQLiteConnection, _map: ArrayMap<String, CardEntity?>) {
    val __mapKeySet: Set<String> = _map.keys
    if (__mapKeySet.isEmpty()) {
      return
    }
    if (_map.size > 999) {
      recursiveFetchArrayMap(_map, false) { _tmpMap ->
        __fetchRelationshipcardsAscomScanriftAndroidDataLocalEntityCardEntity(_connection, _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`name`,`riftboundId`,`publicCode`,`collectorNumber`,`energy`,`might`,`power`,`type`,`supertype`,`rarity`,`domains`,`richText`,`plainText`,`setId`,`setLabel`,`imageUrl`,`artist`,`accessibilityText`,`cleanName`,`alternateArt`,`overnumbered`,`signature`,`orientation`,`tags` FROM `cards` WHERE `id` IN (")
    val _inputSize: Int = __mapKeySet.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    val _stmt: SQLiteStatement = _connection.prepare(_sql)
    var _argIndex: Int = 1
    for (_item: String in __mapKeySet) {
      _stmt.bindText(_argIndex, _item)
      _argIndex++
    }
    try {
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "id")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfName: Int = 1
      val _columnIndexOfRiftboundId: Int = 2
      val _columnIndexOfPublicCode: Int = 3
      val _columnIndexOfCollectorNumber: Int = 4
      val _columnIndexOfEnergy: Int = 5
      val _columnIndexOfMight: Int = 6
      val _columnIndexOfPower: Int = 7
      val _columnIndexOfType: Int = 8
      val _columnIndexOfSupertype: Int = 9
      val _columnIndexOfRarity: Int = 10
      val _columnIndexOfDomains: Int = 11
      val _columnIndexOfRichText: Int = 12
      val _columnIndexOfPlainText: Int = 13
      val _columnIndexOfSetId: Int = 14
      val _columnIndexOfSetLabel: Int = 15
      val _columnIndexOfImageUrl: Int = 16
      val _columnIndexOfArtist: Int = 17
      val _columnIndexOfAccessibilityText: Int = 18
      val _columnIndexOfCleanName: Int = 19
      val _columnIndexOfAlternateArt: Int = 20
      val _columnIndexOfOvernumbered: Int = 21
      val _columnIndexOfSignature: Int = 22
      val _columnIndexOfOrientation: Int = 23
      val _columnIndexOfTags: Int = 24
      while (_stmt.step()) {
        val _tmpKey: String
        _tmpKey = _stmt.getText(_itemKeyIndex)
        if (_map.containsKey(_tmpKey)) {
          val _item_1: CardEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpRiftboundId: String
          _tmpRiftboundId = _stmt.getText(_columnIndexOfRiftboundId)
          val _tmpPublicCode: String
          _tmpPublicCode = _stmt.getText(_columnIndexOfPublicCode)
          val _tmpCollectorNumber: Int
          _tmpCollectorNumber = _stmt.getLong(_columnIndexOfCollectorNumber).toInt()
          val _tmpEnergy: Int?
          if (_stmt.isNull(_columnIndexOfEnergy)) {
            _tmpEnergy = null
          } else {
            _tmpEnergy = _stmt.getLong(_columnIndexOfEnergy).toInt()
          }
          val _tmpMight: Int?
          if (_stmt.isNull(_columnIndexOfMight)) {
            _tmpMight = null
          } else {
            _tmpMight = _stmt.getLong(_columnIndexOfMight).toInt()
          }
          val _tmpPower: Int?
          if (_stmt.isNull(_columnIndexOfPower)) {
            _tmpPower = null
          } else {
            _tmpPower = _stmt.getLong(_columnIndexOfPower).toInt()
          }
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpSupertype: String?
          if (_stmt.isNull(_columnIndexOfSupertype)) {
            _tmpSupertype = null
          } else {
            _tmpSupertype = _stmt.getText(_columnIndexOfSupertype)
          }
          val _tmpRarity: String
          _tmpRarity = _stmt.getText(_columnIndexOfRarity)
          val _tmpDomains: List<String>
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfDomains)
          _tmpDomains = __converters.toStringList(_tmp)
          val _tmpRichText: String?
          if (_stmt.isNull(_columnIndexOfRichText)) {
            _tmpRichText = null
          } else {
            _tmpRichText = _stmt.getText(_columnIndexOfRichText)
          }
          val _tmpPlainText: String?
          if (_stmt.isNull(_columnIndexOfPlainText)) {
            _tmpPlainText = null
          } else {
            _tmpPlainText = _stmt.getText(_columnIndexOfPlainText)
          }
          val _tmpSetId: String
          _tmpSetId = _stmt.getText(_columnIndexOfSetId)
          val _tmpSetLabel: String
          _tmpSetLabel = _stmt.getText(_columnIndexOfSetLabel)
          val _tmpImageUrl: String?
          if (_stmt.isNull(_columnIndexOfImageUrl)) {
            _tmpImageUrl = null
          } else {
            _tmpImageUrl = _stmt.getText(_columnIndexOfImageUrl)
          }
          val _tmpArtist: String?
          if (_stmt.isNull(_columnIndexOfArtist)) {
            _tmpArtist = null
          } else {
            _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          }
          val _tmpAccessibilityText: String?
          if (_stmt.isNull(_columnIndexOfAccessibilityText)) {
            _tmpAccessibilityText = null
          } else {
            _tmpAccessibilityText = _stmt.getText(_columnIndexOfAccessibilityText)
          }
          val _tmpCleanName: String
          _tmpCleanName = _stmt.getText(_columnIndexOfCleanName)
          val _tmpAlternateArt: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfAlternateArt).toInt()
          _tmpAlternateArt = _tmp_1 != 0
          val _tmpOvernumbered: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfOvernumbered).toInt()
          _tmpOvernumbered = _tmp_2 != 0
          val _tmpSignature: Boolean
          val _tmp_3: Int
          _tmp_3 = _stmt.getLong(_columnIndexOfSignature).toInt()
          _tmpSignature = _tmp_3 != 0
          val _tmpOrientation: String
          _tmpOrientation = _stmt.getText(_columnIndexOfOrientation)
          val _tmpTags: List<String>
          val _tmp_4: String
          _tmp_4 = _stmt.getText(_columnIndexOfTags)
          _tmpTags = __converters.toStringList(_tmp_4)
          _item_1 = CardEntity(_tmpId,_tmpName,_tmpRiftboundId,_tmpPublicCode,_tmpCollectorNumber,_tmpEnergy,_tmpMight,_tmpPower,_tmpType,_tmpSupertype,_tmpRarity,_tmpDomains,_tmpRichText,_tmpPlainText,_tmpSetId,_tmpSetLabel,_tmpImageUrl,_tmpArtist,_tmpAccessibilityText,_tmpCleanName,_tmpAlternateArt,_tmpOvernumbered,_tmpSignature,_tmpOrientation,_tmpTags)
          _map.put(_tmpKey, _item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
