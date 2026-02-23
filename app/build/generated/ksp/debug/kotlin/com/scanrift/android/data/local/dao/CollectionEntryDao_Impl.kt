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
import com.scanrift.android.`data`.local.entity.CollectionEntryEntity
import com.scanrift.android.`data`.local.entity.CollectionEntryWithCard
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
public class CollectionEntryDao_Impl(
  __db: RoomDatabase,
) : CollectionEntryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfCollectionEntryEntity: EntityInsertAdapter<CollectionEntryEntity>

  private val __deleteAdapterOfCollectionEntryEntity:
      EntityDeleteOrUpdateAdapter<CollectionEntryEntity>

  private val __updateAdapterOfCollectionEntryEntity:
      EntityDeleteOrUpdateAdapter<CollectionEntryEntity>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__insertAdapterOfCollectionEntryEntity = object : EntityInsertAdapter<CollectionEntryEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `collection_entries` (`id`,`cardId`,`quantity`,`isFoil`,`dateAdded`,`condition`,`notes`,`folder`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CollectionEntryEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.cardId)
        statement.bindLong(3, entity.quantity.toLong())
        val _tmp: Int = if (entity.isFoil) 1 else 0
        statement.bindLong(4, _tmp.toLong())
        statement.bindLong(5, entity.dateAdded)
        statement.bindText(6, entity.condition)
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpNotes)
        }
        val _tmpFolder: String? = entity.folder
        if (_tmpFolder == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpFolder)
        }
      }
    }
    this.__deleteAdapterOfCollectionEntryEntity = object : EntityDeleteOrUpdateAdapter<CollectionEntryEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `collection_entries` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: CollectionEntryEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfCollectionEntryEntity = object : EntityDeleteOrUpdateAdapter<CollectionEntryEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `collection_entries` SET `id` = ?,`cardId` = ?,`quantity` = ?,`isFoil` = ?,`dateAdded` = ?,`condition` = ?,`notes` = ?,`folder` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: CollectionEntryEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.cardId)
        statement.bindLong(3, entity.quantity.toLong())
        val _tmp: Int = if (entity.isFoil) 1 else 0
        statement.bindLong(4, _tmp.toLong())
        statement.bindLong(5, entity.dateAdded)
        statement.bindText(6, entity.condition)
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpNotes)
        }
        val _tmpFolder: String? = entity.folder
        if (_tmpFolder == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpFolder)
        }
        statement.bindLong(9, entity.id)
      }
    }
  }

  public override suspend fun insert(entry: CollectionEntryEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfCollectionEntryEntity.insertAndReturnId(_connection, entry)
    _result
  }

  public override suspend fun insertAll(entries: List<CollectionEntryEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfCollectionEntryEntity.insert(_connection, entries)
  }

  public override suspend fun delete(entry: CollectionEntryEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfCollectionEntryEntity.handle(_connection, entry)
  }

  public override suspend fun update(entry: CollectionEntryEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfCollectionEntryEntity.handle(_connection, entry)
  }

  public override fun getAllWithCards(): Flow<List<CollectionEntryWithCard>> {
    val _sql: String = "SELECT * FROM collection_entries ORDER BY dateAdded DESC"
    return createFlow(__db, true, arrayOf("cards", "collection_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCardId: Int = getColumnIndexOrThrow(_stmt, "cardId")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfIsFoil: Int = getColumnIndexOrThrow(_stmt, "isFoil")
        val _columnIndexOfDateAdded: Int = getColumnIndexOrThrow(_stmt, "dateAdded")
        val _columnIndexOfCondition: Int = getColumnIndexOrThrow(_stmt, "condition")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfFolder: Int = getColumnIndexOrThrow(_stmt, "folder")
        val _collectionCard: ArrayMap<String, CardEntity?> = ArrayMap<String, CardEntity?>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfCardId)
          _collectionCard.put(_tmpKey, null)
        }
        _stmt.reset()
        __fetchRelationshipcardsAscomScanriftAndroidDataLocalEntityCardEntity(_connection, _collectionCard)
        val _result: MutableList<CollectionEntryWithCard> = mutableListOf()
        while (_stmt.step()) {
          val _item: CollectionEntryWithCard
          val _tmpEntry: CollectionEntryEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCardId: String
          _tmpCardId = _stmt.getText(_columnIndexOfCardId)
          val _tmpQuantity: Int
          _tmpQuantity = _stmt.getLong(_columnIndexOfQuantity).toInt()
          val _tmpIsFoil: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFoil).toInt()
          _tmpIsFoil = _tmp != 0
          val _tmpDateAdded: Long
          _tmpDateAdded = _stmt.getLong(_columnIndexOfDateAdded)
          val _tmpCondition: String
          _tmpCondition = _stmt.getText(_columnIndexOfCondition)
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpFolder: String?
          if (_stmt.isNull(_columnIndexOfFolder)) {
            _tmpFolder = null
          } else {
            _tmpFolder = _stmt.getText(_columnIndexOfFolder)
          }
          _tmpEntry = CollectionEntryEntity(_tmpId,_tmpCardId,_tmpQuantity,_tmpIsFoil,_tmpDateAdded,_tmpCondition,_tmpNotes,_tmpFolder)
          val _tmpCard: CardEntity?
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfCardId)
          _tmpCard = _collectionCard.get(_tmpKey_1)
          if (_tmpCard == null) {
            error("Relationship item 'card' was expected to be NON-NULL but is NULL in @Relation involving a parent column named 'cardId' and entityColumn named 'id'.")
          }
          _item = CollectionEntryWithCard(_tmpEntry,_tmpCard)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllWithCardsList(): List<CollectionEntryWithCard> {
    val _sql: String = "SELECT * FROM collection_entries ORDER BY dateAdded DESC"
    return performSuspending(__db, true, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCardId: Int = getColumnIndexOrThrow(_stmt, "cardId")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfIsFoil: Int = getColumnIndexOrThrow(_stmt, "isFoil")
        val _columnIndexOfDateAdded: Int = getColumnIndexOrThrow(_stmt, "dateAdded")
        val _columnIndexOfCondition: Int = getColumnIndexOrThrow(_stmt, "condition")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfFolder: Int = getColumnIndexOrThrow(_stmt, "folder")
        val _collectionCard: ArrayMap<String, CardEntity?> = ArrayMap<String, CardEntity?>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfCardId)
          _collectionCard.put(_tmpKey, null)
        }
        _stmt.reset()
        __fetchRelationshipcardsAscomScanriftAndroidDataLocalEntityCardEntity(_connection, _collectionCard)
        val _result: MutableList<CollectionEntryWithCard> = mutableListOf()
        while (_stmt.step()) {
          val _item: CollectionEntryWithCard
          val _tmpEntry: CollectionEntryEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCardId: String
          _tmpCardId = _stmt.getText(_columnIndexOfCardId)
          val _tmpQuantity: Int
          _tmpQuantity = _stmt.getLong(_columnIndexOfQuantity).toInt()
          val _tmpIsFoil: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFoil).toInt()
          _tmpIsFoil = _tmp != 0
          val _tmpDateAdded: Long
          _tmpDateAdded = _stmt.getLong(_columnIndexOfDateAdded)
          val _tmpCondition: String
          _tmpCondition = _stmt.getText(_columnIndexOfCondition)
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpFolder: String?
          if (_stmt.isNull(_columnIndexOfFolder)) {
            _tmpFolder = null
          } else {
            _tmpFolder = _stmt.getText(_columnIndexOfFolder)
          }
          _tmpEntry = CollectionEntryEntity(_tmpId,_tmpCardId,_tmpQuantity,_tmpIsFoil,_tmpDateAdded,_tmpCondition,_tmpNotes,_tmpFolder)
          val _tmpCard: CardEntity?
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfCardId)
          _tmpCard = _collectionCard.get(_tmpKey_1)
          if (_tmpCard == null) {
            error("Relationship item 'card' was expected to be NON-NULL but is NULL in @Relation involving a parent column named 'cardId' and entityColumn named 'id'.")
          }
          _item = CollectionEntryWithCard(_tmpEntry,_tmpCard)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getEntriesForCard(cardId: String): List<CollectionEntryEntity> {
    val _sql: String = "SELECT * FROM collection_entries WHERE cardId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, cardId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCardId: Int = getColumnIndexOrThrow(_stmt, "cardId")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfIsFoil: Int = getColumnIndexOrThrow(_stmt, "isFoil")
        val _columnIndexOfDateAdded: Int = getColumnIndexOrThrow(_stmt, "dateAdded")
        val _columnIndexOfCondition: Int = getColumnIndexOrThrow(_stmt, "condition")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfFolder: Int = getColumnIndexOrThrow(_stmt, "folder")
        val _result: MutableList<CollectionEntryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CollectionEntryEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCardId: String
          _tmpCardId = _stmt.getText(_columnIndexOfCardId)
          val _tmpQuantity: Int
          _tmpQuantity = _stmt.getLong(_columnIndexOfQuantity).toInt()
          val _tmpIsFoil: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFoil).toInt()
          _tmpIsFoil = _tmp != 0
          val _tmpDateAdded: Long
          _tmpDateAdded = _stmt.getLong(_columnIndexOfDateAdded)
          val _tmpCondition: String
          _tmpCondition = _stmt.getText(_columnIndexOfCondition)
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpFolder: String?
          if (_stmt.isNull(_columnIndexOfFolder)) {
            _tmpFolder = null
          } else {
            _tmpFolder = _stmt.getText(_columnIndexOfFolder)
          }
          _item = CollectionEntryEntity(_tmpId,_tmpCardId,_tmpQuantity,_tmpIsFoil,_tmpDateAdded,_tmpCondition,_tmpNotes,_tmpFolder)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun findEntry(cardId: String, isFoil: Boolean): CollectionEntryEntity? {
    val _sql: String = "SELECT * FROM collection_entries WHERE cardId = ? AND isFoil = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, cardId)
        _argIndex = 2
        val _tmp: Int = if (isFoil) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCardId: Int = getColumnIndexOrThrow(_stmt, "cardId")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfIsFoil: Int = getColumnIndexOrThrow(_stmt, "isFoil")
        val _columnIndexOfDateAdded: Int = getColumnIndexOrThrow(_stmt, "dateAdded")
        val _columnIndexOfCondition: Int = getColumnIndexOrThrow(_stmt, "condition")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfFolder: Int = getColumnIndexOrThrow(_stmt, "folder")
        val _result: CollectionEntryEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCardId: String
          _tmpCardId = _stmt.getText(_columnIndexOfCardId)
          val _tmpQuantity: Int
          _tmpQuantity = _stmt.getLong(_columnIndexOfQuantity).toInt()
          val _tmpIsFoil: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsFoil).toInt()
          _tmpIsFoil = _tmp_1 != 0
          val _tmpDateAdded: Long
          _tmpDateAdded = _stmt.getLong(_columnIndexOfDateAdded)
          val _tmpCondition: String
          _tmpCondition = _stmt.getText(_columnIndexOfCondition)
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpFolder: String?
          if (_stmt.isNull(_columnIndexOfFolder)) {
            _tmpFolder = null
          } else {
            _tmpFolder = _stmt.getText(_columnIndexOfFolder)
          }
          _result = CollectionEntryEntity(_tmpId,_tmpCardId,_tmpQuantity,_tmpIsFoil,_tmpDateAdded,_tmpCondition,_tmpNotes,_tmpFolder)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getEntryCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM collection_entries"
    return createFlow(__db, false, arrayOf("collection_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getTotalCardCount(): Flow<Int?> {
    val _sql: String = "SELECT SUM(quantity) FROM collection_entries"
    return createFlow(__db, false, arrayOf("collection_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int?
        if (_stmt.step()) {
          val _tmp: Int?
          if (_stmt.isNull(0)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(0).toInt()
          }
          _result = _tmp
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getUniqueCardCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(DISTINCT cardId) FROM collection_entries"
    return createFlow(__db, false, arrayOf("collection_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM collection_entries"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
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
