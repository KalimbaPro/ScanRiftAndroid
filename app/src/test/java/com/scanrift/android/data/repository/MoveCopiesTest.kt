package com.scanrift.android.data.repository

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.dao.CollectionEntryDao
import com.scanrift.android.data.local.dao.OwnedQuantity
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.CollectionEntryWithCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The scanner's auto-add re-key: [CollectionEntryDao.moveCopies] is a default method,
 * so its logic runs here against an in-memory fake with no Room or Robolectric.
 */
class MoveCopiesTest {

    private class FakeEntryDao : CollectionEntryDao {
        val rows = mutableListOf<CollectionEntryEntity>()
        private var nextId = 1L

        override suspend fun insert(entry: CollectionEntryEntity): Long {
            check(findEntry(entry.cardId!!, entry.isFoil, entry.condition, entry.language) == null) { "unique index" }
            val row = entry.copy(id = nextId++)
            rows += row
            return row.id
        }
        override suspend fun upsertAll(entries: List<CollectionEntryEntity>) = error("unused")
        override suspend fun update(entry: CollectionEntryEntity) {
            rows[rows.indexOfFirst { it.id == entry.id }] = entry
        }
        override suspend fun delete(entry: CollectionEntryEntity) {
            rows.removeAll { it.id == entry.id }
        }
        override fun observeAllWithCards(): Flow<List<CollectionEntryWithCard>> = emptyFlow()
        override suspend fun getAllWithCards(): List<CollectionEntryWithCard> = emptyList()
        override suspend fun getAll(): List<CollectionEntryEntity> = rows.toList()
        override suspend fun getForCard(cardId: String) = rows.filter { it.cardId == cardId }
        override suspend fun findEntry(cardId: String, isFoil: Boolean, condition: String, language: String) =
            rows.firstOrNull { it.cardId == cardId && it.isFoil == isFoil && it.condition == condition && it.language == language }
        override fun observeTotalCardCount(): Flow<Int?> = emptyFlow()
        override fun observeUniqueCardCount(): Flow<Int> = emptyFlow()
        override fun observeOwnedQuantities(): Flow<List<OwnedQuantity>> = emptyFlow()
        override suspend fun deleteAll() = rows.clear()
        override suspend fun deleteOrphaned(): Int = 0
    }

    private val nm = "Near Mint"

    private fun FakeEntryDao.quantities() = rows.associate { (it.cardId to it.isFoil) to it.quantity }

    @Test fun `moves the scanned copies to the new printing`() = runTest {
        val dao = FakeEntryDao()
        dao.adjustQuantity("regular", false, nm, "fr", 2, now = 1)

        val moved = dao.moveCopies("regular", false, "alt", false, nm, "fr", quantity = 2, now = 2)

        assertThat(moved).isEqualTo(2)
        assertThat(dao.quantities()).containsExactly(("alt" to false), 2)
        assertThat(dao.rows.single().language).isEqualTo("fr")
    }

    @Test fun `copies owned before the scan stay on the old printing`() = runTest {
        val dao = FakeEntryDao()
        dao.adjustQuantity("regular", false, nm, "en", 3, now = 1) // 2 owned + 1 just scanned

        dao.moveCopies("regular", false, "alt", false, nm, "en", quantity = 1, now = 2)

        assertThat(dao.quantities()).containsExactly(("regular" to false), 2, ("alt" to false), 1)
    }

    @Test fun `merges into an existing stack of the new printing`() = runTest {
        val dao = FakeEntryDao()
        dao.adjustQuantity("regular", false, nm, "en", 1, now = 1)
        dao.adjustQuantity("showcase", true, nm, "en", 4, now = 1)

        dao.moveCopies("regular", false, "showcase", true, nm, "en", quantity = 1, now = 2)

        assertThat(dao.quantities()).containsExactly(("showcase" to true), 5)
    }

    @Test fun `moves no more than the source stack still holds`() = runTest {
        val dao = FakeEntryDao()
        dao.adjustQuantity("regular", false, nm, "en", 1, now = 1)
        assertThat(dao.moveCopies("regular", false, "alt", false, nm, "en", quantity = 3, now = 2)).isEqualTo(1)
        assertThat(dao.moveCopies("gone", false, "alt", false, nm, "en", quantity = 1, now = 2)).isEqualTo(0)
        assertThat(dao.quantities()).containsExactly(("alt" to false), 1)
    }
}
