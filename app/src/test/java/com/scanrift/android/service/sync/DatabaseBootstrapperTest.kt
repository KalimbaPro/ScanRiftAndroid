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
    private lateinit var prefs: UserPreferences
    private val remote = FakeRiftboundDataSource()
    private val bundled = FakeRiftboundDataSource()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScanRiftDatabase::class.java,
        ).allowMainThreadQueries().build()
        prefs = UserPreferences(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = db.close()

    private fun bootstrapper(remoteSource: RiftboundDataSource = remote) = DatabaseBootstrapper(
        db = db,
        cardDao = db.cardDao(),
        maintenanceDao = db.maintenanceDao(),
        bundledSource = bundled,
        remoteSource = remoteSource,
        listRepository = CardListRepository(db.cardListDao(), Dispatchers.Unconfined),
        userPreferences = prefs,
        io = Dispatchers.Unconfined,
    )

    private suspend fun DatabaseBootstrapper.settledState() =
        state.first { it !is BootstrapState.Idle && !it.isLoading }

    /** A catalogue already holding the two Akali printings, synced to [tag]. */
    private suspend fun populated(tag: String) {
        db.cardDao().upsertAll(AkaliFixture.decode().filter { it.id.startsWith("final") }.map { it.toEntity() })
        prefs.setCatalogueTag(tag)
    }

    @Test
    fun `a launch with the same tag fetches and writes nothing`() = runTest {
        populated("tag-a")
        remote.versionTag = "tag-a"
        remote.cards = AkaliFixture.decode().map { it.copy(name = "changed") }

        bootstrapper().runLaunch()

        assertThat(remote.fetchAllCalls).isEqualTo(0)
        assertThat(remote.legalityCalls).isEqualTo(0)
        assertThat(db.cardDao().getAll().map { it.name }).doesNotContain("changed")
        assertThat(prefs.catalogueTag.first()).isEqualTo("tag-a")
    }

    @Test
    fun `a launch with a new tag refreshes everything and stores the tag`() = runTest {
        populated("tag-a")
        remote.versionTag = "tag-b"
        remote.cards = AkaliFixture.decode().map { it.copy(name = "${it.name} v2") }

        bootstrapper().runLaunch()

        assertThat(remote.fetchAllCalls).isEqualTo(1)
        assertThat(remote.legalityCalls).isEqualTo(1)
        assertThat(db.cardDao().getAll().map { it.name }).containsExactly(
            "Akali, Deadly Weapon v2", "Akali, Deadly Weapon (Alternate Art) v2",
        )
        assertThat(prefs.catalogueTag.first()).isEqualTo("tag-b")
    }

    @Test
    fun `a failed refresh on launch keeps the old tag and stays silent`() = runTest {
        populated("tag-a")
        remote.versionTag = "tag-b"
        val failing = object : RiftboundDataSource by remote {
            override suspend fun fetchAllCards(onProgress: ((Int, Int) -> Unit)?) = error("HTTP error: 503")
        }
        val bootstrapper = bootstrapper(failing)

        bootstrapper.runLaunch()

        assertThat(prefs.catalogueTag.first()).isEqualTo("tag-a")
        assertThat(bootstrapper.state.value).isEqualTo(BootstrapState.Idle)
        assertThat(db.cardDao().count()).isEqualTo(2)
    }

    @Test
    fun `first install stores the seed's tag, so a matching api needs no refresh`() = runTest {
        bundled.cards = AkaliFixture.decode()
        bundled.versionTag = "seed-tag"
        remote.versionTag = "seed-tag"

        bootstrapper().runLaunch()

        assertThat(db.cardDao().count()).isEqualTo(2)
        assertThat(prefs.catalogueTag.first()).isEqualTo("seed-tag")
        assertThat(remote.fetchAllCalls).isEqualTo(0)
    }

    @Test
    fun `first install refreshes when the api has moved past the seed`() = runTest {
        bundled.cards = AkaliFixture.decode()
        bundled.versionTag = "seed-tag"
        remote.versionTag = "live-tag"
        remote.cards = AkaliFixture.decode()

        bootstrapper().runLaunch()

        assertThat(remote.fetchAllCalls).isEqualTo(1)
        assertThat(prefs.catalogueTag.first()).isEqualTo("live-tag")
    }

    @Test
    fun `the manual refresh is forced even when the tags match`() = runTest {
        populated("tag-a")
        remote.versionTag = "tag-a"
        remote.cards = AkaliFixture.decode()
        val bootstrapper = bootstrapper()

        bootstrapper.checkForUpdates()

        assertThat(bootstrapper.settledState()).isEqualTo(BootstrapState.Updated(2))
        assertThat(remote.fetchAllCalls).isEqualTo(1)
        bootstrapper.resetSyncState()
        assertThat(bootstrapper.state.value).isEqualTo(BootstrapState.Idle)
    }

    @Test
    fun `a failed manual refresh surfaces its message until dismissed`() = runTest {
        populated("tag-a")
        remote.versionTag = "tag-b"
        val failing = object : RiftboundDataSource by remote {
            override suspend fun fetchSets(): List<RiftboundSetDto> = error("HTTP error: 503")
        }
        val bootstrapper = bootstrapper(failing)

        bootstrapper.checkForUpdates()

        assertThat(bootstrapper.settledState()).isEqualTo(BootstrapState.Failed("HTTP error: 503"))
        assertThat(prefs.catalogueTag.first()).isEqualTo("tag-a")
        bootstrapper.resetSyncState()
        assertThat(bootstrapper.state.value).isInstanceOf(BootstrapState.Failed::class.java)
        bootstrapper.dismissError()
        assertThat(bootstrapper.state.value).isEqualTo(BootstrapState.Idle)
    }
}
