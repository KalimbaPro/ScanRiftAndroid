package com.scanrift.android.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.scanrift.android.`data`.local.converter.Converters
import com.scanrift.android.`data`.local.entity.CardEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class CardDao_Impl(
  __db: RoomDatabase,
) : CardDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfCardEntity: EntityInsertAdapter<CardEntity>

  private val __converters: Converters = Converters()

  private val __updateAdapterOfCardEntity: EntityDeleteOrUpdateAdapter<CardEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfCardEntity = object : EntityInsertAdapter<CardEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `cards` (`id`,`name`,`riftboundId`,`publicCode`,`collectorNumber`,`energy`,`might`,`power`,`type`,`supertype`,`rarity`,`domains`,`richText`,`plainText`,`setId`,`setLabel`,`imageUrl`,`artist`,`accessibilityText`,`cleanName`,`alternateArt`,`overnumbered`,`signature`,`orientation`,`tags`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CardEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.riftboundId)
        statement.bindText(4, entity.publicCode)
        statement.bindLong(5, entity.collectorNumber.toLong())
        val _tmpEnergy: Int? = entity.energy
        if (_tmpEnergy == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpEnergy.toLong())
        }
        val _tmpMight: Int? = entity.might
        if (_tmpMight == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpMight.toLong())
        }
        val _tmpPower: Int? = entity.power
        if (_tmpPower == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpPower.toLong())
        }
        statement.bindText(9, entity.type)
        val _tmpSupertype: String? = entity.supertype
        if (_tmpSupertype == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpSupertype)
        }
        statement.bindText(11, entity.rarity)
        val _tmp: String = __converters.fromStringList(entity.domains)
        statement.bindText(12, _tmp)
        val _tmpRichText: String? = entity.richText
        if (_tmpRichText == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpRichText)
        }
        val _tmpPlainText: String? = entity.plainText
        if (_tmpPlainText == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpPlainText)
        }
        statement.bindText(15, entity.setId)
        statement.bindText(16, entity.setLabel)
        val _tmpImageUrl: String? = entity.imageUrl
        if (_tmpImageUrl == null) {
          statement.bindNull(17)
        } else {
          statement.bindText(17, _tmpImageUrl)
        }
        val _tmpArtist: String? = entity.artist
        if (_tmpArtist == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpArtist)
        }
        val _tmpAccessibilityText: String? = entity.accessibilityText
        if (_tmpAccessibilityText == null) {
          statement.bindNull(19)
        } else {
          statement.bindText(19, _tmpAccessibilityText)
        }
        statement.bindText(20, entity.cleanName)
        val _tmp_1: Int = if (entity.alternateArt) 1 else 0
        statement.bindLong(21, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.overnumbered) 1 else 0
        statement.bindLong(22, _tmp_2.toLong())
        val _tmp_3: Int = if (entity.signature) 1 else 0
        statement.bindLong(23, _tmp_3.toLong())
        statement.bindText(24, entity.orientation)
        val _tmp_4: String = __converters.fromStringList(entity.tags)
        statement.bindText(25, _tmp_4)
      }
    }
    this.__updateAdapterOfCardEntity = object : EntityDeleteOrUpdateAdapter<CardEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `cards` SET `id` = ?,`name` = ?,`riftboundId` = ?,`publicCode` = ?,`collectorNumber` = ?,`energy` = ?,`might` = ?,`power` = ?,`type` = ?,`supertype` = ?,`rarity` = ?,`domains` = ?,`richText` = ?,`plainText` = ?,`setId` = ?,`setLabel` = ?,`imageUrl` = ?,`artist` = ?,`accessibilityText` = ?,`cleanName` = ?,`alternateArt` = ?,`overnumbered` = ?,`signature` = ?,`orientation` = ?,`tags` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: CardEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.riftboundId)
        statement.bindText(4, entity.publicCode)
        statement.bindLong(5, entity.collectorNumber.toLong())
        val _tmpEnergy: Int? = entity.energy
        if (_tmpEnergy == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpEnergy.toLong())
        }
        val _tmpMight: Int? = entity.might
        if (_tmpMight == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpMight.toLong())
        }
        val _tmpPower: Int? = entity.power
        if (_tmpPower == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpPower.toLong())
        }
        statement.bindText(9, entity.type)
        val _tmpSupertype: String? = entity.supertype
        if (_tmpSupertype == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpSupertype)
        }
        statement.bindText(11, entity.rarity)
        val _tmp: String = __converters.fromStringList(entity.domains)
        statement.bindText(12, _tmp)
        val _tmpRichText: String? = entity.richText
        if (_tmpRichText == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpRichText)
        }
        val _tmpPlainText: String? = entity.plainText
        if (_tmpPlainText == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpPlainText)
        }
        statement.bindText(15, entity.setId)
        statement.bindText(16, entity.setLabel)
        val _tmpImageUrl: String? = entity.imageUrl
        if (_tmpImageUrl == null) {
          statement.bindNull(17)
        } else {
          statement.bindText(17, _tmpImageUrl)
        }
        val _tmpArtist: String? = entity.artist
        if (_tmpArtist == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpArtist)
        }
        val _tmpAccessibilityText: String? = entity.accessibilityText
        if (_tmpAccessibilityText == null) {
          statement.bindNull(19)
        } else {
          statement.bindText(19, _tmpAccessibilityText)
        }
        statement.bindText(20, entity.cleanName)
        val _tmp_1: Int = if (entity.alternateArt) 1 else 0
        statement.bindLong(21, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.overnumbered) 1 else 0
        statement.bindLong(22, _tmp_2.toLong())
        val _tmp_3: Int = if (entity.signature) 1 else 0
        statement.bindLong(23, _tmp_3.toLong())
        statement.bindText(24, entity.orientation)
        val _tmp_4: String = __converters.fromStringList(entity.tags)
        statement.bindText(25, _tmp_4)
        statement.bindText(26, entity.id)
      }
    }
  }

  public override suspend fun insertAll(cards: List<CardEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfCardEntity.insert(_connection, cards)
  }

  public override suspend fun insert(card: CardEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfCardEntity.insert(_connection, card)
  }

  public override suspend fun update(card: CardEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfCardEntity.handle(_connection, card)
  }

  public override fun getAllCards(): Flow<List<CardEntity>> {
    val _sql: String = "SELECT * FROM cards ORDER BY name ASC"
    return createFlow(__db, false, arrayOf("cards")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfRiftboundId: Int = getColumnIndexOrThrow(_stmt, "riftboundId")
        val _columnIndexOfPublicCode: Int = getColumnIndexOrThrow(_stmt, "publicCode")
        val _columnIndexOfCollectorNumber: Int = getColumnIndexOrThrow(_stmt, "collectorNumber")
        val _columnIndexOfEnergy: Int = getColumnIndexOrThrow(_stmt, "energy")
        val _columnIndexOfMight: Int = getColumnIndexOrThrow(_stmt, "might")
        val _columnIndexOfPower: Int = getColumnIndexOrThrow(_stmt, "power")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfSupertype: Int = getColumnIndexOrThrow(_stmt, "supertype")
        val _columnIndexOfRarity: Int = getColumnIndexOrThrow(_stmt, "rarity")
        val _columnIndexOfDomains: Int = getColumnIndexOrThrow(_stmt, "domains")
        val _columnIndexOfRichText: Int = getColumnIndexOrThrow(_stmt, "richText")
        val _columnIndexOfPlainText: Int = getColumnIndexOrThrow(_stmt, "plainText")
        val _columnIndexOfSetId: Int = getColumnIndexOrThrow(_stmt, "setId")
        val _columnIndexOfSetLabel: Int = getColumnIndexOrThrow(_stmt, "setLabel")
        val _columnIndexOfImageUrl: Int = getColumnIndexOrThrow(_stmt, "imageUrl")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfAccessibilityText: Int = getColumnIndexOrThrow(_stmt, "accessibilityText")
        val _columnIndexOfCleanName: Int = getColumnIndexOrThrow(_stmt, "cleanName")
        val _columnIndexOfAlternateArt: Int = getColumnIndexOrThrow(_stmt, "alternateArt")
        val _columnIndexOfOvernumbered: Int = getColumnIndexOrThrow(_stmt, "overnumbered")
        val _columnIndexOfSignature: Int = getColumnIndexOrThrow(_stmt, "signature")
        val _columnIndexOfOrientation: Int = getColumnIndexOrThrow(_stmt, "orientation")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: MutableList<CardEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CardEntity
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
          _item = CardEntity(_tmpId,_tmpName,_tmpRiftboundId,_tmpPublicCode,_tmpCollectorNumber,_tmpEnergy,_tmpMight,_tmpPower,_tmpType,_tmpSupertype,_tmpRarity,_tmpDomains,_tmpRichText,_tmpPlainText,_tmpSetId,_tmpSetLabel,_tmpImageUrl,_tmpArtist,_tmpAccessibilityText,_tmpCleanName,_tmpAlternateArt,_tmpOvernumbered,_tmpSignature,_tmpOrientation,_tmpTags)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllCardsList(): List<CardEntity> {
    val _sql: String = "SELECT * FROM cards ORDER BY name ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfRiftboundId: Int = getColumnIndexOrThrow(_stmt, "riftboundId")
        val _columnIndexOfPublicCode: Int = getColumnIndexOrThrow(_stmt, "publicCode")
        val _columnIndexOfCollectorNumber: Int = getColumnIndexOrThrow(_stmt, "collectorNumber")
        val _columnIndexOfEnergy: Int = getColumnIndexOrThrow(_stmt, "energy")
        val _columnIndexOfMight: Int = getColumnIndexOrThrow(_stmt, "might")
        val _columnIndexOfPower: Int = getColumnIndexOrThrow(_stmt, "power")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfSupertype: Int = getColumnIndexOrThrow(_stmt, "supertype")
        val _columnIndexOfRarity: Int = getColumnIndexOrThrow(_stmt, "rarity")
        val _columnIndexOfDomains: Int = getColumnIndexOrThrow(_stmt, "domains")
        val _columnIndexOfRichText: Int = getColumnIndexOrThrow(_stmt, "richText")
        val _columnIndexOfPlainText: Int = getColumnIndexOrThrow(_stmt, "plainText")
        val _columnIndexOfSetId: Int = getColumnIndexOrThrow(_stmt, "setId")
        val _columnIndexOfSetLabel: Int = getColumnIndexOrThrow(_stmt, "setLabel")
        val _columnIndexOfImageUrl: Int = getColumnIndexOrThrow(_stmt, "imageUrl")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfAccessibilityText: Int = getColumnIndexOrThrow(_stmt, "accessibilityText")
        val _columnIndexOfCleanName: Int = getColumnIndexOrThrow(_stmt, "cleanName")
        val _columnIndexOfAlternateArt: Int = getColumnIndexOrThrow(_stmt, "alternateArt")
        val _columnIndexOfOvernumbered: Int = getColumnIndexOrThrow(_stmt, "overnumbered")
        val _columnIndexOfSignature: Int = getColumnIndexOrThrow(_stmt, "signature")
        val _columnIndexOfOrientation: Int = getColumnIndexOrThrow(_stmt, "orientation")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: MutableList<CardEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CardEntity
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
          _item = CardEntity(_tmpId,_tmpName,_tmpRiftboundId,_tmpPublicCode,_tmpCollectorNumber,_tmpEnergy,_tmpMight,_tmpPower,_tmpType,_tmpSupertype,_tmpRarity,_tmpDomains,_tmpRichText,_tmpPlainText,_tmpSetId,_tmpSetLabel,_tmpImageUrl,_tmpArtist,_tmpAccessibilityText,_tmpCleanName,_tmpAlternateArt,_tmpOvernumbered,_tmpSignature,_tmpOrientation,_tmpTags)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getCardById(id: String): CardEntity? {
    val _sql: String = "SELECT * FROM cards WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfRiftboundId: Int = getColumnIndexOrThrow(_stmt, "riftboundId")
        val _columnIndexOfPublicCode: Int = getColumnIndexOrThrow(_stmt, "publicCode")
        val _columnIndexOfCollectorNumber: Int = getColumnIndexOrThrow(_stmt, "collectorNumber")
        val _columnIndexOfEnergy: Int = getColumnIndexOrThrow(_stmt, "energy")
        val _columnIndexOfMight: Int = getColumnIndexOrThrow(_stmt, "might")
        val _columnIndexOfPower: Int = getColumnIndexOrThrow(_stmt, "power")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfSupertype: Int = getColumnIndexOrThrow(_stmt, "supertype")
        val _columnIndexOfRarity: Int = getColumnIndexOrThrow(_stmt, "rarity")
        val _columnIndexOfDomains: Int = getColumnIndexOrThrow(_stmt, "domains")
        val _columnIndexOfRichText: Int = getColumnIndexOrThrow(_stmt, "richText")
        val _columnIndexOfPlainText: Int = getColumnIndexOrThrow(_stmt, "plainText")
        val _columnIndexOfSetId: Int = getColumnIndexOrThrow(_stmt, "setId")
        val _columnIndexOfSetLabel: Int = getColumnIndexOrThrow(_stmt, "setLabel")
        val _columnIndexOfImageUrl: Int = getColumnIndexOrThrow(_stmt, "imageUrl")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfAccessibilityText: Int = getColumnIndexOrThrow(_stmt, "accessibilityText")
        val _columnIndexOfCleanName: Int = getColumnIndexOrThrow(_stmt, "cleanName")
        val _columnIndexOfAlternateArt: Int = getColumnIndexOrThrow(_stmt, "alternateArt")
        val _columnIndexOfOvernumbered: Int = getColumnIndexOrThrow(_stmt, "overnumbered")
        val _columnIndexOfSignature: Int = getColumnIndexOrThrow(_stmt, "signature")
        val _columnIndexOfOrientation: Int = getColumnIndexOrThrow(_stmt, "orientation")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: CardEntity?
        if (_stmt.step()) {
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
          _result = CardEntity(_tmpId,_tmpName,_tmpRiftboundId,_tmpPublicCode,_tmpCollectorNumber,_tmpEnergy,_tmpMight,_tmpPower,_tmpType,_tmpSupertype,_tmpRarity,_tmpDomains,_tmpRichText,_tmpPlainText,_tmpSetId,_tmpSetLabel,_tmpImageUrl,_tmpArtist,_tmpAccessibilityText,_tmpCleanName,_tmpAlternateArt,_tmpOvernumbered,_tmpSignature,_tmpOrientation,_tmpTags)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getCardByIdFlow(id: String): Flow<CardEntity?> {
    val _sql: String = "SELECT * FROM cards WHERE id = ?"
    return createFlow(__db, false, arrayOf("cards")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfRiftboundId: Int = getColumnIndexOrThrow(_stmt, "riftboundId")
        val _columnIndexOfPublicCode: Int = getColumnIndexOrThrow(_stmt, "publicCode")
        val _columnIndexOfCollectorNumber: Int = getColumnIndexOrThrow(_stmt, "collectorNumber")
        val _columnIndexOfEnergy: Int = getColumnIndexOrThrow(_stmt, "energy")
        val _columnIndexOfMight: Int = getColumnIndexOrThrow(_stmt, "might")
        val _columnIndexOfPower: Int = getColumnIndexOrThrow(_stmt, "power")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfSupertype: Int = getColumnIndexOrThrow(_stmt, "supertype")
        val _columnIndexOfRarity: Int = getColumnIndexOrThrow(_stmt, "rarity")
        val _columnIndexOfDomains: Int = getColumnIndexOrThrow(_stmt, "domains")
        val _columnIndexOfRichText: Int = getColumnIndexOrThrow(_stmt, "richText")
        val _columnIndexOfPlainText: Int = getColumnIndexOrThrow(_stmt, "plainText")
        val _columnIndexOfSetId: Int = getColumnIndexOrThrow(_stmt, "setId")
        val _columnIndexOfSetLabel: Int = getColumnIndexOrThrow(_stmt, "setLabel")
        val _columnIndexOfImageUrl: Int = getColumnIndexOrThrow(_stmt, "imageUrl")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfAccessibilityText: Int = getColumnIndexOrThrow(_stmt, "accessibilityText")
        val _columnIndexOfCleanName: Int = getColumnIndexOrThrow(_stmt, "cleanName")
        val _columnIndexOfAlternateArt: Int = getColumnIndexOrThrow(_stmt, "alternateArt")
        val _columnIndexOfOvernumbered: Int = getColumnIndexOrThrow(_stmt, "overnumbered")
        val _columnIndexOfSignature: Int = getColumnIndexOrThrow(_stmt, "signature")
        val _columnIndexOfOrientation: Int = getColumnIndexOrThrow(_stmt, "orientation")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: CardEntity?
        if (_stmt.step()) {
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
          _result = CardEntity(_tmpId,_tmpName,_tmpRiftboundId,_tmpPublicCode,_tmpCollectorNumber,_tmpEnergy,_tmpMight,_tmpPower,_tmpType,_tmpSupertype,_tmpRarity,_tmpDomains,_tmpRichText,_tmpPlainText,_tmpSetId,_tmpSetLabel,_tmpImageUrl,_tmpArtist,_tmpAccessibilityText,_tmpCleanName,_tmpAlternateArt,_tmpOvernumbered,_tmpSignature,_tmpOrientation,_tmpTags)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun searchCards(query: String): Flow<List<CardEntity>> {
    val _sql: String = "SELECT * FROM cards WHERE name LIKE '%' || ? || '%' OR publicCode LIKE '%' || ? || '%' ORDER BY name ASC"
    return createFlow(__db, false, arrayOf("cards")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        _argIndex = 2
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfRiftboundId: Int = getColumnIndexOrThrow(_stmt, "riftboundId")
        val _columnIndexOfPublicCode: Int = getColumnIndexOrThrow(_stmt, "publicCode")
        val _columnIndexOfCollectorNumber: Int = getColumnIndexOrThrow(_stmt, "collectorNumber")
        val _columnIndexOfEnergy: Int = getColumnIndexOrThrow(_stmt, "energy")
        val _columnIndexOfMight: Int = getColumnIndexOrThrow(_stmt, "might")
        val _columnIndexOfPower: Int = getColumnIndexOrThrow(_stmt, "power")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfSupertype: Int = getColumnIndexOrThrow(_stmt, "supertype")
        val _columnIndexOfRarity: Int = getColumnIndexOrThrow(_stmt, "rarity")
        val _columnIndexOfDomains: Int = getColumnIndexOrThrow(_stmt, "domains")
        val _columnIndexOfRichText: Int = getColumnIndexOrThrow(_stmt, "richText")
        val _columnIndexOfPlainText: Int = getColumnIndexOrThrow(_stmt, "plainText")
        val _columnIndexOfSetId: Int = getColumnIndexOrThrow(_stmt, "setId")
        val _columnIndexOfSetLabel: Int = getColumnIndexOrThrow(_stmt, "setLabel")
        val _columnIndexOfImageUrl: Int = getColumnIndexOrThrow(_stmt, "imageUrl")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfAccessibilityText: Int = getColumnIndexOrThrow(_stmt, "accessibilityText")
        val _columnIndexOfCleanName: Int = getColumnIndexOrThrow(_stmt, "cleanName")
        val _columnIndexOfAlternateArt: Int = getColumnIndexOrThrow(_stmt, "alternateArt")
        val _columnIndexOfOvernumbered: Int = getColumnIndexOrThrow(_stmt, "overnumbered")
        val _columnIndexOfSignature: Int = getColumnIndexOrThrow(_stmt, "signature")
        val _columnIndexOfOrientation: Int = getColumnIndexOrThrow(_stmt, "orientation")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: MutableList<CardEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CardEntity
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
          _item = CardEntity(_tmpId,_tmpName,_tmpRiftboundId,_tmpPublicCode,_tmpCollectorNumber,_tmpEnergy,_tmpMight,_tmpPower,_tmpType,_tmpSupertype,_tmpRarity,_tmpDomains,_tmpRichText,_tmpPlainText,_tmpSetId,_tmpSetLabel,_tmpImageUrl,_tmpArtist,_tmpAccessibilityText,_tmpCleanName,_tmpAlternateArt,_tmpOvernumbered,_tmpSignature,_tmpOrientation,_tmpTags)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun findBySetAndNumber(setId: String, collectorNumber: Int): CardEntity? {
    val _sql: String = "SELECT * FROM cards WHERE setId = ? AND collectorNumber = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, setId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, collectorNumber.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfRiftboundId: Int = getColumnIndexOrThrow(_stmt, "riftboundId")
        val _columnIndexOfPublicCode: Int = getColumnIndexOrThrow(_stmt, "publicCode")
        val _columnIndexOfCollectorNumber: Int = getColumnIndexOrThrow(_stmt, "collectorNumber")
        val _columnIndexOfEnergy: Int = getColumnIndexOrThrow(_stmt, "energy")
        val _columnIndexOfMight: Int = getColumnIndexOrThrow(_stmt, "might")
        val _columnIndexOfPower: Int = getColumnIndexOrThrow(_stmt, "power")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfSupertype: Int = getColumnIndexOrThrow(_stmt, "supertype")
        val _columnIndexOfRarity: Int = getColumnIndexOrThrow(_stmt, "rarity")
        val _columnIndexOfDomains: Int = getColumnIndexOrThrow(_stmt, "domains")
        val _columnIndexOfRichText: Int = getColumnIndexOrThrow(_stmt, "richText")
        val _columnIndexOfPlainText: Int = getColumnIndexOrThrow(_stmt, "plainText")
        val _columnIndexOfSetId: Int = getColumnIndexOrThrow(_stmt, "setId")
        val _columnIndexOfSetLabel: Int = getColumnIndexOrThrow(_stmt, "setLabel")
        val _columnIndexOfImageUrl: Int = getColumnIndexOrThrow(_stmt, "imageUrl")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfAccessibilityText: Int = getColumnIndexOrThrow(_stmt, "accessibilityText")
        val _columnIndexOfCleanName: Int = getColumnIndexOrThrow(_stmt, "cleanName")
        val _columnIndexOfAlternateArt: Int = getColumnIndexOrThrow(_stmt, "alternateArt")
        val _columnIndexOfOvernumbered: Int = getColumnIndexOrThrow(_stmt, "overnumbered")
        val _columnIndexOfSignature: Int = getColumnIndexOrThrow(_stmt, "signature")
        val _columnIndexOfOrientation: Int = getColumnIndexOrThrow(_stmt, "orientation")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: CardEntity?
        if (_stmt.step()) {
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
          _result = CardEntity(_tmpId,_tmpName,_tmpRiftboundId,_tmpPublicCode,_tmpCollectorNumber,_tmpEnergy,_tmpMight,_tmpPower,_tmpType,_tmpSupertype,_tmpRarity,_tmpDomains,_tmpRichText,_tmpPlainText,_tmpSetId,_tmpSetLabel,_tmpImageUrl,_tmpArtist,_tmpAccessibilityText,_tmpCleanName,_tmpAlternateArt,_tmpOvernumbered,_tmpSignature,_tmpOrientation,_tmpTags)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun findByPublicCode(publicCode: String): CardEntity? {
    val _sql: String = "SELECT * FROM cards WHERE publicCode = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, publicCode)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfRiftboundId: Int = getColumnIndexOrThrow(_stmt, "riftboundId")
        val _columnIndexOfPublicCode: Int = getColumnIndexOrThrow(_stmt, "publicCode")
        val _columnIndexOfCollectorNumber: Int = getColumnIndexOrThrow(_stmt, "collectorNumber")
        val _columnIndexOfEnergy: Int = getColumnIndexOrThrow(_stmt, "energy")
        val _columnIndexOfMight: Int = getColumnIndexOrThrow(_stmt, "might")
        val _columnIndexOfPower: Int = getColumnIndexOrThrow(_stmt, "power")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfSupertype: Int = getColumnIndexOrThrow(_stmt, "supertype")
        val _columnIndexOfRarity: Int = getColumnIndexOrThrow(_stmt, "rarity")
        val _columnIndexOfDomains: Int = getColumnIndexOrThrow(_stmt, "domains")
        val _columnIndexOfRichText: Int = getColumnIndexOrThrow(_stmt, "richText")
        val _columnIndexOfPlainText: Int = getColumnIndexOrThrow(_stmt, "plainText")
        val _columnIndexOfSetId: Int = getColumnIndexOrThrow(_stmt, "setId")
        val _columnIndexOfSetLabel: Int = getColumnIndexOrThrow(_stmt, "setLabel")
        val _columnIndexOfImageUrl: Int = getColumnIndexOrThrow(_stmt, "imageUrl")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfAccessibilityText: Int = getColumnIndexOrThrow(_stmt, "accessibilityText")
        val _columnIndexOfCleanName: Int = getColumnIndexOrThrow(_stmt, "cleanName")
        val _columnIndexOfAlternateArt: Int = getColumnIndexOrThrow(_stmt, "alternateArt")
        val _columnIndexOfOvernumbered: Int = getColumnIndexOrThrow(_stmt, "overnumbered")
        val _columnIndexOfSignature: Int = getColumnIndexOrThrow(_stmt, "signature")
        val _columnIndexOfOrientation: Int = getColumnIndexOrThrow(_stmt, "orientation")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: CardEntity?
        if (_stmt.step()) {
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
          _result = CardEntity(_tmpId,_tmpName,_tmpRiftboundId,_tmpPublicCode,_tmpCollectorNumber,_tmpEnergy,_tmpMight,_tmpPower,_tmpType,_tmpSupertype,_tmpRarity,_tmpDomains,_tmpRichText,_tmpPlainText,_tmpSetId,_tmpSetLabel,_tmpImageUrl,_tmpArtist,_tmpAccessibilityText,_tmpCleanName,_tmpAlternateArt,_tmpOvernumbered,_tmpSignature,_tmpOrientation,_tmpTags)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getCardCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM cards"
    return createFlow(__db, false, arrayOf("cards")) { _connection ->
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

  public override suspend fun getCardCountValue(): Int {
    val _sql: String = "SELECT COUNT(*) FROM cards"
    return performSuspending(__db, true, false) { _connection ->
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

  public override suspend fun getAllSetIds(): List<String> {
    val _sql: String = "SELECT DISTINCT setId FROM cards ORDER BY setId ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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

  public override suspend fun getAllSetLabels(): List<String> {
    val _sql: String = "SELECT DISTINCT setLabel FROM cards ORDER BY setLabel ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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

  public override suspend fun getAllTypes(): List<String> {
    val _sql: String = "SELECT DISTINCT type FROM cards ORDER BY type ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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

  public override suspend fun getAllRarities(): List<String> {
    val _sql: String = "SELECT DISTINCT rarity FROM cards ORDER BY rarity ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM cards"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
