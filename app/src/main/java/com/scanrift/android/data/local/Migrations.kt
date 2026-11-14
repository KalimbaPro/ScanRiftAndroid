package com.scanrift.android.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written migrations. There is deliberately no destructive fallback: a missing
 * migration must crash in testing, not silently wipe a user's collection.
 */
object Migrations {

    /**
     * v1 → v2: own-API legality and localisations on `cards`, and the printed language
     * of each owned copy on `collection_entries`.
     *
     * Every new column is either nullable or carries a DEFAULT, so existing rows stay
     * valid: cards read as "not banned, no errata" until the next legality refresh, and
     * every existing owned copy becomes English — which is what it was.
     *
     * Language joins the entry identity, so the unique index is dropped and recreated
     * with it. The old index guaranteed `(cardId, isFoil, condition)` was unique, so no
     * existing pair of rows can collide on the wider key.
     *
     * The DEFAULT clauses must match the `@ColumnInfo(defaultValue = …)` on the entities
     * exactly — Room's open-time validation compares them.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `banned` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `bannedFormats` TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `errata` TEXT")
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `errataDate` TEXT")
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `localizations` TEXT")

            db.execSQL("ALTER TABLE `collection_entries` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'en'")
            db.execSQL("DROP INDEX IF EXISTS `index_collection_entries_cardId_isFoil_condition`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_collection_entries_cardId_isFoil_condition_language` " +
                    "ON `collection_entries` (`cardId`, `isFoil`, `condition`, `language`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
