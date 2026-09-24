package com.scanrift.android.service.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.prefs.UserPreferences
import com.scanrift.android.data.remote.datasource.RiftboundDataSource
import com.scanrift.android.data.remote.dto.RiftboundSetDto
import com.scanrift.android.data.repository.CardListRepository
import com.scanrift.android.fake.AkaliFixture
import com.scanrift.android.fake.FakeRiftboundDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseBootstrapperTest {

    private lateinit var db: ScanRiftDatabase
    private val remote = FakeRiftboundDataSource()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScanRiftDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun bootstrapper(remoteSource: RiftboundDataSource = remote) = DatabaseBootstrapper(
        db = db,
        cardDao = db.cardDao(),
        maintenanceDao = db.maintenanceDao(),
        syncedSetDao = db.syncedSetDao(),
        bundledSource = FakeRiftboundDataSource(),
        remoteSource = remoteSource,
        listRepository = CardListRepository(db.cardListDao(), Dispatchers.Unconfined),
        userPreferences = UserPreferences(ApplicationProvider.getApplicationContext()),
        io = Dispatchers.Unconfined,
    )

    private suspend fun DatabaseBootstrapper.settledState() =
        state.first { it !is BootstrapState.Idle && !it.isLoading }

    @Test
    fun `checking for updates reports how many cards changed then settles back to idle`() = runTest {
        remote.cards = AkaliFixture.decode()
        remote.sets = listOf(RiftboundSetDto(id = "s-ven", name = "Vendetta", setId = "VEN", cardCount = 4))
        val bootstrapper = bootstrapper()

        bootstrapper.checkForUpdates()

        assertThat(bootstrapper.settledState()).isEqualTo(BootstrapState.Updated(2))
        bootstrapper.resetSyncState()
        assertThat(bootstrapper.state.value).isEqualTo(BootstrapState.Idle)
    }

    @Test
    fun `checking for updates with nothing new reports up to date`() = runTest {
        val bootstrapper = bootstrapper()

        bootstrapper.checkForUpdates()

        assertThat(bootstrapper.settledState()).isEqualTo(BootstrapState.UpToDate)
    }

    @Test
    fun `a failed check surfaces its message until dismissed`() = runTest {
        val failing = object : RiftboundDataSource by remote {
            override suspend fun fetchSets(): List<RiftboundSetDto> = error("HTTP error: 503")
        }
        val bootstrapper = bootstrapper(failing)

        bootstrapper.checkForUpdates()

        assertThat(bootstrapper.settledState()).isEqualTo(BootstrapState.Failed("HTTP error: 503"))
        bootstrapper.resetSyncState()
        assertThat(bootstrapper.state.value).isInstanceOf(BootstrapState.Failed::class.java)
        bootstrapper.dismissError()
        assertThat(bootstrapper.state.value).isEqualTo(BootstrapState.Idle)
    }
}
