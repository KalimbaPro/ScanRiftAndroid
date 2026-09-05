package com.scanrift.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.scanrift.android.data.local.entity.SyncedSetEntity

@Dao
interface SyncedSetDao {

    @Upsert
    suspend fun upsert(set: SyncedSetEntity)

    @Query("SELECT * FROM synced_sets")
    suspend fun getAll(): List<SyncedSetEntity>

    @Query("DELETE FROM synced_sets")
    suspend fun deleteAll()
}
