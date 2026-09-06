package com.scanrift.android.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanrift.android.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * User settings.
 *
 * Key names match the iOS UserDefaults spellings so the two codebases stay greppable
 * together, even though the stores are unrelated. iCloud-specific keys are absent —
 * Android has no cloud sync.
 */
@Singleton
class UserPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val hapticFeedback = booleanPreferencesKey(Constants.PreferenceKeys.HAPTIC_FEEDBACK)
        val soundFeedback = booleanPreferencesKey(Constants.PreferenceKeys.SOUND_FEEDBACK)
        val autoAddToCollection = booleanPreferencesKey(Constants.PreferenceKeys.AUTO_ADD_TO_COLLECTION)
        val debugMode = booleanPreferencesKey(Constants.PreferenceKeys.DEBUG_MODE)
        val luchoParameter = booleanPreferencesKey(Constants.PreferenceKeys.LUCHO_PARAMETER)
        val showUnownedInColor = booleanPreferencesKey(Constants.PreferenceKeys.SHOW_UNOWNED_IN_COLOR)
        val dynamicColor = booleanPreferencesKey(Constants.PreferenceKeys.DYNAMIC_COLOR)
        val lastDatabaseSync = longPreferencesKey(Constants.PreferenceKeys.LAST_DATABASE_SYNC)
        val lastUpdateCheck = longPreferencesKey(Constants.PreferenceKeys.LAST_UPDATE_CHECK)
        val lastBackup = longPreferencesKey(Constants.PreferenceKeys.LAST_BACKUP)
        val backupUri = stringPreferencesKey(Constants.PreferenceKeys.BACKUP_URI)
        val pointTrackerRoster = stringPreferencesKey(Constants.PreferenceKeys.POINT_TRACKER_ROSTER)
    }

    val hapticFeedback: Flow<Boolean> = boolean(Keys.hapticFeedback, default = true)
    val soundFeedback: Flow<Boolean> = boolean(Keys.soundFeedback, default = true)
    val autoAddToCollection: Flow<Boolean> = boolean(Keys.autoAddToCollection, default = false)
    val debugMode: Flow<Boolean> = boolean(Keys.debugMode, default = false)

    /** Hidden flag that unlocks the artist filter, mirroring iOS. */
    val luchoParameter: Flow<Boolean> = boolean(Keys.luchoParameter, default = false)

    /** When off, unowned cards render greyscale. The 50% fade applies either way. */
    val showUnownedInColor: Flow<Boolean> = boolean(Keys.showUnownedInColor, default = false)

    /**
     * Material You, opt-in and off by default: the app's chrome sits against six fixed
     * domain hues, and a wallpaper-derived primary routinely collides with them.
     */
    val dynamicColor: Flow<Boolean> = boolean(Keys.dynamicColor, default = false)

    val lastDatabaseSync: Flow<Long?> = nullableLong(Keys.lastDatabaseSync)
    val lastUpdateCheck: Flow<Long?> = nullableLong(Keys.lastUpdateCheck)
    val lastBackup: Flow<Long?> = nullableLong(Keys.lastBackup)
    val backupUri: Flow<String?> = context.dataStore.data.map { it[Keys.backupUri] }
    val pointTrackerRoster: Flow<String?> = context.dataStore.data.map { it[Keys.pointTrackerRoster] }

    suspend fun setHapticFeedback(value: Boolean) = put(Keys.hapticFeedback, value)
    suspend fun setSoundFeedback(value: Boolean) = put(Keys.soundFeedback, value)
    suspend fun setAutoAddToCollection(value: Boolean) = put(Keys.autoAddToCollection, value)
    suspend fun setDebugMode(value: Boolean) = put(Keys.debugMode, value)
    suspend fun setLuchoParameter(value: Boolean) = put(Keys.luchoParameter, value)
    suspend fun setShowUnownedInColor(value: Boolean) = put(Keys.showUnownedInColor, value)
    suspend fun setDynamicColor(value: Boolean) = put(Keys.dynamicColor, value)
    suspend fun setLastDatabaseSync(value: Long) = put(Keys.lastDatabaseSync, value)
    suspend fun setLastUpdateCheck(value: Long) = put(Keys.lastUpdateCheck, value)
    suspend fun setLastBackup(value: Long) = put(Keys.lastBackup, value)
    suspend fun setBackupUri(value: String?) = context.dataStore.edit { prefs ->
        if (value == null) prefs.remove(Keys.backupUri) else prefs[Keys.backupUri] = value
    }
    suspend fun setPointTrackerRoster(value: String) = put(Keys.pointTrackerRoster, value)

    private fun boolean(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
        context.dataStore.data.map { it[key] ?: default }

    private fun nullableLong(key: Preferences.Key<Long>): Flow<Long?> =
        context.dataStore.data.map { it[key] }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
