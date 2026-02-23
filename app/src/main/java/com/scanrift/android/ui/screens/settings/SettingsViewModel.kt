package com.scanrift.android.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.remote.api.RetrofitInstance
import com.scanrift.android.data.repository.CardRepository
import com.scanrift.android.data.repository.UserPreferences
import com.scanrift.android.service.export.CollectionExporter
import com.scanrift.android.service.export.ShareHelper
import com.scanrift.android.service.import_.CollectionImportService
import com.scanrift.android.service.import_.ImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class SyncState {
    data object Idle : SyncState()
    data class Syncing(val page: Int = 0, val totalPages: Int = 0) : SyncState()
    data class Success(val cardCount: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ScanRiftDatabase.getInstance(application)
    private val cardRepository = CardRepository(
        cardDao = database.cardDao(),
        api = RetrofitInstance.api,
        context = application
    )
    private val cardDao = database.cardDao()
    private val collectionEntryDao = database.collectionEntryDao()
    private val importService = CollectionImportService(cardDao, collectionEntryDao)
    val preferences = UserPreferences(application)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    val cardCount: StateFlow<Int> = cardRepository.getCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCollectionCards: StateFlow<Int?> = collectionEntryDao.getTotalCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uniqueCollectionCards: StateFlow<Int> = collectionEntryDao.getUniqueCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastSyncDate: StateFlow<Long?> = preferences.lastDatabaseSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hapticFeedback: StateFlow<Boolean> = preferences.hapticFeedback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val soundFeedback: StateFlow<Boolean> = preferences.soundFeedback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoAddToCollection: StateFlow<Boolean> = preferences.autoAddToCollection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val debugMode: StateFlow<Boolean> = preferences.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun syncDatabase() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing()
            val result = cardRepository.syncDatabase { current, total ->
                _syncState.value = SyncState.Syncing(current, total)
            }
            result.fold(
                onSuccess = { count ->
                    preferences.setLastDatabaseSync(System.currentTimeMillis())
                    _syncState.value = SyncState.Success(count)
                    Timber.d("Database sync complete: %d cards", count)
                },
                onFailure = { error ->
                    _syncState.value = SyncState.Error(
                        error.localizedMessage ?: "Unknown error"
                    )
                    Timber.e(error, "Database sync failed")
                }
            )
        }
    }

    fun resetSyncState() {
        if (_syncState.value is SyncState.Success) {
            _syncState.value = SyncState.Idle
        }
    }

    fun dismissError() {
        if (_syncState.value is SyncState.Error) {
            _syncState.value = SyncState.Idle
        }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { preferences.setHapticFeedback(enabled) }
    }

    fun setSoundFeedback(enabled: Boolean) {
        viewModelScope.launch { preferences.setSoundFeedback(enabled) }
    }

    fun setAutoAddToCollection(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoAddToCollection(enabled) }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setDebugMode(enabled) }
    }

    // Merge duplicates
    private val _mergeResult = MutableStateFlow<Int?>(null)
    val mergeResult: StateFlow<Int?> = _mergeResult.asStateFlow()

    fun mergeDuplicates() {
        viewModelScope.launch {
            val entries = collectionEntryDao.getAllWithCardsList()
            val groups = entries.groupBy { it.entry.cardId to it.entry.isFoil }
            var mergedCount = 0
            for ((_, group) in groups) {
                if (group.size <= 1) continue
                var keep = group[0].entry
                for (i in 1 until group.size) {
                    keep = keep.copy(quantity = keep.quantity + group[i].entry.quantity)
                    collectionEntryDao.delete(group[i].entry)
                    mergedCount++
                }
                collectionEntryDao.update(keep)
            }
            _mergeResult.value = mergedCount
            Timber.d("Merged %d duplicate entries", mergedCount)
        }
    }

    fun dismissMergeResult() {
        _mergeResult.value = null
    }

    // Import/Export
    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    fun importCollection(data: String, fileExtension: String) {
        viewModelScope.launch {
            try {
                val result = importService.importCollection(data, fileExtension)
                _importResult.value = result
            } catch (e: Exception) {
                Timber.e(e, "Import failed")
                _importResult.value = ImportResult(0, 0, listOf("Error: ${e.message}"))
            }
        }
    }

    fun dismissImportResult() {
        _importResult.value = null
    }

    fun exportAsRiftboundGG(context: Context) {
        viewModelScope.launch {
            val entries = collectionEntryDao.getAllWithCardsList()
            val csv = CollectionExporter.exportAsRiftboundGG(entries)
            ShareHelper.shareCSV(context, csv, "collection_riftbound.csv")
        }
    }

    fun exportAsCSV(context: Context) {
        viewModelScope.launch {
            val entries = collectionEntryDao.getAllWithCardsList()
            val csv = CollectionExporter.exportAsCSV(entries)
            ShareHelper.shareCSV(context, csv, "collection.csv")
        }
    }

    fun exportAsJSON(context: Context) {
        viewModelScope.launch {
            val entries = collectionEntryDao.getAllWithCardsList()
            val json = CollectionExporter.exportAsJSON(entries)
            ShareHelper.shareJSON(context, json, "collection.json")
        }
    }

    fun clearCollection() {
        viewModelScope.launch {
            collectionEntryDao.deleteAll()
            Timber.d("Collection cleared")
        }
    }
}
