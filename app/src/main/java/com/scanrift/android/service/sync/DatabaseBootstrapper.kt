package com.scanrift.android.service.sync

import androidx.annotation.VisibleForTesting
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
import com.scanrift.android.domain.model.CompetitiveRegion
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
 * against. What remains, on every launch:
 *
 * 1. Empty catalogue → seed from the bundled JSON and store the seed's `/version` tag.
 * 2. Ask the API for its `/version` tag. Same as stored → nothing at all is written.
 *    Different or never stored → full refresh (every set, every card, legality), and
 *    the new tag is stored only once all of it succeeded. A failure anywhere, including
 *    the version check itself, is logged and leaves the old tag, so the next launch
 *    retries. See [CatalogueTagSync].
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
    private val mutex = Mutex()

    /** Idempotent: safe to call from every Activity creation. Runs once per process. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            runCatching { runLaunch() }.onFailure { failure -> _state.value = failed(failure) }
        }
    }

    /** The launch sequence itself, callable directly so tests can await it. */
    @VisibleForTesting
    internal suspend fun runLaunch() = mutex.withLock { bootstrap() }

    /** Settings, empty catalogue: load the bundled seed. */
    fun loadCardDatabase() = runManually {
        _state.value = BootstrapState.Syncing(0, 0)
        BootstrapState.Updated(seedFromBundle())
    }

    /** Settings: a forced full refresh, whatever the tags say. */
    fun checkForUpdates() = runManually {
        _state.value = BootstrapState.CheckingForUpdates
        when (val outcome = syncCatalogue(force = true)) {
            is CatalogueSyncOutcome.Refreshed -> BootstrapState.Updated(outcome.cardCount)
            is CatalogueSyncOutcome.UpToDate -> BootstrapState.UpToDate
            is CatalogueSyncOutcome.CheckFailed -> throw outcome.error
            is CatalogueSyncOutcome.RefreshFailed -> throw outcome.error
        }
    }

    /**
     * Re-applies `/legality` for the current competitive region. Called when the
     * region setting changes, so the ban list follows it at once. The catalogue tag is
     * untouched: it covers every region's legality, so it has not changed.
     */
    fun refreshLegality() {
        scope.launch {
            mutex.withLock {
                runCatching { applyLegality(service(remoteSource)) }
                    .onFailure { Log.database.w(it, "Legality refresh failed; keeping the current ban list") }
            }
        }
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
        if (cardDao.count() == 0) {
            _state.value = BootstrapState.SeedingBundle
            seedFromBundle()
        }

        _state.value = BootstrapState.CheckingForUpdates
        // Never blocks launch and never surfaces an error: the local catalogue is
        // already usable, so a failure only means "slightly stale until next launch".
        when (val outcome = syncCatalogue(force = false)) {
            is CatalogueSyncOutcome.UpToDate -> Log.database.i("Catalogue up to date (%s)", outcome.tag)
            is CatalogueSyncOutcome.Refreshed -> Log.database.i("Catalogue refreshed to %s", outcome.tag)
            is CatalogueSyncOutcome.CheckFailed ->
                Log.database.w(outcome.error, "Version check failed; keeping the local catalogue")
            is CatalogueSyncOutcome.RefreshFailed ->
                Log.database.w(outcome.error, "Catalogue refresh failed; keeping the old tag to retry next launch")
        }
        _state.value = BootstrapState.Idle

        listRepository.reconcileWishlist()
    }

    /**
     * Seeds from the bundle and records the bundle's tag, so the version check right
     * after compares the live API against what the seed actually holds.
     */
    private suspend fun seedFromBundle(): Int {
        val count = service(bundledSource).fetchAndStoreCards { done, total ->
            _state.value = BootstrapState.Syncing(done, total)
        }
        bundledSource.fetchVersion()?.tag?.let { userPreferences.setCatalogueTag(it) }
        userPreferences.setLastDatabaseSync(System.currentTimeMillis())
        // The seed carries the global ban list. A restored install (Auto Backup brings
        // the region preference back) may need another one, and a matching tag would
        // otherwise never re-apply it.
        if (userPreferences.competitiveRegion.first() != CompetitiveRegion.GLOBAL) {
            runCatching { applyLegality(service(remoteSource)) }
                .onFailure { Log.database.w(it, "Legality refresh after seeding failed") }
        }
        return count
    }

    private suspend fun syncCatalogue(force: Boolean): CatalogueSyncOutcome {
        val remote = service(remoteSource)
        return CatalogueTagSync(
            fetchRemoteTag = { remoteSource.fetchVersion()?.tag ?: error("The catalogue source has no /version") },
            readStoredTag = { userPreferences.catalogueTag.first() },
            writeStoredTag = { userPreferences.setCatalogueTag(it) },
            refresh = {
                _state.value = BootstrapState.Syncing(0, 0)
                val region = userPreferences.competitiveRegion.first().value
                remote.refreshCatalogue(region) { done, total -> _state.value = BootstrapState.Syncing(done, total) }
                    .also { userPreferences.setLastDatabaseSync(System.currentTimeMillis()) }
            },
        ).run(force)
    }

    private suspend fun applyLegality(service: CardDatabaseService) {
        service.refreshLegality(userPreferences.competitiveRegion.first().value)
    }

    private fun failed(error: Throwable): BootstrapState {
        Log.database.e(error, "Card database sync failed")
        return BootstrapState.Failed(error.message ?: "Could not load the card database")
    }

    private fun service(source: RiftboundDataSource) =
        CardDatabaseService(db, cardDao, maintenanceDao, source, io)
}
