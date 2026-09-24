package com.scanrift.android.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.core.Constants
import com.scanrift.android.core.log.Log
import com.scanrift.android.data.prefs.UserPreferences
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.data.repository.DeckRepository
import com.scanrift.android.di.IoDispatcher
import com.scanrift.android.domain.model.ScoreInputMode
import com.scanrift.android.service.backup.BackupService
import com.scanrift.android.service.export.CollectionExporter
import com.scanrift.android.service.importer.CollectionImportService
import com.scanrift.android.service.sync.BootstrapState
import com.scanrift.android.service.sync.DatabaseBootstrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DataOperation {
    data object None : DataOperation
    data object Running : DataOperation
    data class ImportComplete(val message: String) : DataOperation
    data class ImportFailed(val message: String) : DataOperation
    data class RestoreComplete(val message: String) : DataOperation
    data class BackupFailed(val message: String) : DataOperation
}

enum class ExportFormat(val fileName: String, val mimeType: String) {
    RIFTBOUND_GG(Constants.FileNames.EXPORT_DOTGG_CSV, "text/csv"),
    CSV(Constants.FileNames.EXPORT_CSV, "text/csv"),
    JSON(Constants.FileNames.EXPORT_JSON, "application/json"),
}

data class SharedExport(val file: File, val mimeType: String)

