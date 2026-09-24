package com.scanrift.android.service.sync

import com.scanrift.android.core.Constants
import com.scanrift.android.core.log.Log
import com.scanrift.android.data.prefs.UserPreferences
import com.scanrift.android.data.repository.CardListRepository
import com.scanrift.android.di.Bundled
import com.scanrift.android.di.Remote
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.dao.CardDao
import com.scanrift.android.data.local.dao.MaintenanceDao
import com.scanrift.android.data.local.dao.SyncedSetDao
import com.scanrift.android.data.remote.datasource.RiftboundDataSource
import com.scanrift.android.di.IoDispatcher
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface BootstrapState {
    data object Idle : BootstrapState
    data object SeedingBundle : BootstrapState
    data object CheckingForUpdates : BootstrapState
    data class Syncing(val done: Int, val total: Int) : BootstrapState
    data object UpToDate : BootstrapState
    data class Updated(val cardCount: Int) : BootstrapState
    data class Failed(val message: String) : BootstrapState

    val isLoading: Boolean
        get() = this is SeedingBundle || this is CheckingForUpdates || this is Syncing
}

/**
 * Launch orchestration, ported from iOS `ContentView.checkDatabaseStatus`.
 *
 * Every CloudKit wait is gone — with no cloud there is no import race to guard
 * against, so the 8-second catalogue wait and the 5-second wishlist poll simply do not
 * apply. What remains:
 *
 * 1. Empty catalogue → seed from the bundled JSON, then force a delta sync. The
 *    bundle is a snapshot: the live API already serves 1451 cards to its 1064, so a
 *    seed-only install would be missing an entire set.
 * 2. Otherwise → de-duplicate, then a TTL-gated delta check (one per hour).
 * 3. Always → reconcile the single system Wishlist.
 */
@Singleton
class DatabaseBootstrapper @Inject constructor(
    private val db: ScanRiftDatabase,
    private val cardDao: CardDao,
    private val maintenanceDao: MaintenanceDao,
    private val syncedSetDao: SyncedSetDao,
    @param:Bundled private val bundledSource: RiftboundDataSource,
    @param:Remote private val remoteSource: RiftboundDataSource,
    private val listRepository: CardListRepository,
    private val userPreferences: UserPreferences,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow<BootstrapState>(BootstrapState.Idle)
    val state: StateFlow<BootstrapState> = _state.asStateFlow()

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val mutex = Mutex()

    /** Idempotent: safe to call from every Activity creation. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            runCatching { mutex.withLock { bootstrap() } }.onFailure { failure -> _state.value = failed(failure) }
        }
    }

    fun loadCardDatabase() = runManually {
        _state.value = BootstrapState.Syncing(0, 0)
        val count = service(bundledSource).fetchAndStoreCards { done, total ->
            _state.value = BootstrapState.Syncing(done, total)
        }
        userPreferences.setLastDatabaseSync(System.currentTimeMillis())
        BootstrapState.Updated(count)
    }

    fun checkForUpdates() = runManually {
        _state.value = BootstrapState.CheckingForUpdates
        val report = syncDelta(service(remoteSource))
        if (report.hasChanges) BootstrapState.Updated(report.added + report.updated) else BootstrapState.UpToDate
    }

    fun resetSyncState() {
        _state.update { if (it is BootstrapState.UpToDate || it is BootstrapState.Updated) BootstrapState.Idle else it }
    }

    fun dismissError() {
        _state.update { if (it is BootstrapState.Failed) BootstrapState.Idle else it }
    }

    private fun runManually(block: suspend () -> BootstrapState) {
        if (_state.value.isLoading) return
        scope.launch {
            _state.value = runCatching { mutex.withLock { block() } }.getOrElse(::failed)
        }
    }

    private suspend fun bootstrap() {
        val bundledService = service(bundledSource)
        val remoteService = service(remoteSource)

        if (cardDao.count() == 0) {
            _state.value = BootstrapState.SeedingBundle
            bundledService.fetchAndStoreCards()
            userPreferences.setLastDatabaseSync(System.currentTimeMillis())
            // Forced, not TTL-gated: the bundle is always behind the API.
            syncOnLaunch(remoteService, force = true)
        } else {
            remoteService.deduplicateLocalCards()
            maintenanceDao.deleteOrphans()
            syncOnLaunch(remoteService, force = false)
        }

        listRepository.reconcileWishlist()
    }

    private suspend fun syncOnLaunch(service: CardDatabaseService, force: Boolean) {
        if (!force) {
            val lastCheck = userPreferences.lastUpdateCheck.first()
            if (lastCheck != null && System.currentTimeMillis() - lastCheck < Constants.Api.UPDATE_CHECK_TTL_MS) return
        }

        _state.value = BootstrapState.CheckingForUpdates
        // A failed network sync must not block launch — the bundled catalogue is
        // already usable, so this degrades to "you have slightly stale data".
        runCatching { syncDelta(service) }
            .onFailure { error -> Log.database.w(error, "Delta sync failed; continuing with the local catalogue") }
        _state.value = BootstrapState.Idle
    }

    private suspend fun syncDelta(service: CardDatabaseService): SyncReport {
        val now = System.currentTimeMillis()
        val report = service.syncDelta { done, total -> _state.value = BootstrapState.Syncing(done, total) }
        userPreferences.setLastUpdateCheck(now)
        if (report.hasChanges) userPreferences.setLastDatabaseSync(now)
        return report
    }

    private fun failed(error: Throwable): BootstrapState {
        Log.database.e(error, "Card database sync failed")
        return BootstrapState.Failed(error.message ?: "Could not load the card database")
    }

    private fun service(source: RiftboundDataSource) =
        CardDatabaseService(db, cardDao, maintenanceDao, syncedSetDao, source, io)
}
