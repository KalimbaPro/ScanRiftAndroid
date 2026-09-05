package com.scanrift.android.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.dao.CardDao
import com.scanrift.android.data.local.dao.CardListDao
import com.scanrift.android.data.local.dao.CollectionEntryDao
import com.scanrift.android.data.local.dao.DeckDao
import com.scanrift.android.data.local.dao.GameRecordDao
import com.scanrift.android.data.local.dao.MaintenanceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScanRiftDatabase =
        Room.databaseBuilder(context, ScanRiftDatabase::class.java, ScanRiftDatabase.NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides fun provideCardDao(db: ScanRiftDatabase): CardDao = db.cardDao()
    @Provides fun provideCollectionEntryDao(db: ScanRiftDatabase): CollectionEntryDao = db.collectionEntryDao()
    @Provides fun provideCardListDao(db: ScanRiftDatabase): CardListDao = db.cardListDao()
    @Provides fun provideDeckDao(db: ScanRiftDatabase): DeckDao = db.deckDao()
    @Provides fun provideGameRecordDao(db: ScanRiftDatabase): GameRecordDao = db.gameRecordDao()
    @Provides fun provideMaintenanceDao(db: ScanRiftDatabase): MaintenanceDao = db.maintenanceDao()
}
