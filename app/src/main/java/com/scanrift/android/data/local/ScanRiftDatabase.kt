package com.scanrift.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.scanrift.android.data.local.dao.CardDao
import com.scanrift.android.data.local.dao.CardListDao
import com.scanrift.android.data.local.dao.CollectionEntryDao
import com.scanrift.android.data.local.dao.DeckDao
import com.scanrift.android.data.local.dao.GameRecordDao
import com.scanrift.android.data.local.dao.MaintenanceDao
import com.scanrift.android.data.local.dao.SyncedSetDao
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CardListCrossRef
import com.scanrift.android.data.local.entity.CardListEntity
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.entity.GameRecordEntity
import com.scanrift.android.data.local.entity.SyncedSetEntity

/**
 * Schema v1.
 *
 * The app has never shipped, so all seven tables — including `game_records`, which the
 * old build had no equivalent of — go into a fresh v1 rather than arriving via a
 * migration. `exportSchema` is on from the start and `app/schemas/**/1.json` is
 * committed, so the first real migration has a baseline to diff against.
 *
 * There is deliberately no `fallbackToDestructiveMigration()`. The old build had one,
 * which would have silently wiped every user's collection on the first schema bump.
 */
@Database(
    entities = [
        CardEntity::class,
        CollectionEntryEntity::class,
        CardListEntity::class,
        CardListCrossRef::class,
        DeckEntity::class,
        DeckEntryEntity::class,
        GameRecordEntity::class,
        SyncedSetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ScanRiftDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun collectionEntryDao(): CollectionEntryDao
    abstract fun cardListDao(): CardListDao
    abstract fun deckDao(): DeckDao
    abstract fun gameRecordDao(): GameRecordDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun syncedSetDao(): SyncedSetDao

    companion object {
        const val NAME = "scanrift.db"
    }
}
