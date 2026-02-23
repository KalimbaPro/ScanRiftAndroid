package com.scanrift.android.`data`.local.dao

import androidx.collection.ArrayMap
import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.room.util.recursiveFetchArrayMap
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.scanrift.android.`data`.local.converter.Converters
import com.scanrift.android.`data`.local.entity.CardEntity
import com.scanrift.android.`data`.local.entity.CardListCrossRef
import com.scanrift.android.`data`.local.entity.CardListEntity
import com.scanrift.android.`data`.local.entity.CardListWithCards
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
public class CardListDao_Impl(
  __db: RoomDatabase,
) : CardListDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfCardListEntity: EntityInsertAdapter<CardListEntity>

  private val __insertAdapterOfCardListCrossRef: EntityInsertAdapter<CardListCrossRef>

  private val __deleteAdapterOfCardListEntity: EntityDeleteOrUpdateAdapter<CardListEntity>

  private val __deleteAdapterOfCardListCrossRef: EntityDeleteOrUpdateAdapter<CardListCrossRef>

  private val __updateAdapterOfCardListEntity: EntityDeleteOrUpdateAdapter<CardListEntity>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__insertAdapterOfCardListEntity = object : EntityInsertAdapter<CardListEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `card_lists` (`id`,`name`,`colorHex`,`isSystem`,`systemType`,`createdDate`) VALUES (?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CardListEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.colorHex)
        val _tmp: Int = if (entity.isSystem) 1 else 0
        statement.bindLong(4, _tmp.toLong())
        val _tmpSystemType: String? = entity.systemType
        if (_tmpSystemType == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpSystemType)
        }
        statement.bindLong(6, entity.createdDate)
      }
    }
    this.__insertAdapterOfCardListCrossRef = object : EntityInsertAdapter<CardListCrossRef>() {
      protected override fun createQuery(): String = "INSERT OR IGNORE INTO `card_list_cross_ref` (`listId`,`cardId`) VALUES (?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CardListCrossRef) {
        statement.bindText(1, entity.listId)
        statement.bindText(2, entity.cardId)
      }
    }
    this.__deleteAdapterOfCardListEntity = object : EntityDeleteOrUpdateAdapter<CardListEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `card_lists` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: CardListEntity) {
        statement.bindText(1, entity.id)
      }
    }
    this.__deleteAdapterOfCardListCrossRef = object : EntityDeleteOrUpdateAdapter<CardListCrossRef>() {
      protected override fun createQuery(): String = "DELETE FROM `card_list_cross_ref` WHERE `listId` = ? AND `cardId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: CardListCrossRef) {
        statement.bindText(1, entity.listId)
        statement.bindText(2, entity.cardId)
      }
    }
    this.__updateAdapterOfCardListEntity = object : EntityDeleteOrUpdateAdapter<CardListEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `card_lists` SET `id` = ?,`name` = ?,`colorHex` = ?,`isSystem` = ?,`systemType` = ?,`createdDate` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: CardListEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.colorHex)
        val _tmp: Int = if (entity.isSystem) 1 else 0
        statement.bindLong(4, _tmp.toLong())
        val _tmpSystemType: String? = entity.systemType
        if (_tmpSystemType == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpSystemType)
        }
        statement.bindLong(6, entity.createdDate)
        statement.bindText(7, entity.id)
      }
    }
  }

  public override suspend fun insert(list: CardListEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfCardListEntity.insert(_connection, list)
  }

  public override suspend fun addCardToList(crossRef: CardListCrossRef): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfCardListCrossRef.insert(_connection, crossRef)
  }

  public override suspend fun delete(list: CardListEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfCardListEntity.handle(_connection, list)
  }

  public override suspend fun removeCardFromList(crossRef: CardListCrossRef): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfCardListCrossRef.handle(_connection, crossRef)
  }

  public override suspend fun update(list: CardListEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfCardListEntity.handle(_connection, list)
  }

  public override fun getAllLists(): Flow<List<CardListEntity>> {
    val _sql: String = "SELECT * FROM card_lists ORDER BY isSystem DESC, createdDate ASC"
    return createFlow(__db, false, arrayOf("card_lists")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfColorHex: Int = getColumnIndexOrThrow(_stmt, "colorHex")
        val _columnIndexOfIsSystem: Int = getColumnIndexOrThrow(_stmt, "isSystem")
        val _columnIndexOfSystemType: Int = getColumnIndexOrThrow(_stmt, "systemType")
        val _columnIndexOfCreatedDate: Int = getColumnIndexOrThrow(_stmt, "createdDate")
        val _result: MutableList<CardListEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CardListEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpColorHex: String
          _tmpColorHex = _stmt.getText(_columnIndexOfColorHex)
          val _tmpIsSystem: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsSystem).toInt()
          _tmpIsSystem = _tmp != 0
          val _tmpSystemType: String?
          if (_stmt.isNull(_columnIndexOfSystemType)) {
            _tmpSystemType = null
          } else {
            _tmpSystemType = _stmt.getText(_columnIndexOfSystemType)
          }
          val _tmpCreatedDate: Long
          _tmpCreatedDate = _stmt.getLong(_columnIndexOfCreatedDate)
          _item = CardListEntity(_tmpId,_tmpName,_tmpColorHex,_tmpIsSystem,_tmpSystemType,_tmpCreatedDate)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getListById(id: String): CardListEntity? {
    val _sql: String = "SELECT * FROM card_lists WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfColorHex: Int = getColumnIndexOrThrow(_stmt, "colorHex")
        val _columnIndexOfIsSystem: Int = getColumnIndexOrThrow(_stmt, "isSystem")
        val _columnIndexOfSystemType: Int = getColumnIndexOrThrow(_stmt, "systemType")
        val _columnIndexOfCreatedDate: Int = getColumnIndexOrThrow(_stmt, "createdDate")
        val _result: CardListEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpColorHex: String
          _tmpColorHex = _stmt.getText(_columnIndexOfColorHex)
          val _tmpIsSystem: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsSystem).toInt()
          _tmpIsSystem = _tmp != 0
          val _tmpSystemType: String?
          if (_stmt.isNull(_columnIndexOfSystemType)) {
            _tmpSystemType = null
          } else {
            _tmpSystemType = _stmt.getText(_columnIndexOfSystemType)
          }
          val _tmpCreatedDate: Long
          _tmpCreatedDate = _stmt.getLong(_columnIndexOfCreatedDate)
          _result = CardListEntity(_tmpId,_tmpName,_tmpColorHex,_tmpIsSystem,_tmpSystemType,_tmpCreatedDate)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getSystemList(systemType: String): CardListEntity? {
    val _sql: String = "SELECT * FROM card_lists WHERE systemType = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, systemType)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfColorHex: Int = getColumnIndexOrThrow(_stmt, "colorHex")
        val _columnIndexOfIsSystem: Int = getColumnIndexOrThrow(_stmt, "isSystem")
        val _columnIndexOfSystemType: Int = getColumnIndexOrThrow(_stmt, "systemType")
        val _columnIndexOfCreatedDate: Int = getColumnIndexOrThrow(_stmt, "createdDate")
        val _result: CardListEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpColorHex: String
          _tmpColorHex = _stmt.getText(_columnIndexOfColorHex)
          val _tmpIsSystem: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsSystem).toInt()
          _tmpIsSystem = _tmp != 0
          val _tmpSystemType: String?
          if (_stmt.isNull(_columnIndexOfSystemType)) {
            _tmpSystemType = null
          } else {
            _tmpSystemType = _stmt.getText(_columnIndexOfSystemType)
          }
          val _tmpCreatedDate: Long
          _tmpCreatedDate = _stmt.getLong(_columnIndexOfCreatedDate)
          _result = CardListEntity(_tmpId,_tmpName,_tmpColorHex,_tmpIsSystem,_tmpSystemType,_tmpCreatedDate)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getListWithCards(id: String): Flow<CardListWithCards?> {
    val _sql: String = "SELECT * FROM card_lists WHERE id = ?"
    return createFlow(__db, true, arrayOf("card_list_cross_ref", "cards", "card_lists")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfColorHex: Int = getColumnIndexOrThrow(_stmt, "colorHex")
        val _columnIndexOfIsSystem: Int = getColumnIndexOrThrow(_stmt, "isSystem")
        val _columnIndexOfSystemType: Int = getColumnIndexOrThrow(_stmt, "systemType")
        val _columnIndexOfCreatedDate: Int = getColumnIndexOrThrow(_stmt, "createdDate")
        val _collectionCards: ArrayMap<String, MutableList<CardEntity>> = ArrayMap<String, MutableList<CardEntity>>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfId)
          if (!_collectionCards.containsKey(_tmpKey)) {
            _collectionCards.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipcardsAscomScanriftAndroidDataLocalEntityCardEntity(_connection, _collectionCards)
        val _result: CardListWithCards?
        if (_stmt.step()) {
          val _tmpList: CardListEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpColorHex: String
          _tmpColorHex = _stmt.getText(_columnIndexOfColorHex)
          val _tmpIsSystem: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsSystem).toInt()
          _tmpIsSystem = _tmp != 0
          val _tmpSystemType: String?
          if (_stmt.isNull(_columnIndexOfSystemType)) {
            _tmpSystemType = null
          } else {
            _tmpSystemType = _stmt.getText(_columnIndexOfSystemType)
          }
          val _tmpCreatedDate: Long
          _tmpCreatedDate = _stmt.getLong(_columnIndexOfCreatedDate)
          _tmpList = CardListEntity(_tmpId,_tmpName,_tmpColorHex,_tmpIsSystem,_tmpSystemType,_tmpCreatedDate)
          val _tmpCardsCollection: MutableList<CardEntity>
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfId)
          _tmpCardsCollection = _collectionCards.getValue(_tmpKey_1)
          _result = CardListWithCards(_tmpList,_tmpCardsCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllListsWithCards(): Flow<List<CardListWithCards>> {
    val _sql: String = "SELECT * FROM card_lists ORDER BY isSystem DESC, createdDate ASC"
    return createFlow(__db, true, arrayOf("card_list_cross_ref", "cards", "card_lists")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfColorHex: Int = getColumnIndexOrThrow(_stmt, "colorHex")
        val _columnIndexOfIsSystem: Int = getColumnIndexOrThrow(_stmt, "isSystem")
        val _columnIndexOfSystemType: Int = getColumnIndexOrThrow(_stmt, "systemType")
        val _columnIndexOfCreatedDate: Int = getColumnIndexOrThrow(_stmt, "createdDate")
        val _collectionCards: ArrayMap<String, MutableList<CardEntity>> = ArrayMap<String, MutableList<CardEntity>>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfId)
          if (!_collectionCards.containsKey(_tmpKey)) {
            _collectionCards.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipcardsAscomScanriftAndroidDataLocalEntityCardEntity(_connection, _collectionCards)
        val _result: MutableList<CardListWithCards> = mutableListOf()
        while (_stmt.step()) {
          val _item: CardListWithCards
          val _tmpList: CardListEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpColorHex: String
          _tmpColorHex = _stmt.getText(_columnIndexOfColorHex)
          val _tmpIsSystem: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsSystem).toInt()
          _tmpIsSystem = _tmp != 0
          val _tmpSystemType: String?
          if (_stmt.isNull(_columnIndexOfSystemType)) {
            _tmpSystemType = null
          } else {
            _tmpSystemType = _stmt.getText(_columnIndexOfSystemType)
          }
          val _tmpCreatedDate: Long
          _tmpCreatedDate = _stmt.getLong(_columnIndexOfCreatedDate)
          _tmpList = CardListEntity(_tmpId,_tmpName,_tmpColorHex,_tmpIsSystem,_tmpSystemType,_tmpCreatedDate)
          val _tmpCardsCollection: MutableList<CardEntity>
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfId)
          _tmpCardsCollection = _collectionCards.getValue(_tmpKey_1)
          _item = CardListWithCards(_tmpList,_tmpCardsCollection)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getListIdsForCard(cardId: String): List<String> {
    val _sql: String = "SELECT listId FROM card_list_cross_ref WHERE cardId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, cardId)
        val _result: MutableList<String> = mutableListOf()
        while (_stmt.step()) {
          val _item: String
          _item = _stmt.getText(0)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getCardCountForList(listId: String): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM card_list_cross_ref WHERE listId = ?"
    return createFlow(__db, false, arrayOf("card_list_cross_ref")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, listId)
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

  public override suspend fun existsWithName(name: String, excludeId: String): Boolean {
    val _sql: String = "SELECT EXISTS(SELECT 1 FROM card_lists WHERE name = ? AND id != ?)"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, name)
        _argIndex = 2
        _stmt.bindText(_argIndex, excludeId)
        val _result: Boolean
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp != 0
        } else {
          _result = false
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  private fun __fetchRelationshipcardsAscomScanriftAndroidDataLocalEntityCardEntity(_connection: SQLiteConnection, _map: ArrayMap<String, MutableList<CardEntity>>) {
    val __mapKeySet: Set<String> = _map.keys
    if (__mapKeySet.isEmpty()) {
      return
    }
    if (_map.size > 999) {
      recursiveFetchArrayMap(_map, true) { _tmpMap ->
        __fetchRelationshipcardsAscomScanriftAndroidDataLocalEntityCardEntity(_connection, _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `cards`.`id` AS `id`,`cards`.`name` AS `name`,`cards`.`riftboundId` AS `riftboundId`,`cards`.`publicCode` AS `publicCode`,`cards`.`collectorNumber` AS `collectorNumber`,`cards`.`energy` AS `energy`,`cards`.`might` AS `might`,`cards`.`power` AS `power`,`cards`.`type` AS `type`,`cards`.`supertype` AS `supertype`,`cards`.`rarity` AS `rarity`,`cards`.`domains` AS `domains`,`cards`.`richText` AS `richText`,`cards`.`plainText` AS `plainText`,`cards`.`setId` AS `setId`,`cards`.`setLabel` AS `setLabel`,`cards`.`imageUrl` AS `imageUrl`,`cards`.`artist` AS `artist`,`cards`.`accessibilityText` AS `accessibilityText`,`cards`.`cleanName` AS `cleanName`,`cards`.`alternateArt` AS `alternateArt`,`cards`.`overnumbered` AS `overnumbered`,`cards`.`signature` AS `signature`,`cards`.`orientation` AS `orientation`,`cards`.`tags` AS `tags`,_junction.`listId` FROM `card_list_cross_ref` AS _junction INNER JOIN `cards` ON (_junction.`cardId` = `cards`.`id`) WHERE _junction.`listId` IN (")
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
      // _junction.listId
      val _itemKeyIndex: Int = 25
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
        val _tmpRelation: MutableList<CardEntity>? = _map.get(_tmpKey)
        if (_tmpRelation != null) {
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
          _tmpRelation.add(_item_1)
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
