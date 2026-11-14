package com.scanrift.android.data.remote.datasource

import android.content.Context
import com.scanrift.android.R
import com.scanrift.android.data.remote.dto.CardDto
import com.scanrift.android.data.remote.dto.PaginatedCardsResponse
import com.scanrift.android.data.remote.dto.RiftboundSetDto
import com.scanrift.android.data.remote.dto.VersionDto
import com.scanrift.android.di.ApiJson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * The offline cold-start seed: `res/raw/cards.json`.
 *
 * A snapshot of the own API, carrying the `/version` tag it was built from. The
 * bootstrapper stores that tag after seeding, so the first launch refreshes from the
 * network only if the live catalogue has moved on since the APK was built.
 */
class BundledCardDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApiJson private val json: Json,
) : RiftboundDataSource {

    /** Remembered from the last read, so seeding then asking for the tag parses once. */
    @Volatile private var bundledTag: String? = null

    @OptIn(ExperimentalSerializationApi::class)
    private fun readBundle(): List<CardDto> =
        context.resources.openRawResource(R.raw.cards).use { stream ->
            // Streamed rather than read into a 1.7MB String first.
            json.decodeFromStream<PaginatedCardsResponse>(stream)
        }.also { bundledTag = it.tag }.items

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

    override suspend fun fetchVersion(): VersionDto? {
        val tag = bundledTag ?: readBundle().let { bundledTag }
        return tag?.let { VersionDto(tag = it) }
    }
}
