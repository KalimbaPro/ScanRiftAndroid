package com.scanrift.android.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.prefs.UserPreferences
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.service.backup.BackupService
import com.scanrift.android.service.backup.RestoreResult
import com.scanrift.android.service.sync.BootstrapState
import com.scanrift.android.service.sync.DatabaseBootstrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Transient result of a backup or restore, shown in a dialog then dismissed. */
sealed interface DataOperation {
    data object None : DataOperation
    data object Running : DataOperation
    data class BackupComplete(val itemCount: Int) : DataOperation
    data class RestoreComplete(val result: RestoreResult) : DataOperation
    data class Failed(val message: String) : DataOperation
}

data class SettingsState(
    val cardCount: Int = 0,
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = true,
    val autoAddToCollection: Boolean = false,
    val debugMode: Boolean = false,
    val showUnownedInColor: Boolean = false,
    val dynamicColor: Boolean = false,
    val lastDatabaseSync: Long? = null,
    val lastBackup: Long? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val collectionRepository: CollectionRepository,
    private val backupService: BackupService,
    private val bootstrapper: DatabaseBootstrapper,
) : ViewModel() {

    private val _operation = MutableStateFlow<DataOperation>(DataOperation.None)
    val operation: StateFlow<DataOperation> = _operation.asStateFlow()

    val bootstrapState: StateFlow<BootstrapState> = bootstrapper.state

    val state: StateFlow<SettingsState> = combine(
        combine(
            collectionRepository.observeCardCount(),
            collectionRepository.observeTotalCardCount(),
            collectionRepository.observeUniqueCardCount(),
            ::Triple,
        ),
        combine(
            userPreferences.hapticFeedback,
            userPreferences.soundFeedback,
            userPreferences.autoAddToCollection,
            ::Triple,
        ),
        combine(
            userPreferences.debugMode,
            userPreferences.showUnownedInColor,
            userPreferences.dynamicColor,
            ::Triple,
        ),
        combine(userPreferences.lastDatabaseSync, userPreferences.lastBackup, ::Pair),
    ) { counts, feedback, display, timestamps ->
        SettingsState(
            cardCount = counts.first,
            totalCards = counts.second,
            uniqueCards = counts.third,
            hapticFeedback = feedback.first,
            soundFeedback = feedback.second,
            autoAddToCollection = feedback.third,
            debugMode = display.first,
            showUnownedInColor = display.second,
            dynamicColor = display.third,
            lastDatabaseSync = timestamps.first,
            lastBackup = timestamps.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setHapticFeedback(value: Boolean) = update { userPreferences.setHapticFeedback(value) }
    fun setSoundFeedback(value: Boolean) = update { userPreferences.setSoundFeedback(value) }
    fun setAutoAddToCollection(value: Boolean) = update { userPreferences.setAutoAddToCollection(value) }
    fun setDebugMode(value: Boolean) = update { userPreferences.setDebugMode(value) }
    fun setShowUnownedInColor(value: Boolean) = update { userPreferences.setShowUnownedInColor(value) }
    fun setDynamicColor(value: Boolean) = update { userPreferences.setDynamicColor(value) }

    fun clearCollection() = update { collectionRepository.clearCollection() }

    /** @param uri the destination the user picked through the system file picker. */
    fun backUp(uri: Uri) {
        viewModelScope.launch {
            _operation.value = DataOperation.Running
            val now = System.currentTimeMillis()
            backupService.writeSnapshot(uri, now)
                .onSuccess { count ->
                    userPreferences.setLastBackup(now)
                    userPreferences.setBackupUri(uri.toString())
                    _operation.value = DataOperation.BackupComplete(count)
                }
                .onFailure { _operation.value = DataOperation.Failed(it.readableMessage()) }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            _operation.value = DataOperation.Running
            backupService.readAndMerge(uri)
                .onSuccess { _operation.value = DataOperation.RestoreComplete(it) }
                .onFailure { _operation.value = DataOperation.Failed(it.readableMessage()) }
        }
    }

    fun dismissOperation() { _operation.value = DataOperation.None }

    private fun Throwable.readableMessage(): String =
        message ?: this::class.simpleName ?: "Something went wrong"

    private inline fun update(crossinline block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
