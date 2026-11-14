package com.scanrift.android.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.domain.model.CardLanguage
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs the production migrations against a real v1 database.
 *
 * The v1 database is built from the committed `app/schemas/…/1.json` — the exact SQL
 * Room generated for the shipped schema — and then opened by Room with
 * [Migrations.ALL]. Room validates every table against the compiled v2 entities on
 * open, defaults included, so a DEFAULT clause that drifts from an entity's
 * `@ColumnInfo` fails here rather than on a user's phone.
 *
 * (`MigrationTestHelper` reads schemas from instrumentation assets, which under
 * Robolectric are the *debug* app assets; shipping schemas in the debug APK just for
 * this is not worth it, so the helper's job is done by hand.)
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.deleteDatabase(DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB)
    }

    @Test
    fun `migrating 1 to 2 keeps the collection and makes every copy English`() = runTest {
        createV1 { db ->
            db.insertCard("card-1")
            db.execSQL(
                "INSERT INTO collection_entries (cardId, quantity, isFoil, dateAdded, condition) " +
                    "VALUES ('card-1', 3, 0, 1000, 'Near Mint'), ('card-1', 1, 1, 2000, 'Near Mint')",
            )
        }

        val room = openMigrated()
        try {
            val entries = room.collectionEntryDao().getAll().sortedBy { it.dateAdded }
            assertThat(entries.map { it.quantity }).containsExactly(3, 1).inOrder()
            assertThat(entries.map { it.language }).containsExactly("en", "en")

            val card = room.cardDao().getById("card-1")!!
            assertThat(card.banned).isFalse()
            assertThat(card.bannedFormats).isEmpty()
            assertThat(card.errata).isNull()
            assertThat(card.localizations).isNull()
        } finally {
            room.close()
        }
    }

    @Test
    fun `after migrating, the same card in two languages is two stacks`() = runTest {
        createV1 { db ->
            db.insertCard("card-1")
            db.execSQL(
                "INSERT INTO collection_entries (cardId, quantity, isFoil, dateAdded, condition) " +
                    "VALUES ('card-1', 2, 0, 1000, 'Near Mint')",
            )
        }

        val room = openMigrated()
        try {
            val dao = room.collectionEntryDao()
            // The widened unique index accepts a French copy beside the English one...
            dao.insert(CollectionEntryEntity(cardId = "card-1", quantity = 1, dateAdded = 3000, language = CardLanguage.FR.value))
            assertThat(dao.getAll()).hasSize(2)
            assertThat(dao.findEntry("card-1", false, "Near Mint", "fr")!!.quantity).isEqualTo(1)
            assertThat(dao.findEntry("card-1", false, "Near Mint", "en")!!.quantity).isEqualTo(2)

            // ...and still rejects a second English stack with the same foil and condition.
            val duplicate = runCatching {
                dao.insert(CollectionEntryEntity(cardId = "card-1", quantity = 1, dateAdded = 4000))
            }
            assertThat(duplicate.isFailure).isTrue()
        } finally {
            room.close()
        }
    }

    @Test
    fun `the old three-part unique index is gone`() = runTest {
        createV1 { }
        val room = openMigrated()
        try {
            room.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'collection_entries'",
            ).use { cursor ->
                val names = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertThat(names).contains("index_collection_entries_cardId_isFoil_condition_language")
                assertThat(names).doesNotContain("index_collection_entries_cardId_isFoil_condition")
            }
        } finally {
            room.close()
        }
    }

    private fun openMigrated(): ScanRiftDatabase =
        Room.databaseBuilder(context, ScanRiftDatabase::class.java, DB)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
            .also { it.openHelper.writableDatabase } // Forces the migration and schema validation now.

    /** Builds the shipped v1 schema from its exported JSON, exactly as Room would have. */
    private fun createV1(seed: (SupportSQLiteDatabase) -> Unit) {
        val schema = Json.parseToJsonElement(File(SCHEMA_V1).readText()).jsonObject.getValue("database").jsonObject
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                schema.getValue("entities").jsonArray.forEach { element ->
                    val entity = element.jsonObject
                    val table = entity.getValue("tableName").jsonPrimitive.content
                    db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                    entity["indices"]?.jsonArray?.forEach { index ->
                        db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                    }
                }
                schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
                db.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '" +
                        schema.getValue("identityHash").jsonPrimitive.content + "')",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB).callback(callback).build(),
        )
        helper.writableDatabase.also(seed)
        helper.close()
    }

    private fun SupportSQLiteDatabase.insertCard(id: String) = execSQL(
        """
        INSERT INTO cards (id, name, riftboundId, publicCode, collectorNumber, type, rarity, domains,
            setId, setLabel, sourceSetId, cleanName, alternateArt, overnumbered, signature, tags)
        VALUES ('$id', 'Blazing Scorcher', 'ogn-001-298', 'OGN-001/298', 1, 'Unit', 'Common', '["Fury"]',
            'OGN', 'Origins', 'OGN', 'Blazing Scorcher', 0, 0, 0, '[]')
        """.trimIndent(),
    )

    private companion object {
        const val DB = "migration-test.db"
        const val SCHEMA_V1 = "schemas/com.scanrift.android.data.local.ScanRiftDatabase/1.json"
    }
}
