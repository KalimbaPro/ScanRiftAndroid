package com.scanrift.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.scanrift.android.data.local.converter.Converters
import com.scanrift.android.data.local.dao.CardDao
import com.scanrift.android.data.local.dao.CardListDao
import com.scanrift.android.data.local.dao.CollectionEntryDao
import com.scanrift.android.data.local.dao.DeckDao
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CardListCrossRef
import com.scanrift.android.data.local.entity.CardListEntity
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity

@Database(
    entities = [
        CardEntity::class,
        CollectionEntryEntity::class,
        CardListEntity::class,
        CardListCrossRef::class,
        DeckEntity::class,
        DeckEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ScanRiftDatabase : RoomDatabase() {

    abstract fun cardDao(): CardDao
    abstract fun collectionEntryDao(): CollectionEntryDao
    abstract fun cardListDao(): CardListDao
    abstract fun deckDao(): DeckDao

    companion object {
        @Volatile
        private var INSTANCE: ScanRiftDatabase? = null

        fun getInstance(context: Context): ScanRiftDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScanRiftDatabase::class.java,
                    "scanrift_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