data class SettingsState(
    val cardCount: Int = 0,
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
    val hasDecks: Boolean = false,
    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = true,
    val autoAddToCollection: Boolean = false,
    val debugMode: Boolean = false,
    val luchoParameter: Boolean = false,
    val showUnownedInColor: Boolean = false,
    val dynamicColor: Boolean = false,
    val lastDatabaseSync: Long? = null,
    val lastBackup: Long? = null,
    val cloudSnapshotAutoSync: Boolean = true,
    val backupFileName: String? = null,
    val scoreInputMode: ScoreInputMode = ScoreInputMode.TAP_ZONES,
) {
    val hasEntries: Boolean get() = uniqueCards > 0
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
    private val userPreferences: UserPreferences,
    private val collectionRepository: CollectionRepository,
    private val deckRepository: DeckRepository,
    private val backupService: BackupService,
    private val importService: CollectionImportService,
    private val bootstrapper: DatabaseBootstrapper,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _operation = MutableStateFlow<DataOperation>(DataOperation.None)
    val operation: StateFlow<DataOperation> = _operation.asStateFlow()

    private val _sharedExports = Channel<SharedExport>(Channel.BUFFERED)
    val sharedExports: Flow<SharedExport> = _sharedExports.receiveAsFlow()

    val syncState: StateFlow<BootstrapState> = bootstrapper.state

    private val backupFileName: Flow<String?> = userPreferences.backupUri.distinctUntilChanged().map { uri ->
        uri?.let { Uri.parse(it) }?.let { withContext(io) { displayName(it) ?: it.lastPathSegment } }
    }

    val state: StateFlow<SettingsState> = combine(
        combine(
            collectionRepository.observeCardCount(),
            collectionRepository.observeTotalCardCount(),
            collectionRepository.observeUniqueCardCount(),
            deckRepository.observeDecks().map { it.isNotEmpty() },
            ::Quadruple,
        ),
        combine(
            userPreferences.hapticFeedback,
            userPreferences.soundFeedback,
            userPreferences.autoAddToCollection,
            ::Triple,
        ),
        combine(
            userPreferences.debugMode,
            userPreferences.luchoParameter,
            userPreferences.showUnownedInColor,
            userPreferences.dynamicColor,
            ::Quadruple,
        ),
        combine(
            userPreferences.lastDatabaseSync,
            userPreferences.lastBackup,
            userPreferences.cloudSnapshotAutoSync,
            backupFileName,
            ::Quadruple,
        ),
        userPreferences.scoreInputMode,
    ) { counts, feedback, display, backup, scoreInputMode ->
        SettingsState(
            cardCount = counts.first,
            totalCards = counts.second,
            uniqueCards = counts.third,
            hasDecks = counts.fourth,
            hapticFeedback = feedback.first,
            soundFeedback = feedback.second,
            autoAddToCollection = feedback.third,
            debugMode = display.first,
            luchoParameter = display.second,
            showUnownedInColor = display.third,
            dynamicColor = display.fourth,
            lastDatabaseSync = backup.first,
            lastBackup = backup.second,
            cloudSnapshotAutoSync = backup.third,
            backupFileName = backup.fourth,
            scoreInputMode = scoreInputMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setHapticFeedback(value: Boolean) = update { userPreferences.setHapticFeedback(value) }
    fun setSoundFeedback(value: Boolean) = update { userPreferences.setSoundFeedback(value) }
    fun setAutoAddToCollection(value: Boolean) = update { userPreferences.setAutoAddToCollection(value) }
    fun setDebugMode(value: Boolean) = update { userPreferences.setDebugMode(value) }
    fun setLuchoParameter(value: Boolean) = update { userPreferences.setLuchoParameter(value) }
    fun setShowUnownedInColor(value: Boolean) = update { userPreferences.setShowUnownedInColor(value) }
    fun setDynamicColor(value: Boolean) = update { userPreferences.setDynamicColor(value) }
    fun setScoreInputMode(value: ScoreInputMode) = update { userPreferences.setScoreInputMode(value) }
    fun setCloudSnapshotAutoSync(value: Boolean) = update { userPreferences.setCloudSnapshotAutoSync(value) }

    fun loadCardDatabase() = bootstrapper.loadCardDatabase()
    fun checkForUpdates() = bootstrapper.checkForUpdates()
    fun resetSyncState() = bootstrapper.resetSyncState()
    fun dismissSyncError() = bootstrapper.dismissError()

    fun clearCollection() = update { collectionRepository.clearCollection() }

    fun backUpNow(onLocationNeeded: () -> Unit) {
        viewModelScope.launch {
            val result = backupService.backUpToSavedLocation(System.currentTimeMillis())
            when {
                result == null -> onLocationNeeded()
                result.isFailure -> {
                    userPreferences.setBackupUri(null)
                    _operation.value = DataOperation.BackupFailed(result.exceptionOrNull().readableMessage())
                }
            }
        }
    }

    fun backUp(uri: Uri) {
        viewModelScope.launch {
            backupService.backUp(uri, System.currentTimeMillis())
                .onFailure { _operation.value = DataOperation.BackupFailed(it.readableMessage()) }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            _operation.value = DataOperation.Running
            _operation.value = DataOperation.RestoreComplete(
                backupService.readAndMerge(uri).fold({ it.summary }, { it.readableMessage() }),
            )
        }
    }

    fun importCollection(uri: Uri) {
        viewModelScope.launch {
            _operation.value = DataOperation.Running
            _operation.value = runCatching {
                val bytes = withContext(io) { contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                    ?: error("Could not access the selected file.")
                val extension = displayName(uri)?.substringAfterLast('.', "")
                importService.import(bytes, extension, System.currentTimeMillis())
            }.fold(
                { DataOperation.ImportComplete(it.message) },
                {
                    Log.database.e(it, "Import failed")
                    DataOperation.ImportFailed(it.readableMessage())
                },
            )
        }
    }

    fun export(format: ExportFormat) {
        viewModelScope.launch {
            runCatching {
                val entries = collectionRepository.observeEntries().first()
                val content = when (format) {
                    ExportFormat.RIFTBOUND_GG -> CollectionExporter.exportAsRiftboundGg(entries)
                    ExportFormat.CSV -> CollectionExporter.exportAsCsv(entries)
                    ExportFormat.JSON -> {
                        val decks = deckRepository.observeDecks().first()
                        val history = decks.associate { it.id to deckRepository.observeGameRecords(it.id).first() }
                        CollectionExporter.exportAsJson(entries, decks, history, System.currentTimeMillis())
                    }
                }
                withContext(io) {
                    context.cacheDir.resolve(SHARED_DIRECTORY).apply { mkdirs() }
                        .resolve(format.fileName).apply { writeText(content) }
                }
            }.onSuccess { _sharedExports.send(SharedExport(it, format.mimeType)) }
                .onFailure { Log.general.e(it, "Export failed") }
        }
    }

    fun dismissOperation() { _operation.value = DataOperation.None }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun Throwable?.readableMessage(): String =
        this?.message ?: this?.let { it::class.simpleName } ?: "Something went wrong"

    private inline fun update(crossinline block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private companion object {
        const val SHARED_DIRECTORY = "shared"
    }
}
