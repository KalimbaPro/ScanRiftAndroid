package com.scanrift.android.`data`.local

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.scanrift.android.`data`.local.dao.CardDao
import com.scanrift.android.`data`.local.dao.CardDao_Impl
import com.scanrift.android.`data`.local.dao.CardListDao
import com.scanrift.android.`data`.local.dao.CardListDao_Impl
import com.scanrift.android.`data`.local.dao.CollectionEntryDao
import com.scanrift.android.`data`.local.dao.CollectionEntryDao_Impl
import com.scanrift.android.`data`.local.dao.DeckDao
import com.scanrift.android.`data`.local.dao.DeckDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ScanRiftDatabase_Impl : ScanRiftDatabase() {
  private val _cardDao: Lazy<CardDao> = lazy {
    CardDao_Impl(this)
  }

  private val _collectionEntryDao: Lazy<CollectionEntryDao> = lazy {
    CollectionEntryDao_Impl(this)
  }

  private val _cardListDao: Lazy<CardListDao> = lazy {
    CardListDao_Impl(this)
  }

  private val _deckDao: Lazy<DeckDao> = lazy {
    DeckDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "fca73a0c1a39124a4901a04062d0e7e4", "73586ddb92fde71aac3f55358cbaee22") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cards` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `riftboundId` TEXT NOT NULL, `publicCode` TEXT NOT NULL, `collectorNumber` INTEGER NOT NULL, `energy` INTEGER, `might` INTEGER, `power` INTEGER, `type` TEXT NOT NULL, `supertype` TEXT, `rarity` TEXT NOT NULL, `domains` TEXT NOT NULL, `richText` TEXT, `plainText` TEXT, `setId` TEXT NOT NULL, `setLabel` TEXT NOT NULL, `imageUrl` TEXT, `artist` TEXT, `accessibilityText` TEXT, `cleanName` TEXT NOT NULL, `alternateArt` INTEGER NOT NULL, `overnumbered` INTEGER NOT NULL, `signature` INTEGER NOT NULL, `orientation` TEXT NOT NULL, `tags` TEXT NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `collection_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cardId` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `isFoil` INTEGER NOT NULL, `dateAdded` INTEGER NOT NULL, `condition` TEXT NOT NULL, `notes` TEXT, `folder` TEXT, FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_entries_cardId` ON `collection_entries` (`cardId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `card_lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `isSystem` INTEGER NOT NULL, `systemType` TEXT, `createdDate` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `card_list_cross_ref` (`listId` TEXT NOT NULL, `cardId` TEXT NOT NULL, PRIMARY KEY(`listId`, `cardId`), FOREIGN KEY(`listId`) REFERENCES `card_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_card_list_cross_ref_cardId` ON `card_list_cross_ref` (`cardId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `decks` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdDate` INTEGER NOT NULL, `lastModifiedDate` INTEGER NOT NULL, `legendCardId` TEXT, `championCardId` TEXT, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `deck_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deckId` TEXT NOT NULL, `cardId` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `section` TEXT NOT NULL, FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_deck_entries_deckId` ON `deck_entries` (`deckId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_deck_entries_cardId` ON `deck_entries` (`cardId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'fca73a0c1a39124a4901a04062d0e7e4')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `cards`")
        connection.execSQL("DROP TABLE IF EXISTS `collection_entries`")
        connection.execSQL("DROP TABLE IF EXISTS `card_lists`")
        connection.execSQL("DROP TABLE IF EXISTS `card_list_cross_ref`")
        connection.execSQL("DROP TABLE IF EXISTS `decks`")
        connection.execSQL("DROP TABLE IF EXISTS `deck_entries`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsCards: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCards.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("riftboundId", TableInfo.Column("riftboundId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("publicCode", TableInfo.Column("publicCode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("collectorNumber", TableInfo.Column("collectorNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("energy", TableInfo.Column("energy", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("might", TableInfo.Column("might", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("power", TableInfo.Column("power", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("supertype", TableInfo.Column("supertype", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("rarity", TableInfo.Column("rarity", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("domains", TableInfo.Column("domains", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("richText", TableInfo.Column("richText", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("plainText", TableInfo.Column("plainText", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("setId", TableInfo.Column("setId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("setLabel", TableInfo.Column("setLabel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("imageUrl", TableInfo.Column("imageUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("artist", TableInfo.Column("artist", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("accessibilityText", TableInfo.Column("accessibilityText", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("cleanName", TableInfo.Column("cleanName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("alternateArt", TableInfo.Column("alternateArt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("overnumbered", TableInfo.Column("overnumbered", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("signature", TableInfo.Column("signature", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("orientation", TableInfo.Column("orientation", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCards.put("tags", TableInfo.Column("tags", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCards: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCards: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCards: TableInfo = TableInfo("cards", _columnsCards, _foreignKeysCards, _indicesCards)
        val _existingCards: TableInfo = read(connection, "cards")
        if (!_infoCards.equals(_existingCards)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |cards(com.scanrift.android.data.local.entity.CardEntity).
              | Expected:
              |""".trimMargin() + _infoCards + """
              |
              | Found:
              |""".trimMargin() + _existingCards)
        }
        val _columnsCollectionEntries: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCollectionEntries.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCollectionEntries.put("cardId", TableInfo.Column("cardId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCollectionEntries.put("quantity", TableInfo.Column("quantity", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCollectionEntries.put("isFoil", TableInfo.Column("isFoil", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCollectionEntries.put("dateAdded", TableInfo.Column("dateAdded", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCollectionEntries.put("condition", TableInfo.Column("condition", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCollectionEntries.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCollectionEntries.put("folder", TableInfo.Column("folder", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCollectionEntries: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysCollectionEntries.add(TableInfo.ForeignKey("cards", "CASCADE", "NO ACTION", listOf("cardId"), listOf("id")))
        val _indicesCollectionEntries: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesCollectionEntries.add(TableInfo.Index("index_collection_entries_cardId", false, listOf("cardId"), listOf("ASC")))
        val _infoCollectionEntries: TableInfo = TableInfo("collection_entries", _columnsCollectionEntries, _foreignKeysCollectionEntries, _indicesCollectionEntries)
        val _existingCollectionEntries: TableInfo = read(connection, "collection_entries")
        if (!_infoCollectionEntries.equals(_existingCollectionEntries)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |collection_entries(com.scanrift.android.data.local.entity.CollectionEntryEntity).
              | Expected:
              |""".trimMargin() + _infoCollectionEntries + """
              |
              | Found:
              |""".trimMargin() + _existingCollectionEntries)
        }
        val _columnsCardLists: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCardLists.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCardLists.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCardLists.put("colorHex", TableInfo.Column("colorHex", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCardLists.put("isSystem", TableInfo.Column("isSystem", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCardLists.put("systemType", TableInfo.Column("systemType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCardLists.put("createdDate", TableInfo.Column("createdDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCardLists: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCardLists: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCardLists: TableInfo = TableInfo("card_lists", _columnsCardLists, _foreignKeysCardLists, _indicesCardLists)
        val _existingCardLists: TableInfo = read(connection, "card_lists")
        if (!_infoCardLists.equals(_existingCardLists)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |card_lists(com.scanrift.android.data.local.entity.CardListEntity).
              | Expected:
              |""".trimMargin() + _infoCardLists + """
              |
              | Found:
              |""".trimMargin() + _existingCardLists)
        }
        val _columnsCardListCrossRef: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCardListCrossRef.put("listId", TableInfo.Column("listId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCardListCrossRef.put("cardId", TableInfo.Column("cardId", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCardListCrossRef: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysCardListCrossRef.add(TableInfo.ForeignKey("card_lists", "CASCADE", "NO ACTION", listOf("listId"), listOf("id")))
        _foreignKeysCardListCrossRef.add(TableInfo.ForeignKey("cards", "CASCADE", "NO ACTION", listOf("cardId"), listOf("id")))
        val _indicesCardListCrossRef: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesCardListCrossRef.add(TableInfo.Index("index_card_list_cross_ref_cardId", false, listOf("cardId"), listOf("ASC")))
        val _infoCardListCrossRef: TableInfo = TableInfo("card_list_cross_ref", _columnsCardListCrossRef, _foreignKeysCardListCrossRef, _indicesCardListCrossRef)
        val _existingCardListCrossRef: TableInfo = read(connection, "card_list_cross_ref")
        if (!_infoCardListCrossRef.equals(_existingCardListCrossRef)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |card_list_cross_ref(com.scanrift.android.data.local.entity.CardListCrossRef).
              | Expected:
              |""".trimMargin() + _infoCardListCrossRef + """
              |
              | Found:
              |""".trimMargin() + _existingCardListCrossRef)
        }
        val _columnsDecks: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDecks.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDecks.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDecks.put("createdDate", TableInfo.Column("createdDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDecks.put("lastModifiedDate", TableInfo.Column("lastModifiedDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDecks.put("legendCardId", TableInfo.Column("legendCardId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDecks.put("championCardId", TableInfo.Column("championCardId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDecks: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDecks: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDecks: TableInfo = TableInfo("decks", _columnsDecks, _foreignKeysDecks, _indicesDecks)
        val _existingDecks: TableInfo = read(connection, "decks")
        if (!_infoDecks.equals(_existingDecks)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |decks(com.scanrift.android.data.local.entity.DeckEntity).
              | Expected:
              |""".trimMargin() + _infoDecks + """
              |
              | Found:
              |""".trimMargin() + _existingDecks)
        }
        val _columnsDeckEntries: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDeckEntries.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDeckEntries.put("deckId", TableInfo.Column("deckId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDeckEntries.put("cardId", TableInfo.Column("cardId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDeckEntries.put("quantity", TableInfo.Column("quantity", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDeckEntries.put("section", TableInfo.Column("section", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDeckEntries: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysDeckEntries.add(TableInfo.ForeignKey("decks", "CASCADE", "NO ACTION", listOf("deckId"), listOf("id")))
        _foreignKeysDeckEntries.add(TableInfo.ForeignKey("cards", "CASCADE", "NO ACTION", listOf("cardId"), listOf("id")))
        val _indicesDeckEntries: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesDeckEntries.add(TableInfo.Index("index_deck_entries_deckId", false, listOf("deckId"), listOf("ASC")))
        _indicesDeckEntries.add(TableInfo.Index("index_deck_entries_cardId", false, listOf("cardId"), listOf("ASC")))
        val _infoDeckEntries: TableInfo = TableInfo("deck_entries", _columnsDeckEntries, _foreignKeysDeckEntries, _indicesDeckEntries)
        val _existingDeckEntries: TableInfo = read(connection, "deck_entries")
        if (!_infoDeckEntries.equals(_existingDeckEntries)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |deck_entries(com.scanrift.android.data.local.entity.DeckEntryEntity).
              | Expected:
              |""".trimMargin() + _infoDeckEntries + """
              |
              | Found:
              |""".trimMargin() + _existingDeckEntries)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "cards", "collection_entries", "card_lists", "card_list_cross_ref", "decks", "deck_entries")
  }

  public override fun clearAllTables() {
    super.performClear(true, "cards", "collection_entries", "card_lists", "card_list_cross_ref", "decks", "deck_entries")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(CardDao::class, CardDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(CollectionEntryDao::class, CollectionEntryDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(CardListDao::class, CardListDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(DeckDao::class, DeckDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun cardDao(): CardDao = _cardDao.value

  public override fun collectionEntryDao(): CollectionEntryDao = _collectionEntryDao.value

  public override fun cardListDao(): CardListDao = _cardListDao.value

  public override fun deckDao(): DeckDao = _deckDao.value
}
