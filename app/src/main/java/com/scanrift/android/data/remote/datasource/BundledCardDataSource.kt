package com.scanrift.android.data.remote.datasource

import android.content.Context
import com.scanrift.android.R
import com.scanrift.android.data.remote.dto.CardDto
import com.scanrift.android.data.remote.dto.PaginatedCardsResponse
import com.scanrift.android.data.remote.dto.RiftboundSetDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * The offline cold-start seed: `res/raw/cards.json`, 1064 cards.
 *
 * This is a snapshot, not the whole catalogue — the live API already serves 1451 cards
 * across 7 sets, including all of Vendetta. So seeding from here must always be
 * followed by a forced delta sync, or the app is missing a quarter of the game.
 */
class BundledCardDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) : RiftboundDataSource {

    @OptIn(ExperimentalSerializationApi::class)
    private fun readBundle(): List<CardDto> =
        context.resources.openRawResource(R.raw.cards).use { stream ->
            // Streamed rather than read into a 1.7MB String first.
            json.decodeFromStream<PaginatedCardsResponse>(stream).items
        }

    override suspend fun fetchAllCards(onProgress: ((Int, Int) -> Unit)?): List<CardDto> {
        onProgress?.invoke(1, 1)
        return readBundle()
    }

    override suspend fun fetchCardsInSet(setId: String, onProgress: ((Int, Int) -> Unit)?): List<CardDto> {
        onProgress?.invoke(1, 1)
        return readBundle().filter { it.set.setId == setId }
    }

    /** Derived by grouping the bundle on the raw set id. */
    override suspend fun fetchSets(): List<RiftboundSetDto> =
        readBundle()
            .groupBy { it.set.setId }
            .map { (setId, cards) ->
                RiftboundSetDto(
                    id = setId,
                    name = cards.first().set.label,
                    setId = setId,
                    cardCount = cards.size,
                )
            }
}
