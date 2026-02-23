package com.scanrift.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {

    private object Keys {
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SOUND_FEEDBACK = booleanPreferencesKey("sound_feedback")
        val AUTO_ADD_TO_COLLECTION = booleanPreferencesKey("auto_add_to_collection")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val LAST_DATABASE_SYNC = longPreferencesKey("last_database_sync")
    }

    val hapticFeedback: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.HAPTIC_FEEDBACK] ?: true }

    val soundFeedback: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.SOUND_FEEDBACK] ?: true }

    val autoAddToCollection: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.AUTO_ADD_TO_COLLECTION] ?: false }

    val debugMode: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.DEBUG_MODE] ?: false }

    val lastDatabaseSync: Flow<Long?> = context.dataStore.data
        .map { it[Keys.LAST_DATABASE_SYNC] }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setSoundFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_FEEDBACK] = enabled }
    }

    suspend fun setAutoAddToCollection(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_ADD_TO_COLLECTION] = enabled }
    }

    suspend fun setDebugMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEBUG_MODE] = enabled }
    }

    suspend fun setLastDatabaseSync(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_DATABASE_SYNC] = timestamp }
    }
}
