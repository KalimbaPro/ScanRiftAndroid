package com.scanrift.android.service.sync

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.entity.CardIdentity
import com.scanrift.android.fake.AkaliFixture
import org.junit.Test

/** Layer 1 de-duplication: pure, no database. Ports the DTO half of the iOS suite. */
class CardDedupTest {

    @Test
    fun `four api records collapse to one per riftbound id`() {
        val deduped = CardDedup.dedupedByRiftboundId(AkaliFixture.decode())

        assertThat(deduped).hasSize(2)
        assertThat(deduped.map { it.riftboundId })
            .containsExactly("ven-021-166", "ven-021a-166")
    }

    @Test
    fun `the finalised record wins over the preliminary stub`() {
        val deduped = CardDedup.dedupedByRiftboundId(AkaliFixture.decode()).associateBy { it.riftboundId }

        // The stub has a null clean_name; the finalised record carries one.
        assertThat(deduped.getValue("ven-021-166").id).isEqualTo("final-021")
        assertThat(deduped.getValue("ven-021a-166").id).isEqualTo("final-021a")
    }

    @Test
    fun `clean name beats a newer updated_on`() {
        val dtos = AkaliFixture.decode()
        val stub = dtos.first { it.id == "stub-021" }
        val finalised = dtos.first { it.id == "final-021" }

        // Give the stub the newer timestamp; clean_name should still decide.
        val newerStub = stub.copy(metadata = stub.metadata.copy(updatedOn = "2027-01-01T00:00:00+00:00"))
        val result = CardDedup.dedupedByRiftboundId(listOf(finalised, newerStub))

        assertThat(result.single().id).isEqualTo("final-021")
    }

    @Test
    fun `when both have clean names the newer updated_on wins`() {
        val dtos = AkaliFixture.decode()
        val older = dtos.first { it.id == "final-021" }
        val newer = older.copy(
            id = "final-021-v2",
            metadata = older.metadata.copy(updatedOn = "2027-01-01T00:00:00+00:00"),
        )

        assertThat(CardDedup.dedupedByRiftboundId(listOf(older, newer)).single().id)
            .isEqualTo("final-021-v2")
    }

    @Test
    fun `an exact tie keeps the incumbent`() {
        // Swift only replaces when isMoreComplete returns true, so equal records leave
        // the first one standing. Order stability matters for deterministic upserts.
        val first = AkaliFixture.decode().first { it.id == "final-021" }
        val second = first.copy(id = "same-but-later")

        assertThat(CardDedup.dedupedByRiftboundId(listOf(first, second)).single().id)
            .isEqualTo("final-021")
    }

    @Test
    fun `output preserves first-appearance order of each group`() {
        val dtos = AkaliFixture.decode()
        // stub-021a appears before final-021 in this ordering, so its group emits first.
        val reordered = listOf(
            dtos.first { it.id == "stub-021a" },
            dtos.first { it.id == "final-021" },
            dtos.first { it.id == "final-021a" },
            dtos.first { it.id == "stub-021" },
        )

        assertThat(CardDedup.dedupedByRiftboundId(reordered).map { it.riftboundId })
            .containsExactly("ven-021a-166", "ven-021-166").inOrder()
    }

    @Test
    fun `a missing updated_on sorts as the oldest possible`() {
        val dtos = AkaliFixture.decode()
        val dated = dtos.first { it.id == "stub-021" }
        val undated = dated.copy(id = "no-date", metadata = dated.metadata.copy(updatedOn = null))

        assertThat(CardDedup.dedupedByRiftboundId(listOf(undated, dated)).single().id)
            .isEqualTo("stub-021")
    }

    @Test
    fun `a single element list is returned untouched`() {
        val one = AkaliFixture.decode().take(1)
        assertThat(CardDedup.dedupedByRiftboundId(one)).isEqualTo(one)
        assertThat(CardDedup.dedupedByRiftboundId(emptyList())).isEmpty()
    }

    // ── Layer 2: local row selection ─────────────────────────────────────────

    private fun identity(id: String, updatedOn: Long?, cleanName: String = "x") =
        CardIdentity(id = id, riftboundId = "ven-021-166", sourceSetId = "VEN", updatedOn = updatedOn, cleanName = cleanName)

    @Test
    fun `a referenced row beats an unreferenced newer one`() {
        val stale = identity("owned-stub", updatedOn = 1_000L)
        val fresh = identity("unowned-final", updatedOn = 9_000L)

        val keeper = CardDedup.pickKeeper(listOf(fresh, stale), referencedIds = setOf("owned-stub"))

        // Losing the row the user owns would orphan their collection entry.
        assertThat(keeper.id).isEqualTo("owned-stub")
    }

    @Test
    fun `with neither referenced the newest row wins`() {
        val older = identity("a", updatedOn = 1_000L)
        val newer = identity("b", updatedOn = 9_000L)

        assertThat(CardDedup.pickKeeper(listOf(older, newer), emptySet()).id).isEqualTo("b")
        assertThat(CardDedup.pickKeeper(listOf(newer, older), emptySet()).id).isEqualTo("b")
    }

    @Test
    fun `with both referenced the newest row still wins`() {
        val older = identity("a", updatedOn = 1_000L)
        val newer = identity("b", updatedOn = 9_000L)

        assertThat(CardDedup.pickKeeper(listOf(older, newer), setOf("a", "b")).id).isEqualTo("b")
    }

    @Test
    fun `a tie keeps the first row`() {
        // Swift's max(by:) keeps the first element on a tie, so reduce must too.
        val first = identity("first", updatedOn = 5_000L)
        val second = identity("second", updatedOn = 5_000L)

        assertThat(CardDedup.pickKeeper(listOf(first, second), emptySet()).id).isEqualTo("first")
    }

    @Test
    fun `a null updated_on loses to any timestamp`() {
        val undated = identity("undated", updatedOn = null)
        val dated = identity("dated", updatedOn = 1L)

        assertThat(CardDedup.pickKeeper(listOf(undated, dated), emptySet()).id).isEqualTo("dated")
    }
}
