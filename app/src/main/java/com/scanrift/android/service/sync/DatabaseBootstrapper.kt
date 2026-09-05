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
import kotlinx.coroutines.launch

sealed interface BootstrapState {
    data object Idle : BootstrapState
    data object SeedingBundle : BootstrapState
    data object CheckingForUpdates : BootstrapState
    data class Syncing(val done: Int, val total: Int) : BootstrapState
    data class Ready(val cardCount: Int) : BootstrapState
    data class Failed(val message: String) : BootstrapState
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

    /** Idempotent: safe to call from every Activity creation. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { runCatching { bootstrap() }.onFailure { failure -> fail(failure) } }
    }

    private suspend fun bootstrap() {
        val bundledService = service(bundledSource)
        val remoteService = service(remoteSource)

        if (cardDao.count() == 0) {
            _state.value = BootstrapState.SeedingBundle
            bundledService.fetchAndStoreCards()
            // Forced, not TTL-gated: the bundle is always behind the API.
            syncWith(remoteService, force = true)
        } else {
            remoteService.deduplicateLocalCards()
            maintenanceDao.deleteOrphans()
            syncWith(remoteService, force = false)
        }

        listRepository.reconcileWishlist()
        _state.value = BootstrapState.Ready(cardDao.count())
    }

    private suspend fun syncWith(service: CardDatabaseService, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force) {
            val lastCheck = userPreferences.lastUpdateCheck.first()
            if (lastCheck != null && now - lastCheck < Constants.Api.UPDATE_CHECK_TTL_MS) return
        }

        _state.value = BootstrapState.CheckingForUpdates
        // A failed network sync must not block launch — the bundled catalogue is
        // already usable, so this degrades to "you have slightly stale data".
        runCatching {
            service.syncDelta { done, total -> _state.value = BootstrapState.Syncing(done, total) }
        }.onSuccess { report ->
            userPreferences.setLastUpdateCheck(now)
            if (report.hasChanges) userPreferences.setLastDatabaseSync(now)
        }.onFailure { error ->
            Log.database.w(error, "Delta sync failed; continuing with the local catalogue")
        }
    }

    private fun fail(error: Throwable) {
        Log.database.e(error, "Bootstrap failed")
        _state.value = BootstrapState.Failed(error.message ?: "Could not load the card database")
    }

    private fun service(source: RiftboundDataSource) =
        CardDatabaseService(db, cardDao, maintenanceDao, source, io)
}
