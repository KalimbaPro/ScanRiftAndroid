package com.scanrift.android.service.sync

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The launch freshness rule: plain JUnit, no Room, no network, no DataStore. */
class CatalogueTagSyncTest {

    private class Harness(
        var remoteTag: String? = "new",
        var storedTag: String? = "old",
        var refreshError: Throwable? = null,
        var checkError: Throwable? = null,
    ) {
        var refreshes = 0
        val writes = mutableListOf<String>()

        val sync = CatalogueTagSync(
            fetchRemoteTag = { checkError?.let { throw it }; remoteTag ?: error("no tag") },
            readStoredTag = { storedTag },
            writeStoredTag = { writes += it; storedTag = it },
            refresh = {
                refreshes++
                refreshError?.let { throw it }
                1708
            },
        )
    }

    @Test
    fun `same tag does nothing and writes nothing`() = runTest {
        val h = Harness(remoteTag = "e25fc029cc4e97b9", storedTag = "e25fc029cc4e97b9")

        val outcome = h.sync.run()

        assertThat(outcome).isEqualTo(CatalogueSyncOutcome.UpToDate("e25fc029cc4e97b9"))
        assertThat(h.refreshes).isEqualTo(0)
        assertThat(h.writes).isEmpty()
    }

    @Test
    fun `different tag refreshes then stores the new tag`() = runTest {
        val h = Harness(remoteTag = "new", storedTag = "old")

        val outcome = h.sync.run()

        assertThat(outcome).isEqualTo(CatalogueSyncOutcome.Refreshed("new", 1708))
        assertThat(h.refreshes).isEqualTo(1)
        assertThat(h.writes).containsExactly("new")
    }

    @Test
    fun `missing stored tag refreshes then stores the tag`() = runTest {
        val h = Harness(remoteTag = "new", storedTag = null)

        val outcome = h.sync.run()

        assertThat(outcome).isEqualTo(CatalogueSyncOutcome.Refreshed("new", 1708))
        assertThat(h.storedTag).isEqualTo("new")
    }

    @Test
    fun `a failed refresh keeps the old tag so the next launch retries`() = runTest {
        val failure = IOException("page 7 timed out")
        val h = Harness(remoteTag = "new", storedTag = "old", refreshError = failure)

        val outcome = h.sync.run()

        assertThat(outcome).isEqualTo(CatalogueSyncOutcome.RefreshFailed(failure))
        assertThat(h.writes).isEmpty()
        assertThat(h.storedTag).isEqualTo("old")

        // Next launch: same remote tag, still different from the stored one, so it retries.
        h.refreshError = null
        assertThat(h.sync.run()).isEqualTo(CatalogueSyncOutcome.Refreshed("new", 1708))
        assertThat(h.refreshes).isEqualTo(2)
        assertThat(h.storedTag).isEqualTo("new")
    }

    @Test
    fun `a failed refresh with no stored tag stores nothing`() = runTest {
        val h = Harness(remoteTag = "new", storedTag = null, refreshError = IOException("offline"))

        h.sync.run()

        assertThat(h.storedTag).isNull()
    }

    @Test
    fun `a failed version check is silent and touches nothing`() = runTest {
        val failure = IOException("no network")
        val h = Harness(storedTag = "old", checkError = failure)

        val outcome = h.sync.run()

        assertThat(outcome).isEqualTo(CatalogueSyncOutcome.CheckFailed(failure))
        assertThat(h.refreshes).isEqualTo(0)
        assertThat(h.writes).isEmpty()
    }

    @Test
    fun `a version check timeout counts as a failed check, not a cancellation`() = runTest {
        val h = Harness(storedTag = "old")
        val timingOut = CatalogueTagSync(
            fetchRemoteTag = { kotlinx.coroutines.withTimeout(1) { kotlinx.coroutines.awaitCancellation() } },
            readStoredTag = { h.storedTag },
            writeStoredTag = { h.storedTag = it },
            refresh = { 0 },
        )

        assertThat(timingOut.run()).isInstanceOf(CatalogueSyncOutcome.CheckFailed::class.java)
        assertThat(h.storedTag).isEqualTo("old")
    }

    @Test
    fun `forcing refreshes even when the tags match`() = runTest {
        val h = Harness(remoteTag = "same", storedTag = "same")

        val outcome = h.sync.run(force = true)

        assertThat(outcome).isEqualTo(CatalogueSyncOutcome.Refreshed("same", 1708))
        assertThat(h.refreshes).isEqualTo(1)
    }

    @Test
    fun `forcing still refreshes when the version check fails, but stores no tag`() = runTest {
        val h = Harness(storedTag = "old", checkError = IOException("version endpoint down"))

        val outcome = h.sync.run(force = true)

        assertThat(outcome).isEqualTo(CatalogueSyncOutcome.Refreshed(null, 1708))
        assertThat(h.writes).isEmpty()
        assertThat(h.storedTag).isEqualTo("old")
    }
}
